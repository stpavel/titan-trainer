@echo off
chcp 65001 >nul
setlocal

set VERSION_FILE=version.properties
set OUT_DIR=builds

for /f "usebackq delims=" %%n in (`powershell -NoProfile -Command "$n=0; if (Test-Path '%VERSION_FILE%') { $c = Get-Content '%VERSION_FILE%' -Raw; if ($c -match 'BUILD_NUMBER=(\d+)') { $n = [int]$Matches[1] } }; $n + 1"`) do set BUILD_NUMBER=%%n

powershell -NoProfile -Command "[System.IO.File]::WriteAllText('%VERSION_FILE%', 'BUILD_NUMBER=%BUILD_NUMBER%')"

set /a VER_MAJOR=BUILD_NUMBER/100
set /a VER_MINOR=BUILD_NUMBER%%100
if %VER_MINOR% LSS 10 set VER_MINOR=0%VER_MINOR%
set VERSION_STR=%VER_MAJOR%.%VER_MINOR%

echo ==== Сборка TitanTrainer %VERSION_STR% ====

call gradlew.bat assembleDebug
if errorlevel 1 (
    echo.
    echo СБОРКА УПАЛА — поправь ошибку и запусти build.bat заново.
    exit /b 1
)

if not exist "%OUT_DIR%" mkdir "%OUT_DIR%"

copy /y "app\build\outputs\apk\debug\app-debug.apk" "%OUT_DIR%\TitanTrainer-%VERSION_STR%.apk" >nul

echo.
echo Готово: %OUT_DIR%\TitanTrainer-%VERSION_STR%.apk

endlocal