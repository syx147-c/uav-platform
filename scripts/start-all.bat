@echo off
chcp 65001 >nul
:: ============================================
:: UAV 飞行控制平台 — 一键启动所有服务
:: ============================================

echo.
echo   ========================================
echo     UAV 飞行控制平台 - 一键启动
echo   ========================================
echo.

echo [1/5] 启动 Docker 基础设施...
cd /d C:\UAVAgent\UAVAgent-main
docker compose -f docker-compose.yml up -d 2>nul
if %errorlevel% neq 0 echo    Docker 可能已在运行，跳过...

echo [2/5] 启动 WSL2 仿真 (PX4 + Gazebo)...
start wsl -e bash -c "cd ~/px4v1.14.3 && make px4_sitl gazebo-classic"

echo [3/5] 启动 MAVSDK Bridge...
start wsl -e bash -c "python3 -m uvicorn bridge:app --host 0.0.0.0 --port 8000"

echo [4/5] 配置端口转发 (需管理员权限)...
powershell -Command "Start-Process powershell -Verb RunAs -ArgumentList '-ExecutionPolicy Bypass -File C:\UAVAgent\UAVAgent-main\scripts\setup-portproxy.ps1'"

echo [5/5] 启动前端开发服务器...
start cmd /k "cd /d C:\UAVAgent\UAVAgent-main\uav-web && npm run dev"

echo.
echo   ========================================
echo     启动完毕！
echo.
echo     请手动在 IDEA 中运行 UavAgentMainApplication
echo     浏览器访问: http://localhost:5173
echo   ========================================
echo.
pause
