package com.robbie.platform.voice;

import android.util.Log;

import com.ainirobot.coreservice.client.listener.Person;
import com.ainirobot.coreservice.client.person.PersonApi;
import com.robbie.data.server.VoiceReportHandler;

import java.util.List;

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
     * Se debe llamar desde onASRResult() cuando el usuario termina de hablar.
     * @param question Texto reconocido del usuario.
     */
    public void startInteraction(String question) {
        if (question == null || question.trim().isEmpty()) return;
        
        Log.i(TAG, "Iniciando tracker para pregunta: " + question);
        lastUserQuestion = question;
        lastUserQuestionTime = System.currentTimeMillis();
    }

    /**
     * Se debe llamar desde onTTSResult() cuando el robot finaliza su respuesta.
     * @param contextName El contexto o modo en el que está el robot ("Robbie Retail", "Robbie Menu", etc.)
     * @param answer La respuesta que el bot dictó.
     */
    public void finishInteraction(String contextName, String answer) {
        if (lastUserQuestion.isEmpty()) {
            return; // No hay pregunta pendiente
        }

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

            // Obtener el ID del usuario directamente usando las camaras
            String userId = "Visitante";
            try {
                List<Person> faces = PersonApi.getInstance().getCompleteFaceList();
                if (faces == null || faces.isEmpty()) {
                    faces = PersonApi.getInstance().getAllFaceList(3); // 3 metros max
                }
                
                if (faces != null && !faces.isEmpty()) {
                    Person best = faces.get(0);
                    for (Person p : faces) {
                        if (p.getDistance() > 0 && p.getDistance() < best.getDistance()) {
                            best = p;
                        }
                    }
                    userId = "User_" + best.getId();
                }
            } catch (Exception e) {
                Log.w(TAG, "Error obteniendo ID visual de la persona", e);
            }

            Log.i(TAG, "Guardando interaccion de " + durationSecs + "s del " + userId);
            
            VoiceReportHandler.logInteraction(
                contextName, lastUserQuestion, answer != null ? answer : "", 
                durationSecs, resolved, userId, startedAtMs, endedAtMs
            );

        } finally {
            // Siempre reiniciar el tracker para evitar falsos enganches futuros
            lastUserQuestion = "";
            lastUserQuestionTime = 0;
        }
    }
}
