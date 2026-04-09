package com.robbie.platform.react.modules;

import android.util.Log;

import com.robbie.base.config.RemoteConfigManager;

import org.json.JSONObject;

import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.io.OutputStream;
import java.net.HttpURLConnection;
import java.net.URL;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.util.Map;
import java.util.TreeMap;
import java.util.UUID;

/**
 * Gestor de autenticacion OAuth2 para la plataforma OrionStar.
 *
 * Implementa el flujo client_credentials descrito en la documentacion:
 * 1. Solicita token a ai-open.ainirobot.com/oauth/2.0/token
 * 2. Firma peticiones con HMAC(app_secret + params ordenados)
 * 3. Refresca tokens automaticamente cuando expiran
 */
public class OrionAuthManager {

    private static final String TAG = "OrionAuthManager";

    private static volatile OrionAuthManager sInstance;

    private String accessToken = null;
    private long tokenExpiresAt = 0;
    private final RemoteConfigManager config;

    private OrionAuthManager() {
        this.config = RemoteConfigManager.getInstance();
    }

    public static OrionAuthManager getInstance() {
        if (sInstance == null) {
            synchronized (OrionAuthManager.class) {
                if (sInstance == null) {
                    sInstance = new OrionAuthManager();
                }
            }
        }
        return sInstance;
    }

    /**
     * Obtiene un token valido, solicitando uno nuevo si es necesario.
     */
    public synchronized String getValidToken() throws Exception {
        if (accessToken != null && System.currentTimeMillis() < tokenExpiresAt) {
            return accessToken;
        }
        return requestNewToken();
    }

    /**
     * Solicita un nuevo token OAuth2 al servidor AI Open.
     * POST ai-open.ainirobot.com/oauth/2.0/token
     * Params: grant_type=client_credentials, client_id=..., client_secret=...
     */
    private String requestNewToken() throws Exception {
        String tokenUrl = config.getTokenUrl();
        String appId = config.getAppId();
        String appSecret = config.getAppSecret();
        String clientId = config.getClientId();

        Log.d(TAG, "Solicitando token a: " + tokenUrl);
        Log.d(TAG, "app_id: " + appId + ", client_id: " + clientId);

        URL url = new URL(tokenUrl);
        HttpURLConnection conn = (HttpURLConnection) url.openConnection();
        conn.setRequestMethod("POST");
        conn.setRequestProperty("Content-Type", "application/x-www-form-urlencoded");
        conn.setDoOutput(true);
        conn.setConnectTimeout(10000);
        conn.setReadTimeout(10000);

        String body = "grant_type=client_credentials"
                + "&app_id=" + appId
                + "&app_secret=" + appSecret
                + "&client_id=" + clientId
                + "&client_secret=" + appSecret;

        try (OutputStream os = conn.getOutputStream()) {
            os.write(body.getBytes(StandardCharsets.UTF_8));
        }

        int responseCode = conn.getResponseCode();
        StringBuilder response = new StringBuilder();

        if (responseCode == HttpURLConnection.HTTP_OK) {
            try (BufferedReader reader = new BufferedReader(
                    new InputStreamReader(conn.getInputStream(), StandardCharsets.UTF_8))) {
                String line;
                while ((line = reader.readLine()) != null) {
                    response.append(line);
                }
            }

            JSONObject json = new JSONObject(response.toString());
            accessToken = json.optString("access_token", null);
            int expiresIn = json.optInt("expires_in", 3600);
            tokenExpiresAt = System.currentTimeMillis() + (expiresIn * 1000L) - 60000;

            Log.i(TAG, "Token obtenido, expira en " + expiresIn + "s");
            return accessToken;
        } else {
            try (BufferedReader reader = new BufferedReader(
                    new InputStreamReader(conn.getErrorStream(), StandardCharsets.UTF_8))) {
                String line;
                while ((line = reader.readLine()) != null) {
                    response.append(line);
                }
            }
            Log.e(TAG, "Error obteniendo token: HTTP " + responseCode + " - " + response);
            throw new Exception("Token request failed: HTTP " + responseCode);
        }
    }

    /**
     * Genera la firma (sign) para una peticion a la API de OrionStar.
     * La firma se calcula: MD5(params_ordenados_concatenados + app_secret)
     */
    public String generateSign(Map<String, String> params) {
        try {
            TreeMap<String, String> sorted = new TreeMap<>(params);
            StringBuilder sb = new StringBuilder();
            for (Map.Entry<String, String> entry : sorted.entrySet()) {
                sb.append(entry.getKey()).append("=").append(entry.getValue());
            }
            sb.append(config.getAppSecret());

            MessageDigest md = MessageDigest.getInstance("MD5");
            byte[] digest = md.digest(sb.toString().getBytes(StandardCharsets.UTF_8));

            StringBuilder hex = new StringBuilder();
            for (byte b : digest) {
                hex.append(String.format("%02x", b));
            }
            return hex.toString().toUpperCase();
        } catch (Exception e) {
            Log.e(TAG, "Error generando firma", e);
            return "";
        }
    }

    /**
     * Construye los headers de autenticacion para una peticion a OrionBase.
     */
    public Map<String, String> buildAuthParams() throws Exception {
        String token = getValidToken();
        String timestamp = String.valueOf(System.currentTimeMillis() / 1000);
        String requestId = UUID.randomUUID().toString();

        Map<String, String> params = new TreeMap<>();
        params.put("app_id", config.getAppId());
        params.put("timestamp", timestamp);
        params.put("request_id", requestId);
        params.put("token", token);

        String sign = generateSign(params);
        params.put("sign", sign);

        return params;
    }

    /**
     * Ejecuta una peticion GET autenticada a la API de OrionBase.
     */
    public String authenticatedGet(String endpoint) throws Exception {
        String baseUrl = config.getServerDomain();
        Map<String, String> authParams = buildAuthParams();

        StringBuilder urlBuilder = new StringBuilder(baseUrl + endpoint);
        urlBuilder.append("?");
        for (Map.Entry<String, String> entry : authParams.entrySet()) {
            urlBuilder.append(entry.getKey()).append("=").append(entry.getValue()).append("&");
        }

        String fullUrl = urlBuilder.toString();
        Log.d(TAG, "GET: " + fullUrl);

        URL url = new URL(fullUrl);
        HttpURLConnection conn = (HttpURLConnection) url.openConnection();
        conn.setRequestMethod("GET");
        conn.setRequestProperty("Content-Type", "application/json");
        conn.setConnectTimeout(10000);
        conn.setReadTimeout(10000);

        int responseCode = conn.getResponseCode();
        StringBuilder response = new StringBuilder();

        try (BufferedReader reader = new BufferedReader(
                new InputStreamReader(
                        responseCode >= 400 ? conn.getErrorStream() : conn.getInputStream(),
                        StandardCharsets.UTF_8))) {
            String line;
            while ((line = reader.readLine()) != null) {
                response.append(line);
            }
        }

        Log.d(TAG, "Response HTTP " + responseCode + ": " + response.toString().substring(0, Math.min(200, response.length())));

        if (responseCode >= 400) {
            throw new Exception("HTTP " + responseCode + ": " + response);
        }

        return response.toString();
    }

    /**
     * Ejecuta una peticion POST autenticada a la API de OrionBase.
     */
    public String authenticatedPost(String endpoint, String jsonBody) throws Exception {
        String baseUrl = config.getServerDomain();
        Map<String, String> authParams = buildAuthParams();

        StringBuilder urlBuilder = new StringBuilder(baseUrl + endpoint);
        urlBuilder.append("?");
        for (Map.Entry<String, String> entry : authParams.entrySet()) {
            urlBuilder.append(entry.getKey()).append("=").append(entry.getValue()).append("&");
        }

        String fullUrl = urlBuilder.toString();
        Log.d(TAG, "POST: " + fullUrl);

        URL url = new URL(fullUrl);
        HttpURLConnection conn = (HttpURLConnection) url.openConnection();
        conn.setRequestMethod("POST");
        conn.setRequestProperty("Content-Type", "application/json");
        conn.setDoOutput(true);
        conn.setConnectTimeout(10000);
        conn.setReadTimeout(10000);

        if (jsonBody != null && !jsonBody.isEmpty()) {
            try (OutputStream os = conn.getOutputStream()) {
                os.write(jsonBody.getBytes(StandardCharsets.UTF_8));
            }
        }

        int responseCode = conn.getResponseCode();
        StringBuilder response = new StringBuilder();

        try (BufferedReader reader = new BufferedReader(
                new InputStreamReader(
                        responseCode >= 400 ? conn.getErrorStream() : conn.getInputStream(),
                        StandardCharsets.UTF_8))) {
            String line;
            while ((line = reader.readLine()) != null) {
                response.append(line);
            }
        }

        Log.d(TAG, "Response HTTP " + responseCode + ": " + response.toString().substring(0, Math.min(200, response.length())));

        if (responseCode >= 400) {
            throw new Exception("HTTP " + responseCode + ": " + response);
        }

        return response.toString();
    }

    public boolean hasValidToken() {
        return accessToken != null && System.currentTimeMillis() < tokenExpiresAt;
    }

    public void invalidateToken() {
        accessToken = null;
        tokenExpiresAt = 0;
    }
}
