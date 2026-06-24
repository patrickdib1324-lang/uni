@echo off
REM ============================================================
REM  UniPortal — start the website
REM  Double-click this file to run the server, then open
REM  http://localhost:8080 in your browser.
REM ============================================================
cd /d "%~dp0"

echo Starting UniPortal server...
echo Keep this window OPEN while you use the website.
echo Close this window to stop the server.
echo.

REM open the website in your default browser after a short delay
start "" cmd /c "timeout /t 3 >nul & start http://localhost:8080"

REM run the server (this line keeps the window open)
java -cp "out;lib/postgresql.jar" Server

echo.
echo Server stopped. Press any key to close.
pause >nul
