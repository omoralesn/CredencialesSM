@echo off
cd /d "%~dp0"
java -Djava.library.path=%~dp0natives -jar zxp-prueba.jar
if errorlevel 1 pause
