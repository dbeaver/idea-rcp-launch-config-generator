@echo off

setlocal

set "MAVEN_ARGS=-T1C -Djdk.xml.maxGeneralEntitySizeLimit=2097152 -Djdk.xml.totalEntitySizeLimit=2097152"
set "GENERATOR_DIR=%~dp0"
set "WORKING_DIR="

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
    goto :end
)

echo Build generator
call %GENERATOR_DIR%..\dbeaver-common\mvnw.cmd clean install %MAVEN_ARGS% -q -f "%GENERATOR_DIR%aggregate"

echo Run generator
:: Run the Maven commands with the specified options
call %GENERATOR_DIR%..\dbeaver-common\mvnw.cmd -f "%GENERATOR_DIR%pom.xml" package %MAVEN_ARGS% -q exec:java -Dexec.args="-eclipse.version ${eclipse-version} -updateWorkspace -config %WORKING_DIR%osgi-app.properties -projectsFolder %WORKING_DIR%..\ -eclipse %WORKING_DIR%..\dbeaver-workspace\dependencies -output %WORKING_DIR%..\dbeaver-workspace\products"

:end
endlocal
