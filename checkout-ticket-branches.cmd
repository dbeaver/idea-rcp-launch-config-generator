@echo off

setlocal EnableExtensions EnableDelayedExpansion

if "%~1"=="" goto :usage
if not "%~3"=="" goto :usage

set "TICKET_URL=%~1"
set "SCRIPT_DIR=%~dp0"
for %%I in ("%SCRIPT_DIR%..") do set "REPOSITORIES_ROOT_DIR=%%~fI"
if not "%~2"=="" (
    for %%I in ("%~2") do set "REPOSITORIES_ROOT_DIR=%%~fI"
)

if not defined GITHUB_TOKEN if not defined GH_TOKEN (
    where gh >nul 2>nul
    if errorlevel 1 (
        echo GITHUB_TOKEN or GH_TOKEN environment variable is required 1>&2
        exit /b 2
    )
    for /f "usebackq delims=" %%T in (`gh auth token`) do set "GITHUB_TOKEN=%%T"
)

if exist "%REPOSITORIES_ROOT_DIR%\dbeaver-common\mvnw.cmd" (
    set "MVN_CMD=%REPOSITORIES_ROOT_DIR%\dbeaver-common\mvnw.cmd"
) else (
    set "MVN_CMD=mvn.cmd"
)

set "BRANCHES_FILE=%TEMP%\checkout-ticket-branches-%RANDOM%%RANDOM%.txt"
call "%MVN_CMD%" -q ^
    -f "%SCRIPT_DIR%pom.xml" ^
    package exec:java ^
    -Dexec.mainClass=org.jkiss.tools.rcplaunchconfig.github.GitHubTicketBranchResolver ^
    -Dexec.args="%TICKET_URL%" > "%BRANCHES_FILE%"
if errorlevel 1 goto :cleanupFailed

for %%I in ("%BRANCHES_FILE%") do if %%~zI==0 (
    echo No branches are attached to %TICKET_URL%
    goto :cleanupSuccess
)

for /f "usebackq tokens=1,* delims=	" %%A in ("%BRANCHES_FILE%") do (
    set "REPOSITORY=%%A"
    set "BRANCH=%%B"
    if defined REPOSITORY (
        call :findRepositoryDir "!REPOSITORY!"
        if not defined REPOSITORY_DIR (
            echo Repository '!REPOSITORY!' is not cloned under %REPOSITORIES_ROOT_DIR% 1>&2
            goto :cleanupFailed
        )

        echo Checkout !REPOSITORY! -^> !BRANCH!
        call :checkoutBranch "!REPOSITORY_DIR!" "!BRANCH!"
        if errorlevel 1 goto :cleanupFailed
    )
)

goto :cleanupSuccess

:usage
echo Usage: %~nx0 ^<github-ticket-url^> [repositories-root-dir] 1>&2
exit /b 2

:findRepositoryDir
set "REPOSITORY_DIR="
set "REPOSITORY=%~1"
for /f "tokens=2 delims=/" %%R in ("%REPOSITORY%") do set "REPOSITORY_NAME=%%R"
set "CANDIDATE=%REPOSITORIES_ROOT_DIR%\%REPOSITORY_NAME%"

if exist "%CANDIDATE%\.git" (
    call :repositoryMatchesOrigin "%CANDIDATE%" "%REPOSITORY%"
    if not errorlevel 1 (
        set "REPOSITORY_DIR=%CANDIDATE%"
        exit /b 0
    )
)

for /d %%D in ("%REPOSITORIES_ROOT_DIR%\*") do (
    if exist "%%~fD\.git" (
        call :repositoryMatchesOrigin "%%~fD" "%REPOSITORY%"
        if not errorlevel 1 (
            set "REPOSITORY_DIR=%%~fD"
            exit /b 0
        )
    )
)

exit /b 1

:repositoryMatchesOrigin
set "ORIGIN_URL="
for /f "usebackq delims=" %%U in (`git -C "%~1" remote get-url origin 2^>nul`) do set "ORIGIN_URL=%%U"

if "%ORIGIN_URL%"=="https://github.com/%~2" exit /b 0
if "%ORIGIN_URL%"=="https://github.com/%~2.git" exit /b 0
if "%ORIGIN_URL%"=="git@github.com:%~2.git" exit /b 0
if "%ORIGIN_URL%"=="ssh://git@github.com/%~2.git" exit /b 0
exit /b 1

:checkoutBranch
git -C "%~1" show-ref --verify --quiet "refs/heads/%~2"
if not errorlevel 1 (
    git -C "%~1" checkout "%~2"
    exit /b %ERRORLEVEL%
)

git -C "%~1" ls-remote --exit-code --heads origin "%~2" >nul
if not errorlevel 1 (
    git -C "%~1" fetch origin "refs/heads/%~2:refs/heads/%~2"
    if errorlevel 1 exit /b %ERRORLEVEL%
    git -C "%~1" checkout "%~2"
    exit /b %ERRORLEVEL%
)

echo Branch '%~2' was not found on origin in %~1 1>&2
exit /b 1

:cleanupSuccess
del "%BRANCHES_FILE%" >nul 2>nul
exit /b 0

:cleanupFailed
del "%BRANCHES_FILE%" >nul 2>nul
exit /b 1
