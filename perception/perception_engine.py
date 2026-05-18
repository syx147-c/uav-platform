"""
Perception Engine — Full Pipeline Orchestrator
================================================
Central engine integrating all perception modules into a unified pipeline.

Pipeline Flow:
  Camera Source → Detector → Tracker → Depth Estimator → Obstacle Avoider
       │              │          │            │                │
       ▼              ▼          ▼            ▼                ▼
    [frame]    [Detection]  [Track]     [depth_map]    [velocity_cmd]
       │              │          │            │                │
       └──────────────┴──────────┴────────────┴────────────────┘
                              │
                              ▼
                      PerceptionResult

线程安全: 所有结果通过 threading.Lock 保护，可从多个线程安全访问
GPU 优化: 支持 CUDA 加速，批处理模式减少 GPU 调用开销
"""

import time
import threading
from dataclasses import dataclass, field
from typing import List, Optional
import numpy as np
import logging

from .config import PerceptionConfig
from .detector import Detector, Detection
from .tracker import ByteTrack, Track
from .depth_estimator import DepthEstimator
from .obstacle_avoider import ObstacleAvoider, AvoidanceCommand
from .camera_source import CameraSource

log = logging.getLogger(__name__)


@dataclass
class PerceptionResult:
    """Complete perception frame result.

    Attributes:
        timestamp_ms: Frame timestamp
        detections: Object detections
        tracks: Active tracked objects
        depth_map: Depth estimation (may be None if depth is disabled)
        obstacles: Obstacle sector analysis
        avoidance_cmd: Computed avoidance velocity command
        frame_shape: (H, W) of input frame
    """
    timestamp_ms: float = 0.0
    detections: List[Detection] = field(default_factory=list)
    tracks: List[Track] = field(default_factory=list)
    depth_map: Optional[np.ndarray] = None
    obstacles: list = field(default_factory=list)
    avoidance_cmd: Optional[AvoidanceCommand] = None
    frame_shape: tuple = (0, 0)

    def to_dict(self) -> dict:
        """Convert to JSON-serializable dict for API responses."""
        return {
            "timestamp_ms": round(self.timestamp_ms, 1),
            "frame_shape": list(self.frame_shape),
            "detections": [d.to_dict() for d in self.detections],
            "tracks": [t.get_state() for t in self.tracks],
            "obstacles": self.obstacles,
            "avoidance_cmd": self.avoidance_cmd.to_dict() if self.avoidance_cmd else None,
            "detection_count": len(self.detections),
            "track_count": len(self.tracks),
            "obstacle_detected": self.avoidance_cmd.obstacle_detected if self.avoidance_cmd else False,
        }


class PerceptionEngine:
    """Complete perception pipeline for UAV autonomy.

    支持三种运行模式:
      1. 单帧处理 (process_frame): 手动传入每帧
      2. 相机流模式 (run): 从 CameraSource 自动读取
      3. 后台线程 (start/stop): 异步处理，通过 get_latest_result() 获取结果

    Usage:
        engine = PerceptionEngine(PerceptionConfig())
        engine.enable_detection().enable_tracking().enable_depth().enable_avoidance()
        result = engine.process_frame(frame)
        print(f"Found {len(result.detections)} objects, {len(result.tracks)} tracks")
    """

    def __init__(self, config: PerceptionConfig):
        self.config = config

        # Modules (lazy-loaded)
        self._detector: Optional[Detector] = None
        self._tracker: Optional[ByteTrack] = None
        self._depth_estimator: Optional[DepthEstimator] = None
        self._avoider: Optional[ObstacleAvoider] = None
        self._camera_source: Optional[CameraSource] = None

        # Feature flags
        self._detection_enabled = False
        self._tracking_enabled = False
        self._depth_enabled = False
        self._avoidance_enabled = False

        # State
        self._running = False
        self._thread: Optional[threading.Thread] = None
        self._lock = threading.Lock()
        self._latest_result: Optional[PerceptionResult] = None
        self._frame_count = 0
        self._total_fps = 0.0

    # ═══════════════════════════════════════════════════
    # Builder pattern for enabling features
    # ═══════════════════════════════════════════════════

    def enable_detection(self):
        self._detection_enabled = True
        return self

    def enable_tracking(self):
        self._tracking_enabled = True
        return self

    def enable_depth(self):
        self._depth_enabled = True
        return self

    def enable_avoidance(self):
        self._avoidance_enabled = True
        return self

    def set_camera_source(self, source: CameraSource):
        self._camera_source = source
        return self

    # ═══════════════════════════════════════════════════
    # Lazy property accessors
    # ═══════════════════════════════════════════════════

    @property
    def detector(self) -> Detector:
        if self._detector is None:
            self._detector = Detector(self.config)
        return self._detector

    @property
    def tracker(self) -> ByteTrack:
        if self._tracker is None:
            self._tracker = ByteTrack(self.config)
        return self._tracker

    @property
    def depth_estimator(self) -> DepthEstimator:
        if self._depth_estimator is None:
            self._depth_estimator = DepthEstimator(self.config)
        return self._depth_estimator

    @property
    def avoider(self) -> ObstacleAvoider:
        if self._avoider is None:
            self._avoider = ObstacleAvoider(self.config)
        return self._avoider

    # ═══════════════════════════════════════════════════
    # Main processing
    # ═══════════════════════════════════════════════════

    def process_frame(self, frame: np.ndarray,
                      timestamp_ms: float = 0.0) -> PerceptionResult:
        """Run full perception pipeline on a single frame.

        Args:
            frame: BGR image as numpy array (H, W, 3)
            timestamp_ms: Frame timestamp (milliseconds)

        Returns:
            PerceptionResult with all enabled module outputs
        """
        start = time.perf_counter()
        self._frame_count += 1
        h, w = frame.shape[:2]

        result = PerceptionResult(
            timestamp_ms=timestamp_ms,
            frame_shape=(h, w),
        )

        # ─── Stage 1: Object Detection ───
        if self._detection_enabled:
            result.detections = self.detector.detect(frame)

        # ─── Stage 2: Multi-Object Tracking ───
        if self._tracking_enabled and self._detection_enabled:
            result.tracks = self.tracker.update(result.detections)

        # ─── Stage 3: Monocular Depth Estimation ───
        if self._depth_enabled and self._frame_count % self.config.depth_interval == 0:
            result.depth_map = self.depth_estimator.estimate(frame)
            result.obstacles = self.depth_estimator.detect_obstacles(result.depth_map)

        # ─── Stage 4: Obstacle Avoidance ───
        if self._avoidance_enabled and result.depth_map is not None:
            # Default target: forward (north)
            result.avoidance_cmd = self.avoider.compute(result.depth_map)

        # Update FPS estimation
        elapsed = time.perf_counter() - start
        alpha = 0.1  # EMA smoothing
        self._total_fps = alpha * (1.0 / max(elapsed, 0.001)) + (1 - alpha) * self._total_fps

        # Thread-safe update
        with self._lock:
            self._latest_result = result

        return result

    def get_latest_result(self) -> Optional[PerceptionResult]:
        """Thread-safe access to the most recent perception result."""
        with self._lock:
            return self._latest_result

    # ═══════════════════════════════════════════════════
    # Background processing mode
    # ═══════════════════════════════════════════════════

    def start(self):
        """Start background perception thread (requires camera source)."""
        if self._camera_source is None:
            raise RuntimeError("No camera source set. Call set_camera_source() first.")
        if self._running:
            return

        self._running = True
        self._thread = threading.Thread(target=self._run_loop, daemon=True, name="perception")
        self._thread.start()
        log.info("Perception engine started (detection=%s, tracking=%s, depth=%s, avoidance=%s)",
                 self._detection_enabled, self._tracking_enabled,
                 self._depth_enabled, self._avoidance_enabled)

    def stop(self):
        """Stop background perception thread."""
        self._running = False
        if self._thread is not None:
            self._thread.join(timeout=5.0)
            self._thread = None
        if self._camera_source is not None:
            self._camera_source.release()
        log.info("Perception engine stopped (%d frames processed)", self._frame_count)

    def _run_loop(self):
        """Background processing loop."""
        while self._running:
            frame, ts = self._camera_source.read()
            if frame is None:
                log.warning("Camera source ended, stopping perception engine")
                self._running = False
                break
            self.process_frame(frame, ts or time.time() * 1000)

    def is_running(self) -> bool:
        return self._running

    @property
    def current_fps(self) -> float:
        """Estimated processing FPS."""
        return self._total_fps
