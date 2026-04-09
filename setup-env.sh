#!/bin/bash

echo "========================================"
echo "Robbie - Setup de Variables de Entorno"
echo "========================================"
echo ""

# Verificar si gradle.properties ya existe
if [ -f "gradle.properties" ]; then
    echo "[!] gradle.properties ya existe"
    echo ""
    read -p "¿Deseas sobrescribirlo? (y/n): " -n 1 -r
    echo ""
    if [[ ! $REPLY =~ ^[Yy]$ ]]; then
        exit 0
    fi
fi

echo ""
echo "Creando gradle.properties desde .env.example..."
cp .env.example gradle.properties

echo ""
echo "========================================"
echo "[OK] gradle.properties creado"
echo "========================================"
echo ""
echo "IMPORTANTE:"
echo "1. Edita gradle.properties con tus credenciales reales"
echo "2. NO commitees gradle.properties al repositorio"
echo "3. Haz Gradle Sync en Android Studio"
echo ""
echo "Archivo: $(pwd)/gradle.properties"
echo ""
