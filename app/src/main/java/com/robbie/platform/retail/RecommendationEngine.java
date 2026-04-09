package com.robbie.platform.retail;

import android.util.Log;

import com.robbie.BuildConfig;

import org.json.JSONArray;
import org.json.JSONObject;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

import okhttp3.MediaType;
import okhttp3.OkHttpClient;
import okhttp3.Request;
import okhttp3.RequestBody;
import okhttp3.Response;

public class RecommendationEngine {

    private static final String TAG = "RecommendationEngine";
    private static final MediaType JSON = MediaType.get("application/json; charset=utf-8");

    private final OkHttpClient client = new OkHttpClient();
    private final ExecutorService executor = Executors.newSingleThreadExecutor();

    public interface RecommendationCallback {
        void onResult(String explanation, List<String> recommendedProductIds);
        void onError(String error);
    }

    public void recommend(List<Product> allProducts,
                          String userNeed,
                          String restriction,
                          RecommendationCallback callback) {

        executor.execute(() -> {
            try {
                StringBuilder catalog = new StringBuilder();
                for (int i = 0; i < allProducts.size(); i++) {
                    Product p = allProducts.get(i);
                    catalog.append("[").append(p.getId()).append("] ")
                           .append(p.toAiSummary()).append("\n");
                }

                String systemPrompt =
                    "Eres un asesor experto de tienda retail. Tu trabajo es recomendar " +
                    "productos del catalogo segun las necesidades del cliente.\n\n" +
                    "REGLAS:\n" +
                    "1. Solo recomienda productos del catalogo proporcionado\n" +
                    "2. Considera restricciones dieteticas (sin lactosa, vegano, sin gluten, etc.)\n" +
                    "3. Maximo 3 productos recomendados\n" +
                    "4. Responde en espanol, de forma breve y amigable\n" +
                    "5. Al final incluye una linea con los IDs: PRODUCT_IDS: [id1, id2, id3]\n\n" +
                    "CATALOGO:\n" + catalog.toString();

                String userMessage = "Necesito: " + userNeed;
                if (restriction != null && !restriction.isEmpty()) {
                    userMessage += ". Restriccion: " + restriction;
                }

                JSONObject body = new JSONObject();
                JSONArray messages = new JSONArray();

                JSONObject sysMsg = new JSONObject();
                sysMsg.put("role", "system");
                sysMsg.put("content", systemPrompt);
                messages.put(sysMsg);

                JSONObject usrMsg = new JSONObject();
                usrMsg.put("role", "user");
                usrMsg.put("content", userMessage);
                messages.put(usrMsg);

                body.put("messages", messages);
                body.put("max_tokens", 500);
                body.put("temperature", 0.7);

                Request request = new Request.Builder()
                        .url(BuildConfig.AI_API_BASE_URL)
                        .addHeader("api-key", BuildConfig.AI_API_KEY)
                        .addHeader("Content-Type", "application/json")
                        .post(RequestBody.create(body.toString(), JSON))
                        .build();

                try (Response response = client.newCall(request).execute()) {
                    if (!response.isSuccessful()) {
                        callback.onError("API error: " + response.code());
                        return;
                    }

                    String responseBody = response.body().string();
                    JSONObject json = new JSONObject(responseBody);
                    String content = json.getJSONArray("choices")
                            .getJSONObject(0)
                            .getJSONObject("message")
                            .getString("content");

                    List<String> ids = parseProductIds(content);
                    String explanation = content.replaceAll("(?i)PRODUCT_IDS:.*", "").trim();

                    Log.d(TAG, "AI recommended " + ids.size() + " products: " + ids);
                    callback.onResult(explanation, ids);
                }

            } catch (Exception e) {
                Log.e(TAG, "Recommendation error", e);
                callback.onError(e.getMessage());
            }
        });
    }

    private List<String> parseProductIds(String content) {
        List<String> ids = new ArrayList<>();
        int idx = content.toUpperCase().indexOf("PRODUCT_IDS:");
        if (idx >= 0) {
            String tail = content.substring(idx + 12).trim();
            tail = tail.replace("[", "").replace("]", "").trim();
            String[] parts = tail.split(",");
            for (String part : parts) {
                String id = part.trim();
                if (!id.isEmpty()) ids.add(id);
            }
        }
        return ids;
    }
}
