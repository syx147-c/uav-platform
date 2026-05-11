#!/bin/bash
# ============================================
# WSL2 仿真环境一键启动脚本
# 用法: bash start-sim.sh [gazebo|bridge|all]
# ============================================

MODE=${1:-all}

start_px4() {
    echo ">>> 启动 PX4 SITL + Gazebo Classic..."
    cd ~/px4v1.14.3
    make px4_sitl gazebo-classic
}

start_bridge() {
    echo ">>> 启动 MAVSDK Bridge (FastAPI)..."
    cd ~
    # 检查 bridge.py 是否存在
    if [ ! -f ~/bridge.py ]; then
        echo "ERROR: ~/bridge.py 不存在，请先创建 bridge.py"
        exit 1
    fi
    python3 -m uvicorn bridge:app --host 0.0.0.0 --port 8000
}

case "$MODE" in
    gazebo) start_px4 ;;
    bridge) start_bridge ;;
    all)
        echo "=== 启动 PX4 仿真 (终端1) ==="
        start_px4 &
        sleep 5
        echo "=== 启动 Bridge (终端2) ==="
        start_bridge
        ;;
    *)
        echo "用法: bash start-sim.sh [gazebo|bridge|all]"
        ;;
esac
