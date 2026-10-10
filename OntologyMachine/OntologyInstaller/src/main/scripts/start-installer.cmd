@echo off
chcp 936 >nul
setlocal enableextensions

rem ============================================================
rem  本体开发环境安装器 启动脚本
rem  作用：先用随附的 JDK 压缩包解压出 JDK，再用该 JDK 运行安装器。
rem        因此本机无需预先安装 Java。
rem  要求：本脚本须与 OntologyInstaller.jar、softwares/ 放在同一目录。
rem ============================================================

set "HERE=%~dp0"
set "JAR=%HERE%OntologyInstaller.jar"
set "SOFT=%HERE%softwares"
set "JDKDIR=%HERE%jdk-runtime"

if not exist "%JAR%" (
    echo [错误] 未找到安装器：%JAR%
    echo 请把本脚本与 OntologyInstaller.jar 放在同一目录后重试。
    pause
    exit /b 1
)

set "JDKZIP="
for %%f in ("%SOFT%\jdk-*.zip") do if exist "%%f" set "JDKZIP=%%f"
if not defined JDKZIP (
    echo [错误] 未在 softwares 目录找到 JDK 压缩包 ^(jdk-*.zip^)：%SOFT%
    pause
    exit /b 1
)

set "JAVA="
for /d %%d in ("%JDKDIR%\*") do if exist "%%d\bin\java.exe" set "JAVA=%%d\bin\java.exe"

if not defined JAVA (
    echo 未检测到已解压的 JDK，正在解压：
    echo   压缩包：%JDKZIP%
    echo   目标目录：%JDKDIR%
    if not exist "%JDKDIR%" mkdir "%JDKDIR%"
    powershell -NoProfile -ExecutionPolicy Bypass -Command "Expand-Archive -LiteralPath '%JDKZIP%' -DestinationPath '%JDKDIR%' -Force"
    for /d %%d in ("%JDKDIR%\*") do if exist "%%d\bin\java.exe" set "JAVA=%%d\bin\java.exe"
)

if not defined JAVA (
    echo [错误] JDK 解压后仍未找到 bin\java.exe，请检查 JDK 压缩包是否有效。
    pause
    exit /b 1
)

echo 使用 JDK：%JAVA%
echo 正在启动安装器 ...
cd /d "%HERE%"
"%JAVA%" -jar "%JAR%"

set "RC=%errorlevel%"
if not "%RC%"=="0" (
    echo 安装器退出码：%RC%
    pause
)
endlocal
