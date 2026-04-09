package com.robbie.base.config;

import android.content.Context;
import android.content.SharedPreferences;
import android.text.TextUtils;
import android.util.Log;

/**
 * Gestor centralizado de configuracion remota para xiabao_lidd.
 *
 * Reemplaza todas las URLs, dominios y credenciales hardcodeadas del proyecto xiabao original
 * por valores configurables almacenados en SharedPreferences. Cada parametro tiene un valor
 * por defecto identico al original de OrionStar, pero puede ser modificado en tiempo de
 * ejecucion a traves de RemoteConfigActivity.
 *
 * Categorias de configuracion:
 *   - Dominios API OrionBase (por region: Global, US, JP, China prod/test/dev)
 *   - Dominios BI/Telemetria (por region)
 *   - Dominios AI Open (autenticacion OAuth2)
 *   - Credenciales de aplicacion (app_id, app_secret, client_id)
 *   - Portal AgentPOI
 *   - Portal de prestamos (jiedai)
 *   - Baidu Maps API Key
 *   - Zona/region activa
 */
public class RemoteConfigManager {

    private static final String TAG = "RemoteConfigManager";
    private static final String PREFS_NAME = "xiabao_lidd_remote_config";

    private static volatile RemoteConfigManager sInstance;
    private final SharedPreferences mPrefs;

    // -------------------------------------------------------------------------
    // Claves de SharedPreferences
    // -------------------------------------------------------------------------

    // --- Region activa ---
    public static final String KEY_ACTIVE_REGION = "active_region";
    public static final String KEY_ENABLE_DOMAIN_GLOBAL = "enable_domain_global";

    // --- Dominios API OrionBase ---
    public static final String KEY_SERVER_DOMAIN_GLOBAL = "server_domain_global";
    public static final String KEY_SERVER_DOMAIN_US = "server_domain_us";
    public static final String KEY_SERVER_DOMAIN_JP = "server_domain_jp";
    public static final String KEY_SERVER_DOMAIN_CN_PROD = "server_domain_cn_prod";
    public static final String KEY_SERVER_DOMAIN_CN_TEST = "server_domain_cn_test";
    public static final String KEY_SERVER_DOMAIN_CN_DEV = "server_domain_cn_dev";

    // --- Dominios BI / Telemetria ---
    public static final String KEY_BI_DOMAIN_GLOBAL = "bi_domain_global";
    public static final String KEY_BI_DOMAIN_US = "bi_domain_us";
    public static final String KEY_BI_DOMAIN_JP = "bi_domain_jp";
    public static final String KEY_BI_DOMAIN_CN_PROD = "bi_domain_cn_prod";
    public static final String KEY_BI_DOMAIN_CN_TEST = "bi_domain_cn_test";
    public static final String KEY_BI_DOMAIN_CN_DEV = "bi_domain_cn_dev";

    // --- Dominios AI Open (autenticacion) ---
    public static final String KEY_AI_OPEN_PROD = "ai_open_prod";
    public static final String KEY_AI_OPEN_TEST = "ai_open_test";

    // --- Credenciales de aplicacion ---
    public static final String KEY_APP_ID = "app_id";
    public static final String KEY_APP_SECRET = "app_secret";
    public static final String KEY_CLIENT_ID = "client_id";

    // --- Portal AgentPOI ---
    public static final String KEY_AGENTPOI_PROD = "agentpoi_prod";
    public static final String KEY_AGENTPOI_TEST = "agentpoi_test";

    // --- Portal de prestamos ---
    public static final String KEY_JIEDAI_URL = "jiedai_url";

    // --- Baidu Maps ---
    public static final String KEY_BAIDU_API_KEY = "baidu_api_key";

    // --- Endpoints REST (paths relativos) ---
    public static final String KEY_ENDPOINT_APPS_PAGE = "endpoint_apps_page";
    public static final String KEY_ENDPOINT_APPS_VERSION = "endpoint_apps_version";
    public static final String KEY_ENDPOINT_GET_CORE_JS = "endpoint_get_core_js";
    public static final String KEY_ENDPOINT_CRASH_UPLOAD = "endpoint_crash_upload";
    public static final String KEY_ENDPOINT_ANR_UPLOAD = "endpoint_anr_upload";
    public static final String KEY_ENDPOINT_BI_POST = "endpoint_bi_post";
    public static final String KEY_ENDPOINT_TOKEN = "endpoint_token";
    public static final String KEY_ENDPOINT_COS_SECRET = "endpoint_cos_secret";

    // --- MQTT ---
    public static final String KEY_MQTT_BROKER_URL = "mqtt_broker_url";
    public static final String KEY_MQTT_TOPIC_ROOT = "mqtt_topic_root";

    // -------------------------------------------------------------------------
    // Valores por defecto (identicos al xiabao original)
    // -------------------------------------------------------------------------

    // Region
    public static final String DEFAULT_ACTIVE_REGION = "global";
    public static final boolean DEFAULT_ENABLE_DOMAIN_GLOBAL = true;

    // OrionBase API
    public static final String DEFAULT_SERVER_DOMAIN_GLOBAL = "https://global-api-orionbase.orionstar.com";
    public static final String DEFAULT_SERVER_DOMAIN_US = "https://us-api-orionbase.orionstar.com";
    public static final String DEFAULT_SERVER_DOMAIN_JP = "https://jp-api-orionbase.orionstar.com";
    public static final String DEFAULT_SERVER_DOMAIN_CN_PROD = "https://api-orionbase.ainirobot.com";
    public static final String DEFAULT_SERVER_DOMAIN_CN_TEST = "http://test-api-orionbase.ainirobot.com";
    public static final String DEFAULT_SERVER_DOMAIN_CN_DEV = "http://dev-api-orionbase.ainirobot.com";

    // BI / Telemetria
    public static final String DEFAULT_BI_DOMAIN_GLOBAL = "https://global-recv-bi.orionstar.com";
    public static final String DEFAULT_BI_DOMAIN_US = "https://us-recv-bi.orionstar.com";
    public static final String DEFAULT_BI_DOMAIN_JP = "https://jp-recv-bi.orionstar.com";
    public static final String DEFAULT_BI_DOMAIN_CN_PROD = "https://recv-bi.ainirobot.com";
    public static final String DEFAULT_BI_DOMAIN_CN_TEST = "http://recv-bi.ainirobot.com";
    public static final String DEFAULT_BI_DOMAIN_CN_DEV = "https://dev-recv-bi.ainirobot.com";

    // AI Open
    public static final String DEFAULT_AI_OPEN_PROD = "https://ai-open.ainirobot.com";
    public static final String DEFAULT_AI_OPEN_TEST = "http://ai-open-test.ainirobot.com";

    // Credenciales
    public static final String DEFAULT_APP_ID = "orion.appid.1581420888108";
    public static final String DEFAULT_APP_SECRET = "824416C04CC211EAB2499D538CA28633";
    public static final String DEFAULT_CLIENT_ID = "orion.ovs.client.1514259512471";

    // AgentPOI
    public static final String DEFAULT_AGENTPOI_PROD = "https://agentpoi.orionstar.com";
    public static final String DEFAULT_AGENTPOI_TEST = "https://test-agentpoi.orionstar.com";

    // Jiedai
    public static final String DEFAULT_JIEDAI_URL = "http://jiedai.ainirobot.com";

    // Baidu
    public static final String DEFAULT_BAIDU_API_KEY = "SivSVn56Gy0wcNMfD7t5bIyAarV5pqN3";

    // Endpoints REST
    public static final String DEFAULT_ENDPOINT_APPS_PAGE = "/api/v1/apps/page";
    public static final String DEFAULT_ENDPOINT_APPS_VERSION = "/api/v1/apps/version";
    public static final String DEFAULT_ENDPOINT_GET_CORE_JS = "/api/v1/open/other/getCoreJS";
    public static final String DEFAULT_ENDPOINT_CRASH_UPLOAD = "/ob/file/crash/upload";
    public static final String DEFAULT_ENDPOINT_ANR_UPLOAD = "/ob/file/anr/upload";
    public static final String DEFAULT_ENDPOINT_BI_POST = "/post/json/last/zip";
    public static final String DEFAULT_ENDPOINT_TOKEN = "/oauth/2.0/token";
    public static final String DEFAULT_ENDPOINT_COS_SECRET = "/bigdata/api/v1/cos/anr/file/secret";

    // MQTT
    public static final String DEFAULT_MQTT_BROKER_URL = "";
    public static final String DEFAULT_MQTT_TOPIC_ROOT = "/call/server";

    // -------------------------------------------------------------------------
    // Regiones disponibles
    // -------------------------------------------------------------------------

    public static final String REGION_GLOBAL = "global";
    public static final String REGION_US = "us";
    public static final String REGION_JP = "jp";
    public static final String REGION_CN_PROD = "cn_prod";
    public static final String REGION_CN_TEST = "cn_test";
    public static final String REGION_CN_DEV = "cn_dev";

    // -------------------------------------------------------------------------
    // Singleton
    // -------------------------------------------------------------------------

    private RemoteConfigManager(Context context) {
        mPrefs = context.getApplicationContext()
                .getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE);
    }

    public static RemoteConfigManager getInstance(Context context) {
        if (sInstance == null) {
            synchronized (RemoteConfigManager.class) {
                if (sInstance == null) {
                    sInstance = new RemoteConfigManager(context);
                }
            }
        }
        return sInstance;
    }

    public static RemoteConfigManager getInstance() {
        if (sInstance == null) {
            throw new IllegalStateException(
                    "RemoteConfigManager no ha sido inicializado. Llamar getInstance(Context) primero.");
        }
        return sInstance;
    }

    // -------------------------------------------------------------------------
    // Getters genericos
    // -------------------------------------------------------------------------

    public String getString(String key, String defaultValue) {
        return mPrefs.getString(key, defaultValue);
    }

    public boolean getBoolean(String key, boolean defaultValue) {
        return mPrefs.getBoolean(key, defaultValue);
    }

    public void putString(String key, String value) {
        mPrefs.edit().putString(key, value).apply();
    }

    public void putBoolean(String key, boolean value) {
        mPrefs.edit().putBoolean(key, value).apply();
    }

    // -------------------------------------------------------------------------
    // Region activa
    // -------------------------------------------------------------------------

    public String getActiveRegion() {
        return getString(KEY_ACTIVE_REGION, DEFAULT_ACTIVE_REGION);
    }

    public void setActiveRegion(String region) {
        putString(KEY_ACTIVE_REGION, region);
        Log.d(TAG, "Region activa cambiada a: " + region);
    }

    public boolean isGlobalDomainEnabled() {
        return getBoolean(KEY_ENABLE_DOMAIN_GLOBAL, DEFAULT_ENABLE_DOMAIN_GLOBAL);
    }

    public void setGlobalDomainEnabled(boolean enabled) {
        putBoolean(KEY_ENABLE_DOMAIN_GLOBAL, enabled);
    }

    // -------------------------------------------------------------------------
    // Dominio API OrionBase segun region activa
    // -------------------------------------------------------------------------

    public String getServerDomain() {
        String region = getActiveRegion();
        switch (region) {
            case REGION_US:
                return getString(KEY_SERVER_DOMAIN_US, DEFAULT_SERVER_DOMAIN_US);
            case REGION_JP:
                return getString(KEY_SERVER_DOMAIN_JP, DEFAULT_SERVER_DOMAIN_JP);
            case REGION_CN_PROD:
                return getString(KEY_SERVER_DOMAIN_CN_PROD, DEFAULT_SERVER_DOMAIN_CN_PROD);
            case REGION_CN_TEST:
                return getString(KEY_SERVER_DOMAIN_CN_TEST, DEFAULT_SERVER_DOMAIN_CN_TEST);
            case REGION_CN_DEV:
                return getString(KEY_SERVER_DOMAIN_CN_DEV, DEFAULT_SERVER_DOMAIN_CN_DEV);
            case REGION_GLOBAL:
            default:
                return getString(KEY_SERVER_DOMAIN_GLOBAL, DEFAULT_SERVER_DOMAIN_GLOBAL);
        }
    }

    public String getServerDomainForRegion(String region) {
        switch (region) {
            case REGION_US:
                return getString(KEY_SERVER_DOMAIN_US, DEFAULT_SERVER_DOMAIN_US);
            case REGION_JP:
                return getString(KEY_SERVER_DOMAIN_JP, DEFAULT_SERVER_DOMAIN_JP);
            case REGION_CN_PROD:
                return getString(KEY_SERVER_DOMAIN_CN_PROD, DEFAULT_SERVER_DOMAIN_CN_PROD);
            case REGION_CN_TEST:
                return getString(KEY_SERVER_DOMAIN_CN_TEST, DEFAULT_SERVER_DOMAIN_CN_TEST);
            case REGION_CN_DEV:
                return getString(KEY_SERVER_DOMAIN_CN_DEV, DEFAULT_SERVER_DOMAIN_CN_DEV);
            case REGION_GLOBAL:
            default:
                return getString(KEY_SERVER_DOMAIN_GLOBAL, DEFAULT_SERVER_DOMAIN_GLOBAL);
        }
    }

    // -------------------------------------------------------------------------
    // Dominio BI segun region activa
    // -------------------------------------------------------------------------

    public String getBiDomain() {
        String region = getActiveRegion();
        switch (region) {
            case REGION_US:
                return getString(KEY_BI_DOMAIN_US, DEFAULT_BI_DOMAIN_US);
            case REGION_JP:
                return getString(KEY_BI_DOMAIN_JP, DEFAULT_BI_DOMAIN_JP);
            case REGION_CN_PROD:
                return getString(KEY_BI_DOMAIN_CN_PROD, DEFAULT_BI_DOMAIN_CN_PROD);
            case REGION_CN_TEST:
                return getString(KEY_BI_DOMAIN_CN_TEST, DEFAULT_BI_DOMAIN_CN_TEST);
            case REGION_CN_DEV:
                return getString(KEY_BI_DOMAIN_CN_DEV, DEFAULT_BI_DOMAIN_CN_DEV);
            case REGION_GLOBAL:
            default:
                return getString(KEY_BI_DOMAIN_GLOBAL, DEFAULT_BI_DOMAIN_GLOBAL);
        }
    }

    // -------------------------------------------------------------------------
    // URLs completas construidas (dominio + endpoint)
    // -------------------------------------------------------------------------

    public String getAppsPageUrl() {
        return getServerDomain() + getString(KEY_ENDPOINT_APPS_PAGE, DEFAULT_ENDPOINT_APPS_PAGE);
    }

    public String getAppsVersionUrl() {
        return getServerDomain() + getString(KEY_ENDPOINT_APPS_VERSION, DEFAULT_ENDPOINT_APPS_VERSION);
    }

    public String getCoreJsUrl() {
        return getServerDomain() + getString(KEY_ENDPOINT_GET_CORE_JS, DEFAULT_ENDPOINT_GET_CORE_JS);
    }

    public String getCrashUploadUrl() {
        return getBiDomain() + getString(KEY_ENDPOINT_CRASH_UPLOAD, DEFAULT_ENDPOINT_CRASH_UPLOAD);
    }

    public String getAnrUploadUrl() {
        return getBiDomain() + getString(KEY_ENDPOINT_ANR_UPLOAD, DEFAULT_ENDPOINT_ANR_UPLOAD);
    }

    public String getBiPostUrl() {
        return getBiDomain() + getString(KEY_ENDPOINT_BI_POST, DEFAULT_ENDPOINT_BI_POST);
    }

    // -------------------------------------------------------------------------
    // AI Open (autenticacion OAuth2)
    // -------------------------------------------------------------------------

    public String getAiOpenUrl() {
        String region = getActiveRegion();
        if (REGION_CN_TEST.equals(region) || REGION_CN_DEV.equals(region)) {
            return getString(KEY_AI_OPEN_TEST, DEFAULT_AI_OPEN_TEST);
        }
        return getString(KEY_AI_OPEN_PROD, DEFAULT_AI_OPEN_PROD);
    }

    public String getTokenUrl() {
        return getAiOpenUrl() + getString(KEY_ENDPOINT_TOKEN, DEFAULT_ENDPOINT_TOKEN);
    }

    public String getCosSecretUrl() {
        return getAiOpenUrl() + getString(KEY_ENDPOINT_COS_SECRET, DEFAULT_ENDPOINT_COS_SECRET);
    }

    // -------------------------------------------------------------------------
    // Credenciales
    // -------------------------------------------------------------------------

    public String getAppId() {
        return getString(KEY_APP_ID, DEFAULT_APP_ID);
    }

    public String getAppSecret() {
        return getString(KEY_APP_SECRET, DEFAULT_APP_SECRET);
    }

    public String getClientId() {
        return getString(KEY_CLIENT_ID, DEFAULT_CLIENT_ID);
    }

    // -------------------------------------------------------------------------
    // Portal AgentPOI
    // -------------------------------------------------------------------------

    public String getAgentPoiUrl() {
        String region = getActiveRegion();
        if (REGION_CN_TEST.equals(region) || REGION_CN_DEV.equals(region)) {
            return getString(KEY_AGENTPOI_TEST, DEFAULT_AGENTPOI_TEST);
        }
        return getString(KEY_AGENTPOI_PROD, DEFAULT_AGENTPOI_PROD);
    }

    // -------------------------------------------------------------------------
    // Portal Jiedai
    // -------------------------------------------------------------------------

    public String getJiedaiUrl() {
        return getString(KEY_JIEDAI_URL, DEFAULT_JIEDAI_URL);
    }

    // -------------------------------------------------------------------------
    // Baidu Maps
    // -------------------------------------------------------------------------

    public String getBaiduApiKey() {
        return getString(KEY_BAIDU_API_KEY, DEFAULT_BAIDU_API_KEY);
    }

    // -------------------------------------------------------------------------
    // MQTT
    // -------------------------------------------------------------------------

    public String getMqttBrokerUrl() {
        return getString(KEY_MQTT_BROKER_URL, DEFAULT_MQTT_BROKER_URL);
    }

    public String getMqttTopicRoot() {
        return getString(KEY_MQTT_TOPIC_ROOT, DEFAULT_MQTT_TOPIC_ROOT);
    }

    // -------------------------------------------------------------------------
    // Utilidades
    // -------------------------------------------------------------------------

    /**
     * Restaura todos los valores a los defaults originales de OrionStar.
     */
    public void resetToDefaults() {
        mPrefs.edit().clear().apply();
        Log.d(TAG, "Configuracion remota restaurada a valores por defecto");
    }

    /**
     * Verifica si se ha modificado al menos un valor respecto a los defaults.
     */
    public boolean hasCustomConfig() {
        return mPrefs.getAll().size() > 0;
    }

    /**
     * Imprime en log toda la configuracion activa (para debug).
     */
    public void logCurrentConfig() {
        Log.d(TAG, "=== Configuracion Remota Activa ===");
        Log.d(TAG, "Region: " + getActiveRegion());
        Log.d(TAG, "Server Domain: " + getServerDomain());
        Log.d(TAG, "BI Domain: " + getBiDomain());
        Log.d(TAG, "AI Open URL: " + getAiOpenUrl());
        Log.d(TAG, "App ID: " + getAppId());
        Log.d(TAG, "Client ID: " + getClientId());
        Log.d(TAG, "AgentPOI URL: " + getAgentPoiUrl());
        Log.d(TAG, "Apps Page URL: " + getAppsPageUrl());
        Log.d(TAG, "Apps Version URL: " + getAppsVersionUrl());
        Log.d(TAG, "Crash Upload URL: " + getCrashUploadUrl());
        Log.d(TAG, "ANR Upload URL: " + getAnrUploadUrl());
        Log.d(TAG, "Token URL: " + getTokenUrl());
        Log.d(TAG, "MQTT Broker: " + getMqttBrokerUrl());
        Log.d(TAG, "Baidu API Key: " + getBaiduApiKey());
        Log.d(TAG, "=================================");
    }
}
