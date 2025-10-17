@echo off
setlocal enabledelayedexpansion

echo ==============================================
echo   AutoSwapEng Android Build Script (package.bat)
echo   Usage: package.bat ^<debug^|release^|bundle^|keygen^>
echo   Options: debug=Debug APK  release=Release APK  bundle=Release AAB  keygen=Generate JKS
echo ==============================================

rem Load external config file (auto-generate on first run)
set CONFIG_FILE=package.env
if not exist "%CONFIG_FILE%" (
  echo [INFO] Config not found, creating template: %CONFIG_FILE%
  (
    echo # AutoSwapEng package signing/config. Remove '#' to activate.
    echo # KEYSTORE=.\keystore\release.jks
    echo # KEY_ALIAS=autoswapeng
    echo # KEYSTORE_PASS=autoswapeng_store
    echo # KEY_PASS=autoswapeng_store
    echo # ANDROID_SDK_ROOT=.path\to\android\sdk
    echo # ANDROID_HOME=.path\to\android\sdk
  ) > "%CONFIG_FILE%"
) else (
  for /f "usebackq eol=# tokens=1,* delims==" %%A in ("%CONFIG_FILE%") do (
    if not "%%A"=="" set "%%A=%%B"
  )
  echo [INFO] Loaded config: %CONFIG_FILE%
)

set MODE=%1
if "%MODE%"=="" set MODE=release

rem Standalone keystore generation mode
if /I "%MODE%"=="keygen" goto :keygen

rem Check Java
java -version >nul 2>nul
if errorlevel 1 (
  echo [ERROR] Java not found. Please install JDK and configure PATH/JAVA_HOME.
  exit /b 1
)

rem Select Gradle command
set GRADLE_CMD=
if exist gradlew.bat (
  set GRADLE_CMD=gradlew.bat
) else (
  where gradle >nul 2>nul
  if %ERRORLEVEL%==0 (
    set GRADLE_CMD=gradle
    echo [INFO] gradlew.bat not found, using system gradle.
  ) else (
    echo [ERROR] Neither gradlew.bat nor gradle found. Please install Gradle or generate Wrapper.
    echo        Example: Install Gradle then run "gradle wrapper".
    exit /b 1
  )
)

rem Parse task
set TASK=
if /I "%MODE%"=="debug" (
  set TASK=assembleDebug
) else if /I "%MODE%"=="release" (
  set TASK=assembleRelease
) else if /I "%MODE%"=="bundle" (
  set TASK=bundleRelease
) else (
  echo [ERROR] Unsupported mode: %MODE%
  echo Usage: package.bat ^<debug^|release^|bundle^>
  exit /b 1
)

echo [INFO] Starting build: %TASK%
call %GRADLE_CMD% --no-daemon --warning-mode all %TASK%
if errorlevel 1 (
  echo [ERROR] Build failed
  exit /b 1
)

rem Locate output
set OUT=
if /I "%MODE%"=="debug" (
  set OUT=app\build\outputs\apk\debug\app-debug.apk
) else if /I "%MODE%"=="release" (
  set OUT=app\release\AutoSwapEng.apk
) else if /I "%MODE%"=="bundle" (
  set OUT=app\build\outputs\bundle\release\app-release.aab
)

if not exist "%OUT%" (
  echo [WARN] Default output not found: %OUT%
  echo        Please check Gradle output directory.
) else (
  echo [OK] Build artifact: %OUT%
)

rem Try signing only for release APK (optional)
if /I not "%MODE%"=="release" goto :end

if not exist "%OUT%" goto :end

if "%KEYSTORE%"=="" goto :end
if "%KEY_ALIAS%"=="" goto :end
if "%KEYSTORE_PASS%"=="" goto :end
if "%KEY_PASS%"=="" goto :end

echo [INFO] Signing variables detected, attempting to sign with apksigner
call :find_apksigner
if errorlevel 1 (
  echo [WARN] apksigner not found, skipping signing
  goto :end
)

set SIGNED_OUT=%OUT:.apk=-signed.apk%

"%APK_SIGNER%" sign ^
  --ks "%KEYSTORE%" ^
  --ks-key-alias "%KEY_ALIAS%" ^
  --ks-pass pass:%KEYSTORE_PASS% ^
  --key-pass pass:%KEY_PASS% ^
  --v1-signing-enabled false ^
  --v2-signing-enabled true ^
  --v3-signing-enabled true ^
  --out "%SIGNED_OUT%" ^
  "%OUT%"

if errorlevel 1 (
  echo [ERROR] Signing failed
  exit /b 1
) else (
  echo [OK] Signing completed: %SIGNED_OUT%
)

goto :end

:: Keystore generation (non-interactive)
:keygen
echo [INFO] Keystore generation mode
call :find_keytool
if errorlevel 1 (
  echo [ERROR] keytool not found. Please ensure JAVA_HOME points to a JDK or keytool is in PATH.
  exit /b 1
)

if not exist keystore (
  mkdir keystore
)

rem Defaults can be overridden by package.env
if "%KEY_ALIAS%"=="" set KEY_ALIAS=autoswapeng
if "%KEYSTORE_PASS%"=="" set KEYSTORE_PASS=autoswapeng_store
if "%KEY_PASS%"=="" set KEY_PASS=autoswapeng_key

set DST=keystore\release.jks
if not "%KEYSTORE%"=="" set DST=%KEYSTORE%

for %%I in ("%DST%") do set DST_DIR=%%~dpI
if not "%DST_DIR%"=="" if not exist "%DST_DIR%" mkdir "%DST_DIR%"

echo [INFO] Using keytool: %KEY_TOOL%
echo [INFO] Generating keystore: %DST%
"%KEY_TOOL%" -genkeypair -noprompt -v -keystore "%DST%" -storepass %KEYSTORE_PASS% -keypass %KEY_PASS% -alias %KEY_ALIAS% -keyalg RSA -keysize 2048 -validity 36500 -dname "CN=AutoSwapEng, OU=Dev, O=AutoSwapEng, L=, S=, C=CN"
if errorlevel 1 (
  echo [ERROR] keytool failed
  exit /b 1
)

rem Ensure config entries exist (do not overwrite existing active lines)
call :ensure_kv KEYSTORE "%DST%"
call :ensure_kv KEY_ALIAS "%KEY_ALIAS%"
call :ensure_kv KEYSTORE_PASS "%KEYSTORE_PASS%"
call :ensure_kv KEY_PASS "%KEY_PASS%"

echo [OK] Keystore generated and configuration ensured in %CONFIG_FILE%
goto :end

:: Find apksigner (prioritize APK_SIGNER if specified; otherwise auto-detect from ANDROID_HOME/ANDROID_SDK_ROOT build-tools latest version)
:find_apksigner
if not "%APK_SIGNER%"=="" (
  if exist "%APK_SIGNER%" (
    goto :eof
  )
)

set SDK_BASE=
if not "%ANDROID_HOME%"=="" set SDK_BASE=%ANDROID_HOME%
if "%SDK_BASE%"=="" if not "%ANDROID_SDK_ROOT%"=="" set SDK_BASE=%ANDROID_SDK_ROOT%
if "%SDK_BASE%"=="" (
  echo [WARN] ANDROID_HOME/ANDROID_SDK_ROOT not set
  exit /b 1
)

set BT_DIR=%SDK_BASE%\build-tools
if not exist "%BT_DIR%" (
  echo [WARN] build-tools directory not found: %BT_DIR%
  exit /b 1
)

set APK_SIGNER=
for /f "delims=" %%v in ('dir /b /ad "%BT_DIR%" ^| sort /r') do (
  if exist "%BT_DIR%\%%v\apksigner.bat" (
    set APK_SIGNER=%BT_DIR%\%%v\apksigner.bat
    goto :found_signer
  )
  if exist "%BT_DIR%\%%v\apksigner" (
    set APK_SIGNER=%BT_DIR%\%%v\apksigner
    goto :found_signer
  )
)

echo [WARN] apksigner not found in %BT_DIR%
exit /b 1

:found_signer
echo [INFO] Using apksigner: %APK_SIGNER%
exit /b 0

:find_keytool
set KEY_TOOL=
if not "%JAVA_HOME%"=="" (
  if exist "%JAVA_HOME%\bin\keytool.exe" set KEY_TOOL=%JAVA_HOME%\bin\keytool.exe
  if exist "%JAVA_HOME%\bin\keytool" set KEY_TOOL=%JAVA_HOME%\bin\keytool
)
if "%KEY_TOOL%"=="" (
  where keytool >nul 2>nul
  if %ERRORLEVEL%==0 (
    for /f "delims=" %%P in ('where keytool') do (
      set KEY_TOOL=%%P
      goto :found_keytool
    )
  )
)
if "%KEY_TOOL%"=="" (
  echo [WARN] keytool not found via JAVA_HOME or PATH
  exit /b 1
)
:found_keytool
exit /b 0

:ensure_kv
rem %1=KEY  %2="VALUE"
if not exist "%CONFIG_FILE%" (
  >"%CONFIG_FILE%" echo %1=%~2
) else (
  findstr /b /c:"%1=" "%CONFIG_FILE%" >nul 2>nul
  if errorlevel 1 (
    >>"%CONFIG_FILE%" echo %1=%~2
  ) else (
    echo [INFO] %1 already present in %CONFIG_FILE%
  )
)
exit /b 0

:end
echo Done.
endlocal


