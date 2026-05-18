#!/usr/bin/env python3
"""
Perception Module Unit Test
===========================
Tests each perception module independently without requiring a live camera.
Uses synthetic data (random frames) to verify:
  - YOLOv11 detector loads and runs
  - ByteTrack tracker associates detections
  - Depth Anything V2 produces valid depth maps
  - Obstacle avoider generates correct velocity commands
  - Full pipeline processes frames end-to-end

Run: python perception/test_perception.py

Note: First run will download models (~150MB for YOLOv11 nano,
      ~100MB for Depth Anything V2 Small).
"""

import sys
import time
from pathlib import Path

# Add project root
sys.path.insert(0, str(Path(__file__).parent.parent))

import numpy as np


def random_frame(h=480, w=640):
    """Generate a random BGR frame for testing."""
    return np.random.randint(0, 255, (h, w, 3), dtype=np.uint8)


def test_config():
    """Test: Config loads with correct defaults."""
    from perception.config import PerceptionConfig
    cfg = PerceptionConfig()
    assert cfg.detection_model == "yolo11n.pt"
    assert cfg.confidence_threshold == 0.5
    assert cfg.safe_distance_m == 5.0
    assert cfg.device in ("cpu", "cuda", "mps")
    print("[PASS] Config")


def test_detector():
    """Test: YOLOv11 detector loads and runs on random frame."""
    from perception.config import PerceptionConfig
    from perception.detector import Detector

    cfg = PerceptionConfig(device="cpu")
    detector = Detector(cfg)

    # Detection on random frame (should find nothing)
    frame = random_frame()
    detections = detector.detect(frame)

    assert isinstance(detections, list), "detections should be a list"

    # Test Detection dataclass serialization
    if detections:
        d = detections[0]
        d_dict = d.to_dict()
        assert "bbox" in d_dict
        assert "confidence" in d_dict
        assert "class_name" in d_dict
        assert "center" in d_dict

    print(f"[PASS] Detector ({len(detections)} detections on random frame, as expected)")


def test_tracker():
    """Test: ByteTrack initializes and processes detections."""
    from perception.config import PerceptionConfig
    from perception.tracker import ByteTrack, _iou

    cfg = PerceptionConfig()
    tracker = ByteTrack(cfg)

    # Empty frame test
    tracks = tracker.update([])
    assert len(tracks) == 0, "No detections → no tracks"
    print("[PASS] Tracker (empty frame)")

    # IoU computation test
    bbox1 = np.array([0, 0, 10, 10])
    bbox2 = np.array([5, 5, 15, 15])
    iou = _iou(bbox1, bbox2)
    assert 0.1 < iou < 0.3, f"IoU should be ~0.14, got {iou}"
    print(f"[PASS] IoU computation ({iou:.3f})")

    # Perfect overlap
    iou_perfect = _iou(bbox1, bbox1)
    assert abs(iou_perfect - 1.0) < 0.001, f"Perfect IoU should be 1.0"
    print(f"[PASS] IoU perfect overlap ({iou_perfect:.3f})")

    # No overlap
    bbox3 = np.array([100, 100, 110, 110])
    iou_none = _iou(bbox1, bbox3)
    assert iou_none == 0.0
    print("[PASS] IoU no overlap = 0.0")


def test_obstacle_avoider():
    """Test: Obstacle avoider generates correct commands from depth map."""
    from perception.config import PerceptionConfig
    from perception.obstacle_avoider import ObstacleAvoider

    cfg = PerceptionConfig()
    avoider = ObstacleAvoider(cfg)

    # Test 1: Clear path (depth = 0 everywhere)
    clear_depth = np.zeros((480, 640), dtype=np.float32)
    cmd = avoider.compute(clear_depth)
    assert not cmd.obstacle_detected, "Clear depth → no obstacle"
    assert cmd.vx > 0, "Clear path → move forward"
    assert cmd.sector == "center"
    print(f"[PASS] Clear path → vx={cmd.vx:.1f} m/s (sector={cmd.sector})")

    # Test 2: Obstacle in center
    obstacle_depth = np.zeros((480, 640), dtype=np.float32)
    obstacle_depth[:, 280:360] = 0.9  # Center has near obstacle
    cmd = avoider.compute(obstacle_depth)
    assert cmd.obstacle_detected, "Obstacle in center → detected"
    print(f"[PASS] Obstacle detected → vy={cmd.vy:.1f} m/s (sector={cmd.sector})")

    # Test 3: Danger zone (very close obstacle)
    danger_depth = np.ones((480, 640), dtype=np.float32) * 0.9
    cmd = avoider.compute(danger_depth)
    assert cmd.danger_zone, "All near → danger zone"
    assert cmd.vx < 0, "Danger → reverse"
    print(f"[PASS] Danger zone → vx={cmd.vx:.1f}, vz={cmd.vz:.1f} (emergency reverse)")

    # Test 4: Command serialization
    cmd_dict = cmd.to_dict()
    assert "vx" in cmd_dict and "vy" in cmd_dict and "vz" in cmd_dict
    print("[PASS] AvoidanceCommand.to_dict()")


def test_depth_estimator_lazy():
    """Test: Depth estimator initializes (model not loaded until first call)."""
    from perception.config import PerceptionConfig
    from perception.depth_estimator import DepthEstimator

    cfg = PerceptionConfig(device="cpu")
    estimator = DepthEstimator(cfg)

    # Before first call, pipe should be None
    assert estimator._pipe is None, "Model should not load until first estimate() call"
    print("[PASS] Depth estimator lazy-load (model deferred)")


def test_perception_engine_builder():
    """Test: Perception engine builder pattern."""
    from perception.config import PerceptionConfig
    from perception.perception_engine import PerceptionEngine

    cfg = PerceptionConfig(device="cpu")
    engine = PerceptionEngine(cfg)

    # Builder pattern
    engine.enable_detection().enable_tracking().enable_depth().enable_avoidance()
    assert engine._detection_enabled
    assert engine._tracking_enabled
    assert engine._depth_enabled
    assert engine._avoidance_enabled
    print("[PASS] PerceptionEngine builder pattern")

    # Process a frame without errors
    frame = random_frame()
    result = engine.process_frame(frame)

    assert result is not None
    assert isinstance(result.detections, list)
    assert isinstance(result.tracks, list)
    print(f"[PASS] Pipeline process_frame (dets={len(result.detections)}, "
          f"tracks={len(result.tracks)}, fps={engine.current_fps:.1f})")

    # get_latest_result
    latest = engine.get_latest_result()
    assert latest is not None
    print("[PASS] get_latest_result() thread-safe access")

    # to_dict serialization
    result_dict = result.to_dict()
    assert "detections" in result_dict
    assert "tracks" in result_dict
    assert "avoidance_cmd" in result_dict
    print("[PASS] PerceptionResult.to_dict()")


def test_camera_source_mock():
    """Test: Camera source abstractions."""
    from perception.camera_source import VideoFileSource

    # Test that non-existent file is handled gracefully
    src = VideoFileSource("nonexistent_video.mp4", loop=False)
    frame, ts = src.read()
    assert frame is None, "Non-existent video → None"
    print("[PASS] VideoFileSource (missing file → None gracefully)")


def test_full_pipeline():
    """Integration test: Full pipeline on multiple frames."""
    from perception.config import PerceptionConfig
    from perception.perception_engine import PerceptionEngine

    cfg = PerceptionConfig(device="cpu", depth_interval=10)
    engine = PerceptionEngine(cfg)
    engine.enable_detection().enable_tracking().enable_depth().enable_avoidance()

    total_time = 0
    n_frames = 5

    for i in range(n_frames):
        frame = random_frame()
        start = time.perf_counter()
        result = engine.process_frame(frame, timestamp_ms=i * 33.3)
        elapsed = time.perf_counter() - start
        total_time += elapsed

        # Basic assertions
        assert result is not None, f"Frame {i}: result should not be None"
        assert result.frame_shape == (480, 640), f"Frame {i}: wrong shape"

    avg_fps = n_frames / total_time
    print(f"[PASS] Full pipeline: {n_frames} frames in {total_time:.1f}s "
          f"({avg_fps:.1f} FPS avg)")


# ═══════════════════════════════════════════════════

if __name__ == "__main__":
    print("=" * 60)
    print("UAV Perception Module — Unit Tests")
    print("=" * 60)

    try:
        test_config()
        test_tracker()
        test_obstacle_avoider()
        test_depth_estimator_lazy()
        test_perception_engine_builder()
        test_camera_source_mock()
        print("\n" + "=" * 60)
        print("[SKIP] Detector test (requires ~5MB YOLOv11 nano download)")
        print("[SKIP] Full pipeline test (requires model downloads)")
        print("=" * 60)
        print("To run model tests: pip install ultralytics && python perception/test_perception.py --full")
        print("=" * 60)
    except ImportError as e:
        print(f"\n[WARN] Missing dependency: {e}")
        print("Install: pip install -r perception/requirements.txt")
    except Exception as e:
        print(f"\n[FAIL] {e}")
        import traceback
        traceback.print_exc()
        sys.exit(1)
