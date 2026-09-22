@echo off
REM ===========================================================================
REM  Jingfang fangzheng JUnit suite - one-click runner (cmd wrapper)
REM  All arguments are forwarded to run_all_fangzheng_tests.ps1, e.g.:
REM      run_all_fangzheng_tests.cmd -Fork
REM      run_all_fangzheng_tests.cmd -Classes ZabingFangzhengTest
REM      run_all_fangzheng_tests.cmd -SkipBuild
REM ===========================================================================
powershell -NoProfile -ExecutionPolicy Bypass -File "%~dp0run_all_fangzheng_tests.ps1" %*
exit /b %ERRORLEVEL%
