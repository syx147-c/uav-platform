"""
Perception Server — Standalone FastAPI Service
================================================
REST API for running perception independently of bridge.py.

Start:
  python perception/perception_server.py --port 8001

Endpoints:
  POST /detect          — Run object detection
  POST /track/start     — Start tracking session
  GET  /track/status    — Get current tracking state
  POST /depth           — Get depth estimation
  POST /avoid/enable    — Enable obstacle avoidance
  POST /avoid/disable   — Disable obstacle avoidance
  POST /avoid/command   — Get current avoidance velocity
  GET  /status          — System status and metrics
"""

import sys
import time
import base64
from io import BytesIO
from pathlib import Path
from typing import Optional

import numpy as np
from fastapi import FastAPI, HTTPException
from fastapi.middleware.cors import CORSMiddleware
from pydantic import BaseModel, Field
import uvicorn

# Add project root to path
sys.path.insert(0, str(Path(__file__).parent.parent))

from perception.config import PerceptionConfig
from perception.perception_engine import PerceptionEngine
from perception.camera_source import WebcamSource, VideoFileSource
import logging

logging.basicConfig(level=logging.INFO)
log = logging.getLogger("perception-server")

# ─── FastAPI App ───
app = FastAPI(
    title="UAV Perception Engine v3.0",
    description="SOTA computer vision pipeline: YOLOv11 + ByteTrack + Depth Anything V2 + Obstacle Avoidance",
    version="3.0.0",
)
app.add_middleware(CORSMiddleware, allow_origins=["*"], allow_methods=["*"], allow_headers=["*"])

# ─── Global Engine ───
config = PerceptionConfig()
engine = PerceptionEngine(config)
engine.enable_detection().enable_tracking().enable_depth().enable_avoidance()

_sessions: dict = {}  # session_id → session config


# ─── Models ───

class ImageInput(BaseModel):
    """Base64-encoded image input."""
    image_base64: str = Field(..., description="Base64-encoded JPEG/PNG image")
    session_id: str = Field(default="default", description="Session identifier")


class DetectResponse(BaseModel):
    detections: list
    count: int
    elapsed_ms: float


class TrackResponse(BaseModel):
    tracks: list
    count: int
    session_id: str


class DepthResponse(BaseModel):
    obstacle_regions: list
    obstacle_detected: bool
    depth_map_shape: list
    elapsed_ms: float


class AvoidanceResponse(BaseModel):
    enabled: bool
    command: Optional[dict]
    message: str


class StatusResponse(BaseModel):
    detection_enabled: bool
    tracking_enabled: bool
    depth_enabled: bool
    avoidance_enabled: bool
    processing_fps: float
    active_sessions: int
    device: str
    models: dict


# ─── Helpers ───

def decode_image(b64_str: str) -> np.ndarray:
    """Decode base64 image to numpy array."""
    import cv2
    img_data = base64.b64decode(b64_str)
    nparr = np.frombuffer(img_data, np.uint8)
    return cv2.imdecode(nparr, cv2.IMREAD_COLOR)


# ─── Endpoints ───

@app.post("/detect", response_model=DetectResponse)
async def detect(input: ImageInput):
    """Run YOLOv11 object detection on an image."""
    frame = decode_image(input.image_base64)
    if frame is None:
        raise HTTPException(400, "Invalid image data")

    start = time.perf_counter()
    detections = engine.detector.detect(frame)
    elapsed_ms = (time.perf_counter() - start) * 1000

    return DetectResponse(
        detections=[d.to_dict() for d in detections],
        count=len(detections),
        elapsed_ms=round(elapsed_ms, 1),
    )


@app.post("/track/start", response_model=TrackResponse)
async def start_tracking(input: ImageInput):
    """Run detection + tracking on an image."""
    frame = decode_image(input.image_base64)
    if frame is None:
        raise HTTPException(400, "Invalid image data")

    detections = engine.detector.detect(frame)
    tracks = engine.tracker.update(detections)

    return TrackResponse(
        tracks=[t.get_state() for t in tracks],
        count=len(tracks),
        session_id=input.session_id,
    )


@app.get("/track/status/{session_id}")
async def track_status(session_id: str = "default"):
    """Get current tracking state."""
    result = engine.get_latest_result()
    if result is None or not result.tracks:
        return {"tracks": [], "count": 0}
    return {
        "tracks": [t.get_state() for t in result.tracks],
        "count": len(result.tracks),
        "session_id": session_id,
    }


@app.post("/depth", response_model=DepthResponse)
async def estimate_depth(input: ImageInput):
    """Run Depth Anything V2 on an image."""
    frame = decode_image(input.image_base64)
    if frame is None:
        raise HTTPException(400, "Invalid image data")

    start = time.perf_counter()
    depth_map = engine.depth_estimator.estimate(frame)
    obstacles = engine.depth_estimator.detect_obstacles(depth_map)
    elapsed_ms = (time.perf_counter() - start) * 1000

    return DepthResponse(
        obstacle_regions=obstacles,
        obstacle_detected=any(o["obstacle"] for o in obstacles),
        depth_map_shape=list(depth_map.shape),
        elapsed_ms=round(elapsed_ms, 1),
    )


@app.post("/avoid/enable", response_model=AvoidanceResponse)
async def enable_avoidance():
    """Enable obstacle avoidance mode."""
    engine.enable_avoidance()
    return AvoidanceResponse(
        enabled=True,
        command=None,
        message="Obstacle avoidance enabled. Depth estimation will run every frame.",
    )


@app.post("/avoid/disable", response_model=AvoidanceResponse)
async def disable_avoidance():
    """Disable obstacle avoidance mode."""
    engine._avoidance_enabled = False
    return AvoidanceResponse(
        enabled=False,
        command=None,
        message="Obstacle avoidance disabled.",
    )


@app.get("/avoid/command")
async def avoidance_command():
    """Get current avoidance velocity command."""
    result = engine.get_latest_result()
    if result is None or result.avoidance_cmd is None:
        return {"command": None, "message": "No avoidance data available yet"}
    return {
        "command": result.avoidance_cmd.to_dict(),
        "message": "Active" if result.avoidance_cmd.obstacle_detected else "Clear path",
    }


@app.get("/status", response_model=StatusResponse)
async def system_status():
    """Get perception system status."""
    return StatusResponse(
        detection_enabled=engine._detection_enabled,
        tracking_enabled=engine._tracking_enabled,
        depth_enabled=engine._depth_enabled,
        avoidance_enabled=engine._avoidance_enabled,
        processing_fps=round(engine.current_fps, 1),
        active_sessions=len(_sessions),
        device=config.device,
        models={
            "detection": config.detection_model,
            "depth": config.depth_model.split("/")[-1],
        },
    )


@app.get("/health")
async def health():
    return {"status": "ok", "version": "3.0.0"}


# ─── Main ───

if __name__ == "__main__":
    import argparse

    parser = argparse.ArgumentParser(description="UAV Perception Server")
    parser.add_argument("--port", type=int, default=8001, help="Server port")
    parser.add_argument("--device", type=str, default="cpu", help="Device (cpu/cuda)")
    parser.add_argument("--camera", type=int, default=-1, help="Camera device ID (-1 for none)")
    parser.add_argument("--video", type=str, default=None, help="Video file path")
    args = parser.parse_args()

    config.device = args.device

    if args.video:
        engine.set_camera_source(VideoFileSource(args.video, loop=True))
    elif args.camera >= 0:
        engine.set_camera_source(WebcamSource(args.camera))

    log.info("Starting Perception Server on port %d (device=%s)", args.port, args.device)
    uvicorn.run(app, host="0.0.0.0", port=args.port)
