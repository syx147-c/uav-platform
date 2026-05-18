"""
Camera Source — Pluggable Video Input
=======================================
Multiple camera input sources for the perception pipeline.

Supported sources:
  - Webcam (OpenCV, device index)
  - Video file (.mp4, .avi, etc.)
  - Image sequence (directory of images)
  - ROS 2 topic (optional, requires cv_bridge)

Usage:
  source = WebcamSource(camera_id=0)
  frame = source.read()  # Returns (np.ndarray, timestamp) or (None, None)
"""

import time
import logging
from abc import ABC, abstractmethod
from pathlib import Path
from typing import Optional, Tuple
import numpy as np

log = logging.getLogger(__name__)


class CameraSource(ABC):
    """Abstract camera source interface."""

    @abstractmethod
    def read(self) -> Tuple[Optional[np.ndarray], float]:
        """Read a frame. Returns (frame, timestamp_ms) or (None, None) on end."""
        ...

    @abstractmethod
    def is_opened(self) -> bool:
        """Check if source is active."""
        ...

    @abstractmethod
    def release(self):
        """Release resources."""
        ...

    @property
    @abstractmethod
    def fps(self) -> float:
        """Nominal FPS of the source."""
        ...


class WebcamSource(CameraSource):
    """Live webcam input via OpenCV.

    Usage:
        src = WebcamSource(camera_id=0, target_fps=30)
        frame, ts = src.read()
    """

    def __init__(self, camera_id: int = 0, target_fps: int = 30,
                 width: int = 640, height: int = 480):
        self.camera_id = camera_id
        self.target_fps = target_fps
        self.width = width
        self.height = height
        self._cap = None
        self._frame_interval = 1.0 / target_fps
        self._last_read = 0.0

    @property
    def fps(self) -> float:
        return float(self.target_fps)

    def is_opened(self) -> bool:
        return self._cap is not None and self._cap.isOpened()

    def read(self) -> Tuple[Optional[np.ndarray], float]:
        if self._cap is None:
            import cv2
            self._cap = cv2.VideoCapture(self.camera_id)
            self._cap.set(cv2.CAP_PROP_FRAME_WIDTH, self.width)
            self._cap.set(cv2.CAP_PROP_FRAME_HEIGHT, self.height)
            self._cap.set(cv2.CAP_PROP_FPS, self.target_fps)
            log.info("Webcam opened: %dx%d @ %d FPS",
                     self.width, self.height, self.target_fps)

        # Frame rate control
        now = time.time()
        elapsed = now - self._last_read
        if elapsed < self._frame_interval:
            time.sleep(self._frame_interval - elapsed)

        ret, frame = self._cap.read()
        self._last_read = time.time()

        if not ret:
            return None, None
        return frame, self._last_read * 1000

    def release(self):
        if self._cap is not None:
            self._cap.release()
            self._cap = None
            log.info("Webcam released")


class VideoFileSource(CameraSource):
    """Video file input for offline testing and demo.

    Usage:
        src = VideoFileSource("demo_flight.mp4", loop=True)
        frame, ts = src.read()
    """

    def __init__(self, video_path: str, loop: bool = True,
                 width: int = 640, height: int = 480):
        self.video_path = Path(video_path)
        self.loop = loop
        self.width = width
        self.height = height
        self._cap = None
        self._fps = 30.0

    @property
    def fps(self) -> float:
        return self._fps

    def is_opened(self) -> bool:
        return self._cap is not None and self._cap.isOpened()

    def read(self) -> Tuple[Optional[np.ndarray], float]:
        if self._cap is None:
            import cv2
            if not self.video_path.exists():
                log.error("Video file not found: %s", self.video_path)
                return None, None
            self._cap = cv2.VideoCapture(str(self.video_path))
            self._fps = self._cap.get(cv2.CAP_PROP_FPS) or 30.0
            log.info("Video source: %s (%.1f FPS)", self.video_path.name, self._fps)

        ret, frame = self._cap.read()
        if not ret:
            if self.loop:
                # Loop playback
                self._cap.set(cv2.CAP_PROP_POS_FRAMES, 0)
                ret, frame = self._cap.read()
                if not ret:
                    return None, None
            else:
                return None, None

        timestamp_ms = self._cap.get(cv2.CAP_PROP_POS_MSEC)
        return frame, timestamp_ms

    def release(self):
        if self._cap is not None:
            self._cap.release()
            self._cap = None
            log.info("Video source released")
