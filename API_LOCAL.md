# Robbie Local API

API REST embebida en el robot para administración remota desde el panel web LIDDAI.

> **IMPORTANTE:** Este sistema NO usa Firebase/Firestore. Todos los datos se almacenan localmente en el robot usando SQLite (Room) y se sincronizan vía HTTP REST.

## Arquitectura

```
┌─────────────────────┐       HTTP REST        ┌─────────────────────────┐
│    LIDDAI Panel     │ ◄────────────────────► │     Robot (Android)     │
│    (Next.js Web)    │   http://robot:8080    │                         │
│                     │                         │  ┌─────────────────┐   │
│  - Robot Maps       │  GET /api/robot-maps   │  │ /sdcard/robot/  │   │
│  - Persona Config   │  GET/PUT /api/persona  │  │   map/*.jpeg    │   │
│  - Products         │  CRUD /api/products    │  └─────────────────┘   │
│  - Tour Stops       │  CRUD /api/tour-stops  │                         │
│                     │                         │  ┌─────────────────┐   │
│                     │                         │  │  SQLite (Room)  │   │
│                     │                         │  │  - products     │   │
│                     │                         │  │  - config       │   │
│                     │                         │  │  - maps         │   │
│                     │                         │  │  - tour_stops   │   │
│                     │                         │  └─────────────────┘   │
└─────────────────────┘                         └─────────────────────────┘
```

## ¿Cómo funciona?

### Flujo de datos

1. **El panel web se conecta al robot** usando la IP del robot (ej: `http://192.168.1.215:8080`)
2. **El robot tiene un servidor HTTP embebido** (NanoHTTPD) que expone endpoints REST
3. **Los datos se guardan en SQLite** dentro del robot (no en la nube)
4. **Los mapas del sistema OrionStar** se leen directamente del filesystem (`/sdcard/robot/map/`)

### Ventajas de este enfoque
- ✅ **Sin dependencia de internet** - Funciona en redes locales sin conexión a internet
- ✅ **Datos siempre en el robot** - No hay sincronización con servidores externos
- ✅ **Baja latencia** - Comunicación directa robot ↔ panel
- ✅ **Sin costos de Firebase** - No hay límites de lectura/escritura

## Configuración

El servidor se inicia automáticamente cuando arranca la aplicación del robot.

- **Puerto por defecto:** 8080
- **URL:** `http://<robot-ip>:8080`

Para obtener la IP del robot:
1. Ver en la notificación del servicio
2. Revisar los logs: `adb logcat | grep RobbieApiService`
3. Usar `adb shell ip addr show wlan0`
4. En el panel LIDDAI, ir a Settings y configurar la IP

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

### Config (Configuración general)

#### Obtener toda la configuración
```
GET /api/config
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

### Robot Maps (Mapas del sistema OrionStar)

Los mapas son creados por la app "Herramientas de Mapa" del robot y se almacenan en `/sdcard/robot/map/`.

#### Listar mapas del robot
```
GET /api/robot-maps
```

Respuesta:
```json
{
  "maps": [
    {
      "id": "LIDD-0318092859",
      "name": "LIDD-0318092859",
      "path": "/sdcard/robot/map/LIDD-0318092859",
      "hasImage": true,
      "imageUrl": "/api/robot-maps/LIDD-0318092859/image",
      "hasZip": true,
      "lastModified": 1774538712000
    }
  ],
  "count": 3
}
```

#### Obtener imagen del mapa
```
GET /api/robot-maps/{id}/image
```

Retorna la imagen JPEG del mapa directamente (Content-Type: image/jpeg).

---

### Persona (Personalidad del robot)

La configuración de personalidad se guarda en SQLite y el robot la usa para su comportamiento.

#### Obtener configuración de persona
```
GET /api/persona
```

Respuesta:
```json
{
  "robotName": "Robbie",
  "robotIdentity": "Un asistente amigable que ayuda a los clientes",
  "enterpriseIntro": "Bienvenido a nuestra tienda",
  "greeting": "¡Hola! Soy Robbie, ¿en qué puedo ayudarte?",
  "farewell": "¡Gracias por visitarnos!",
  "idleMessage": "¿Necesitas ayuda?",
  "personality": "friendly",
  "language": "es-MX",
  "voiceId": "es-mx-x-efg-local",
  "speakSpeed": 1.0,
  "conversationStyles": ["natural", "friendly"],
  "autoChatEnabled": true,
  "autoChatInterval": 30
}
```

#### Guardar configuración de persona
```
PUT /api/persona
Content-Type: application/json

{
  "robotName": "Robbie",
  "robotIdentity": "Soy un asistente de GNC...",
  "greeting": "¡Hola! Bienvenido a GNC...",
  ...
}
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
│   ├── RobbieDatabase.java        # Base de datos Room (SQLite)
│   ├── converter/
│   │   └── StringListConverter.java
│   ├── dao/
│   │   ├── ProductDao.java
│   │   ├── MapDao.java
│   │   ├── ConfigDao.java
│   │   └── TourStopDao.java
│   └── entity/
│       ├── ProductEntity.java
│       ├── MapEntity.java
│       ├── ConfigEntity.java
│       └── TourStopEntity.java
├── repository/
│   └── ProductRepository.java
└── server/
    ├── RobbieApiServer.java       # Servidor HTTP (NanoHTTPD)
    └── RobbieApiService.java      # Servicio Android foreground
```

---

## Resumen de Endpoints

| Recurso | Método | Endpoint | Descripción |
|---------|--------|----------|-------------|
| Health | GET | `/api/health` | Estado del servidor |
| Products | GET | `/api/products` | Listar productos |
| Products | GET | `/api/products/{id}` | Obtener producto |
| Products | POST | `/api/products` | Crear producto |
| Products | PUT | `/api/products/{id}` | Actualizar producto |
| Products | DELETE | `/api/products/{id}` | Eliminar producto |
| Robot Maps | GET | `/api/robot-maps` | Listar mapas del sistema |
| Robot Maps | GET | `/api/robot-maps/{id}/image` | Imagen del mapa |
| Persona | GET | `/api/persona` | Obtener personalidad |
| Persona | PUT | `/api/persona` | Guardar personalidad |
| Config | GET | `/api/config` | Toda la configuración |
| Config | GET | `/api/config/{key}` | Valor específico |
| Config | PUT | `/api/config/{key}` | Establecer valor |
| Tour Stops | GET | `/api/tour-stops` | Listar paradas |
| Tour Stops | POST | `/api/tour-stops` | Crear parada |

---

## Flujo completo: Panel Web ↔ Robot

### 1. Mapas (Solo lectura)
```
Panel Web                          Robot
    │                                │
    │  GET /api/robot-maps           │
    │ ──────────────────────────────►│
    │                                │ Lee /sdcard/robot/map/
    │◄────────────────────────────── │
    │  { maps: [...], count: 3 }     │
    │                                │
    │  GET /api/robot-maps/X/image   │
    │ ──────────────────────────────►│
    │                                │ Lee /sdcard/robot/map/X.jpeg
    │◄────────────────────────────── │
    │  [imagen JPEG binaria]         │
```

### 2. Persona (Lectura/Escritura)
```
Panel Web                          Robot
    │                                │
    │  GET /api/persona              │
    │ ──────────────────────────────►│
    │                                │ SELECT * FROM config WHERE key='persona'
    │◄────────────────────────────── │
    │  { robotName: "Robbie", ... }  │
    │                                │
    │  PUT /api/persona              │
    │  { robotName: "Liddai", ... }  │
    │ ──────────────────────────────►│
    │                                │ INSERT/UPDATE config SET value='{...}'
    │◄────────────────────────────── │
    │  { robotName: "Liddai", ... }  │
```

### 3. Productos (CRUD completo)
```
Panel Web                          Robot
    │                                │
    │  GET /api/products             │
    │ ──────────────────────────────►│
    │                                │ SELECT * FROM products
    │◄────────────────────────────── │
    │  [{ id, name, price, ... }]    │
    │                                │
    │  POST /api/products            │
    │  { name: "Proteina", ... }     │
    │ ──────────────────────────────►│
    │                                │ INSERT INTO products VALUES(...)
    │◄────────────────────────────── │
    │  { id: "abc123", ... }         │
    │                                │
    │  PUT /api/products/abc123      │
    │  { price: 1500 }               │
    │ ──────────────────────────────►│
    │                                │ UPDATE products SET price=1500 WHERE id='abc123'
    │◄────────────────────────────── │
    │  { id: "abc123", price: 1500 } │
```
