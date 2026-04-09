#!/bin/bash

set -e

PROJECT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
APP_NAME="robbie"
BUILD_TYPE="release"
OUTPUT_DIR="$PROJECT_DIR/app/build/outputs/apk/$BUILD_TYPE"
UNSIGNED_APK="$OUTPUT_DIR/app-$BUILD_TYPE-unsigned.apk"
ALIGNED_APK="$OUTPUT_DIR/app-$BUILD_TYPE-unsigned-aligned.apk"
FINAL_APK="$OUTPUT_DIR/$APP_NAME-$BUILD_TYPE-signed.apk"

KEYSTORE_FILE="${KEYSTORE_FILE:-$PROJECT_DIR/release.keystore}"
KEYSTORE_PASS="${KEYSTORE_PASS:-}"
KEY_ALIAS="${KEY_ALIAS:-release}"
KEY_PASS="${KEY_PASS:-}"

echo "========================================="
echo "  Build, Align & Sign - $APP_NAME"
echo "========================================="
echo ""

if [ -z "$KEYSTORE_PASS" ]; then
    echo "Error: KEYSTORE_PASS no esta definida"
    echo "Uso: KEYSTORE_PASS=tu_password KEY_PASS=tu_key_password ./build-sign-app.sh"
    echo ""
    echo "O exporta las variables de entorno:"
    echo "  export KEYSTORE_FILE=/ruta/a/tu.keystore"
    echo "  export KEYSTORE_PASS=tu_password"
    echo "  export KEY_ALIAS=tu_alias"
    echo "  export KEY_PASS=tu_key_password"
    exit 1
fi

if [ ! -f "$KEYSTORE_FILE" ]; then
    echo "Error: Keystore no encontrado en: $KEYSTORE_FILE"
    echo ""
    echo "Para crear un keystore nuevo:"
    echo "  keytool -genkey -v -keystore release.keystore -alias release \\"
    echo "    -keyalg RSA -keysize 2048 -validity 10000"
    exit 1
fi

echo "Paso 1: Limpiando proyecto..."
./gradlew clean

echo ""
echo "Paso 2: Compilando APK release (sin firmar)..."
./gradlew assembleRelease

if [ ! -f "$UNSIGNED_APK" ]; then
    echo "Error: APK sin firmar no encontrado en $UNSIGNED_APK"
    exit 1
fi

echo ""
echo "Paso 3: Alineando APK con zipalign..."
if command -v zipalign &> /dev/null; then
    zipalign -v -p 4 "$UNSIGNED_APK" "$ALIGNED_APK"
else
    ZIPALIGN="$ANDROID_HOME/build-tools/$(ls $ANDROID_HOME/build-tools | sort -V | tail -1)/zipalign"
    if [ ! -f "$ZIPALIGN" ]; then
        echo "Error: zipalign no encontrado. Asegurate de tener ANDROID_HOME configurado"
        exit 1
    fi
    "$ZIPALIGN" -v -p 4 "$UNSIGNED_APK" "$ALIGNED_APK"
fi

echo ""
echo "Paso 4: Firmando APK con apksigner..."
if command -v apksigner &> /dev/null; then
    apksigner sign --ks "$KEYSTORE_FILE" \
        --ks-pass "pass:$KEYSTORE_PASS" \
        --key-pass "pass:$KEY_PASS" \
        --ks-key-alias "$KEY_ALIAS" \
        --out "$FINAL_APK" \
        "$ALIGNED_APK"
else
    APKSIGNER="$ANDROID_HOME/build-tools/$(ls $ANDROID_HOME/build-tools | sort -V | tail -1)/apksigner"
    if [ ! -f "$APKSIGNER" ]; then
        echo "Error: apksigner no encontrado. Asegurate de tener ANDROID_HOME configurado"
        exit 1
    fi
    "$APKSIGNER" sign --ks "$KEYSTORE_FILE" \
        --ks-pass "pass:$KEYSTORE_PASS" \
        --key-pass "pass:$KEY_PASS" \
        --ks-key-alias "$KEY_ALIAS" \
        --out "$FINAL_APK" \
        "$ALIGNED_APK"
fi

echo ""
echo "Paso 5: Verificando firma..."
if command -v apksigner &> /dev/null; then
    apksigner verify "$FINAL_APK"
else
    "$APKSIGNER" verify "$FINAL_APK"
fi

rm -f "$ALIGNED_APK"

echo ""
echo "========================================="
echo "  Compilacion exitosa!"
echo "========================================="
echo "APK firmado: $FINAL_APK"
echo ""
ls -lh "$FINAL_APK"
echo ""
