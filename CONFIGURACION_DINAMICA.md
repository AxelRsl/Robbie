# Configuracion Dinamica de Actions

## Resumen

El sistema ahora permite configurar las Actions del robot dinamicamente desde la API, sin necesidad de recompilar el APK.

## Arquitectura

### 1. API (`robbie-config-api/server.js`)
- Define configuracion por defecto con 4 Actions
- Expone endpoint `GET /robbie/config` que retorna:
  - `persona`: Personalidad del robot
  - `objective`: Objetivo del AppAgent
  - `pageObjective`: Objetivo del PageAgent
  - `validScreens`: Pantallas disponibles
  - `actions`: Array de Actions con parametros
  - `products`: Catalogo de productos

### 2. RobbieConfig.java
- Carga configuracion desde API
- Clase `ActionConfig`: Define una Action (id, nombre, descripcion, parametros, evento)
- Clase `ParameterConfig`: Define un parametro (nombre, tipo, descripcion, requerido)
- Metodo `initializeDefaultActions()`: Crea Actions por defecto si la API falla

### 3. RobbieRetailActivity.java
- Lee Actions desde `RobbieConfig`
- Metodo `registerDynamicAction()`: Registra Actions genericamente
- Metodo `parseParameterType()`: Convierte strings a tipos del SDK
- Envia eventos a React Native con el `eventName` configurado

## Flujo Completo

1. **Robot inicia** → `RobotApp.onCreate()`
2. **Carga config** → `RobbieConfig.loadFromApi(API_URL)`
3. **Parsea Actions** → Convierte JSON a `ActionConfig`
4. **Activity inicia** → `RobbieRetailActivity.onCreate()`
5. **Setup PageAgent** → `setupPageAgent()`
6. **Registra Actions** → Loop sobre `robbieConfig.getActions()`
7. **Usuario habla** → "Robbie, recomiendame proteinas"
8. **LLM identifica** → Action `RECOMMEND_PRODUCTS` con parametro `user_need="proteinas"`
9. **Action ejecuta** → `registerDynamicAction.onExecute()`
10. **Envia evento** → `sendEventToReactNative("recommend_products", "proteinas", "")`
11. **React Native** → Escucha `onRetailAction` y navega

## Formato de Action en API

```json
{
  "actionId": "com.robbie.NOMBRE_ACTION",
  "displayName": "Nombre para el LLM",
  "description": "Descripcion detallada de que hace la Action",
  "eventName": "nombre_evento_react_native",
  "enumValues": ["Opcion1", "Opcion2"],
  "parameters": [
    {
      "name": "nombre_param",
      "type": "STRING|INT|FLOAT|BOOLEAN|ENUM",
      "description": "Que es este parametro",
      "required": true
    }
  ]
}
```

## Agregar Nueva Action

### Opcion 1: Via API (Dinamico)

```bash
curl -X PUT http://localhost:3000/robbie/config \
  -H "Content-Type: application/json" \
  -d '{
    "actions": [
      {
        "actionId": "com.robbie.ADD_TO_CART",
        "displayName": "Agregar al carrito",
        "description": "Agrega un producto al carrito de compras del usuario",
        "eventName": "add_to_cart",
        "parameters": [
          {
            "name": "product_id",
            "type": "STRING",
            "description": "ID del producto a agregar al carrito",
            "required": true
          },
          {
            "name": "quantity",
            "type": "INT",
            "description": "Cantidad a agregar (por defecto 1)",
            "required": false
          }
        ]
      }
    ]
  }'
```

Reinicia la app para cargar la nueva configuracion.

### Opcion 2: Modificar Defaults (Estatico)

Edita `RobbieConfig.java` → `initializeDefaultActions()`:

```java
ActionConfig addToCart = new ActionConfig();
addToCart.actionId = "com.robbie.ADD_TO_CART";
addToCart.displayName = "Agregar al carrito";
addToCart.description = "Agrega un producto al carrito de compras";
addToCart.addParameter("product_id", "STRING", "ID del producto", true);
addToCart.addParameter("quantity", "INT", "Cantidad", false);
addToCart.eventName = "add_to_cart";
actions.add(addToCart);
```

Recompila el APK.

## Implementar en React Native

```javascript
import { NativeEventEmitter, NativeModules } from 'react-native';

const { AgentModule } = NativeModules;

useEffect(() => {
  const eventEmitter = new NativeEventEmitter(AgentModule);
  
  const subscription = eventEmitter.addListener('onRetailAction', (event) => {
    console.log('Action:', event.action);
    console.log('Param1:', event.param1);
    console.log('Param2:', event.param2);
    
    switch(event.action) {
      case 'recommend_products':
        navigation.navigate('Products', { 
          recommend: true,
          need: event.param1,
          restriction: event.param2 
        });
        break;
        
      case 'search_products':
        navigation.navigate('Products', { 
          searchQuery: event.param1 
        });
        break;
        
      case 'show_product_detail':
        navigation.navigate('ProductDetail', { 
          productName: event.param1 
        });
        break;
        
      case 'navigate_to_screen':
        navigation.navigate(event.param1);
        break;
        
      case 'add_to_cart':
        addToCart(event.param1, parseInt(event.param2) || 1);
        break;
    }
  });
  
  return () => subscription.remove();
}, []);
```

## Ventajas

1. **Sin recompilar**: Cambia Actions desde la API
2. **Flexible**: Agrega/elimina Actions segun necesidades
3. **Testeable**: Prueba diferentes configuraciones rapidamente
4. **Escalable**: Facil agregar nuevas funcionalidades
5. **Centralizado**: Una sola fuente de verdad (API)

## Limitaciones

- Solo soporta hasta 2 parametros por Action (param1, param2)
- Todos los parametros se envian como strings a React Native
- React Native debe parsear tipos (ej: parseInt para INT)

## Proximos Pasos

1. Configurar `ROBBIE_CONFIG_API_URL` en `gradle.properties`
2. Iniciar API: `cd robbie-config-api && npm start`
3. Implementar listeners en React Native
4. Recompilar APK
5. Probar diciendo: "Robbie, recomiendame proteinas"
