# Xiabao Retail App - React Native

Aplicación React Native para robot Xiabao con múltiples modos de operación: Minorista, Promoción y Menú.

## Características

### Modo Minorista
- **2 Templates disponibles**:
  - **Grid (Cuadrícula)**: Vista en rejilla de productos
  - **List (Lista)**: Vista detallada en lista
- **Búsqueda inteligente**: 
  - Búsqueda por texto
  - Búsqueda por voz (integrada con el robot)
  - Filtrado por categorías y tags
- **Integración con robot**: El robot anuncia los productos encontrados

### Modo Promoción
- **2 Templates disponibles**:
  - **Video**: Reproducción de videos promocionales
  - **Carousel**: Carrusel de imágenes y productos
- **Soporte de video**: Reproducción de videos desde la nube
- **Productos destacados**: Muestra productos en promoción

### Sistema de Menú
- **2 Templates disponibles**:
  - **Classic**: Menú clásico con botones grandes
  - **Modern**: Menú moderno con cards
- **Navegación entre modos**: Acceso rápido a todos los modos
- **Configuración**: Acceso a configuración de la nube

## Instalación

### 1. Instalar dependencias

```bash
cd react-native-app
npm install
```

### 2. Instalar dependencias de iOS (solo macOS)

```bash
cd ios
pod install
cd ..
```

### 3. Ejecutar en desarrollo

```bash
# Android
npm run android

# iOS
npm run ios
```

## Generar Bundle para Producción

### Generar bundle Android

```bash
npm run bundle:android
```

Esto generará el bundle en:
- `../app/src/main/assets/platform.android.bundle`

### Generar bundle para distribución

```bash
npm run bundle:release
```

Esto generará el bundle en:
- `./build/platform.android.bundle`

## Estructura del Proyecto

```
react-native-app/
├── src/
│   ├── components/          # Componentes reutilizables
│   │   ├── ProductCard.tsx  # Card de producto (grid/list)
│   │   ├── SearchBar.tsx    # Barra de búsqueda
│   │   ├── PromoVideo.tsx   # Reproductor de video
│   │   └── MenuCard.tsx     # Card de menú
│   │
│   ├── screens/             # Pantallas principales
│   │   ├── RetailScreen.tsx # Modo Minorista
│   │   ├── PromoScreen.tsx  # Modo Promoción
│   │   ├── MenuScreen.tsx   # Menú principal
│   │   └── ConfigScreen.tsx # Configuración
│   │
│   ├── services/            # Servicios y APIs
│   │   ├── RobotBridge.ts   # Bridge nativo
│   │   └── CloudApi.ts      # Cliente API nube
│   │
│   ├── stores/              # Estado global (Zustand)
│   │   └── useAppStore.ts   # Store principal
│   │
│   └── types/               # Tipos TypeScript
│       └── index.ts         # Tipos principales
│
├── package.json
├── tsconfig.json
├── babel.config.js
└── metro.config.js
```

## Configuración de la Nube

La aplicación se conecta a la nube de OrionStar usando `RemoteConfigManager` del lado nativo.

### Endpoints configurables:
- API OrionBase (6 regiones)
- BI/Telemetría
- AI Open
- Portal AgentPOI
- MQTT Broker

### Cambiar configuración:

Desde JavaScript:
```typescript
import { RobotBridge } from '@/services/RobotBridge';

const config = await RobotBridge.getConfig();
await RobotBridge.updateConfig({
  cloudConfig: {
    apiDomain: 'https://global-api-orionbase.orionstar.com',
    region: 'global'
  }
});
```

## Módulos Nativos

### RobotConfigModule
```typescript
// Obtener configuración
const config = await RobotBridge.getConfig();

// Actualizar configuración
await RobotBridge.updateConfig(newConfig);
```

### RobotSkillModule
```typescript
// Ejecutar acción del robot
await RobotBridge.executeAction('orion.agent.action.SAY', {
  text: 'Hola, bienvenido'
});

// Navegar
await RobotBridge.navigate('recepción');
```

### CloudApiModule
```typescript
// Conectar a la nube
await RobotBridge.connectToCloud();

// Obtener estado
const status = await RobotBridge.getCloudStatus();
```

### ProductsModule
```typescript
// Buscar productos
const results = await RobotBridge.searchProducts('laptop');

// Obtener productos por categoría
const products = await RobotBridge.getProducts('Electrónica');
```

## Personalización

### Cambiar template de Minorista

```typescript
import { useAppStore } from '@/stores/useAppStore';

const { setRetailTemplate } = useAppStore();
setRetailTemplate('list'); // o 'grid'
```

### Cambiar template de Promoción

```typescript
const { setPromoTemplate } = useAppStore();
setPromoTemplate('carousel'); // o 'video'
```

### Cambiar template de Menú

```typescript
const { setMenuTemplate } = useAppStore();
setMenuTemplate('modern'); // o 'classic'
```

## Actualización Remota (OTA)

La aplicación soporta actualizaciones remotas del bundle JavaScript sin reinstalar el APK.

### Servidor de actualizaciones

Crea un archivo `manifest.json` en tu servidor:

```json
{
  "bundle_version": "1.0.1",
  "bundle_url": "https://tu-servidor.com/bundles/platform.android.bundle",
  "bundle_hash": "sha256_hash_del_bundle",
  "release_notes": "Nuevas funcionalidades y mejoras"
}
```

### Configurar URL de actualizaciones

En `RemoteConfigManager.java`, agrega:

```java
public String getBundleUpdateUrl() {
    return prefs.getString("bundle_update_url", 
        "https://tu-servidor.com/updates");
}
```

## Testing

```bash
# Ejecutar tests
npm test

# Ejecutar tests en watch mode
npm test -- --watch
```

## Troubleshooting

### Error: "Cannot find module"
```bash
rm -rf node_modules
npm install
```

### Error de Metro Bundler
```bash
npm start -- --reset-cache
```

### Error de build Android
```bash
cd android
./gradlew clean
cd ..
npm run android
```

## Licencia

Proyecto interno - OrionStar/AiNiRobot
