@echo off
setlocal
cd /d "%~dp0"

echo ================================================
echo   GreenThumb AI - Frontend Launcher
echo ================================================
echo.

REM ---- 1. Check node ----
where node >nul 2>nul
if errorlevel 1 (
  echo [ERROR] "node" was not found on PATH.
  echo         Please install Node.js 18 or newer: https://nodejs.org/
  echo.
  pause
  exit /b 1
)
for /f "delims=" %%v in ('node -v') do set NODEVER=%%v
echo [OK] Node %NODEVER%

REM ---- 2. Install dependencies on first run ----
REM      Uses npm.cmd instead of npm on purpose. PowerShell's default
REM      execution policy is Restricted and refuses to load npm.ps1,
REM      which fails with "running scripts is disabled on this system".
if not exist "node_modules" (
  echo.
  echo [1/2] Installing dependencies. First run takes 1-3 minutes...
  echo.
  call npm.cmd install
  if errorlevel 1 (
    echo.
    echo [ERROR] npm install failed.
    echo.
    echo   If it was a network timeout, switch to a mirror and retry:
    echo       npm.cmd config set registry https://registry.npmmirror.com
    echo.
    pause
    exit /b 1
  )
) else (
  echo [OK] node_modules already present, skipping install
)

REM ---- 3. Start the dev server ----
echo.
echo [2/2] Starting dev server...
echo.
echo       Browser opens http://localhost:5173/ automatically
echo       Login with any username, e.g. demo / 123456
echo       Usernames starting with "admin" also get the Admin menu
echo       Press Ctrl+C to stop the server
echo.
call npm.cmd run dev

echo.
echo Server stopped.
pause
