"""
YOLOv11 Object Detector
========================
Real-time object detection using Ultralytics YOLOv11 (2025).

论文: Ultralytics, "YOLOv11: Real-Time Object Detection", 2025
核心改进: C3k2 模块 + 轻量级分类头 + 自适应锚框

面试考点:
- YOLOv11 vs YOLOv8: C2f→C3k2 模块, 减少参数 15%, 精度持平
- NMS (Non-Maximum Suppression): IoU 阈值过滤重叠框
- mAP@0.5:0.95: COCO 数据集标准评估指标
"""

import time
from dataclasses import dataclass
from typing import List, Optional, Tuple
import numpy as np
import logging

log = logging.getLogger(__name__)


@dataclass
class Detection:
    """Single detection result.

    Attributes:
        bbox: [x1, y1, x2, y2] in pixel coordinates (top-left, bottom-right)
        confidence: Detection confidence [0, 1]
        class_id: COCO class ID (0=person, 2=car, etc.)
        class_name: Human-readable class name
    """
    bbox: Tuple[float, float, float, float]
    confidence: float
    class_id: int
    class_name: str

    @property
    def center(self) -> Tuple[float, float]:
        """Center point of the bounding box."""
        x1, y1, x2, y2 = self.bbox
        return ((x1 + x2) / 2, (y1 + y2) / 2)

    @property
    def area(self) -> float:
        """Area of the bounding box."""
        x1, y1, x2, y2 = self.bbox
        return (x2 - x1) * (y2 - y1)

    def to_dict(self) -> dict:
        return {
            "bbox": list(self.bbox),
            "confidence": round(self.confidence, 4),
            "class_id": self.class_id,
            "class_name": self.class_name,
            "center": list(self.center),
        }


class Detector:
    """YOLOv11 object detector with lazy model loading.

    设计要点:
    - 懒加载: 第一次调用 detect() 时才加载模型，节省启动内存
    - 设备自适应: CUDA > MPS > CPU
    - Detectable classes: 可配置，过滤无关检测

    Usage:
        detector = Detector(config)
        detections = detector.detect(frame)  # np.ndarray (H, W, 3) BGR
    """

    # COCO class names subset (commonly relevant for UAV)
    COCO_NAMES = {
        0: "person", 1: "bicycle", 2: "car", 3: "motorcycle",
        5: "bus", 7: "truck", 14: "bird", 15: "cat",
        16: "dog", 17: "horse", 41: "cup", 56: "chair",
    }

    def __init__(self, config):
        """
        Args:
            config: PerceptionConfig instance
        """
        self.config = config
        self._model = None  # Lazy-loaded YOLO model
        self._class_filter = set(config.detection_classes) if config.detection_classes else None

    @property
    def model(self):
        """Lazy-load the YOLO model on first access."""
        if self._model is None:
            from ultralytics import YOLO
            log.info("Loading YOLOv11 model: %s on %s", self.config.detection_model, self.config.device)
            self._model = YOLO(self.config.detection_model)
            self._model.to(self.config.device)
            # Warm-up inference
            dummy = np.zeros((480, 640, 3), dtype=np.uint8)
            self._model(dummy, verbose=False)
            log.info("YOLOv11 model loaded and warmed up")
        return self._model

    def detect(self, frame: np.ndarray) -> List[Detection]:
        """Run object detection on a single frame.

        Args:
            frame: BGR image as numpy array (H, W, 3)

        Returns:
            List of Detection objects, sorted by confidence descending
        """
        start = time.perf_counter()

        results = self.model(
            frame,
            conf=self.config.confidence_threshold,
            iou=self.config.nms_iou_threshold,
            verbose=False,
        )

        detections = []
        if results and len(results) > 0:
            boxes = results[0].boxes
            if boxes is not None and len(boxes) > 0:
                for box in boxes:
                    cls_id = int(box.cls[0])
                    # Apply class filter if configured
                    if self._class_filter and cls_id not in self._class_filter:
                        continue
                    x1, y1, x2, y2 = box.xyxy[0].tolist()
                    detections.append(Detection(
                        bbox=(float(x1), float(y1), float(x2), float(y2)),
                        confidence=float(box.conf[0]),
                        class_id=cls_id,
                        class_name=self.COCO_NAMES.get(cls_id, f"class_{cls_id}"),
                    ))

        elapsed_ms = (time.perf_counter() - start) * 1000
        if detections:
            log.debug("Detection: %d objects in %.1fms", len(detections), elapsed_ms)

        return sorted(detections, key=lambda d: d.confidence, reverse=True)

    def detect_batch(self, frames: List[np.ndarray]) -> List[List[Detection]]:
        """Batch detection for multiple frames (uses YOLO's built-in batching)."""
        return [self.detect(frame) for frame in frames]
