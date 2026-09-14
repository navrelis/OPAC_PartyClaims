@echo off
setlocal

echo Stopping OPAC server and client instances...

REM Close launcher windows started by start-all.bat
taskkill /FI "WINDOWTITLE eq OPAC Server" /T /F >nul 2>&1
taskkill /FI "WINDOWTITLE eq OPAC Client 1" /T /F >nul 2>&1
taskkill /FI "WINDOWTITLE eq OPAC Client 2" /T /F >nul 2>&1

REM Cleanup any lingering Java/Gradle processes for this workspace runs
powershell -NoProfile -Command "$procs = Get-CimInstance Win32_Process | Where-Object { $_.CommandLine -and $_.CommandLine -like '*OPAC_PartyClaims*' -and ($_.CommandLine -like '*forgeserverdev*' -or $_.CommandLine -like '*forgeclientdev*' -or $_.CommandLine -like '*:NeoForge:runServer*' -or $_.CommandLine -like '*:NeoForge:runClient*') }; if ($procs) { $procs | ForEach-Object { Stop-Process -Id $_.ProcessId -Force -ErrorAction SilentlyContinue } }"

echo Stop command sent.
endlocal
