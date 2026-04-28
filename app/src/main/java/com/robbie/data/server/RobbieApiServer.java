package com.robbie.data.server;

import android.content.Context;
import android.util.Log;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.robbie.data.local.RobbieDatabase;

import com.robbie.data.local.entity.ProductEntity;
import com.robbie.RobotApp;
import com.ainirobot.agent.AgentCore;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import fi.iki.elonen.NanoHTTPD;

/**
 * Main HTTP server for Robbie's REST API.
 * Routes requests to domain-specific handlers.
 */
public class RobbieApiServer extends NanoHTTPD {

    private static final String TAG = "RobbieApiServer";
    public static final int DEFAULT_PORT = 8080;

    private final Context context;
    private RobbieDatabase database;
    private final Gson gson;

    // Handlers
    private ProductHandler productHandler;
    private MapHandler mapHandler;
    private RobotMapHandler robotMapHandler;
    private TourHandler tourHandler;
    private ConfigHandler configHandler;
    private AnimationHandler animationHandler;
    private ModuleDataHandler moduleDataHandler;
    private GuideRouteHandler guideRouteHandler;
    private KnowledgeQAHandler knowledgeQAHandler;
    private KnowledgeLexiconHandler lexiconHandler;
    private KnowledgeDocumentHandler documentHandler;
    private VoiceReportHandler voiceReportHandler;

    public RobbieApiServer(Context context) {
        this(context, DEFAULT_PORT);
    }

    public RobbieApiServer(Context context, int port) {
        super(port);
        this.context = context;
        this.gson = new GsonBuilder().setPrettyPrinting().create();
    }

    private RobbieDatabase getDatabase() {
        if (database == null) {
            database = RobbieDatabase.getInstance(context);
        }
        return database;
    }

    private void initHandlers() {
        RobbieDatabase db = getDatabase();
        if (productHandler == null) {
            productHandler = new ProductHandler(db, gson);
            productHandler.setOnProductsChangedListener(() -> {
                // Cuando los productos cambian via la API, actualizar contexto del agente
                try {
                    List<ProductEntity> products = db.productDao().getAllProductsSync();
                    if (products != null && !products.isEmpty()) {
                        StringBuilder info = new StringBuilder();
                        info.append("CATALOGO DE PRODUCTOS ACTUALIZADO - ").append(products.size()).append(" productos:\n");
                        for (ProductEntity p : products) {
                            info.append("- ").append(p.getName());
                            if (p.getPrice() > 0)
                                info.append(" ($").append(String.format("%.0f", p.getPrice())).append(")");
                            info.append(" [").append(p.getCategory()).append("]");
                            if (p.getBrand() != null && !p.getBrand().isEmpty())
                                info.append(" marca: ").append(p.getBrand());
                            info.append("\n");
                        }
                        AgentCore.INSTANCE.uploadInterfaceInfo(info.toString());
                        Log.i(TAG, "Agent context updated with " + products.size() + " products from DB");
                    }
                } catch (Exception e) {
                    Log.w(TAG, "Could not update agent context after product change", e);
                }
            });
            mapHandler = new MapHandler(db, gson);
            robotMapHandler = new RobotMapHandler(db, gson);
            tourHandler = new TourHandler(db, gson);
            configHandler = new ConfigHandler(db, gson);
            animationHandler = new AnimationHandler(db, gson);
            moduleDataHandler = new ModuleDataHandler(db, gson);
            guideRouteHandler = new GuideRouteHandler(db, gson);
            knowledgeQAHandler = new KnowledgeQAHandler(db, gson);
            lexiconHandler = new KnowledgeLexiconHandler(db, gson);
            documentHandler = new KnowledgeDocumentHandler(db, gson);
            voiceReportHandler = new VoiceReportHandler(db, gson);
            configHandler.setOnPersonaChangedListener(persona -> {
                // Cuando la persona cambia via el panel, actualizar AgentOS en tiempo real
                try {
                    RobotApp app = RobotApp.getInstance();
                    if (app == null) return;

                    String robotName = persona.get("robotName") != null ? persona.get("robotName").toString() : "";
                    String robotIdentity = persona.get("robotIdentity") != null ? persona.get("robotIdentity").toString() : "";
                    String enterpriseIntro = persona.get("enterpriseIntro") != null ? persona.get("enterpriseIntro").toString() : "";
                    String additionalInfo = persona.get("additionalInfo") != null ? persona.get("additionalInfo").toString() : "";

                    // Construir persona para el AgentOS
                    StringBuilder personaStr = new StringBuilder();
                    personaStr.append("Tu nombre es ").append(robotName).append(". ");
                    if (!robotIdentity.isEmpty()) personaStr.append(robotIdentity).append(" ");
                    if (!enterpriseIntro.isEmpty()) personaStr.append("Sobre la empresa: ").append(enterpriseIntro).append(" ");
                    if (!additionalInfo.isEmpty()) personaStr.append(additionalInfo);

                    // Construir objetivo
                    String objective = "Ayudar a los clientes de forma amigable y profesional.";
                    if (!robotIdentity.isEmpty()) {
                        objective = robotIdentity;
                    }

                    app.updateAgentPersona(personaStr.toString().trim(), objective, robotName);
                    Log.i(TAG, "Agent persona updated from panel: " + robotName);
                } catch (Exception e) {
                    Log.w(TAG, "Could not update agent persona after panel change", e);
                }
            });
        }
    }

    @Override
    public Response serve(IHTTPSession session) {
        String uri = session.getUri();
        Method method = session.getMethod();

        Log.d(TAG, "Request: " + method + " " + uri);

        Response response;
        try {
            initHandlers();
            response = routeRequest(method, uri, session);
        } catch (Exception e) {
            Log.e(TAG, "Error processing request", e);
            Map<String, Object> err = new HashMap<>();
            err.put("error", e.getMessage() != null ? e.getMessage() : "Unknown error");
            response = newFixedLengthResponse(Response.Status.INTERNAL_ERROR,
                "application/json", gson.toJson(err));
        }

        response.addHeader("Access-Control-Allow-Origin", "*");
        response.addHeader("Access-Control-Allow-Methods", "GET, POST, PUT, DELETE, OPTIONS");
        response.addHeader("Access-Control-Allow-Headers", "Content-Type, Authorization");

        return response;
    }

    private Response routeRequest(Method method, String uri, IHTTPSession session) {
        if (method == Method.OPTIONS) {
            return newFixedLengthResponse(Response.Status.OK, MIME_PLAINTEXT, "");
        }

        String[] parts = uri.split("/");
        List<String> partsList = new ArrayList<>();
        for (String part : parts) {
            if (!part.isEmpty()) partsList.add(part);
        }

        if (partsList.isEmpty() || !partsList.get(0).equals("api")) {
            return notFound("Not found");
        }

        if (partsList.size() < 2) {
            return badRequest("Invalid endpoint");
        }

        String endpoint = partsList.get(1);
        switch (endpoint) {
            case "voice-reports":
                return voiceReportHandler.handle(method, partsList, session);
            case "products":
                return productHandler.handle(method, partsList, session);
            case "maps":
                return mapHandler.handle(method, partsList, session);
            case "robot-maps":
                return robotMapHandler.handle(method, partsList, session);
            case "tour-stops":
                return tourHandler.handleStops(method, partsList, session);
            case "tour-routes":
                return tourHandler.handleRoutes(method, partsList, session);
            case "config":
                return configHandler.handle(method, partsList, session);
            case "persona":
                return configHandler.handlePersona(method, session);
            case "animations":
                return animationHandler.handle(method, partsList, session);
            case "moduledata":
                return moduleDataHandler.handle(method, partsList, session);
            case "guide-routes":
                return guideRouteHandler.handle(method, partsList, session);
            case "knowledge":
                return knowledgeQAHandler.handle(method, partsList, session);
            case "lexicon":
                return lexiconHandler.handle(method, partsList, session);
            case "knowledge-documents":
                return documentHandler.handle(method, partsList, session);
            case "health":
                return handleHealth();
            default:
                return notFound("Unknown endpoint");
        }
    }

    // ─────────────────────────────────────────────────────────────────────────
    // HEALTH (kept here — it's tiny and cross-cutting)
    // ─────────────────────────────────────────────────────────────────────────

    private Response handleHealth() {
        int productCount = getDatabase().productDao().getProductCount();
        Map<String, Object> health = new HashMap<>();
        health.put("status", "ok");
        health.put("server", "RobbieApiServer");
        health.put("version", "1.0.0");
        health.put("productCount", productCount);
        String json = gson.toJson(health);
        return newFixedLengthResponse(Response.Status.OK, "application/json", json);
    }

    // ─── Quick response helpers ──────────────────────────────────────────────

    private Response notFound(String msg) {
        Map<String, Object> err = new HashMap<>();
        err.put("error", msg);
        return newFixedLengthResponse(Response.Status.NOT_FOUND, "application/json", gson.toJson(err));
    }

    private Response badRequest(String msg) {
        Map<String, Object> err = new HashMap<>();
        err.put("error", msg);
        return newFixedLengthResponse(Response.Status.BAD_REQUEST, "application/json", gson.toJson(err));
    }
}
