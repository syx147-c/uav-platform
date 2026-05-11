@echo off
:: ============================================
:: UAV 飞行控制平台 - 前端启动脚本
:: 双击运行或: start-frontend.bat
:: ============================================
chcp 65001 >nul
cd /d C:\UAVAgent\UAVAgent-main\uav-web
echo ============================================
echo   UAV 飞行控制平台 - 前端开发服务器
echo   浏览器访问: http://localhost:5173
echo ============================================
echo.
call npm run dev
pause
