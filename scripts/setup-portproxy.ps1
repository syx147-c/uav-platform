# ============================================
# Windows 端口转发配置脚本（需管理员运行）
# 用法（管理员 PowerShell）:
#   cd C:\UAVAgent\UAVAgent-main\scripts
#   .\setup-portproxy.ps1
# ============================================

Write-Host ">>> 检测 WSL2 IP 地址..." -ForegroundColor Cyan

# 获取 WSL2 的 IP（从 wsl 命令输出提取）
$wslOutput = wsl -e bash -c "ip route show default | awk '{print \$3}'"
$wslIP = ($wslOutput -split '\s+')[0].Trim()

if (-not $wslIP) {
    Write-Host "ERROR: 无法获取 WSL2 IP，请确认 WSL2 正在运行" -ForegroundColor Red
    exit 1
}
Write-Host "WSL2 IP: $wslIP" -ForegroundColor Green

# 删除旧规则
Write-Host ">>> 清除旧的端口转发规则..." -ForegroundColor Yellow
netsh interface portproxy delete v4tov4 listenport=8000 listenaddress=0.0.0.0 2>$null
netsh interface portproxy delete v4tov4 listenport=14540 listenaddress=0.0.0.0 2>$null

# 添加新规则
Write-Host ">>> 添加端口转发: 0.0.0.0:8000 -> $wslIP`:8000" -ForegroundColor Cyan
netsh interface portproxy add v4tov4 listenport=8000 listenaddress=0.0.0.0 connectport=8000 connectaddress=$wslIP
netsh interface portproxy add v4tov4 listenport=14540 listenaddress=0.0.0.0 connectport=14540 connectaddress=$wslIP

# 显示当前规则
Write-Host ">>> 当前端口转发规则:" -ForegroundColor Green
netsh interface portproxy show all

# 测试连通性
Write-Host ">>> 测试 Bridge 连通性..." -ForegroundColor Cyan
try {
    $response = Invoke-WebRequest -Uri "http://localhost:8000/telemetry" -TimeoutSec 3 -UseBasicParsing
    Write-Host "SUCCESS: Bridge 连通正常！" -ForegroundColor Green
} catch {
    Write-Host "WARN: Bridge 暂不可达，请确保 WSL2 中 bridge.py 已启动" -ForegroundColor Yellow
}

Write-Host "`n=== 完成 ===" -ForegroundColor Green
