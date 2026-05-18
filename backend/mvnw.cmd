@REM ---------------------------------------------------------
@REM Apache Maven Wrapper startup batch script for Windows
@REM ---------------------------------------------------------
@echo off
setlocal
set BASEDIR=%~dp0
set WRAPPER_JAR=%BASEDIR%.mvn\wrapper\maven-wrapper.jar
set WRAPPER_PROPERTIES=%BASEDIR%.mvn\wrapper\maven-wrapper.properties

if not exist "%WRAPPER_JAR%" (
    for /f "tokens=2 delims==" %%A in ('findstr wrapperUrl "%WRAPPER_PROPERTIES%"') do set WRAPPER_URL=%%A
    powershell -Command "Invoke-WebRequest -Uri %WRAPPER_URL% -OutFile %WRAPPER_JAR%"
)

java -jar "%WRAPPER_JAR%" -Dmaven.multiModuleProjectDirectory=%BASEDIR% %*
