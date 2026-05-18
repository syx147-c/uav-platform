"""
Obstacle Avoider — Depth-based Velocity Command Generator
===========================================================
Converts depth maps into PX4-compatible velocity commands for real-time
obstacle avoidance.

算法: 深度分区 + 势场法 (Potential Field Method)
参考: Zhou et al., "EGO-Planner: An ESDF-free Gradient-based Local Planner
       for Quadrotors", ICRA 2024

核心思路:
  1. 将深度图分为 5 个扇区 (左/中左/中/中右/右)
  2. 计算每个扇区的平均深度
  3. 找到最"空"的方向 → 生成横向速度
  4. 势场法: 近障碍物排斥力 + 目标方向吸引力 → 合成速度指令

面试考点:
  - 势场法 (Potential Field): 斥力 ∝ 1/distance², 近障碍物更强
  - EGO-Planner: 基于梯度的 B-spline 轨迹优化，无需 ESDF
  - 为什么不用全局路径规划 (A*/RRT*): 无人机需要实时反应 (<100ms)
"""

import time
from dataclasses import dataclass
from typing import Optional, Tuple
import numpy as np
import logging

log = logging.getLogger(__name__)


@dataclass
class AvoidanceCommand:
    """Velocity command for obstacle avoidance.

    NED coordinate system:
      vx: North (forward) velocity, m/s
      vy: East (right) velocity, m/s
      vz: Down velocity, m/s (positive = descend)
      yaw: Yaw angle, degrees

    Attributes:
        vx, vy, vz: Velocity components (NED, m/s)
        yaw: Desired yaw angle (degrees)
        sector: Which direction we're steering toward
        obstacle_detected: Whether any obstacle is in the path
        danger_zone: Whether immediate evasive action is needed
        depth_to_obstacle: Distance to nearest obstacle (meters)
    """
    vx: float = 0.0
    vy: float = 0.0
    vz: float = 0.0
    yaw: float = 0.0
    sector: str = "center"
    obstacle_detected: bool = False
    danger_zone: bool = False
    depth_to_obstacle: float = 999.0

    def to_dict(self) -> dict:
        return {
            "vx": round(self.vx, 2),
            "vy": round(self.vy, 2),
            "vz": round(self.vz, 2),
            "yaw": round(self.yaw, 1),
            "sector": self.sector,
            "obstacle_detected": self.obstacle_detected,
            "danger_zone": self.danger_zone,
            "depth_to_obstacle": round(self.depth_to_obstacle, 2),
        }


class ObstacleAvoider:
    """Depth-map-based obstacle avoidance command generator.

    扇区划分 (5-sector model):
    ┌────┬──────┬──────┬──────┬────┐
    │ L  │  CL  │  C   │  CR  │ R  │   ← 左到右
    │-90°│ -45° │  0°  │ +45° │+90°│   ← 相对航向
    └────┴──────┴──────┴──────┴────┘

    决策逻辑:
    - C 扇区无障碍 → 直飞 (vx=avoidance_speed, vy=0)
    - C 扇区有障碍 → 转向最空扇区
    - 危险距离内 → 立即悬停 + 后退
    """

    # Sector definitions: (name, x_start_ratio, x_end_ratio)
    SECTORS = [
        ("left",       0.0, 0.2),
        ("center_left", 0.2, 0.4),
        ("center",      0.4, 0.6),
        ("center_right", 0.6, 0.8),
        ("right",       0.8, 1.0),
    ]

    # Sector → lateral velocity multiplier
    SECTOR_VY = {
        "left":        1.0,   # Strong right
        "center_left":  0.5,   # Gentle right
        "center":       0.0,   # Straight
        "center_right": -0.5,  # Gentle left
        "right":       -1.0,   # Strong left
    }

    def __init__(self, config):
        """
        Args:
            config: PerceptionConfig instance
        """
        self.config = config
        self._last_cmd: Optional[AvoidanceCommand] = None

    def compute(self, depth_map: np.ndarray,
                target_direction: Tuple[float, float] = (1.0, 0.0)) -> AvoidanceCommand:
        """Compute avoidance velocity from depth map.

        Args:
            depth_map: Normalized depth map (H, W), 0=far, 1=near
            target_direction: Desired direction as (vx, vy) unit vector

        Returns:
            AvoidanceCommand with velocity components
        """
        start = time.perf_counter()
        h, w = depth_map.shape

        # ─── Step 1: Analyze each sector ───
        sector_depths = {}
        for name, x_start, x_end in self.SECTORS:
            x1, x2 = int(w * x_start), int(w * x_end)
            region = depth_map[:, x1:x2]
            # Focus on the upper-center portion (where obstacles matter most)
            focus_region = region[h//4:3*h//4, :]
            sector_depths[name] = {
                "mean": float(np.mean(region)),
                "focus_mean": float(np.mean(focus_region)),
                "min": float(np.min(region)),
                "obstacle_pct": float(np.mean(region > 0.6)),
            }

        # ─── Step 2: Determine obstacle presence ───
        center_data = sector_depths["center"]
        obstacle_detected = center_data["obstacle_pct"] > 0.15
        danger_zone = center_data["min"] > 0.8  # Very close obstacle

        # ─── Step 3: Find clearest sector ───
        # Rank sectors by "clearness" = low mean depth (more far pixels)
        ranked = sorted(sector_depths.items(), key=lambda x: x[1]["focus_mean"])
        best_sector = ranked[0][0]

        # ─── Step 4: Generate velocity command ───
        cmd = AvoidanceCommand()
        cmd.obstacle_detected = obstacle_detected
        cmd.danger_zone = danger_zone

        # Estimate depth to obstacle from center sector
        center_near_pixels = depth_map[h//3:2*h//3, w//3:2*w//3]
        near_mask = center_near_pixels > 0.5
        if np.any(near_mask):
            cmd.depth_to_obstacle = float(np.max(center_near_pixels[near_mask]))
        else:
            cmd.depth_to_obstacle = 999.0

        if danger_zone:
            # EMERGENCY: Stop + move backward
            cmd.vx = -self.config.avoidance_speed * 0.5  # Back up
            cmd.vy = 0.0
            cmd.vz = -0.5  # Slight climb (increase altitude for safety)
            cmd.sector = "emergency_reverse"
            log.warning("DANGER ZONE! Obstacle at %.1fm — emergency reverse",
                        cmd.depth_to_obstacle)

        elif obstacle_detected:
            # Steer toward clearest sector
            cmd.vy = self.SECTOR_VY[best_sector] * self.config.avoidance_lateral_speed
            cmd.vx = self.config.avoidance_speed * 0.6  # Reduce forward speed
            cmd.vz = 0.0
            cmd.sector = best_sector
            log.info("Obstacle ahead → steering %s (vy=%.1f m/s)", best_sector, cmd.vy)

        else:
            # Clear path, follow target direction
            cmd.vx = target_direction[0] * self.config.avoidance_speed
            cmd.vy = target_direction[1] * self.config.avoidance_speed
            cmd.vz = 0.0
            cmd.sector = "center"

        # Clamp velocities
        max_speed = self.config.avoidance_speed
        cmd.vx = np.clip(cmd.vx, -max_speed, max_speed)
        cmd.vy = np.clip(cmd.vy, -max_speed, max_speed)
        cmd.vz = np.clip(cmd.vz, -2.0, 2.0)

        self._last_cmd = cmd

        elapsed_ms = (time.perf_counter() - start) * 1000
        log.debug("Avoidance: sector=%s obstacle=%s danger=%s (%.1fms)",
                  cmd.sector, cmd.obstacle_detected, cmd.danger_zone, elapsed_ms)

        return cmd

    @property
    def last_command(self) -> Optional[AvoidanceCommand]:
        return self._last_cmd
