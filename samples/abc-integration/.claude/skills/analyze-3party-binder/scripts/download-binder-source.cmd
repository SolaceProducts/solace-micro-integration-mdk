@echo off
setlocal enabledelayedexpansion
rem download-binder-source.cmd  (Windows)
rem
rem Downloads a third-party Spring Cloud Stream binder source from GitHub
rem into a local directory for offline analysis.
rem
rem Handles two cases:
rem   1. Standalone repo  -- shallow clone (only top commit, no history)
rem   2. Monorepo subtree -- git sparse-checkout (fetches ONLY the subtree blobs,
rem      NOT the entire repository)
rem
rem Usage:
rem   download-binder-source.cmd <github_url> <output_dir>
rem
rem Examples:
rem   download-binder-source.cmd ^
rem     https://github.com/solace-spring-cloud/spring-cloud-stream-binder-solace ^
rem     .TEMP
rem
rem   download-binder-source.cmd ^
rem     https://github.com/spring-cloud/spring-cloud-stream/tree/main/binders/kafka-binder ^
rem     .TEMP
rem
rem What gets downloaded for monorepo subtrees:
rem   - The binder subtree itself (all modules under that path)
rem   - The root pom.xml (needed for version/property resolution)
rem   - Nothing else -- the rest of the monorepo is NOT fetched
rem
rem After completion the script prints BINDER_SOURCE_ROOT=<path> which is the
rem directory containing the binder's top-level pom.xml.

rem ── Argument validation ─────────────────────────────────────────────
if "%~1"=="" (
    echo ERROR: missing argument.
    echo Usage: %~nx0 ^<github_url^> ^<output_dir^>
    exit /b 1
)
if "%~2"=="" (
    echo ERROR: missing argument.
    echo Usage: %~nx0 ^<github_url^> ^<output_dir^>
    exit /b 1
)

set "GITHUB_URL=%~1"
set "OUTPUT_DIR=%~2"

rem ── Parse GitHub URL ────────────────────────────────────────────────
rem Supported formats:
rem   https://github.com/owner/repo
rem   https://github.com/owner/repo/tree/branch
rem   https://github.com/owner/repo/tree/branch/path/to/subtree
rem
rem Strategy: strip the https://github.com/ prefix, then split by /

set "URL_PATH=!GITHUB_URL:https://github.com/=!"

rem Strip trailing slash
if "!URL_PATH:~-1!"=="/" set "URL_PATH=!URL_PATH:~0,-1!"

rem Strip .git suffix
if "!URL_PATH:~-4!"==".git" set "URL_PATH=!URL_PATH:~0,-4!"

rem Parse: owner/repo[/tree/branch[/subtree/path]]
rem   tokens: 1=owner  2=repo  3="tree"|empty  4=branch  5*=subtree-path
set "GH_OWNER="
set "GH_REPO="
set "GH_BRANCH=main"
set "GH_SUBTREE="

for /f "tokens=1,2,3,4,* delims=/" %%A in ("!URL_PATH!") do (
    set "GH_OWNER=%%A"
    set "GH_REPO=%%B"
    if "%%C"=="tree" (
        if not "%%D"=="" set "GH_BRANCH=%%D"
        if not "%%E"=="" set "GH_SUBTREE=%%E"
    )
)

rem Validate parse result
if "!GH_OWNER!"=="" (
    echo ERROR: cannot parse GitHub URL: !GITHUB_URL!
    echo Expected format: https://github.com/owner/repo[/tree/branch[/path]]
    exit /b 1
)
if "!GH_REPO!"=="" (
    echo ERROR: cannot parse GitHub URL: !GITHUB_URL!
    echo Expected format: https://github.com/owner/repo[/tree/branch[/path]]
    exit /b 1
)

set "SUBTREE_DISPLAY=!GH_SUBTREE!"
if "!GH_SUBTREE!"=="" set "SUBTREE_DISPLAY=^<entire repo^>"

echo ==========================================
echo  Binder Source Download
echo ==========================================
echo  Repository : !GH_OWNER!/!GH_REPO!
echo  Branch     : !GH_BRANCH!
echo  Subtree    : !SUBTREE_DISPLAY!
echo  Output dir : !OUTPUT_DIR!
echo ==========================================
echo.

rem ── Prepare output directory ────────────────────────────────────────
if exist "!OUTPUT_DIR!" (
    echo [1/4] Cleaning existing output directory...
    rmdir /s /q "!OUTPUT_DIR!"
)
mkdir "!OUTPUT_DIR!"

set "CLONE_DIR=!OUTPUT_DIR!\repo"

rem ── Download ────────────────────────────────────────────────────────

if "!GH_SUBTREE!"=="" goto :clone_standalone
goto :clone_sparse

:clone_standalone
rem ── Strategy A: standalone repo -- shallow clone ─────────────────
echo [2/4] Shallow-cloning standalone repository...
git clone --depth 1 --branch "!GH_BRANCH!" "https://github.com/!GH_OWNER!/!GH_REPO!.git" "!CLONE_DIR!"
if errorlevel 1 (
    echo ERROR: git clone failed.
    exit /b 1
)
set "BINDER_SOURCE_ROOT=!CLONE_DIR!"
goto :post_clone

:clone_sparse
rem ── Strategy B: monorepo subtree -- sparse checkout ──────────────
echo [2/4] Sparse-cloning monorepo (metadata only, no file content yet)...
git clone --depth 1 --filter=blob:none --sparse --branch "!GH_BRANCH!" "https://github.com/!GH_OWNER!/!GH_REPO!.git" "!CLONE_DIR!"
if errorlevel 1 (
    echo ERROR: git clone failed.
    exit /b 1
)

echo.
echo [3/4] Fetching ONLY the binder subtree...
pushd "!CLONE_DIR!"

rem Cone mode only accepts directories -- set the binder subtree
git sparse-checkout set "!GH_SUBTREE!"
if errorlevel 1 (
    echo ERROR: git sparse-checkout failed.
    popd
    exit /b 1
)

rem Fetch root pom.xml individually (needed for version/property resolution)
rem git-show works even when the file is outside the sparse-checkout cone
echo.
echo       Extracting root pom.xml for version resolution...
git show HEAD:pom.xml > pom.xml.tmp 2>nul
if errorlevel 1 (
    echo       root pom.xml: not found (non-fatal)
    del pom.xml.tmp 2>nul
) else (
    move /y pom.xml.tmp pom.xml >nul
    echo       root pom.xml: OK
)

popd

set "GH_SUBTREE_BS=!GH_SUBTREE:/=\!"
set "BINDER_SOURCE_ROOT=!CLONE_DIR!\!GH_SUBTREE_BS!"

:post_clone

rem ── Remove .git to save space ───────────────────────────────────────
echo.
echo [4/4] Removing .git metadata to save disk space...
if exist "!CLONE_DIR!\.git" rmdir /s /q "!CLONE_DIR!\.git"

rem ── Summary ─────────────────────────────────────────────────────────
echo.
echo ==========================================
echo  Download complete
echo ==========================================

rem Count files — use full path to Windows find.exe to avoid MSYS find conflict
set "JAVA_COUNT=0"
set "XML_COUNT=0"
set "TOTAL_COUNT=0"
for /f %%N in ('dir /s /b "!CLONE_DIR!\*.java" 2^>nul ^| %SystemRoot%\System32\find.exe /c /v ""') do set "JAVA_COUNT=%%N"
for /f %%N in ('dir /s /b "!CLONE_DIR!\*.xml" 2^>nul ^| %SystemRoot%\System32\find.exe /c /v ""') do set "XML_COUNT=%%N"
for /f %%N in ('dir /s /b /a:-d "!CLONE_DIR!" 2^>nul ^| %SystemRoot%\System32\find.exe /c /v ""') do set "TOTAL_COUNT=%%N"

echo  Java files : !JAVA_COUNT!
echo  XML files  : !XML_COUNT!
echo  Total files: !TOTAL_COUNT!

echo.
echo  BINDER_SOURCE_ROOT=!BINDER_SOURCE_ROOT!
echo ==========================================

endlocal
