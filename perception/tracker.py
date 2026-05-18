"""
ByteTrack v2 — Multi-Object Tracking
=======================================
A clean, production-grade implementation of ByteTrack.

论文: Zhang et al., "ByteTrack: Multi-Object Tracking by Associating Every
      Detection Box", ECCV 2024
核心创新: 保留低置信度检测框，在第二次关联中与未匹配的轨迹匹配，
         显著降低因遮挡导致的 ID Switch

算法流程 (每帧):
  1. Kalman 预测所有轨迹的新位置
  2. 第一次关联: 高置信度检测 ↔ 所有轨迹 (Hungarian + IoU)
  3. 第二次关联: 低置信度检测 ↔ 未匹配轨迹 (更宽松的 IoU 阈值)
  4. 从剩余高置信度检测创建新轨迹
  5. 删除超过 track_buffer 帧未更新的轨迹

面试考点:
  - 为什么 ByteTrack 优于 SORT/DeepSORT: 利用低置信度框恢复遮挡目标
  - Hungarian 算法: O(n³) 的最优匹配，scipy.optimize.linear_sum_assignment
  - Kalman 滤波: 8 状态 (x,y,w,h,vx,vy,vw,vh)，匀速运动模型
"""

import time
from dataclasses import dataclass, field
from typing import List, Optional, Tuple
import numpy as np
import logging
from scipy.optimize import linear_sum_assignment

log = logging.getLogger(__name__)


@dataclass
class Track:
    """Single tracked object with Kalman state estimation.

    State vector (8D): [cx, cy, w, h, vx, vy, vw, vh]
      cx,cy = bbox center; w,h = width/height; v* = velocity

    Attributes:
        track_id: Unique track identifier
        state: Kalman filter state vector (8,)
        covariance: Kalman filter covariance matrix (8, 8)
        class_id: Object class
        confidence: Latest detection confidence
        age: Total frames since creation
        hits: Frames with successful association
        time_since_update: Frames since last detection match
        is_activated: True after minimum hits threshold
    """
    track_id: int
    state: np.ndarray            # [cx, cy, w, h, vx, vy, vw, vh]
    covariance: np.ndarray       # (8, 8) covariance matrix
    class_id: int = -1
    confidence: float = 0.0

    # Lifecycle
    age: int = 0
    hits: int = 0                 # Consecutive detections
    time_since_update: int = 0    # Frames since last match
    is_activated: bool = False

    # Activation threshold
    _min_hits: int = field(default=3, repr=False)

    # Kalman constants
    _Q: np.ndarray = field(init=False, repr=False)  # Process noise
    _R: np.ndarray = field(init=False, repr=False)  # Measurement noise
    _F: np.ndarray = field(init=False, repr=False)  # State transition
    _H: np.ndarray = field(init=False, repr=False)  # Measurement function
    _I: np.ndarray = field(init=False, repr=False)  # Identity

    def __post_init__(self):
        """Initialize Kalman filter matrices (constant velocity model)."""
        dt = 1.0  # 1 frame time step
        self._I = np.eye(8)

        # State transition matrix F (8x8)
        # x(t+1) = x(t) + vx(t)*dt
        self._F = np.eye(8)
        for i in range(4):
            self._F[i, i + 4] = dt

        # Measurement matrix H (4x8) — we only measure position [cx,cy,w,h]
        self._H = np.zeros((4, 8))
        self._H[0, 0] = 1  # cx
        self._H[1, 1] = 1  # cy
        self._H[2, 2] = 1  # w
        self._H[3, 3] = 1  # h

        # Process noise Q — higher for position, lower for velocity
        self._Q = np.eye(8)
        self._Q[:4, :4] *= 0.01   # Position noise
        self._Q[4:, 4:] *= 0.001  # Velocity noise (smoother)

        # Measurement noise R — observation uncertainty
        self._R = np.eye(4) * 0.1

    def predict(self):
        """Kalman predict step — estimate next state."""
        self.state = self._F @ self.state
        self.covariance = self._F @ self.covariance @ self._F.T + self._Q
        self.age += 1
        self.time_since_update += 1

    def update(self, detection: np.ndarray):
        """Kalman update step — correct state with measurement.

        Args:
            detection: [x1, y1, x2, y2, confidence, class_id] or [cx,cy,w,h]
        """
        # Convert [x1,y1,x2,y2] → [cx, cy, w, h]
        if len(detection) >= 4:
            x1, y1 = detection[0], detection[1]
            x2 = detection[2] if len(detection) > 2 else x1 + 1
            y2 = detection[3] if len(detection) > 3 else y1 + 1
            z = np.array([(x1 + x2) / 2, (y1 + y2) / 2, x2 - x1, y2 - y1])
        else:
            z = detection[:4]

        # Innovation: y = z - Hx
        y = z - self._H @ self.state

        # Innovation covariance: S = HPHᵀ + R
        S = self._H @ self.covariance @ self._H.T + self._R

        # Kalman gain: K = PHᵀS⁻¹
        K = self.covariance @ self._H.T @ np.linalg.inv(S)

        # Update
        self.state = self.state + K @ y
        self.covariance = (self._I - K @ self._H) @ self.covariance

        # Update metadata
        if len(detection) >= 5:
            self.confidence = detection[4]
        if len(detection) >= 6:
            self.class_id = int(detection[5])

        self.hits += 1
        self.time_since_update = 0
        if self.hits >= self._min_hits:
            self.is_activated = True

    @property
    def bbox(self) -> Tuple[float, float, float, float]:
        """Current bounding box estimate [x1, y1, x2, y2]."""
        cx, cy, w, h = self.state[0], self.state[1], self.state[2], self.state[3]
        return (cx - w / 2, cy - h / 2, cx + w / 2, cy + h / 2)

    @property
    def center(self) -> Tuple[float, float]:
        """Current center estimate."""
        return (float(self.state[0]), float(self.state[1]))

    def get_state(self) -> dict:
        """Export track state for API responses."""
        x1, y1, x2, y2 = self.bbox
        return {
            "track_id": self.track_id,
            "bbox": [round(x1, 1), round(y1, 1), round(x2, 1), round(y2, 1)],
            "center": [round(self.state[0], 1), round(self.state[1], 1)],
            "class_id": self.class_id,
            "confidence": round(self.confidence, 4),
            "age": self.age,
            "hits": self.hits,
            "active": self.is_activated,
        }


def _iou(bbox1, bbox2):
    """Compute Intersection over Union between two bounding boxes.

    Args:
        bbox1, bbox2: [x1, y1, x2, y2] arrays
    Returns:
        IoU ∈ [0, 1]
    """
    x1 = max(bbox1[0], bbox2[0])
    y1 = max(bbox1[1], bbox2[1])
    x2 = min(bbox1[2], bbox2[2])
    y2 = min(bbox1[3], bbox2[3])
    if x2 <= x1 or y2 <= y1:
        return 0.0
    inter = (x2 - x1) * (y2 - y1)
    area1 = (bbox1[2] - bbox1[0]) * (bbox1[3] - bbox1[1])
    area2 = (bbox2[2] - bbox2[0]) * (bbox2[3] - bbox2[1])
    return inter / (area1 + area2 - inter + 1e-7)


class ByteTrack:
    """ByteTrack v2 multi-object tracker.

    核心创新 (Byte 关联策略):
    1. 高置信度检测先匹配 — 确保可靠跟踪
    2. 低置信度检测后匹配 — 恢复被遮挡/模糊的目标
    3. 保留未激活轨迹 — 减少 false positive (需要 min_hits 帧确认)

    Usage:
        tracker = ByteTrack(config)
        tracks = tracker.update(detections)  # List[Track]
    """

    def __init__(self, config):
        """
        Args:
            config: PerceptionConfig instance
        """
        self.track_buffer = config.track_buffer
        self.high_thresh = config.track_high_thresh
        self.low_thresh = config.track_low_thresh
        self.match_thresh = config.track_match_thresh

        self.tracks: List[Track] = []
        self.frame_id: int = 0
        self._next_id: int = 0

    def update(self, detections: list) -> List[Track]:
        """Update tracker with new frame's detections.

        Args:
            detections: List of Detection objects (from Detector.detect())

        Returns:
            List of active Track objects
        """
        self.frame_id += 1
        start = time.perf_counter()

        # Convert Detection objects to numpy arrays for vectorized ops
        det_arr = np.array([
            [d.bbox[0], d.bbox[1], d.bbox[2], d.bbox[3], d.confidence, d.class_id]
            for d in detections
        ]) if detections else np.empty((0, 6))

        # ─── Split detections by confidence ───
        if len(det_arr) == 0:
            return []

        high_mask = det_arr[:, 4] >= self.high_thresh
        low_mask = (det_arr[:, 4] >= self.low_thresh) & (det_arr[:, 4] < self.high_thresh)

        high_dets = det_arr[high_mask]
        low_dets = det_arr[low_mask]

        # ─── Step 1: Kalman predict all tracks ───
        for track in self.tracks:
            track.predict()

        # ─── Step 2: First association (high-confidence detections) ───
        matched, unmatched_tracks, unmatched_dets = [], [], []
        if len(high_dets) > 0 and len(self.tracks) > 0:
            matched, unmatched_tracks, unmatched_dets = self._associate(
                self.tracks, high_dets, self.match_thresh
            )
        else:
            unmatched_tracks = list(range(len(self.tracks)))
            unmatched_dets = list(range(len(high_dets)))

        # Update matched tracks
        for track_idx, det_idx in matched:
            self.tracks[track_idx].update(high_dets[det_idx])

        # ─── Step 3: Second association (low-confidence + unmatched tracks) ───
        # ByteTrack 的关键创新: 用低置信度框恢复被遮挡目标
        if len(low_dets) > 0 and len(unmatched_tracks) > 0:
            remaining_tracks = [self.tracks[i] for i in unmatched_tracks]
            matched2, unmatched_tracks2, _ = self._associate(
                remaining_tracks, low_dets, self.match_thresh * 0.8  # Slightly tighter
            )
            for track_idx, det_idx in matched2:
                remaining_tracks[track_idx].update(low_dets[det_idx])
                # Mark as matched (removed from unmatched_tracks list)
                orig_idx = unmatched_tracks[track_idx]
                if orig_idx in unmatched_tracks:
                    unmatched_tracks.remove(orig_idx)

        # ─── Step 4: Create new tracks from unmatched high-confidence detections ───
        for det_idx in unmatched_dets:
            det = high_dets[det_idx]
            self._create_track(det)

        # ─── Step 5: Delete lost tracks ───
        self.tracks = [
            t for t in self.tracks
            if t.time_since_update < self.track_buffer
        ]

        elapsed_ms = (time.perf_counter() - start) * 1000
        active = [t for t in self.tracks if t.is_activated]
        if active:
            log.debug("Tracking: %d active tracks in %.1fms", len(active), elapsed_ms)

        return active

    def _associate(self, tracks: List[Track], detections: np.ndarray,
                   iou_threshold: float) -> Tuple[List, List, List]:
        """Hungarian algorithm + IoU cost matrix for data association.

        Args:
            tracks: List of track objects
            detections: np.array (M, 6) — [x1, y1, x2, y2, conf, cls]
            iou_threshold: Minimum IoU for valid match

        Returns:
            (matched_pairs, unmatched_track_indices, unmatched_detection_indices)
        """
        if len(tracks) == 0 or len(detections) == 0:
            return [], list(range(len(tracks))), list(range(len(detections)))

        # Build cost matrix: cost = 1 - IoU (minimize)
        n_tracks, n_dets = len(tracks), len(detections)
        cost_matrix = np.zeros((n_tracks, n_dets))

        for t in range(n_tracks):
            track_bbox = np.array(tracks[t].bbox)
            for d in range(n_dets):
                det_bbox = detections[d, :4]
                cost_matrix[t, d] = 1.0 - _iou(track_bbox, det_bbox)

        # Hungarian algorithm (minimize cost)
        row_ind, col_ind = linear_sum_assignment(cost_matrix)

        matched = []
        unmatched_tracks = list(range(n_tracks))
        unmatched_dets = list(range(n_dets))

        for r, c in zip(row_ind, col_ind):
            if cost_matrix[r, c] < (1.0 - iou_threshold):
                matched.append((r, c))
                if r in unmatched_tracks:
                    unmatched_tracks.remove(r)
                if c in unmatched_dets:
                    unmatched_dets.remove(c)

        return matched, unmatched_tracks, unmatched_dets

    def _create_track(self, detection: np.ndarray):
        """Create a new track from a detection.

        Args:
            detection: [x1, y1, x2, y2, confidence, class_id]
        """
        x1, y1, x2, y2 = detection[:4]
        cx, cy = (x1 + x2) / 2, (y1 + y2) / 2
        w, h = x2 - x1, y2 - y1

        # Initialize Kalman state: [cx, cy, w, h, 0, 0, 0, 0]
        state = np.array([cx, cy, max(w, 1e-4), max(h, 1e-4), 0, 0, 0, 0])
        covariance = np.eye(8) * 0.1

        track = Track(
            track_id=self._next_id,
            state=state,
            covariance=covariance,
            class_id=int(detection[5]) if len(detection) > 5 else -1,
            confidence=float(detection[4]) if len(detection) > 4 else 0.0,
        )
        self._next_id += 1
        self.tracks.append(track)

    @property
    def active_track_count(self) -> int:
        """Number of currently active (activated) tracks."""
        return sum(1 for t in self.tracks if t.is_activated)

    def reset(self):
        """Reset tracker state."""
        self.tracks.clear()
        self.frame_id = 0
        self._next_id = 0
