@echo off
echo ========================================
echo Robbie - Setup de Variables de Entorno
echo ========================================
echo.

REM Verificar si gradle.properties ya existe
if exist gradle.properties (
    echo [!] gradle.properties ya existe
    echo.
    choice /C YN /M "Deseas sobrescribirlo"
    if errorlevel 2 goto :end
    if errorlevel 1 goto :create
) else (
    goto :create
)

:create
echo.
echo Creando gradle.properties desde .env.example...
copy .env.example gradle.properties >nul

echo.
echo ========================================
echo [OK] gradle.properties creado
echo ========================================
echo.
echo IMPORTANTE:
echo 1. Edita gradle.properties con tus credenciales reales
echo 2. NO commitees gradle.properties al repositorio
echo 3. Haz Gradle Sync en Android Studio
echo.
echo Archivo: %CD%\gradle.properties
echo.

:end
pause
