@echo off
setlocal enableextensions
chcp 936 >nul
cd /d "%~dp0"

echo ============================================================
echo   本体应用开发工程 - 开发环境部署
echo   应用: @{APP_NAME} @{APP_VERSION}
echo   用法: deploy-dev.cmd [目标开发环境目录]
echo         - 目标不存在        -^> 新建开发环境
echo         - 目标是已有开发环境 -^> 并入本模块，与 Example 共用
echo   （路径含空格请用双引号括起，如 deploy-dev.cmd "D:\my env"）
echo ============================================================
echo.

powershell -NoProfile -ExecutionPolicy Bypass -File "%~dp0tools\deploy-dev.ps1" %*
set "RC=%ERRORLEVEL%"
chcp 936 >nul 2>&1

if not "%RC%"=="0" (
    echo.
    echo [错误] 开发环境部署未成功（退出码=%RC%），请查看上方输出。
    pause
    exit /b %RC%
)

echo.
echo [完成] 开发环境部署结束。
pause
