import asyncio, time, sys, os
from pathlib import Path
from fastapi import FastAPI, HTTPException
from fastapi.middleware.cors import CORSMiddleware
from mavsdk import System
from pydantic import BaseModel

# Add project root for perception module import
sys.path.insert(0, str(Path(__file__).parent.parent))

app = FastAPI(title='UAV MAVSDK Bridge', version='3.0')
app.add_middleware(CORSMiddleware, allow_origins=['*'], allow_methods=['*'], allow_headers=['*'])

drone = System()
start_time = time.time()
_takeoff_alt = 10.0

# ─── Perception Engine (lazy init) ───
_perception_engine = None
_perception_enabled = False
_avoidance_active = False


class WaypointBody(BaseModel):
    lat: float
    lon: float
    alt: float


class TakeoffBody(BaseModel):
    altitude: float = 10.0


class VelocityBody(BaseModel):
    vx: float = 0.0
    vy: float = 0.0
    vz: float = 0.0
    yaw: float = 0.0


@app.on_event('startup')
async def connect_drone():
    global _takeoff_alt
    await drone.connect(system_address='udp://:14540')
    await asyncio.sleep(0.5)
    await drone.action.set_takeoff_altitude(_takeoff_alt)
    print(f'Bridge 2.0: Connected to PX4, default takeoff altitude={_takeoff_alt}m')


@app.get('/telemetry')
async def telemetry():
    try:
        # 用 wait_for 取第一条数据，1秒超时防止卡住
        pos = await asyncio.wait_for(drone.telemetry.position().__anext__(), timeout=1.0)
        batt = await asyncio.wait_for(drone.telemetry.battery().__anext__(), timeout=1.0)

        lat = pos.latitude_deg if pos.latitude_deg and abs(pos.latitude_deg) > 0.001 else 47.39775
        lon = pos.longitude_deg if pos.longitude_deg and abs(pos.longitude_deg) > 0.001 else 8.54561
        alt = pos.relative_altitude_m

        vx = vy = vz = 0.0
        try:
            vel = await asyncio.wait_for(drone.telemetry.velocity_ned().__anext__(), timeout=0.5)
            vx, vy, vz = vel.north_m_s, vel.east_m_s, vel.down_m_s
        except:
            pass

        return {
            'latitude':  round(lat, 7),
            'longitude': round(lon, 7),
            'altitude':  round(alt, 2),
            'velocityX': round(vx, 2),
            'velocityY': round(vy, 2),
            'velocityZ': round(vz, 2),
            'battery':   round(batt.remaining_percent * 100) / 100 if batt.remaining_percent else 0,
            'gps_fix':   8,
            'in_air':    alt > 0.5,
            'uptime':    round(time.time() - start_time)
        }
    except Exception as e:
        return {'error': str(e)}


@app.post('/takeoff')
async def takeoff(body: TakeoffBody = TakeoffBody()):
    global _takeoff_alt
    _takeoff_alt = body.altitude
    await drone.action.set_takeoff_altitude(body.altitude)
    await asyncio.sleep(0.15)
    await drone.action.arm()
    await drone.action.takeoff()
    return {'status': 'taking_off', 'altitude': body.altitude}


@app.post('/land')
async def land():
    await drone.action.land()
    return {'status': 'landing'}


@app.post('/hold')
async def hold():
    await drone.action.hold()
    return {'status': 'holding'}


@app.post('/arm')
async def arm():
    await drone.action.arm()
    return {'status': 'armed'}


@app.post('/disarm')
async def disarm():
    await drone.action.disarm()
    return {'status': 'disarmed'}


@app.post('/rtl')
async def rtl():
    await drone.action.return_to_launch()
    return {'status': 'returning_to_launch'}


@app.post('/reboot')
async def reboot():
    await drone.action.reboot()
    return {'status': 'rebooting'}


@app.post('/waypoint')
async def waypoint(body: WaypointBody):
    await drone.action.goto_location(body.lat, body.lon, body.alt, 0.0)
    return {'status': 'waypoint_sent', 'lat': body.lat, 'lon': body.lon, 'alt': body.alt}


@app.post('/velocity')
async def velocity(body: VelocityBody):
    await drone.offboard.set_velocity_ned({
        'north_m_s': body.vx,
        'east_m_s': body.vy,
        'down_m_s': body.vz,
        'yaw_deg': body.yaw
    })
    return {'status': 'velocity_set', 'vx': body.vx, 'vy': body.vy, 'vz': body.vz}


@app.get('/status')
async def status():
    try:
        info = await drone.info.get_version()
        return {
            'flight_sw': info.flight_sw_version if info else 'unknown',
            'bridge': '2.0',
            'uptime': round(time.time() - start_time),
            'takeoff_altitude': _takeoff_alt
        }
    except:
        return {'bridge': '2.0', 'uptime': round(time.time() - start_time)}


class ImageInput(BaseModel):
    """Base64-encoded image for perception endpoints."""
    image_base64: str


class PerceptionInitBody(BaseModel):
    """Perception engine initialization config."""
    device: str = "cpu"
    enable_detection: bool = True
    enable_tracking: bool = True
    enable_depth: bool = True
    enable_avoidance: bool = False


# ═══════════════════════════════════════════════════
# Perception Endpoints (YOLOv11 + ByteTrack + Depth Anything V2)
# ═══════════════════════════════════════════════════

def _get_perception_engine():
    """Lazy-init perception engine."""
    global _perception_engine, _perception_enabled
    if _perception_engine is None:
        from perception.config import PerceptionConfig
        from perception.perception_engine import PerceptionEngine
        cfg = PerceptionConfig()
        _perception_engine = PerceptionEngine(cfg)
        _perception_engine.enable_detection().enable_tracking().enable_depth()
        _perception_enabled = True
        print("Perception engine initialized (YOLOv11 + ByteTrack + Depth Anything V2)")
    return _perception_engine


@app.post('/perception/init')
async def perception_init(body: PerceptionInitBody = PerceptionInitBody()):
    """Initialize perception engine with configuration."""
    global _perception_engine, _perception_enabled, _avoidance_active
    from perception.config import PerceptionConfig
    from perception.perception_engine import PerceptionEngine
    cfg = PerceptionConfig(device=body.device)
    engine = PerceptionEngine(cfg)
    if body.enable_detection:
        engine.enable_detection()
    if body.enable_tracking:
        engine.enable_tracking()
    if body.enable_depth:
        engine.enable_depth()
    if body.enable_avoidance:
        engine.enable_avoidance()
        _avoidance_active = True
    _perception_engine = engine
    _perception_enabled = True
    return {
        'status': 'initialized',
        'device': cfg.device,
        'detection': body.enable_detection,
        'tracking': body.enable_tracking,
        'depth': body.enable_depth,
        'avoidance': body.enable_avoidance,
    }


@app.post('/perception/detect')
async def perception_detect(body: ImageInput):
    """YOLOv11 object detection on a single frame.

    Returns detections: [{bbox, confidence, class_id, class_name, center}, ...]
    面试亮点: YOLOv11 (2025 SOTA), lazy model loading, GPU/CPU auto-detect
    """
    import base64, cv2, numpy as np
    engine = _get_perception_engine()

    img_data = base64.b64decode(body.image_base64)
    nparr = np.frombuffer(img_data, np.uint8)
    frame = cv2.imdecode(nparr, cv2.IMREAD_COLOR)
    if frame is None:
        raise HTTPException(400, 'Invalid image data')

    start = time.perf_counter()
    detections = engine.detector.detect(frame)
    elapsed_ms = (time.perf_counter() - start) * 1000

    return {
        'detections': [d.to_dict() for d in detections],
        'count': len(detections),
        'elapsed_ms': round(elapsed_ms, 1),
        'device': engine.config.device,
    }


@app.post('/perception/track')
async def perception_track(body: ImageInput):
    """ByteTrack v2 multi-object tracking.

    Returns tracks with unique IDs, bbox, Kalman-predicted positions.
    面试亮点: ByteTrack two-stage association (high→low confidence),
             Hungarian matching, Kalman filter state estimation
    """
    import base64, cv2, numpy as np
    engine = _get_perception_engine()

    img_data = base64.b64decode(body.image_base64)
    nparr = np.frombuffer(img_data, np.uint8)
    frame = cv2.imdecode(nparr, cv2.IMREAD_COLOR)
    if frame is None:
        raise HTTPException(400, 'Invalid image data')

    detections = engine.detector.detect(frame)
    tracks = engine.tracker.update(detections)

    return {
        'tracks': [t.get_state() for t in tracks],
        'active_tracks': len(tracks),
        'total_tracks_seen': engine.tracker._next_id,
    }


@app.post('/perception/depth')
async def perception_depth(body: ImageInput):
    """Depth Anything V2 monocular depth estimation.

    Returns depth map shape + obstacle analysis for 5 sectors.
    面试亮点: 单目深度估计 (no stereo/LiDAR needed),
             Depth Anything V2 NeurIPS 2024, DINOv2 encoder
    """
    import base64, cv2, numpy as np
    engine = _get_perception_engine()

    img_data = base64.b64decode(body.image_base64)
    nparr = np.frombuffer(img_data, np.uint8)
    frame = cv2.imdecode(nparr, cv2.IMREAD_COLOR)
    if frame is None:
        raise HTTPException(400, 'Invalid image data')

    start = time.perf_counter()
    depth_map = engine.depth_estimator.estimate(frame)
    obstacles = engine.depth_estimator.detect_obstacles(depth_map)
    elapsed_ms = (time.perf_counter() - start) * 1000

    return {
        'obstacle_regions': obstacles,
        'obstacle_detected': any(o['obstacle'] for o in obstacles),
        'depth_map_shape': list(depth_map.shape),
        'depth_range': [round(float(depth_map.min()), 3), round(float(depth_map.max()), 3)],
        'elapsed_ms': round(elapsed_ms, 1),
    }


@app.post('/perception/avoid/enable')
async def perception_avoid_enable():
    """Enable real-time obstacle avoidance.

    The bridge will process camera frames and autonomously send
    velocity commands through MAVSDK to avoid obstacles.
    面试亮点: EGO-Planner 启发式深度避障, 5-sector potential field,
             <100ms 实时响应
    """
    global _avoidance_active
    engine = _get_perception_engine()
    engine.enable_avoidance().enable_depth()
    _avoidance_active = True
    return {
        'status': 'avoidance_enabled',
        'safe_distance_m': engine.config.safe_distance_m,
        'danger_distance_m': engine.config.danger_distance_m,
        'message': 'Obstacle avoidance active — drone will auto-steer around obstacles',
    }


@app.post('/perception/avoid/disable')
async def perception_avoid_disable():
    """Disable obstacle avoidance."""
    global _avoidance_active
    _avoidance_active = False
    engine = _get_perception_engine()
    engine._avoidance_enabled = False
    return {'status': 'avoidance_disabled', 'message': 'Manual control restored'}


@app.post('/perception/avoid/step')
async def perception_avoid_step(body: ImageInput):
    """Process one frame through the full avoidance pipeline.

    Returns the computed velocity command (NED) for obstacle avoidance.
    Call this per-frame to use avoidance without the background thread.
    """
    import base64, cv2, numpy as np
    engine = _get_perception_engine()
    engine.enable_depth().enable_avoidance()

    img_data = base64.b64decode(body.image_base64)
    nparr = np.frombuffer(img_data, np.uint8)
    frame = cv2.imdecode(nparr, cv2.IMREAD_COLOR)
    if frame is None:
        raise HTTPException(400, 'Invalid image data')

    result = engine.process_frame(frame)

    if result.avoidance_cmd is None:
        return {'error': 'No avoidance command — depth estimation may have failed'}

    cmd = result.avoidance_cmd

    # Execute avoidance via MAVSDK (send velocity command to drone)
    if _avoidance_active and cmd.obstacle_detected:
        await drone.offboard.set_velocity_ned({
            'north_m_s': cmd.vx,
            'east_m_s': cmd.vy,
            'down_m_s': cmd.vz,
            'yaw_deg': cmd.yaw,
        })

    return {
        'command': cmd.to_dict(),
        'obstacles': result.obstacles,
        'detections': len(result.detections),
        'executed': _avoidance_active and cmd.obstacle_detected,
    }


@app.post('/perception/follow')
async def perception_follow(body: ImageInput):
    """Detect and follow a target (person/car).

    Pipeline: Detection → Tracking → compute velocity toward target.
    Requires avoidance_enable first if obstacles are present.
    """
    import base64, cv2, numpy as np
    engine = _get_perception_engine()
    engine.enable_detection().enable_tracking()

    img_data = base64.b64decode(body.image_base64)
    nparr = np.frombuffer(img_data, np.uint8)
    frame = cv2.imdecode(nparr, cv2.IMREAD_COLOR)
    if frame is None:
        raise HTTPException(400, 'Invalid image data')

    h, w = frame.shape[:2]
    detections = engine.detector.detect(frame)
    tracks = engine.tracker.update(detections)

    # Find the most confident tracked target
    if not tracks:
        return {'status': 'no_target', 'message': 'No object detected to follow'}

    target = max(tracks, key=lambda t: t.confidence)

    # Compute velocity to center the target in frame
    center_x, center_y = target.center
    frame_cx, frame_cy = w / 2, h / 2
    error_x = (center_x - frame_cx) / frame_cx  # [-1, 1] normalized horizontal error

    follow_vx = 2.0  # Forward speed (m/s)
    follow_vy = -error_x * 1.5  # Lateral correction
    follow_vz = 0.0
    follow_yaw = -error_x * 15  # Yaw correction (degrees)

    return {
        'status': 'following' if target.is_activated else 'acquiring',
        'target': target.get_state(),
        'command': {
            'vx': round(follow_vx, 2),
            'vy': round(follow_vy, 2),
            'vz': follow_vz,
            'yaw': round(follow_yaw, 1),
        },
        'error_pixels': [round(error_x * frame_cx, 1), round(center_y - frame_cy, 1)],
    }


@app.get('/perception/status')
async def perception_status():
    """Get perception engine status and metrics."""
    if not _perception_enabled or _perception_engine is None:
        return {'status': 'disabled', 'message': 'Call POST /perception/init first'}

    engine = _perception_engine
    latest = engine.get_latest_result()

    return {
        'status': 'running' if engine.is_running() else 'idle',
        'detection_enabled': engine._detection_enabled,
        'tracking_enabled': engine._tracking_enabled,
        'depth_enabled': engine._depth_enabled,
        'avoidance_enabled': engine._avoidance_enabled,
        'avoidance_active': _avoidance_active,
        'processing_fps': round(engine.current_fps, 1),
        'device': engine.config.device,
        'models': {
            'detection': 'YOLOv11',
            'depth': 'Depth Anything V2',
            'tracking': 'ByteTrack v2',
        },
        'latest_frame': bool(latest is not None),
        'active_tracks': len(latest.tracks) if latest else 0,
    }


if __name__ == '__main__':
    import uvicorn
    uvicorn.run(app, host='0.0.0.0', port=8000)
