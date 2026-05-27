# Alineación OrionStar AgentOS ↔ Robbie App

Este documento mapea las funcionalidades nativas de OrionStar/AgentOS con la implementación actual
de la app Robbie, identifica duplicaciones y conflictos, y define la estrategia de alineación.

---

## 1. Seguimiento Facial (Face Tracking)

### OrionStar nativo
- **VisionSDK** ejecuta detección de personas continuamente usando la cámara frontal.
- **PersonApi** expone los resultados: `getAllFaceList()`, `getCompleteFaceList()`, `getFocusPerson()`.
- **RobotApi.startFocusFollow()** hace que el gimbal siga a una persona específica por face ID.
- El sistema **wake-free** usa internamente la misma pipeline de visión para determinar si alguien
  está frente al robot y activar el micrófono.

### Robbie actual
- `OrionPersonGatewayImpl` → hace polling de `PersonApi` (usa la misma fuente que OrionStar).
- `RobbieFaceTrackManager` → máquina de estados (IDLE→ACQUIRING→TRACKING→REACQUIRING) que
  decide cuándo llamar a `startFocusFollow()`.
- `RobotActionHandler` → traduce diagnósticos del manager a `updateVoiceListeningState()`.
- `RobbieAgentBridge.canProcessVoiceInput()` → gate adicional que filtra ASR results.

### Conflicto/Duplicación
| Aspecto | OrionStar nativo | Robbie duplicado |
|---------|-----------------|-----------------|
| Detección de personas | VisionSDK (siempre activo) | OrionPersonGatewayImpl (polling PersonApi) |
| Seguimiento de cabeza | startFocusFollow via wake-free | RobbieFaceTrackManager.startFocusFollow |
| Gate de voz | Wake-free (visión + acústica) | canProcessVoiceInput (visionGateOpen) |

### Alineación requerida
- **NO duplicar** la detección de personas — PersonApi ya lee de VisionSDK, está bien usarlo.
- **Potencial conflicto**: dos llamadas concurrentes a `startFocusFollow()` (wake-free internamente
  y nuestro RobbieFaceTrackManager). Verificar si wake-free maneja focus follow internamente.
- **Simplificación propuesta**: Confiar más en el estado de wake-free (vía `OnAgentStatusChangedListener`
  status=`listening`) como señal primaria de "persona frente al robot", en lugar de nuestro gate propio.

---

## 2. Selección de Modo de Interacción Inteligente

### OrionStar nativo
- **Wake-free (`setEnableWakeFree`)**: Modo multimodal visión+acústica.
  - Activado: Solo activa micrófono cuando usuario está **frente al robot**.
  - Desactivado: Escucha incondicional.
- **Wake word (`enableWakeupMode`)**: Requiere palabra de activación.
  - `setWakeupVadTimeout()`: Tiempo de escucha después de que el usuario deja de hablar (1-10s, default 3s).
  - `setWakeupQuestionTimeout()`: Ventana de escucha después de que la IA pregunta (3-30s, default 10s).
- **Agent status transitions**: El sistema cambia automáticamente entre:
  - `listening` → escuchando input del usuario
  - `thinking` → analizando intención
  - `processing` → ejecutando acción
  - `reset_status` → vuelta al estado inicial

### Robbie actual
```java
// onActivityStart():
AgentCore.INSTANCE.enableWakeupMode(false);  // Sin wake word
AgentCore.INSTANCE.setEnableWakeFree(true);   // Wake-free multimodal ON
AgentCore.INSTANCE.setMicrophoneMuted(false); // Mic abierto
AgentCore.INSTANCE.setEnableVoiceBar(true);   // Voice bar del sistema ON
```

### Alineación requerida
- **Configuración actual es correcta** para retail (sin wake word, wake-free activo).
- **Falta**: No usamos `setWakeupVadTimeout` ni `setWakeupQuestionTimeout` — podrían mejorar
  la conversación continua.
- **Voice bar**: Está habilitado pero la vista React Native fullscreen probablemente lo tapa.
  Considerar: o usar el voice bar nativo (ajustar z-order) o implementar nuestro propio
  indicador de transcripción en React Native.

---

## 3. Límite de Personas Atendidas Simultáneamente

### OrionStar nativo
- El sistema wake-free automáticamente selecciona **una persona focal** usando fusión
  visión+acústica (la persona que habla frente al robot).
- `PersonApi.getFocusPerson()` retorna la persona seleccionada por el sistema.
- El sistema maneja cambio de target automáticamente.

### Robbie actual
- `RobbieTargetSelector` implementa su propia selección de target con scoring por
  distancia, ángulo, estabilidad, etc.
- `RobbieFaceTrackManager` maneja switch de target con cooldowns propios.

### Conflicto/Duplicación
- **Duplicación directa**: Nuestra selección de target puede contradecir la del sistema.
  El sistema dice "persona A es el foco" pero nuestro selector dice "persona B".
- **Resultado**: El gimbal sigue a nuestra selección (via startFocusFollow) pero el
  wake-free puede estar escuchando a la persona que el sistema seleccionó.

### Alineación requerida
- **Usar `getFocusPerson()`** como señal principal en lugar de nuestra propia selección.
- **O**: Desactivar wake-free y manejar todo nosotros (más complejo, más riesgo).
- **Recomendación**: Alinear `RobbieTargetSelector` para preferir `focusPersonId` del sistema
  cuando esté disponible.

---

## 4. Interrupción Inteligente

### OrionStar nativo
- Cuando el usuario habla durante TTS, el sistema **automáticamente interrumpe** el TTS.
- Trigger: ASR detecta voz → TTS se detiene → se procesa el nuevo input.
- Callback `onTTSResult` con `status=2` indica interrupción.
- Después de TTS, si wake-free está activo, el sistema vuelve a escuchar automáticamente.

### Robbie actual
```java
// onTTSResult - después de que TTS termina:
AgentCore.INSTANCE.setEnableWakeFree(true);  // Re-habilitar wake-free
```

### Alineación requerida
- **Funciona correctamente** — el sistema maneja la interrupción nativa.
- **Sin embargo**: Nuestro `canProcessVoiceInput()` podría bloquear el ASR que viene
  después de la interrupción si el face tracker no ha actualizado el gate a tiempo.
- **Fix aplicado**: SDK ASR trust window (5s) para confiar en ASR del SDK incluso
  si nuestro gate está cerrado.

---

## 5. Voice Bar y Transcripción ASR

### OrionStar nativo
- **Voice Bar**: Barra de subtítulos del sistema que muestra:
  - Texto ASR parcial mientras el usuario habla
  - Estado del agente (listening, thinking, processing)
  - Texto TTS mientras el robot habla
- Controlado por `AgentCore.setEnableVoiceBar(true/false)`
- `onASRResult` return value:
  - `false` → el sistema muestra el texto en el voice bar
  - `true` → el sistema NO muestra el texto (consumido por la app)

### Robbie actual
- `setEnableVoiceBar(true)` está activado.
- `onASRResult` retorna `false` para parciales y `true` para finales.
- **PERO**: La vista React Native es fullscreen y probablemente tapa el voice bar del sistema.
- **BUG**: El evento `onTranscription` se emite desde Java pero **ningún componente React Native
  lo escucha**. La transcripción ASR es invisible para el usuario.

### Alineación requerida
- **Opción A**: Confiar en el voice bar nativo — ajustar z-order para que sea visible
  sobre React Native. Requiere investigar cómo el voice bar se renderiza (¿WindowManager overlay?).
- **Opción B** (recomendada): Implementar indicador de transcripción propio en React Native
  consumiendo el evento `onTranscription` que ya se emite. Esto da control total sobre el UX.
- **Fix inmediato**: Agregar listener de `onTranscription` en App.tsx, agregar estado en
  `useAppStore`, crear componente visual de transcripción.

---

## 6. Conflicto de Cámara: Wake-Free vs PersonApi

### Documentado en FAQ (línea 198)
> "Si la aplicación está llamando a la cámara, puede probar desactivando la función wake-free
> o desactivando la llamada a la cámara. Después de confirmar que es un problema de wake-free,
> se recomienda desactivar temporalmente wake-free para evitar conflictos con la cámara, o
> adoptar el modo de compartir flujo de datos de cámara para evitar la contención de recursos."

### Análisis para Robbie
- `PersonApi.getCompleteFaceList()` **NO abre la cámara directamente** — lee datos del VisionSDK
  que ya tiene la cámara abierta. No debería haber conflicto.
- `RobotApi.startFocusFollow()` usa el sistema de visión internamente — tampoco abre cámara.
- **Potencial conflicto**: Si wake-free y nuestro `startFocusFollow()` compiten por controlar
  el gimbal/head tracking, podrían interferirse mutuamente.

### Alineación requerida
- Verificar en logcat si hay errores de `VisionSDK` o `camera` cuando ambos sistemas están activos.
- Si hay conflicto, considerar dejar el focus follow al wake-free y solo usar PersonApi para
  lectura pasiva de personas visibles.

---

## 7. Resumen de Acciones

| Prioridad | Acción | Estado |
|-----------|--------|--------|
| **ALTA** | Delegar voice bar/ASR display a AgentOS nativo | ✅ Implementado |
| **ALTA** | Delegar face tracking a OrionStar wake-free | ✅ Implementado |
| **ALTA** | Delegar voice gating a wake-free (remover canProcessVoiceInput) | ✅ Implementado |
| **ALTA** | onASRResult retorna false (no consumir, dejar que AgentOS muestre y procese) | ✅ Implementado |
| **ALTA** | Derivar gateOpen/personVisible de AgentOS status en onStatusChanged | ✅ Implementado |
| **ALTA** | Remover VoiceBar custom (usar el nativo de AgentOS) | ✅ Implementado |
| **MEDIA** | Verificar en robot que voice bar nativo es visible sobre React Native | Pendiente |
| **BAJA** | Limpiar código muerto de RobbieFaceTrackManager si no se reutiliza | Pendiente |
