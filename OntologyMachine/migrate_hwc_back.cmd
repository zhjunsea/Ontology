@echo off
chcp 65001 >nul
echo Running HWCodeArtsDir migration back to OntologyMachine...
echo Make sure ALL IntelliJ IDEA windows are CLOSED before continuing.
echo.
powershell -NoProfile -ExecutionPolicy Bypass -File "%~dp0migrate_hwc_back.ps1"
echo.
echo Script finished. See log at D:\work\Ontology\OntologyMachine\migrate_hwc_back.log
pause
