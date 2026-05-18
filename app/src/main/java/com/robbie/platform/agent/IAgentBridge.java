package com.robbie.platform.agent;

import android.app.Activity;

/**
 * Abstraccion del Agent OS.
 *
 * Define el contrato que cualquier implementacion de agente debe cumplir.
 * Cuando se cambie de Agent OS (ej: de ainirobot a otro), solo se crea
 * una nueva clase que implemente esta interfaz, sin modificar EveActivity
 * ni la logica de acciones del robot.
 *
 * Implementaciones:
 * - RobbieAgentBridge: Agent SDK ainirobot (actual)
 * - [futuro]: nueva implementacion para el Agent OS de reemplazo
 */
public interface IAgentBridge {

    /**
     * Listener para eventos ASR/TTS del agente.
     */
    interface TranscriptionCallback {
        void onASRPartial(String text);
        void onASRFinal(String text);
        void onTTSUpdate(String text, boolean isFinal);
        void onAgentStatusChanged(String status, String message);
        void onListeningGateChanged(boolean gateOpen, boolean personVisible);
    }

    /**
     * Listener para cuando el agente despacha una accion.
     */
    interface ActionDispatchCallback {
        void onActionDispatched(String actionName, android.os.Bundle params);
    }

    /**
     * Inicializa el agente y registra todas las acciones.
     * @param activity Activity donde se ejecuta el agente
     * @param actionCallback callback para acciones despachadas por el LLM
     * @param transcriptionCallback callback para eventos ASR/TTS
     */
    void initialize(Activity activity,
                    ActionDispatchCallback actionCallback,
                    TranscriptionCallback transcriptionCallback);

    /**
     * Envia texto al LLM como si el usuario lo hubiera dicho.
     */
    void query(String text);

    /**
     * Reproduce TTS.
     */
    void tts(String text, int timeoutMs);

    /**
     * Detiene TTS en curso.
     */
    void stopTTS();

    /**
     * Controla estado del microfono.
     */
    void setMicrophoneMuted(boolean muted);

    void setVisionListeningState(boolean gateOpen, boolean personVisible);

    /**
     * Sube informacion de contexto/interfaz al LLM.
     */
    void uploadInterfaceInfo(String info);

    /**
     * Limpia historial de conversacion del LLM.
     */
    void clearContext();

    /**
     * Libera recursos del agente.
     */
    void destroy();

    /**
     * Indica si el agente esta inicializado y listo.
     */
    boolean isReady();
}
