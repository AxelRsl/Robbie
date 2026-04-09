param(
    [string]$KeystoreFile = "$PSScriptRoot\release.keystore"
)

$ErrorActionPreference = "Stop"

$AppName = "robbie"
$BuildType = "release"
$OutputDir = "$PSScriptRoot\app\build\outputs\apk\$BuildType"
$UnsignedApk = "$OutputDir\app-$BuildType-unsigned.apk"
$AlignedApk = "$OutputDir\app-$BuildType-unsigned-aligned.apk"
$FinalApk = "$OutputDir\$AppName-$BuildType-signed.apk"

Write-Host "=========================================" -ForegroundColor Cyan
Write-Host "  Build, Align & Sign - $AppName" -ForegroundColor Cyan
Write-Host "=========================================" -ForegroundColor Cyan
Write-Host ""

if (-not (Test-Path $KeystoreFile)) {
    Write-Host "Error: Keystore no encontrado en: $KeystoreFile" -ForegroundColor Red
    Write-Host ""
    Write-Host "Para crear un keystore nuevo:" -ForegroundColor Yellow
    Write-Host "  keytool -genkey -v -keystore release.keystore -alias release ``"
    Write-Host "    -keyalg RSA -keysize 2048 -validity 10000"
    exit 1
}

Write-Host "Paso 1: Limpiando proyecto..." -ForegroundColor Green
& "$PSScriptRoot\gradlew.bat" clean

Write-Host ""
Write-Host "Paso 2: Compilando bundle de React Native..." -ForegroundColor Green
Push-Location "$PSScriptRoot\react-native-app"
if (Test-Path "package.json") {
    Write-Host "  - Instalando dependencias de npm..." -ForegroundColor Yellow
    npm install
    Write-Host "  - Generando bundle de React Native..." -ForegroundColor Yellow
    npm run bundle:android
} else {
    Write-Host "  - Advertencia: No se encontro package.json en react-native-app" -ForegroundColor Yellow
}
Pop-Location

Write-Host ""
Write-Host "Paso 3: Compilando APK release (sin firmar)..." -ForegroundColor Green
& "$PSScriptRoot\gradlew.bat" assembleRelease

if (-not (Test-Path $UnsignedApk)) {
    Write-Host "Error: APK sin firmar no encontrado en $UnsignedApk" -ForegroundColor Red
    exit 1
}

Write-Host ""
Write-Host "Paso 4: Alineando APK con zipalign..." -ForegroundColor Green

$zipalign = $null
if (Get-Command zipalign -ErrorAction SilentlyContinue) {
    $zipalign = "zipalign"
} else {
    $androidSdkPaths = @(
        $env:ANDROID_HOME,
        "$env:LOCALAPPDATA\Android\Sdk",
        "$env:USERPROFILE\AppData\Local\Android\Sdk"
    )
    
    foreach ($sdkPath in $androidSdkPaths) {
        if ($sdkPath -and (Test-Path "$sdkPath\build-tools")) {
            $buildToolsVersions = Get-ChildItem "$sdkPath\build-tools" | Sort-Object Name -Descending
            if ($buildToolsVersions.Count -gt 0) {
                $zipalignPath = "$($buildToolsVersions[0].FullName)\zipalign.exe"
                if (Test-Path $zipalignPath) {
                    $zipalign = $zipalignPath
                    break
                }
            }
        }
    }
}

if (-not $zipalign) {
    Write-Host "Error: zipalign no encontrado. Asegurate de tener Android SDK instalado" -ForegroundColor Red
    Write-Host "Rutas buscadas:" -ForegroundColor Yellow
    Write-Host "  - ANDROID_HOME: $env:ANDROID_HOME" -ForegroundColor Yellow
    Write-Host "  - $env:LOCALAPPDATA\Android\Sdk" -ForegroundColor Yellow
    exit 1
}

& $zipalign -v -p 4 $UnsignedApk $AlignedApk

Write-Host ""
Write-Host "Paso 5: Firmando APK con apksigner..." -ForegroundColor Green

$apksigner = $null
if (Get-Command apksigner -ErrorAction SilentlyContinue) {
    $apksigner = "apksigner"
} else {
    $androidSdkPaths = @(
        $env:ANDROID_HOME,
        "$env:LOCALAPPDATA\Android\Sdk",
        "$env:USERPROFILE\AppData\Local\Android\Sdk"
    )
    
    foreach ($sdkPath in $androidSdkPaths) {
        if ($sdkPath -and (Test-Path "$sdkPath\build-tools")) {
            $buildToolsVersions = Get-ChildItem "$sdkPath\build-tools" | Sort-Object Name -Descending
            if ($buildToolsVersions.Count -gt 0) {
                $apksignerBat = "$($buildToolsVersions[0].FullName)\apksigner.bat"
                if (Test-Path $apksignerBat) {
                    $apksigner = $apksignerBat
                    break
                }
            }
        }
    }
}

if (-not $apksigner) {
    Write-Host "Error: apksigner no encontrado. Asegurate de tener Android SDK instalado" -ForegroundColor Red
    Write-Host "Rutas buscadas:" -ForegroundColor Yellow
    Write-Host "  - ANDROID_HOME: $env:ANDROID_HOME" -ForegroundColor Yellow
    Write-Host "  - $env:LOCALAPPDATA\Android\Sdk" -ForegroundColor Yellow
    exit 1
}

Write-Host "Firmando APK (se pedira la contraseña del keystore)..." -ForegroundColor Yellow
& $apksigner sign --ks $KeystoreFile --out $FinalApk $AlignedApk

Write-Host ""
Write-Host "Paso 6: Verificando firma..." -ForegroundColor Green
& $apksigner verify $FinalApk

Remove-Item $AlignedApk -Force

Write-Host ""
Write-Host "=========================================" -ForegroundColor Cyan
Write-Host "  Compilacion exitosa!" -ForegroundColor Green
Write-Host "=========================================" -ForegroundColor Cyan
Write-Host "APK firmado: $FinalApk" -ForegroundColor Yellow
Write-Host ""
Get-Item $FinalApk | Format-List Name, Length, LastWriteTime
Write-Host ""
