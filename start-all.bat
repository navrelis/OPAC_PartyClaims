@echo off
setlocal

set "ROOT=%~dp0"
cd /d "%ROOT%"

if not exist "gradlew.bat" (
    echo [ERROR] gradlew.bat not found in %ROOT%
    exit /b 1
)

echo ============================================
echo  Starting OPAC / Team Claims Dev Environment
echo  Server + Client 1 (Player1) + Client 2 (Player2)
echo ============================================
echo.

if exist "run-server\eula.txt" (
    findstr /I /C:"eula=true" "run-server\eula.txt" >nul 2>&1
    if errorlevel 1 (
        echo [ACTION REQUIRED] run-server\eula.txt exists but does not contain "eula=true".
        echo             You must accept the Minecraft EULA yourself: open run-server\eula.txt
        echo             and change it to "eula=true" before the server will actually boot.
    )
) else (
    echo [ACTION REQUIRED] run-server\eula.txt does not exist yet ^(it is generated on first
    echo             server start^). Once it appears, you must accept the Minecraft EULA
    echo             yourself by setting "eula=true" in run-server\eula.txt.
)
echo [HINT] run-server\server.properties needs "online-mode=false" for the two offline
echo        dev accounts ^(Player1 / Player2^) to be able to join.
echo.
echo Continuing to launch the dev environment anyway...
echo.

echo [1/3] Starting Server...
start "OPAC Server" cmd /k "cd /d "%ROOT%" && gradlew.bat :Fabric:runServer --console=plain"

echo Waiting 10 seconds for server to initialize...
timeout /t 10 /nobreak >nul

echo [2/3] Starting Client 1 (Player1)...
start "OPAC Client 1" cmd /k "cd /d "%ROOT%" && gradlew.bat :Fabric:runClient --console=plain"

echo [3/3] Starting Client 2 (Player2)...
start "OPAC Client 2" cmd /k "cd /d "%ROOT%" && gradlew.bat :Fabric:runClient2 --console=plain"

echo.
echo All 3 instances launched. Use stop-all.bat to shut them down.
endlocal
