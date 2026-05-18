@echo off
:: ============================================================
::  UAV Flight Control Platform - Frontend Dev Server
:: ============================================================
cd /d C:\UAVAgent\UAVAgent-main\uav-web
echo ============================================
echo   UAV Platform - Frontend Dev Server
echo   Browser URL: http://localhost:5173
echo ============================================
echo.
call npm run dev
pause
