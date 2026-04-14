# Robbie Local API

API REST embebida en el robot para administración remota desde el panel web.

## Arquitectura

```
┌─────────────────┐         HTTP REST          ┌─────────────────┐
│   LIDDAI Panel  │ ◄─────────────────────────► │  Robot (Android) │
│   (Next.js)     │    http://robot-ip:8080    │  + SQLite/Room   │
└─────────────────┘                             └─────────────────┘
```

## Configuración

El servidor se inicia automáticamente cuando arranca la aplicación del robot.

- **Puerto por defecto:** 8080
- **URL:** `http://<robot-ip>:8080`

Para obtener la IP del robot, puedes:
1. Ver en la notificación del servicio
2. Revisar los logs: `adb logcat | grep RobbieApiService`
3. Usar `adb shell ip addr show wlan0`

## Endpoints

### Health Check

```
GET /api/health
```

Respuesta:
```json
{
  "status": "ok",
  "server": "RobbieApiServer",
  "version": "1.0.0",
  "productCount": 42
}
```

---

### Products

#### Listar todos los productos
```
GET /api/products
```

#### Obtener producto por ID
```
GET /api/products/{id}
```

#### Crear producto
```
POST /api/products
Content-Type: application/json

{
  "name": "Proteina Whey",
  "category": "Proteinas",
  "subcategory": "Whey",
  "price": 1299.00,
  "discount": 15,
  "image": "https://example.com/image.jpg",
  "description": "Proteina de alta calidad",
  "ingredients": "Whey protein isolate",
  "tags": ["proteina", "musculo"],
  "inStock": true,
  "sku": "WP-001",
  "brand": "GNC"
}
```

#### Actualizar producto
```
PUT /api/products/{id}
Content-Type: application/json

{
  "name": "Proteina Whey Gold",
  "price": 1399.00
}
```

#### Eliminar producto
```
DELETE /api/products/{id}
```

---

### Maps

#### Listar todos los mapas
```
GET /api/maps
```

#### Obtener mapa por ID
```
GET /api/maps/{id}
```

#### Crear mapa
```
POST /api/maps
Content-Type: application/json

{
  "name": "Planta Baja",
  "description": "Mapa del primer piso",
  "mapData": "{...}",
  "imageUrl": "https://example.com/map.png",
  "isActive": false
}
```

#### Actualizar mapa
```
PUT /api/maps/{id}
Content-Type: application/json

{
  "name": "Planta Baja - Actualizado"
}
```

#### Activar mapa
```
POST /api/maps/{id}/activate
```

#### Eliminar mapa
```
DELETE /api/maps/{id}
```

---

### Tour Stops

#### Listar todas las paradas
```
GET /api/tour-stops
```

#### Obtener parada por ID
```
GET /api/tour-stops/{id}
```

#### Crear parada
```
POST /api/tour-stops
Content-Type: application/json

{
  "name": "Recepción",
  "description": "Área de bienvenida",
  "speech": "Bienvenidos a nuestra tienda...",
  "positionX": 10.5,
  "positionY": 20.3,
  "mapId": "map-001",
  "orderIndex": 1,
  "durationSeconds": 30
}
```

#### Actualizar parada
```
PUT /api/tour-stops/{id}
Content-Type: application/json

{
  "speech": "Nuevo texto de bienvenida..."
}
```

#### Eliminar parada
```
DELETE /api/tour-stops/{id}
```

---

### Config

#### Obtener toda la configuración
```
GET /api/config
```

Respuesta:
```json
{
  "persona": "Tu nombre es Robbie...",
  "storeName": "GNC",
  "objective": "Ayudar a los clientes..."
}
```

#### Obtener valor específico
```
GET /api/config/{key}
```

#### Establecer valor
```
PUT /api/config/{key}
Content-Type: application/json

{
  "value": "nuevo valor"
}
```

#### Eliminar configuración
```
DELETE /api/config/{key}
```

---

## CORS

El servidor incluye headers CORS para permitir peticiones desde cualquier origen:

```
Access-Control-Allow-Origin: *
Access-Control-Allow-Methods: GET, POST, PUT, DELETE, OPTIONS
Access-Control-Allow-Headers: Content-Type, Authorization
```

---

## Ejemplo de uso desde JavaScript

```javascript
const ROBOT_API = 'http://192.168.1.50:8080';

// Obtener productos
const products = await fetch(`${ROBOT_API}/api/products`).then(r => r.json());

// Crear producto
await fetch(`${ROBOT_API}/api/products`, {
  method: 'POST',
  headers: { 'Content-Type': 'application/json' },
  body: JSON.stringify({
    name: 'Nuevo Producto',
    category: 'Vitaminas',
    price: 299.00
  })
});

// Actualizar producto
await fetch(`${ROBOT_API}/api/products/prod-123`, {
  method: 'PUT',
  headers: { 'Content-Type': 'application/json' },
  body: JSON.stringify({ price: 349.00 })
});

// Eliminar producto
await fetch(`${ROBOT_API}/api/products/prod-123`, {
  method: 'DELETE'
});
```

---

## Estructura de archivos

```
app/src/main/java/com/robbie/data/
├── local/
│   ├── RobbieDatabase.kt          # Base de datos Room
│   ├── converter/
│   │   └── StringListConverter.kt # Convertidor para listas
│   ├── dao/
│   │   ├── ProductDao.kt
│   │   ├── MapDao.kt
│   │   ├── ConfigDao.kt
│   │   └── TourStopDao.kt
│   └── entity/
│       ├── ProductEntity.kt
│       ├── MapEntity.kt
│       ├── ConfigEntity.kt
│       └── TourStopEntity.kt
├── repository/
│   └── ProductRepository.kt       # Repositorio con conversiones
└── server/
    ├── RobbieApiServer.kt         # Servidor HTTP (NanoHTTPD)
    └── RobbieApiService.kt        # Servicio Android foreground
```
