@echo off
setlocal

set "ROOT=%~dp0"
cd /d "%ROOT%"

if not exist "gradlew.bat" (
    echo [ERROR] gradlew.bat not found in %ROOT%
    exit /b 1
)

echo ============================================
echo  Starting OPAC Dev Environment
echo  Server + Client 1 (Player1) + Client 2 (Player2)
echo ============================================
echo.

echo [1/3] Starting Server...
start "OPAC Server" cmd /k "cd /d "%ROOT%" && gradlew.bat :NeoForge:runServer --console=plain"

echo Waiting 10 seconds for server to initialize...
timeout /t 10 /nobreak >nul

echo [2/3] Starting Client 1 (Player1)...
start "OPAC Client 1" cmd /k "cd /d "%ROOT%" && gradlew.bat :NeoForge:runClient --console=plain"

echo [3/3] Starting Client 2 (Player2)...
start "OPAC Client 2" cmd /k "cd /d "%ROOT%" && gradlew.bat :NeoForge:runClient2 --console=plain"

echo.
echo All 3 instances launched. Use stop-all.bat to shut them down.
endlocal
