@echo off
setlocal enableextensions
chcp 936 >nul
cd /d "%~dp0"

set "JAR=%~dp0EnvPrepare-0.0.1-SNAPSHOT.jar"

echo ============================================================
echo   一键准备并启动本应用的运行环境
echo   顺序: createdb 建库灌数，start 启动服务常驻，create-rabbitmq 建 MQ 资源
echo ============================================================
echo.

if not exist "%JAR%" (
    echo [错误] 未找到 EnvPrepare 可执行 jar: %JAR%
    echo        请确认本脚本与 jar 位于同一目录，或先执行打包生成该 jar。
    pause
    exit /b 1
)

echo ------------------------------------------------------------
echo  [1/3] 建库 + 灌数 (createdb)
echo ------------------------------------------------------------
java -jar "%JAR%" createdb
if errorlevel 1 (
    echo.
    echo [错误] createdb 失败，已中止后续步骤。
    pause
    exit /b 1
)

echo.
echo ------------------------------------------------------------
echo  [2/3] 启动环境服务 (start)，将在新窗口常驻运行
echo        按 application.yaml 拉起 RabbitMQ / MySQL / Ontop / Camunda
echo ------------------------------------------------------------
start "OntologyEnv - 环境服务常驻(关闭此窗口即停止全部服务)" cmd /k java -jar "%JAR%" start

echo.
echo        等待 RabbitMQ 就绪 ...
timeout /t 20 /nobreak >nul

echo.
echo ------------------------------------------------------------
echo  [3/3] 创建 RabbitMQ 资源 (create-rabbitmq)
echo ------------------------------------------------------------
java -jar "%JAR%" create-rabbitmq
if errorlevel 1 (
    echo.
    echo [警告] create-rabbitmq 未成功，RabbitMQ 可能仍在启动中。
    echo        待其就绪后可单独重试:
    echo          java -jar "%JAR%" create-rabbitmq
)

echo.
echo ============================================================
echo  [完成] 环境服务已在独立窗口常驻运行；关闭该窗口即停止全部服务。
echo         本次应用本体的 Worker 请另行启动。
echo ============================================================
pause
