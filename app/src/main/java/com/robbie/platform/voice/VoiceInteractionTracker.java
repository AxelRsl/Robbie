package com.robbie.platform.voice;

import android.util.Log;

import com.robbie.data.server.VoiceReportHandler;

/**
 * Gestor global (Singleton) para atrapar, calcular duracion y clasificar 
 * TODAS las interacciones de voz del robot independientemente de la vista
 * o PageAgent en el que se encuentre.
 */
public class VoiceInteractionTracker {
    private static final String TAG = "VoiceInteractionTracker";
    private static VoiceInteractionTracker instance;

    private String lastUserQuestion = "";
    private long lastUserQuestionTime = 0;

    private VoiceInteractionTracker() {}

    public static synchronized VoiceInteractionTracker getInstance() {
        if (instance == null) {
            instance = new VoiceInteractionTracker();
        }
        return instance;
    }

    /**
     * Se debe llamar desde onASRResult() SOLO cuando transcription.getFinal() sea true.
     * @param question Texto reconocido del usuario.
     * @param isFinal true si el ASR ya finalizó la captura.
     */
    public void startInteraction(String question, boolean isFinal) {
        if (!isFinal) return;
        if (question == null || question.trim().isEmpty()) return;
        
        Log.i(TAG, "Iniciando tracker para pregunta final: " + question);
        lastUserQuestion = question;
        lastUserQuestionTime = System.currentTimeMillis();
    }

    /**
     * Se debe llamar desde onTTSResult() SOLO cuando transcription.getFinal() sea true.
     * @param contextName El contexto o modo en el que está el robot ("Robbie Retail", "Robbie Menu", etc.)
     * @param answer La respuesta completa que el bot dictó.
     * @param isFinal true si el TTS ya finalizó la respuesta.
     * @param activeTargetId ID del usuario rastreado por RobbieFaceTrackManager, o -1 si ninguno.
     */
    public void finishInteraction(String contextName, String answer, boolean isFinal, int activeTargetId) {
        if (!isFinal) return;
        if (lastUserQuestion.isEmpty()) return;

        try {
            long endedAtMs = System.currentTimeMillis();
            long startedAtMs = lastUserQuestionTime > 0 ? lastUserQuestionTime : endedAtMs;
            long durationSecs = (endedAtMs - startedAtMs) / 1000;
            if (durationSecs < 1) durationSecs = 1;

            boolean resolved = true;
            if (answer != null) {
                String lowerAnswer = answer.toLowerCase();
                if (lowerAnswer.contains("error") || lowerAnswer.contains("no te entend") 
                    || lowerAnswer.contains("hubo un problema") || answer.trim().isEmpty()) {
                    resolved = false;
                }
            } else {
                resolved = false;
            }

            String userId = (activeTargetId != -1) ? "User_" + activeTargetId : "Visitante";

            Log.i(TAG, "Guardando interaccion finalizada de " + durationSecs + "s para " + userId);
            
            VoiceReportHandler.logInteraction(
                contextName, lastUserQuestion, answer != null ? answer : "", 
                durationSecs, resolved, userId, startedAtMs, endedAtMs
            );

        } finally {
            lastUserQuestion = "";
            lastUserQuestionTime = 0;
        }
    }
}
