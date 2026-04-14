# Robbie - Robot Retail Assistant

Aplicacion Android para robots de servicio OrionStar/AiNiRobot con capacidades de asistente retail inteligente.

## Caracteristicas

- 🤖 **Face Tracking**: Detecta y sigue personas automaticamente
- 💡 **Control de Luces LED**: Indicadores visuales de estado
- 🎤 **Wake Word Detection**: Activacion por voz con "Robbie"
- 🛍️ **Catalogo de Productos**: Gestion dinamica de productos
- 🧠 **Recomendaciones IA**: Motor de recomendaciones con Azure OpenAI
- ⚙️ **Configuracion Dinamica**: API REST para configuracion remota

---

## Setup Rapido

### 1. Clonar Repositorio

```bash
git clone <repository-url>
cd robbie
```

### 2. Configurar Variables de Entorno

#### Windows
```bash
setup-env.bat
```

#### Linux/Mac
```bash
chmod +x setup-env.sh
./setup-env.sh
```

O manualmente:
```bash
cp .env.example gradle.properties
```

### 3. Editar Credenciales

Abrir `gradle.properties` y reemplazar con tus credenciales de Azure OpenAI:

```properties
android.suppressUnsupportedCompileSdk=34
android.useAndroidX=true
android.enableJetifier=true
android.nonTransitiveRClass=false
android.defaults.buildfeatures.buildconfig=true
org.gradle.jvmargs=-Xmx2048m -Dfile.encoding=UTF-8
org.gradle.parallel=true
org.gradle.caching=true

# Azure OpenAI Configuration
# IMPORTANTE: No commitear este archivo con credenciales reales
AI_API_BASE_URL=tu-base-url-aqui
AI_API_KEY=tu-key-aqui

ROBBIE_CONFIG_API_URL=192.168.0.x:3000

```

### 4. Gradle Sync

En Android Studio:
- **File > Sync Project with Gradle Files**

### 5. Build & Run

- **Build > Rebuild Project**
- Conectar robot o emulador
- **Run > Run 'app'**

---

## Estructura del Proyecto

```
robbie/
├── app/                             # Aplicación Android nativa
│   ├── src/main/java/com/robbie/
│   │   ├── base/                    # Configuración base
│   │   │   └── config/
│   │   │       ├── RemoteConfigManager.java      # Gestión de config OrionStar
│   │   │       └── RobbieConfigApiClient.java    # Cliente API de configuración
│   │   ├── moduleapp/lidd/
│   │   │   └── RobotApp.java        # Application class principal
│   │   └── platform/
│   │       ├── react/               # Integración React Native
│   │       │   ├── EveActivity.java             # Activity principal RN
│   │       │   ├── PlatformReactNativeHost.java # Host RN
│   │       │   └── modules/                     # Módulos nativos
│   │       │       ├── ProductsModule.java      # Productos desde API
│   │       │       ├── RobbieConfigModule.java  # Configuración dinámica
│   │       │       ├── RobotSkillModule.java    # Habilidades del robot
│   │       │       ├── AgentModule.java         # Agent SDK
│   │       │       └── ...                      # Otros módulos
│   │       └── retail/              # Funcionalidades retail
│   │           ├── RobbieRetailActivity.java    # Activity retail con agente
│   │           ├── RobbieConfig.java            # Modelo de configuración
│   │           ├── Product.java                 # Modelo de producto
│   │           ├── ProductAdapter.java          # Adaptador de productos
│   │           ├── RecommendationEngine.java    # Motor de recomendaciones AI
│   │           └── AsyncTaskHelper.java         # Utilidades async
│   ├── src/main/res/                # Recursos Android
│   └── build.gradle                 # Dependencias de la app
│
├── react-native-app/                # Aplicación React Native (UI)
│   ├── src/
│   │   ├── components/              # Componentes reutilizables
│   │   │   ├── ProductCard.tsx      # Tarjeta de producto
│   │   │   └── SearchBar.tsx        # Barra de búsqueda
│   │   ├── screens/                 # Pantallas principales
│   │   │   ├── MenuScreen.tsx       # Menú principal (8 opciones)
│   │   │   ├── RetailScreen.tsx     # Catálogo de productos
│   │   │   ├── PromoScreen.tsx      # Promociones
│   │   │   └── ConfigScreen.tsx     # Configuración
│   │   ├── services/                # Servicios
│   │   │   └── CloudApi.ts          # Cliente API
│   │   ├── stores/                  # Estado global
│   │   └── types/                   # Tipos TypeScript
│   ├── package.json                 # Dependencias RN
│   └── tsconfig.json                # Config TypeScript
│
├── gradle/                          # Gradle wrapper
├── .env.example                     # Template de variables de entorno
├── gradle.properties                # Credenciales y config (NO commitear)
├── .gitignore                       # Incluye gradle.properties
├── build-sign-app.ps1               # Script de compilación Windows
├── build-sign-app.sh                # Script de compilación Linux/Mac
├── setup-env.bat                    # Setup Windows
├── setup-env.sh                     # Setup Linux/Mac
├── BUILD_SIGN_README.md             # Guía de compilación y firma
├── CONFIGURACION_API.md             # Documentación API de configuración
├── CONFIGURACION_DINAMICA.md        # Documentación configuración dinámica
├── EJEMPLO_CONFIG_API.json          # Ejemplo de respuesta API
└── README.md                        # Este archivo
```

---

### API REST de Configuracion

Opcionalmente, puedes usar la API REST para configurar persona, objetivo y productos:

```bash
cd robbie-config-api
npm install
npm start
```

Ver [robbie-config-api/README.md](robbie-config-api/README.md) para mas detalles.

---

## Uso

### Interaccion por Voz

```
Usuario: "Robbie, necesito proteina sin lactosa"
→ Robot recomienda productos automaticamente
```

### Wake Word

El robot detecta las siguientes variantes:
- Robbie
- Robi
- Rubi
- Robin
- Robe
- Robby
- Lobi

### Face Tracking

El robot automaticamente:
1. Detecta personas en su campo de vision
2. Sigue con la cabeza a la persona mas cercana
3. Reconecta si pierde el tracking

---

## Desarrollo

### Requisitos

- Android Studio Arctic Fox o superior
- JDK 8 o superior
- Gradle 7.5 o superior
- Android SDK 26+ (minSdk)
- Robot OrionStar o emulador

### Build Variants

```gradle
buildTypes {
    debug {
        // Credenciales de desarrollo
    }
    release {
        // Credenciales de produccion
        minifyEnabled true
        proguardFiles ...
    }
}
```

### Testing

```bash
# Unit tests
./gradlew test

# Instrumented tests
./gradlew connectedAndroidTest

# Logs
adb logcat | grep -E "RobotApp|EveActivity|RecommendationEngine"
```

---

## Arquitectura

### Componentes Principales

1. **RobotApp**: Application class, inicializa servicios
2. **EveActivity**: Activity principal con React Native
3. **PageAgent**: Agente de voz con Actions personalizadas
4. **RecommendationEngine**: Motor de recomendaciones con Azure OpenAI
5. **RobbieConfig**: Gestor de configuracion dinamica

### Flujo de Datos

```
Usuario habla → Wake Word Detection → AgentCore.query()
    ↓
LLM decide Action → RECOMMEND_PRODUCTS
    ↓
RecommendationEngine → Azure OpenAI → Productos recomendados
    ↓
TTS explica → Eventos a React Native → UI actualizada
```

---

## Seguridad

### ✅ Buenas Practicas

1. **NO commitear `gradle.properties` con credenciales**
2. **Usar `.env.example` como plantilla**
3. **Rotar API keys periodicamente**
4. **Diferentes credenciales por ambiente (dev/prod)**

### Archivo .gitignore

```gitignore
# IMPORTANTE: No commitear credenciales
gradle.properties
```

---

## Troubleshooting

### Error: "Cannot resolve symbol 'BuildConfig'"

```bash
# Solucion
1. Verificar gradle.properties existe
2. File > Sync Project with Gradle Files
3. Build > Rebuild Project
```

### Error: "API error: 401"

```bash
# Solucion
1. Verificar AI_API_KEY en gradle.properties
2. Verificar key no expirada en Azure Portal
3. Gradle Sync y Rebuild
```

---

## Documentacion

- [BUILD_SIGN_README.md](BUILD_SIGN_README.md) - Guia de compilacion y firma
- [IMPLEMENTACION_ROBBIE_RETAIL.md](../IMPLEMENTACION_ROBBIE_RETAIL.md) - Documentacion completa
- [robbie-config-api/README.md](robbie-config-api/README.md) - API REST

---

## Deployment

### Build y Firma con Scripts Automatizados

#### Windows (PowerShell)

El script `build-sign-app.ps1` automatiza todo el proceso de compilacion, alineamiento y firma del APK:

```powershell
.\build-sign-app.ps1
```

Opcionalmente, especifica la ruta del keystore:
```powershell
.\build-sign-app.ps1 -KeystoreFile "ruta\a\tu\keystore.jks"
```

**El script realiza los siguientes pasos:**
1. Limpia el proyecto (`gradlew clean`)
2. Compila el bundle de React Native (si existe `react-native-app/package.json`)
3. Compila el APK release sin firmar
4. Alinea el APK con `zipalign`
5. Firma el APK con `apksigner`
6. Verifica la firma del APK

**Requisitos:**
- Keystore file (por defecto busca `release.keystore` en la raiz del proyecto)
- Android SDK instalado con build-tools
- Si no tienes keystore, crea uno con:
  ```powershell
  keytool -genkey -v -keystore release.keystore -alias release -keyalg RSA -keysize 2048 -validity 10000
  ```

**APK final generado en:** `app/build/outputs/apk/release/robbie-release-signed.apk`

#### Linux/Mac

Para sistemas Unix, usa el script equivalente:
```bash
chmod +x build-sign-app.sh
./build-sign-app.sh
```

### CI/CD

Configurar secrets en GitHub Actions o GitLab CI para CI/CD.

---

## Contribuir

1. Fork el repositorio
2. Crear branch: `git checkout -b feature/nueva-funcionalidad`
3. Commit cambios: `git commit -am 'Agregar nueva funcionalidad'`
4. Push: `git push origin feature/nueva-funcionalidad`
5. Crear Pull Request

**IMPORTANTE**: NO commitear `gradle.properties` con credenciales reales.

---

## Licencia

[Especificar licencia]

---

## Contacto

Para soporte o preguntas, contactar al equipo de desarrollo.
