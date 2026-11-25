@echo off
cd /d "%~dp0"
call mvnw.cmd -q -DskipTests package
if errorlevel 1 exit /b 1
java -jar target\falcon-locadora-2.3.0.jar %*
