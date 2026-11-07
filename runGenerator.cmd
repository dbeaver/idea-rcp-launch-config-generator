@echo off

setlocal

set "MAVEN_ARGS=-T1C -Djdk.xml.maxGeneralEntitySizeLimit=2097152 -Djdk.xml.totalEntitySizeLimit=2097152"
set "GENERATOR_DIR=%~dp0"
set "WORKING_DIR="
set "MAVEN_PROJECTS=:idea-rcp-launch-config-generator"

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
    set "EXIT_CODE=1"
    goto :end
)

if exist "%GENERATOR_DIR%..\checkstyle-nullability-annotations\pom.xml" (
    set "MAVEN_PROJECTS=%MAVEN_PROJECTS%,../../checkstyle-nullability-annotations"
)

echo Build and run workspace generator
call "%GENERATOR_DIR%..\dbeaver-common\mvnw.cmd" -f "%GENERATOR_DIR%aggregate\pom.xml" -pl "%MAVEN_PROJECTS%" -am verify -Pgenerate-workspace -DskipTests=true %MAVEN_ARGS% -Dexec.args="-eclipse.version ${eclipse-version} -updateWorkspace -config %WORKING_DIR%osgi-app.properties -projectsFolder %WORKING_DIR%..\ -eclipse %WORKING_DIR%..\.workspace\dependencies -output %WORKING_DIR%..\.workspace\products"
set "EXIT_CODE=%ERRORLEVEL%"

:end
endlocal & exit /b %EXIT_CODE%
