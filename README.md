# Robbie - Robot Retail Assistant

Aplicación Android para robots de servicio OrionStar/AiNiRobot con capacidades de asistente retail inteligente basado en Agent OS SDK 0.4.5-SNAPSHOT.

## Características

- 🤖 **Face Tracking**: Detecta y sigue personas automáticamente
- 💡 **Control de Luces LED**: Efectos visuales programables (SOLID, BREATHING, BLINK, RAINBOW, PULSE, WAVE)
- 🎤 **Agent OS Integration**: Integración completa con Agent OS SDK para ASR/TTS/LLM
- 🛍️ **Catálogo de Productos**: Gestión dinámica con búsqueda inteligente
- 🧠 **Recomendaciones IA**: Motor de recomendaciones con Azure OpenAI
- 🗺️ **Navegación Autónoma**: Sistema de navegación por waypoints
- ⚙️ **Configuración Dinámica**: API REST para configuración remota
- 🏗️ **Arquitectura Modular**: Separación clara entre Agent OS y lógica de negocio

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
│   │   ├── core/                    # Módulos centrales del robot
│   │   │   ├── hardware/
│   │   │   │   ├── LedController.java           # Control de LEDs con efectos
│   │   │   │   ├── SensorManager.java           # Gestión unificada de sensores
│   │   │   │   └── ActuatorManager.java         # Control de movimiento
│   │   │   ├── navigation/
│   │   │   │   └── NavigationManager.java       # Sistema de navegación
│   │   │   └── modes/
│   │   │       ├── ModeManager.java             # Gestión de modos
│   │   │       ├── RetailMode.java              # Comportamientos retail
│   │   │       └── ExhibitionMode.java          # Comportamientos exhibición
│   │   ├── data/                    # Capa de datos
│   │   │   ├── local/
│   │   │   │   ├── RobbieDatabase.java          # Base de datos local
│   │   │   │   └── entity/ProductEntity.java    # Entidades de BD
│   │   │   └── remote/              # APIs remotas
│   │   ├── moduleapp/lidd/
│   │   │   └── RobotApp.java        # Application class principal
│   │   └── platform/
│   │       ├── agent/               # 🏗️ NUEVA ARQUITECTURA AGENT OS
│   │       │   ├── IAgentBridge.java            # Interface para Agent OS
│   │       │   ├── RobbieAgentBridge.java       # Implementación Agent OS SDK
│   │       │   └── RobotActionHandler.java      # Lógica de acciones del robot
│   │       ├── react/               # Integración React Native
│   │       │   ├── EveActivity.java             # Activity principal (refactorizada)
│   │       │   ├── PlatformReactNativeHost.java # Host RN
│   │       │   └── modules/                     # Módulos nativos RN
│   │       │       ├── ProductsModule.java      # Productos desde API
│   │       │       ├── RobbieConfigModule.java  # Configuración dinámica
│   │       │       ├── AgentModule.java         # Bridge Agent SDK
│   │       │       ├── LedModule.java           # Control LEDs desde RN
│   │       │       └── ProductSearchModule.java # Búsqueda de productos
│   │       └── retail/              # Funcionalidades retail
│   │           ├── Product.java                 # Modelo de producto
│   │           ├── RecommendationEngine.java    # Motor de recomendaciones AI
│   │           └── RobbieConfig.java            # Modelo de configuración
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

### Interacción por Voz

```
Usuario: "¿Qué vitaminas tienes?"
→ Robot busca productos → Muestra resultados en pantalla → TTS explica

Usuario: "Llévame a la sección de proteínas"
→ Robot navega automáticamente → Informa llegada

Usuario: "Cambia las luces a azul"
→ Robot cambia LEDs → Confirma acción
```

### Capacidades del Robot

#### 🎤 **Procesamiento de Voz**
- ASR (Automatic Speech Recognition) integrado
- TTS (Text-to-Speech) con respuestas naturales
- LLM para comprensión contextual
- Sin wake word (siempre escuchando)

#### 🤖 **Face Tracking**
El robot automáticamente:
1. Detecta personas en su campo de visión
2. Sigue con la cabeza a la persona más cercana
3. Reconecta si pierde el tracking
4. Se detiene durante navegación

#### 🗺️ **Navegación Autónoma**
- Navegación por waypoints configurados
- Detección de obstáculos
- Cancelación por voz ("detente", "para")
- Reporte de estado en tiempo real

#### 💡 **Control de LEDs**
- 6 efectos programables: SOLID, BREATHING, BLINK, RAINBOW, PULSE, WAVE
- Colores personalizables por voz o API
- Indicadores de estado (escuchando, procesando, navegando)
- Paleta Ikalp por defecto (#E4027C)

---

## Desarrollo

### Requisitos

- Android Studio Arctic Fox o superior
- JDK 8 o superior
- Gradle 7.5 o superior
- Android SDK 26+ (minSdk), compileSdk 34
- **Agent OS SDK 0.4.5-SNAPSHOT** (com.orionstar.agent:sdk)
- Robot OrionStar con Agent OS o emulador
- React Native 0.71.8 (embebido)
- Node.js 16+ (para robbie-config-api opcional)

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

### 🏗️ Nueva Arquitectura Modular (v2.0)

La aplicación ha sido refactorizada con una arquitectura limpia que separa las responsabilidades:

#### Componentes Principales

1. **IAgentBridge**: Interface que abstrae el Agent OS
2. **RobbieAgentBridge**: Implementación concreta del Agent OS SDK 0.4.5-SNAPSHOT
3. **RobotActionHandler**: Centraliza toda la lógica de acciones del robot
4. **EveActivity**: Activity principal, solo maneja eventos y callbacks
5. **Core Modules**: Módulos centrales (hardware, navegación, modos)

#### Beneficios de la Nueva Arquitectura

- ✅ **Separación de responsabilidades**: Agent OS vs. lógica de negocio
- ✅ **Testeable**: Cada componente es independiente
- ✅ **Mantenible**: Cambios en Agent OS no afectan la lógica del robot
- ✅ **Escalable**: Fácil agregar nuevas funcionalidades
- ✅ **Intercambiable**: Se puede cambiar de Agent OS sin afectar el resto

### Flujo de Datos (Nueva Arquitectura)

```
Usuario habla → RobbieAgentBridge (Agent OS SDK)
    ↓
IAgentBridge.ActionCallback → RobotActionHandler
    ↓
RobotActionHandler ejecuta acción → Core Modules (LED, Navigation, etc.)
    ↓
ActionResultCallback → EveActivity
    ↓
Eventos React Native → UI actualizada
```

### Patrón de Arquitectura

```
┌─────────────────┐    ┌──────────────────┐    ┌─────────────────┐
│   EveActivity   │◄──►│ RobbieAgentBridge│◄──►│   Agent OS SDK  │
│   (UI Events)   │    │  (Agent Bridge)  │    │ (ASR/TTS/LLM)   │
└─────────────────┘    └──────────────────┘    └─────────────────┘
         │                       │
         ▼                       ▼
┌─────────────────┐    ┌──────────────────┐
│ React Native UI │    │RobotActionHandler│
│   (Frontend)    │    │ (Business Logic) │
└─────────────────┘    └──────────────────┘
                                │
                                ▼
                    ┌──────────────────┐
                    │   Core Modules   │
                    │ (LED, Nav, etc.) │
                    └──────────────────┘
```

### 🆕 Cambios en la Nueva Arquitectura (v2.0)

#### Antes (Arquitectura Monolítica)
- ❌ EveActivity contenía toda la lógica (>1000 líneas)
- ❌ PageAgent mezclado con lógica de negocio
- ❌ Difícil de testear y mantener
- ❌ Acoplamiento fuerte con Agent OS SDK

#### Ahora (Arquitectura Modular)
- ✅ **EveActivity**: Solo maneja eventos y callbacks (~300 líneas)
- ✅ **IAgentBridge**: Abstracción del Agent OS
- ✅ **RobbieAgentBridge**: Implementación específica del SDK
- ✅ **RobotActionHandler**: Toda la lógica de acciones centralizada
- ✅ **Core Modules**: Hardware, navegación y modos separados

#### Beneficios Técnicos
- 🔧 **Mantenibilidad**: Cada clase tiene una responsabilidad única
- 🧪 **Testeable**: Se pueden hacer mocks de cada componente
- 🔄 **Intercambiable**: Fácil cambiar de Agent OS a otro sistema
- 📈 **Escalable**: Agregar nuevas funcionalidades es más simple
- 🐛 **Debuggeable**: Errores más fáciles de localizar

#### Módulos Eliminados (Limpieza de Código)
- 🗑️ `AsyncTaskHelper.java` - No se usaba
- 🗑️ `ProductAdapter.java` - RecyclerView innecesario
- 🗑️ `RobbieRetailActivity.java` - Reemplazado por EveActivity
- 🗑️ `ModeModule.java` - No se usaba en React Native
- 🗑️ `MovementModule.java` - No se usaba en React Native
- 🗑️ `OrionAuthManager.java` - No se usaba

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
