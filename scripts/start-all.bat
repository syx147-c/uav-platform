@echo off
setlocal enabledelayedexpansion

:: ============================================================
::  UAV Flight Control Platform - One-Click Startup Script
::  Prerequisites:
::    1. WSL2 with Ubuntu installed
::    2. PX4-Autopilot cloned to WSL: ~/PX4-Autopilot
::    3. bridge.py in WSL home: ~/bridge.py
::    4. Docker Desktop running
:: ============================================================

echo.
echo   ========================================
echo     UAV Flight Control Platform
echo     One-Click Startup
echo   ========================================
echo.

echo [1/5] Starting Docker MySQL...
cd /d C:\UAVAgent\UAVAgent-main
docker compose -f docker-compose.yml up -d mysql 2>nul
if %errorlevel% neq 0 (
    echo    Warning: Docker may not be running, continuing anyway.
)

echo [2/5] Starting PX4 SITL + Gazebo in WSL2...
:: cmd /k keeps the window open so you can see PX4 output or errors
start "PX4-SITL-Gazebo" cmd /k "wsl -e bash -c 'cd ~/px4v1.14.3 && make px4_sitl gazebo-classic'"

echo [3/5] Starting MAVSDK Bridge in WSL2...
start "MAVSDK-Bridge" cmd /k "wsl -e bash -c 'cd ~ && python3 bridge.py'"

echo [4/5] Setting up WSL2-to-Windows port forwarding...
powershell -Command "Start-Process powershell -Verb RunAs -ArgumentList '-ExecutionPolicy Bypass -File C:\UAVAgent\UAVAgent-main\scripts\setup-portproxy.ps1'" 2>nul

echo [5/5] Starting Vue frontend dev server...
start "Vue-Dev-Server" cmd /k "cd /d C:\UAVAgent\UAVAgent-main\uav-web && npm run dev"

echo.
echo   ========================================
echo     Startup sequence complete!
echo.
echo     NOTE: PX4 SITL takes 10-30 seconds to initialize.
echo     Wait until you see the Gazebo window and drone model.
echo.
echo     Then start the Java backend in IDEA:
echo       Run -> UavAgentMainApplication
echo.
echo     Browser URL: http://localhost:5173
echo   ========================================
echo.

echo   ---- PX4 SITL Status Check ----
echo   Checking PX4 installation in WSL...
wsl -e bash -c "if [ -d ~/px4v1.14.3 ]; then echo '   [OK] PX4 (~/px4v1.14.3) found'; else echo '   [ERROR] PX4 NOT found at ~/px4v1.14.3 - please fix the path in this script'; fi"
wsl -e bash -c "if [ -f ~/bridge.py ]; then echo '   [OK] bridge.py found'; else echo '   [ERROR] bridge.py NOT found at ~/bridge.py - please fix the path in this script'; fi"
wsl -e bash -c "if command -v gazebo >/dev/null 2>&1; then echo '   [OK] gazebo command found'; else echo '   [WARN] gazebo command not found in PATH'; fi"
echo   ------------------------------------

echo.
pause
