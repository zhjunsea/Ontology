@echo off
setlocal enableextensions enabledelayedexpansion
chcp 936 >nul
cd /d "%~dp0"

set "JAR=%~dp0EnvPrepare-0.0.1-SNAPSHOT.jar"
rem ============================================================
rem 定位 JDK（脚本内不写死任何安装路径）：
rem  1) 先读本模块 application.yaml 的 env-prepare.java-home（分发时由安装器注入实际目录）；
rem  2) 其次环境变量 ONTOLOGY_JAVA_HOME；3) 再次 JAVA_HOME；4) 最后 PATH 中的 java。
rem 其余环境软件（Ontop/Camunda/RabbitMQ/MySQL/Erlang 等）位置均由 EnvPrepare
rem 读取同一 application.yaml 的 env-prepare.* 解析，脚本内不写死。
rem ============================================================
set "CFG=%~dp0src\main\resources\application.yaml"
set "JAVA_HOME_CFG="
if exist "%CFG%" (
    for /f "usebackq tokens=1,* delims=:" %%A in (`findstr /r /c:"^[ ]*java-home[ ]*:" "%CFG%"`) do set "JAVA_HOME_CFG=%%B"
    if defined JAVA_HOME_CFG (
        for /f "tokens=1 delims=#" %%D in ("!JAVA_HOME_CFG!") do set "JAVA_HOME_CFG=%%D"
        for /f "tokens=* delims= " %%C in ("!JAVA_HOME_CFG!") do set "JAVA_HOME_CFG=%%C"
        set "JAVA_HOME_CFG=!JAVA_HOME_CFG:'=!"
    )
)
set "JAVA_EXE="
if defined JAVA_HOME_CFG if exist "%JAVA_HOME_CFG%\bin\java.exe" set "JAVA_EXE=%JAVA_HOME_CFG%\bin\java.exe"
if not defined JAVA_EXE if defined ONTOLOGY_JAVA_HOME if exist "%ONTOLOGY_JAVA_HOME%\bin\java.exe" set "JAVA_EXE=%ONTOLOGY_JAVA_HOME%\bin\java.exe"
if not defined JAVA_EXE if defined JAVA_HOME if exist "%JAVA_HOME%\bin\java.exe" set "JAVA_EXE=%JAVA_HOME%\bin\java.exe"
if not defined JAVA_EXE for /f "delims=" %%J in (`where java 2^>nul`) do if not defined JAVA_EXE set "JAVA_EXE=%%J"
if not defined JAVA_EXE (
    echo [错误] 未找到可用的 JDK。
    echo        请在 application.yaml 设置 env-prepare.java-home，或设置环境变量 JAVA_HOME / ONTOLOGY_JAVA_HOME，
    echo        或将 java 加入 PATH 后重试。
    pause
    exit /b 1
)

echo ============================================================
echo   OntologyEnv - 安全停止环境
echo   复用外部环境(MySQL/RabbitMQ/Camunda等)一律保留，不终止；
echo   自启动服务若仍被使用会询问是否终止；Ontop 自动改过的端口将回滚。
echo ============================================================
echo.

if not exist "%JAR%" (
    echo [错误] 未找到 EnvPrepare 可执行 jar: %JAR%
    pause
    exit /b 1
)

"%JAVA_EXE%" -jar "%JAR%" stop
set "RC=%ERRORLEVEL%"
echo.
echo [完成] 停止流程结束（返回码 %RC%）。
pause
exit /b %RC%
