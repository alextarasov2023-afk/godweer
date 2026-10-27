@echo off
title GodWeer Wet World - Full Build
echo ================================================
echo   GodWeer - Wet World + Static Rain + Splashes
echo ================================================

echo [1/4] Cleaning old build...
del /q "C:\Users\1\OneDrive\Desktop\GodWeer-26.2\build\libs\*.jar" >nul 2>&1
del /q "C:\Users\1\OneDrive\Desktop\GodWeer-26.2\build\*.jar" >nul 2>&1
echo OK

echo [2/4] Building with parameters...
cd "C:\Users\1\OneDrive\Desktop\GodWeer-26.2"

gradlew build --continue -q

if %errorlevel% == 0 (
    echo ================================================
    echo   SUCCESS! New jar created:
    dir /b /s "build\libs\*.jar" | findstr /v "."
    echo ================================================
) else (
    echo ================================================
    echo   BUILD FAILED!
    echo   Check console for errors.
    echo ================================================
)

pause