"""
Depth Anything v2 — Monocular Depth Estimation
================================================
Single-image depth estimation using Depth Anything V2 (NeurIPS 2024).

论文: Yang et al., "Depth Anything V2: A More Capable Monocular Depth
      Estimation Model", NeurIPS 2024
核心改进:
  - 基于 DINOv2 编码器的密集预测架构
  - 在合成 + 真实混合数据上训练，泛化能力极强
  - 三档模型: Small (24M) / Base (97M) / Large (335M)

无人机应用:
  - 从单目摄像头实时估计深度 → 替代昂贵的双目/激光雷达
  - 深度图 → 障碍物检测 → 避障路径规划
  - 深度图 → 3D 场景重建 → 精确降落

面试考点:
  - 单目深度估计 vs 双目: 单目成本低但尺度模糊 (scale ambiguity)
  - Relative depth vs metric depth: Depth Anything 输出相对深度
  - MiDaS 基线: Depth Anything 的核心改进方向
"""

import time
from typing import Optional, Tuple
import numpy as np
import logging

log = logging.getLogger(__name__)


class DepthEstimator:
    """Monocular depth estimation using Depth Anything V2.

    设计要点:
    - Transformers pipeline 封装，自动下载模型
    - 输出深度图归一化到 [0, 1] 或 [0, 255]
    - 支持障碍物区域检测

    Usage:
        depth_est = DepthEstimator(config)
        depth_map = depth_est.estimate(frame)  # np.ndarray (H, W), float32
        obstacles = depth_est.detect_obstacles(depth_map)  # List[ObstacleRegion]
    """

    def __init__(self, config):
        """
        Args:
            config: PerceptionConfig instance
        """
        self.config = config
        self._pipe = None  # Lazy-loaded transformers pipeline
        self._last_depth_map: Optional[np.ndarray] = None

    @property
    def pipe(self):
        """Lazy-load the depth estimation pipeline on first access."""
        if self._pipe is None:
            from transformers import pipeline
            import torch

            log.info("Loading Depth Anything V2: %s on %s",
                     self.config.depth_model, self.config.device)

            self._pipe = pipeline(
                task="depth-estimation",
                model=self.config.depth_model,
                device=self._get_device_index(),
            )
            log.info("Depth Anything V2 loaded successfully")
        return self._pipe

    def _get_device_index(self) -> int:
        """Convert device string to pipeline-compatible argument."""
        device = self.config.device
        if device == "cuda":
            return 0  # First GPU
        elif device == "mps":
            return -1  # MPS not yet supported by transformers pipeline, fallback CPU
        return -1  # CPU

    def estimate(self, frame: np.ndarray) -> np.ndarray:
        """Estimate depth from a single RGB frame.

        Args:
            frame: BGR image as numpy array (H, W, 3)

        Returns:
            Depth map as float32 numpy array (H, W), values in [0, 1]
            where 0 = far, 1 = near (normalized inverse depth)
        """
        start = time.perf_counter()

        # Convert BGR (OpenCV) to RGB (transformers)
        if frame.shape[-1] == 3:
            frame_rgb = frame[..., ::-1]  # BGR → RGB
        else:
            frame_rgb = frame

        # Transformers pipeline expects PIL Image or numpy array
        result = self.pipe(frame_rgb)
        depth = np.array(result["depth"])  # PIL Image → numpy

        # Normalize to [0, 1]
        depth = depth.astype(np.float32)
        if self.config.depth_normalize:
            d_min, d_max = depth.min(), depth.max()
            if d_max > d_min:
                depth = (depth - d_min) / (d_max - d_min)

        self._last_depth_map = depth

        elapsed_ms = (time.perf_counter() - start) * 1000
        log.debug("Depth estimation: %.1fms (range: %.3f-%.3f)",
                  elapsed_ms, depth.min(), depth.max())

        return depth

    def detect_obstacles(self, depth_map: Optional[np.ndarray] = None) -> list:
        """Detect obstacle regions from depth map.

        Splits the depth map into sectors and identifies regions
        with depth below the safe threshold.

        Args:
            depth_map: Depth map (H, W). Uses last estimate if None.

        Returns:
            List of dicts: [{"sector": "center", "min_depth": 0.3, "obstacle": True}, ...]
        """
        depth = depth_map if depth_map is not None else self._last_depth_map
        if depth is None:
            return []

        h, w = depth.shape
        sectors = {
            "left":   depth[:, :w//3],
            "center": depth[:, w//3:2*w//3],
            "right":  depth[:, 2*w//3:],
        }

        obstacles = []
        for name, region in sectors.items():
            near_pixels = np.sum(region > 0.6)  # "near" = high relative depth
            total_pixels = region.size
            near_ratio = near_pixels / total_pixels
            obstacles.append({
                "sector": name,
                "min_depth": float(region.min()),
                "mean_depth": float(region.mean()),
                "obstacle_ratio": round(near_ratio, 3),
                "obstacle": near_ratio > 0.15,  # >15% near pixels = obstacle
            })

        return obstacles

    def depth_at_point(self, x: int, y: int,
                       depth_map: Optional[np.ndarray] = None) -> float:
        """Get depth value at a specific pixel coordinate.

        Args:
            x, y: Pixel coordinates
            depth_map: Depth map. Uses last estimate if None.

        Returns:
            Depth value at (x, y), or -1 if out of bounds
        """
        depth = depth_map if depth_map is not None else self._last_depth_map
        if depth is None:
            return -1.0

        h, w = depth.shape
        if 0 <= y < h and 0 <= x < w:
            return float(depth[y, x])
        return -1.0

    @property
    def last_depth_map(self) -> Optional[np.ndarray]:
        """Get the most recent depth map."""
        return self._last_depth_map
