# Configuración de Robbie Config API

## Descripción

Este documento explica cómo conectar el robot Robbie con la API de configuración para gestionar productos, persona y objetivo del agente.

## Endpoints Disponibles

La API de configuración (`robbie-config-api`) expone los siguientes endpoints:

### Configuración General
- `GET /robbie/config` - Obtener configuración completa (persona, objective, storeName, products)
- `PUT /robbie/config` - Actualizar configuración
- `POST /robbie/config/reset` - Restaurar configuración por defecto

### Productos
- `GET /robbie/config/products` - Obtener lista de productos
- `POST /robbie/config/products` - Agregar nuevo producto
- `PUT /robbie/config/products/:id` - Actualizar producto existente
- `DELETE /robbie/config/products/:id` - Eliminar producto

## Configuración del Robot

### 1. Configurar URL de la API

Edita el archivo `gradle.properties` (o cópialo desde `.env.example`):

```properties
# Robbie Config API
ROBBIE_CONFIG_API_URL=http://TU_IP:3000
```

**Importante**: Reemplaza `TU_IP` con:
- La IP de tu computadora si el robot está en la misma red
- `localhost` si estás usando el emulador
- La URL pública si la API está en la nube

### 2. Iniciar la API

En la carpeta `robbie-config-api`:

```bash
npm install
npm start
```

La API estará disponible en `http://localhost:3000`

### 3. Compilar e Instalar el APK

```powershell
.\build-sign-app.ps1 -KeystoreFile "ruta\al\keystore.jks"
adb install -r app\build\outputs\apk\release\robbie-release-signed.apk
```

## Uso desde React Native

El módulo `RobbieConfig` está disponible en JavaScript:

```javascript
import { NativeModules } from 'react-native';
const { RobbieConfig } = NativeModules;

// Obtener configuración completa
const config = await RobbieConfig.getConfig();
console.log('Persona:', config.persona);
console.log('Objective:', config.objective);
console.log('Products:', config.products);

// Obtener solo productos
const products = await RobbieConfig.getProducts();

// Actualizar configuración
await RobbieConfig.updateConfig({
  persona: "Nuevo texto de persona...",
  objective: "Nuevo objetivo..."
});

// Restaurar configuración por defecto
await RobbieConfig.resetConfig();
```

## Estructura de Datos

### Configuración Completa
```json
{
  "persona": "Tu nombre es Robbie...",
  "objective": "Ayudar a los clientes...",
  "firebaseCollection": "products",
  "products": [...]
}
```

### Producto
```json
{
  "id": "prod_001",
  "name": "Proteina Whey Gold Standard",
  "category": "Proteinas",
  "subcategory": "Whey",
  "price": 1299.00,
  "discount": 15,
  "image": "https://example.com/images/whey-gold.jpg",
  "description": "Proteina de suero de alta calidad...",
  "ingredients": "Whey protein isolate, cocoa...",
  "tags": ["proteina", "whey", "musculo"],
  "inStock": true,
  "sku": "WGS-2LB-CHOC",
  "brand": "Optimum Nutrition"
}
```

## Verificar Conexión

Para verificar que el robot puede conectarse a la API:

1. Asegúrate de que la API esté corriendo
2. Verifica que el robot y la computadora estén en la misma red
3. Revisa los logs del robot:

```bash
adb logcat -s RobbieConfigApiClient:* RobbieConfigModule:*
```

## Troubleshooting

### Error: Connection refused
- Verifica que la API esté corriendo (`npm start` en `robbie-config-api`)
- Verifica que la URL en `gradle.properties` sea correcta
- Si usas `localhost`, asegúrate de que el robot esté en el emulador

### Error: Network unreachable
- Verifica que el robot y la computadora estén en la misma red WiFi
- Usa la IP local de tu computadora, no `localhost`
- Verifica el firewall de Windows

### Error: Timeout
- Aumenta el timeout en `RobbieConfigApiClient.java` (línea 24)
- Verifica la velocidad de la red

## Archivos Relacionados

- `app/build.gradle` - Configuración de BuildConfig con URL de API
- `app/src/main/java/com/robbie/base/config/RobbieConfigApiClient.java` - Cliente HTTP
- `app/src/main/java/com/robbie/platform/react/modules/RobbieConfigModule.java` - Módulo React Native
- `app/src/main/java/com/robbie/platform/react/PlatformReactNativeHost.java` - Registro del módulo
