@echo off
REM Script per visualizzare i log dell'app Wear OS

echo ============================================
echo   LOG VIEWER - AI Helper Wear OS
echo ============================================
echo.

REM Filtra i log per tag specifici
adb logcat -s MainActivity:D MainViewModel:D

REM Alternative:
REM Per vedere tutti i log dell'app:
REM adb logcat | findstr "com.base.aihelperwearos"

REM Per cancellare i log vecchi prima di iniziare:
REM adb logcat -c

pause

