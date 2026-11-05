@echo off
chcp 936 >nul
setlocal enableextensions enabledelayedexpansion

rem ============================================================
rem  本体开发环境安装器 引导脚本
rem  作用：把随附 JDK 压缩包解压出 JDK，并用该 JDK 运行安装器，
rem        因此本机无需预装 Java。
rem  要求：本脚本须与 OntologyInstaller.jar、softwares/ 位于同一目录。
rem ============================================================

set "HERE=%~dp0"
set "JAR=%HERE%OntologyInstaller.jar"
set "SOFT=%HERE%softwares"
set "JDKDIR=%HERE%jdk-runtime"

rem ---- 0) 前置校验：要求 Windows 10 及以上（低于则拒绝启动安装器）----
call :requirewin10
if errorlevel 1 exit /b 1

if not exist "%JAR%" (
    echo [错误] 未找到安装器：%JAR%
    echo 请把本脚本与 OntologyInstaller.jar 放在同一目录后重试。
    pause
    exit /b 1
)

rem ---- 1) 先探测是否已有可用 JDK（直接 bin\java.exe 或已解压的嵌套目录）----
set "JAVA="
call :findjava
if defined JAVA goto :run

rem ---- 2) 定位随附 JDK 压缩包 ----
set "JDKZIP="
for %%f in ("%SOFT%\jdk-*.zip") do if exist "%%f" set "JDKZIP=%%f"
if not defined JDKZIP (
    echo [错误] 未在 softwares 目录找到 JDK 压缩包 ^(jdk-*.zip^)：%SOFT%
    pause
    exit /b 1
)

echo 未检测到已解压的 JDK，正在解压 ...
echo   压缩包：%JDKZIP%
echo   目标目录：%JDKDIR%
if not exist "%JDKDIR%" mkdir "%JDKDIR%"

rem ---- 3) 解压：优先用系统自带 tar.exe（Win10 1803+ 自带、无进度条刷屏），
rem         找不到 tar 或解压失败时回退 Shell.Application COM（PS2.0 / Win7 兼容）。
set "TAR=%SystemRoot%\System32\tar.exe"
if not exist "%TAR%" set "TAR="
if defined TAR (
    "%TAR%" -xf "%JDKZIP%" -C "%JDKDIR%"
    if errorlevel 1 call :psunzip
) else (
    call :psunzip
)

call :findjava
if not defined JAVA (
    echo [错误] JDK 解压后仍未找到 bin\java.exe，请检查 JDK 压缩包是否有效。
    echo   压缩包：%JDKZIP%
    echo   目标目录：%JDKDIR%
    echo   该目录现有内容（应含 jdk-27 等子目录）：
    dir /b "%JDKDIR%" 2>nul
    pause
    exit /b 1
)

:run
echo 使用 JDK：%JAVA%
echo 正在启动安装器 ...
cd /d "%HERE%"
"%JAVA%" -jar "%JAR%"

set "RC=%errorlevel%"
if "%RC%"=="0" (
    rem 安装成功：清理仅用于运行安装器的 JDK 运行时（安装目录内已有独立 JDK），回收分发目录空间。
    if exist "%JDKDIR%" rd /s /q "%JDKDIR%" >nul 2>&1
) else (
    echo 安装器退出码：%RC%
    pause
)
endlocal & exit /b %RC%

rem ===================== 子过程 =====================
:requirewin10
rem 前置校验：Windows 主版本 >= 10（Win10/Win11 的 CurrentMajorVersionNumber 分别为 0xa/0xb）。
rem 该注册表值仅 Windows 10 起存在；Win8.1 及以下取不到，直接拒绝。
set "WINRAW="
for /f "tokens=3" %%v in ('reg query "HKLM\SOFTWARE\Microsoft\Windows NT\CurrentVersion" /v CurrentMajorVersionNumber 2^>nul ^| findstr /i "CurrentMajorVersionNumber"') do set "WINRAW=%%v"
set /a WINMAJOR=0
if defined WINRAW set /a WINMAJOR=%WINRAW% 2>nul
if %WINMAJOR% GEQ 10 exit /b 0
echo ============================================================
echo [错误] 当前 Windows 版本低于 Windows 10（主版本 %WINMAJOR%）。
echo        本安装程序要求 Windows 10 及以上版本，无法继续。请升级系统后重试。
echo ============================================================
pause
exit /b 1

:findjava
rem 递归定位 <JDKDIR> 下任意层级的 bin\java.exe（JDK zip 可能为 jdk-27_windows-x64_bin/jdk-27/bin/java.exe）
set "JAVA="
if exist "%JDKDIR%\bin\java.exe" set "JAVA=%JDKDIR%\bin\java.exe"
if defined JAVA goto :eof
set "JF=%TEMP%\oj_findjava_%RANDOM%%RANDOM%.txt"
if exist "%JF%" del /f /q "%JF%" >nul 2>&1
powershell -NoProfile -ExecutionPolicy Bypass -Command "$h = Get-ChildItem -LiteralPath '%JDKDIR%' -Recurse -Filter java.exe -ErrorAction SilentlyContinue | Where-Object { -not $_.PSIsContainer -and $_.DirectoryName -match '\\bin$' } | Select-Object -First 1; if ($h) { Set-Content -LiteralPath '%JF%' -Value $h.FullName -Encoding ASCII }"
if exist "%JF%" set /p JAVA=<"%JF%"
if exist "%JF%" del /f /q "%JF%" >nul 2>&1
goto :eof

:psunzip
powershell -NoProfile -ExecutionPolicy Bypass -Command "$ErrorActionPreference='Stop'; $zip='%JDKZIP%'; $dst='%JDKDIR%'; if(-not (Test-Path -LiteralPath $dst)){ New-Item -ItemType Directory -Path $dst | Out-Null }; $sh=New-Object -ComObject Shell.Application; try { $sh.NameSpace($dst).CopyHere($sh.NameSpace($zip).Items(), 0x14) } catch { Write-Error $_; exit 1 }; $deadline=(Get-Date).AddMinutes(15); while((Get-Date) -lt $deadline){ $j=Get-ChildItem -LiteralPath $dst -Recurse -Filter java.exe -ErrorAction SilentlyContinue | Where-Object { $_.DirectoryName -match '\\bin$' } | Select-Object -First 1; if($j){ break }; Start-Sleep -Seconds 2 }"
goto :eof
