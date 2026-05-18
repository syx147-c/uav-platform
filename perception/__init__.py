"""
UAV Perception Module v3.0
===========================
State-of-the-art computer vision pipeline for UAV autonomy:
  - YOLOv11:        Real-time object detection (Ultralytics, 2025)
  - ByteTrack v2:   Multi-object tracking (ECCV 2024)
  - Depth Anything v2: Monocular depth estimation (NeurIPS 2024)
  - EGO-Planner-inspired:  Gradient-based obstacle avoidance (ICRA 2024)

Architecture:
  Camera Source → Detector → Tracker → Depth Estimator → Obstacle Avoider
       ↓              ↓          ↓            ↓                ↓
  [frame]      [Detection]  [Track]   [depth_map]     [velocity_cmd]
                                                              ↓
                                             bridge.py → MAVSDK → PX4

Usage:
  from perception import PerceptionEngine, PerceptionConfig
  engine = PerceptionEngine(PerceptionConfig())
  result = engine.process_frame(frame)  # PerceptionResult
"""

from .config import PerceptionConfig
from .detector import Detector, Detection
from .tracker import ByteTrack, Track
from .depth_estimator import DepthEstimator
from .obstacle_avoider import ObstacleAvoider, AvoidanceCommand
from .perception_engine import PerceptionEngine, PerceptionResult
from .camera_source import CameraSource, WebcamSource, VideoFileSource

__version__ = "3.0.0"
__all__ = [
    "PerceptionConfig",
    "Detector", "Detection",
    "ByteTrack", "Track",
    "DepthEstimator",
    "ObstacleAvoider", "AvoidanceCommand",
    "PerceptionEngine", "PerceptionResult",
    "CameraSource", "WebcamSource", "VideoFileSource",
]
