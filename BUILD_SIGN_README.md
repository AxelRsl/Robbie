# Script de Compilacion, Alineacion y Firma

Scripts para compilar, alinear con zipalign y firmar la aplicacion Android de robbie.

## Requisitos Previos

1. Android SDK instalado con ANDROID_HOME configurado
2. Java JDK 8 o superior
3. Un keystore para firmar la aplicacion

## Crear Keystore (primera vez)

Si no tienes un keystore, crea uno con:

```bash
keytool -genkey -v -keystore release.keystore -alias release \
  -keyalg RSA -keysize 2048 -validity 10000
```

Guarda las credenciales de forma segura.

## Uso en Windows (PowerShell)

### Opcion 1: Variables de entorno

```powershell
$env:KEYSTORE_PASS = "tu_password_del_keystore"
$env:KEY_PASS = "tu_password_de_la_key"
.\build-sign-app.ps1
```

### Opcion 2: Parametros

```powershell
.\build-sign-app.ps1 -KeystoreFile "release.keystore" `
  -KeystorePass "tu_password_del_keystore" `
  -KeyAlias "release" `
  -KeyPass "tu_password_de_la_key"
```

## Uso en Linux/Mac (Bash)

### Opcion 1: Variables de entorno

```bash
export KEYSTORE_PASS="tu_password_del_keystore"
export KEY_PASS="tu_password_de_la_key"
./build-sign-app.sh
```

### Opcion 2: Inline

```bash
KEYSTORE_PASS="tu_password_del_keystore" KEY_PASS="tu_password_de_la_key" ./build-sign-app.sh
```

## Variables de Configuracion

- **KEYSTORE_FILE**: Ruta al archivo keystore (default: `release.keystore`)
- **KEYSTORE_PASS**: Password del keystore (requerido)
- **KEY_ALIAS**: Alias de la key en el keystore (default: `release`)
- **KEY_PASS**: Password de la key (requerido)

## Proceso del Script

1. **Clean**: Limpia el proyecto con `gradlew clean`
2. **Build**: Compila el APK release sin firmar con `gradlew assembleRelease`
3. **Align**: Alinea el APK con `zipalign -v -p 4`
4. **Sign**: Firma el APK con `apksigner`
5. **Verify**: Verifica la firma del APK

## Salida

El APK firmado se genera en:
```
app/build/outputs/apk/release/robbie-release-signed.apk
```

## Notas de Seguridad

- Nunca subas el keystore al repositorio
- Nunca subas las credenciales al repositorio
- Usa variables de entorno o un gestor de secretos
- Agrega `*.keystore` y `*.jks` al `.gitignore`

## Troubleshooting

### Error: zipalign no encontrado

Asegurate de que ANDROID_HOME este configurado:

```bash
export ANDROID_HOME=/ruta/a/tu/android-sdk
```

### Error: apksigner no encontrado

Instala las build-tools mas recientes:

```bash
sdkmanager "build-tools;34.0.0"
```

### Error: Keystore no encontrado

Verifica la ruta del keystore o crea uno nuevo con keytool.
