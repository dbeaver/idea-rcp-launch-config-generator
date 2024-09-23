@echo off

setlocal

set "WORKING_DIR="
cd %~dp0
:: Parse command line arguments
:parseArgs
if "%~1"=="" goto :checkDir
if "%~1"=="-f" (
    set "WORKING_DIR=%~2"
    shift
    shift
    goto :parseArgs
)
shift
goto :parseArgs

:checkDir
if "%WORKING_DIR%"=="" (
    echo No folder containing rcp_gen specified
    exit /b 1
)

echo Build generator

call mvn package -q

echo Run generator
:: Run the Maven commands with the specified options
call mvn -f "pom.xml" package -T 1C -q exec:java -Dexec.args="-eclipse.version ${eclipse-version} -config %WORKING_DIR%osgi-app.properties -projectsFolder %WORKING_DIR%..\ -eclipse %WORKING_DIR%..\dbeaver-workspace\dependencies -output %WORKING_DIR%..\dbeaver-workspace/products/"

:end
endlocal