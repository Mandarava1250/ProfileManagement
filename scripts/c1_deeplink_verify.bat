@echo off
chcp 65001 >nul
setlocal enabledelayedexpansion
set ADB=D:\Software\Code\Android\Sdk\platform-tools\adb.exe
set PKG=com.example.personalassistant.debug
set ACT=com.example.personalassistant.MainActivity
set OUT=D:\Projects\Code\Project-Source\ProfileManagement\docs\verified

if not exist "%OUT%" mkdir "%OUT%"

echo [1/4] tasks/detail/1
call :run tasks/detail/1 task_reminder c1-ui-task1
echo [2/4] tasks/edit/1
call :run tasks/edit/1 task_reminder c1-ui-task-edit
echo [3/4] calendar/edit/1
call :run calendar/edit/1 event_reminder c1-ui-event-edit
echo [4/4] timetable/edit/1
call :run timetable/edit/1 course_reminder c1-ui-course-edit
echo ALL DONE
goto :eof

:run
%ADB% shell am force-stop %PKG%
call :sleep 2
%ADB% shell am start -n %PKG%/%ACT% --es nav_route %1 --es deeplink_source %2 --activity-clear-top
call :sleep 8
%ADB% shell uiautomator dump /sdcard/%3.xml
call :sleep 2
%ADB% pull /sdcard/%3.xml "%OUT%\%3.xml"
goto :eof

:sleep
ping -n %1 127.0.0.1 >nul
goto :eof