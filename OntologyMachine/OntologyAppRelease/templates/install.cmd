@echo off
setlocal enableextensions
chcp 936 >nul
cd /d "%~dp0"

rem [应用级] 随包 JDK：若已解压则设 JAVA_HOME 并前置 PATH
rem          仅本次安装进程生效，不写系统/用户环境变量
for /d %%D in ("%~dp0runtime\jdk\jdk-*") do set "JAVA_HOME=%%~fD"
if defined JAVA_HOME set "PATH=%JAVA_HOME%\bin;%PATH%"

echo ============================================================
echo   本体应用运行环境 - 安装器
echo   应用: @{APP_NAME} @{APP_VERSION}
echo   本脚本将解压依赖软件、生成配置与启动脚本。
echo   安装目录: %~dp0
echo ============================================================
echo.

powershell -NoProfile -ExecutionPolicy Bypass -File "%~dp0tools\install.ps1"
set "RC=%ERRORLEVEL%"
chcp 936 >nul 2>&1

if not "%RC%"=="0" (
    echo.
    echo [错误] 安装未成功完成（退出码=%RC%），请查看上方输出。
    pause
    exit /b %RC%
)

echo.
echo [完成] 安装结束。请先运行 start-env.cmd 启动环境服务，
echo        再运行 start-app.cmd 启动应用。
pause
