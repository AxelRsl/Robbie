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
AI_API_BASE_URL=https://tu-endpoint.openai.azure.com/openai/deployments/tu-deployment/chat/completions?api-version=2025-01-01-preview
AI_API_KEY=tu-api-key-aqui
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
├── app/
│   ├── src/main/java/com/ainirobot/
│   │   ├── moduleapp/lidd/          # Application class
│   │   ├── platform/
│   │   │   ├── react/               # React Native integration
│   │   │   │   └── EveActivity.java # Main activity
│   │   │   └── retail/              # Retail features
│   │   │       ├── Product.java
│   │   │       ├── ProductAdapter.java
│   │   │       ├── RecommendationEngine.java
│   │   │       ├── RobbieConfig.java
│   │   │       └── AsyncTaskHelper.java
│   │   └── ...
│   └── build.gradle                 # App dependencies
├── .env.example                     # Template (commitear)
├── gradle.properties                # Credenciales (NO commitear)
├── .gitignore                       # Incluye gradle.properties
├── setup-env.bat                    # Setup Windows
├── setup-env.sh                     # Setup Linux/Mac
├── BUILD_SIGN_README.md             # Guia de compilacion y firma
└── README.md                        # Este archivo
```

---

## Configuracion

### Variables de Entorno

Las credenciales de Azure OpenAI se configuran en `gradle.properties`:

```properties
# Azure OpenAI Configuration
AI_API_BASE_URL=https://...
AI_API_KEY=...
```

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

### Face Tracking

El robot automaticamente:
1. Detecta personas en su campo de vision
2. Sigue con la cabeza a la persona mas cercana
3. Reconecta si pierde el tracking

### Luces LED

- 🔴 **Rojo solido**: Estado por defecto
- 🔵 **Azul pulsante**: Usuario hablando

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

### Actions Disponibles

- **SAY**: Respuesta de voz generica
- **SHOW_HAPPY**: Respuesta alegre
- **SHOW_SAD**: Respuesta empatica
- **SHOW_ANGRY**: Respuesta calmada
- **RECOMMEND_PRODUCTS**: Recomienda productos
- **SEARCH_PRODUCTS**: Busca productos
- **SHOW_PRODUCT_DETAIL**: Detalle de producto

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

### APK Release

```bash
./gradlew assembleRelease
```

APK generado en: `app/build/outputs/apk/release/app-release.apk`

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
