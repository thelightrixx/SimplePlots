@echo off
setlocal
if not defined SIMPLEPLOTS_MVN if defined PLOTSYSTEM_MVN set "SIMPLEPLOTS_MVN=%PLOTSYSTEM_MVN%"
if not defined SIMPLEPLOTS_MVN (
    for /f "delims=" %%M in ('where mvn.cmd 2^>nul') do if not defined SIMPLEPLOTS_MVN set "SIMPLEPLOTS_MVN=%%M"
)
if not defined SIMPLEPLOTS_MVN (
    if exist "%~dp0..\PunishSystem\.tools\apache-maven-3.9.11\bin\mvn.cmd" (
        set "SIMPLEPLOTS_MVN=%~dp0..\PunishSystem\.tools\apache-maven-3.9.11\bin\mvn.cmd"
    )
)
if not defined SIMPLEPLOTS_MVN (
    echo Maven fehlt. Setze SIMPLEPLOTS_MVN auf den Pfad von mvn.cmd oder fuege Maven zum PATH hinzu.
    exit /b 1
)
call "%SIMPLEPLOTS_MVN%" -B -ntp -f "%~dp0pom.xml" -pl paper -am verify %*
if errorlevel 1 exit /b 1
echo Die Plugin-JAR liegt unter "%~dp0paper\target".
