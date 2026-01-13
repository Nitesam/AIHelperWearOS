@echo off
echo ============================================
echo   LOG VIEWER - AI Helper Wear OS
echo ============================================
echo.

adb logcat -s MainActivity:D MainViewModel:D

pause
