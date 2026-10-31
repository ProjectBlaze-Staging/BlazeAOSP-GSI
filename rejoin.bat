@echo off
setlocal enabledelayedexpansion

echo ==============================================
echo   BlazeAOSP GSI Reassembly Utility (Windows)
echo ==============================================

if exist "system.img.gz" (
    echo [*] Found existing system.img.gz.
    goto :verify
)

echo [*] Reassembling system.img.gz from parts...
copy /b system.img.gz.part* system.img.gz

:verify
if exist "system.img.gz" (
    echo [+] Successfully created system.img.gz!
    echo [*] Verifying SHA256 with certutil...
    certutil -hashfile system.img.gz SHA256
) else (
    echo [-] Failed to create system.img.gz!
)

echo.
echo Flash Instructions:
echo   DSU: Push system.img.gz to /sdcard/Download/ and trigger DSU via ADB
echo   Fastboot: Extract system.img using 7-Zip and flash via fastbootd
pause
