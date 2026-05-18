"""
Perception configuration — all tunable parameters in one place.

论文参考:
  YOLOv11:       Ultralytics 2025 — https://docs.ultralytics.com/
  ByteTrack v2:  Zhang et al., ECCV 2024 — ByteTrack: Multi-Object Tracking by
                 Associating Every Detection Box
  Depth Anything v2: Yang et al., NeurIPS 2024 — Depth Anything V2: A More
                     Capable Monocular Depth Estimation Model
"""

from dataclasses import dataclass, field
from typing import Tuple


@dataclass
class PerceptionConfig:
    """Perception pipeline configuration."""

    # ═══════════════════════════════════════════════════
    # Model Selection
    # ═══════════════════════════════════════════════════

    # YOLOv11 variants: yolo11n (nano, fastest), yolo11s, yolo11m, yolo11l, yolo11x
    detection_model: str = "yolo11n.pt"

    # Depth Anything v2 variants:
    #   depth-anything/Depth-Anything-V2-Small-hf  (24M params, CPU-friendly)
    #   depth-anything/Depth-Anything-V2-Base-hf   (97M params)
    #   depth-anything/Depth-Anything-V2-Large-hf  (335M params, best accuracy)
    depth_model: str = "depth-anything/Depth-Anything-V2-Small-hf"

    # Device: "cuda", "cpu", or "mps" (Apple Silicon)
    device: str = "cpu"  # Default CPU for compatibility; override for GPU

    # ═══════════════════════════════════════════════════
    # Detection Thresholds
    # ═══════════════════════════════════════════════════

    confidence_threshold: float = 0.5      # Minimum confidence for detection
    nms_iou_threshold: float = 0.45        # NMS IoU threshold

    # Classes of interest for UAV missions (COCO class IDs)
    # 0=person, 1=bicycle, 2=car, 3=motorcycle, 5=bus, 7=truck, 14=bird,
    # 15=cat, 16=dog, 17=horse, 41=cup, 56=chair
    detection_classes: list = field(default_factory=lambda: [0, 1, 2, 3, 5, 7, 14])

    # ═══════════════════════════════════════════════════
    # Tracking (ByteTrack)
    # ═══════════════════════════════════════════════════

    track_buffer: int = 30       # Frames to keep lost tracks alive
    track_high_thresh: float = 0.5  # High-confidence detection threshold
    track_low_thresh: float = 0.1   # Low-confidence detection threshold
    track_match_thresh: float = 0.8  # IoU threshold for matching

    # ═══════════════════════════════════════════════════
    # Depth Estimation
    # ═══════════════════════════════════════════════════

    depth_input_size: Tuple[int, int] = (518, 518)  # Model input resolution
    depth_normalize: bool = True     # Normalize depth map to [0, 1]

    # ═══════════════════════════════════════════════════
    # Obstacle Avoidance
    # ═══════════════════════════════════════════════════

    safe_distance_m: float = 5.0       # Safe flying distance (meters)
    danger_distance_m: float = 2.0     # Danger zone (meters) — immediate stop
    obstacle_depth_threshold: float = 8.0  # Max depth to consider for obstacles
    avoidance_speed: float = 3.0       # Forward speed during avoidance (m/s)
    avoidance_lateral_speed: float = 2.0  # Lateral speed during avoidance (m/s)
    safety_margin_pct: float = 0.15    # 15% of frame width as safety margin

    # ═══════════════════════════════════════════════════
    # Pipeline Control
    # ═══════════════════════════════════════════════════

    detection_interval: int = 2   # Run detection every N frames
    depth_interval: int = 5       # Run depth estimation every N frames
    max_fps: int = 30             # Maximum processing FPS

    # ═══════════════════════════════════════════════════
    # Image Dimensions
    # ═══════════════════════════════════════════════════

    frame_width: int = 640
    frame_height: int = 480
