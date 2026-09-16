@echo off
chcp 65001 >nul
setlocal enabledelayedexpansion
set ADB=D:\Software\Code\Android\Sdk\platform-tools\adb.exe
set PKG=com.example.personalassistant.debug
set ACT=com.example.personalassistant.MainActivity
set OUT=D:\Projects\Code\Project-Source\ProfileManagement\docs\verified

%ADB% shell pm grant %PKG% android.permission.POST_NOTIFICATIONS >nul 2>&1

echo [1/2] portrait (native)
%ADB% shell wm size reset
%ADB% shell settings put system accelerometer_rotation 1
%ADB% shell am force-stop %PKG%
call :sleep 2
%ADB% shell am start -n %PKG%/%ACT% --activity-clear-top
call :sleep 9
%ADB% shell uiautomator dump /sdcard/c2-portrait.xml
call :sleep 2
%ADB% pull /sdcard/c2-portrait.xml "%OUT%\c2-portrait.xml"

echo [2/2] landscape 1280x800
%ADB% shell wm size 1280x800
%ADB% shell settings put system accelerometer_rotation 0
%ADB% shell settings put system user_rotation 1
%ADB% shell am force-stop %PKG%
call :sleep 2
%ADB% shell am start -n %PKG%/%ACT% --activity-clear-top
call :sleep 9
%ADB% shell uiautomator dump /sdcard/c2-landscape.xml
call :sleep 2
%ADB% pull /sdcard/c2-landscape.xml "%OUT%\c2-landscape.xml"

echo restore
%ADB% shell wm size reset
%ADB% shell settings put system user_rotation 0
%ADB% shell settings put system accelerometer_rotation 1
echo ALL DONE
goto :eof

:sleep
ping -n %1 127.0.0.1 >nul
goto :eof