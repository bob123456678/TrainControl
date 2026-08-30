@echo off
rem Launches the manual-test triage app in its own window, from wherever this is run.
rem
rem   From PowerShell or cmd:  docs\manual-tests\launch-triage.bat
rem   Or just double-click it in Explorer.
rem
rem %~dp0 is this file's own folder, so it works the same regardless of the current directory.

start "" py -3 "%~dp0triage.py"
