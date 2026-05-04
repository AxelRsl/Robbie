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

function Resolve-JavaHome {
    if ($env:JAVA_HOME) {
        $javaExe = Join-Path $env:JAVA_HOME "bin\java.exe"
        if (Test-Path $javaExe) {
            return $env:JAVA_HOME.TrimEnd("\")
        }
        Write-Host "  JAVA_HOME actual invalido: $env:JAVA_HOME" -ForegroundColor Yellow
    }

    $javaCandidates = @()

    try {
        $javaCommand = Get-Command java.exe -ErrorAction SilentlyContinue
        if ($javaCommand -and $javaCommand.Source) {
            $javaCandidates += (Split-Path (Split-Path $javaCommand.Source -Parent) -Parent)
        }
    } catch {}

    $javaCandidates += @(
        "C:\Program Files\Eclipse Adoptium\jdk-17.0.18.8-hotspot",
        "C:\Program Files\Java\jdk-17",
        "C:\Program Files\Android\Android Studio\jbr"
    )

    $javaCandidates = $javaCandidates | Where-Object { $_ } | Select-Object -Unique
    foreach ($candidate in $javaCandidates) {
        $javaExe = Join-Path $candidate "bin\java.exe"
        if (Test-Path $javaExe) {
            return $candidate.TrimEnd("\")
        }
    }

    return $null
}

Write-Host "=========================================" -ForegroundColor Cyan
Write-Host "  Build, Align & Sign - $AppName" -ForegroundColor Cyan
Write-Host "=========================================" -ForegroundColor Cyan
Write-Host ""
Write-Host "Paso 0: Verificando Java..." -ForegroundColor Green
$resolvedJavaHome = Resolve-JavaHome
if (-not $resolvedJavaHome) {
    Write-Host "Error: No se encontro un JDK valido. Configura JAVA_HOME a un JDK con bin\java.exe" -ForegroundColor Red
    exit 1
}
$env:JAVA_HOME = $resolvedJavaHome
Write-Host "  JAVA_HOME activo: $env:JAVA_HOME" -ForegroundColor Green
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
