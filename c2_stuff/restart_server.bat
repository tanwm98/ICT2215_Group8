@echo off
echo Stopping any running Python scripts...
taskkill /f /im python.exe /t 2>nul
echo Waiting a moment for ports to be released...
timeout /t 2 /nobreak >nul

echo Starting C2 server...
start python c2.py

echo C2 server started!
echo Web admin console available at: http://localhost:8080
echo C2 server listening on port 42069
echo.
echo Press any key to exit...
pause >nul