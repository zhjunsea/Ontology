@echo off
setlocal enableextensions
chcp 936 >nul
cd /d "%~dp0"

set "JAR=%~dp0EnvPrepare-0.0.1-SNAPSHOT.jar"
rem 选择 JDK：优先 ONTOLOGY_JAVA_HOME；其次随包 jdk(%~dp0jdk) / 开发环境 jdk(%~dp0..\jdk)；
rem          再仓库内置 jdk-27(%~dp0..\..\jdk-27)；再 JAVA_HOME；最后 PATH 中的 java。
set "JAVA_EXE=java"
if defined ONTOLOGY_JAVA_HOME if exist "%ONTOLOGY_JAVA_HOME%\bin\java.exe" set "JAVA_EXE=%ONTOLOGY_JAVA_HOME%\bin\java.exe"
if "%JAVA_EXE%"=="java" if exist "%~dp0jdk\bin\java.exe" set "JAVA_EXE=%~dp0jdk\bin\java.exe"
if "%JAVA_EXE%"=="java" if exist "%~dp0..\jdk\bin\java.exe" set "JAVA_EXE=%~dp0..\jdk\bin\java.exe"
if "%JAVA_EXE%"=="java" if exist "%~dp0..\..\jdk-27\bin\java.exe" set "JAVA_EXE=%~dp0..\..\jdk-27\bin\java.exe"
if "%JAVA_EXE%"=="java" if defined JAVA_HOME if exist "%JAVA_HOME%\bin\java.exe" set "JAVA_EXE=%JAVA_HOME%\bin\java.exe"

echo ============================================================
echo   一键准备并启动本应用（行业洞察）的运行环境
echo   start: 启动服务并确保已声明的持久化服务存在（缺则建、有则留）
echo   init : 重建环境（清空并重建已声明的持久化服务）后启动
echo ============================================================
echo.

if not exist "%JAR%" (
    echo [错误] 未找到 EnvPrepare 可执行 jar: %JAR%
    echo        请确认本脚本与 jar 位于同一目录，或先执行打包生成该 jar。
    pause
    exit /b 1
)

set "MODE=start"
set /p RESET=是否重建环境？重建将清空已声明持久化服务中的旧数据 [y/N]: 
if /i "%RESET%"=="y" set "MODE=init"

echo.
echo ------------------------------------------------------------
echo  以 %MODE% 模式在独立窗口常驻运行（关闭该窗口即停止全部服务）
echo  start 缺则建、有则留；init 先清空重建已声明服务再启动
echo ------------------------------------------------------------
start "OntologyEnv - 环境服务常驻(关闭此窗口即停止全部服务)" cmd /k ""%JAVA_EXE%" -jar "%JAR%" %MODE% --yes"

echo.
echo ============================================================
echo  [完成] 环境服务已在独立窗口常驻运行；关闭该窗口即停止全部服务。
echo         本次应用本体的 Worker 请另行启动。
echo ============================================================
pause
