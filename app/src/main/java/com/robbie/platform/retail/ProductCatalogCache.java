package com.robbie.platform.retail;

import android.content.Context;
import android.util.Log;

import com.robbie.data.local.RobbieDatabase;
import com.robbie.data.local.entity.ProductEntity;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.concurrent.atomic.AtomicBoolean;

public class ProductCatalogCache {
    private static final String TAG = "ProductCatalogCache";

    private static volatile ProductCatalogCache instance;

    private final Context appContext;
    private final AtomicBoolean refreshScheduled = new AtomicBoolean(false);
    private final Object refreshLock = new Object();

    private volatile List<ProductEntity> cachedProducts = Collections.emptyList();
    private volatile long lastRefreshAtMs = 0L;

    private ProductCatalogCache(Context context) {
        this.appContext = context.getApplicationContext();
    }

    public static ProductCatalogCache getInstance(Context context) {
        if (instance == null) {
            synchronized (ProductCatalogCache.class) {
                if (instance == null) {
                    instance = new ProductCatalogCache(context);
                }
            }
        }
        return instance;
    }

    public void preloadAsync() {
        refreshAsync();
    }

    public void refreshAsync() {
        if (!refreshScheduled.compareAndSet(false, true)) {
            return;
        }
        AsyncTaskHelper.execute(() -> {
            try {
                List<ProductEntity> products = refreshNow();
                ProductSemanticMatcher.preload(products);
            } finally {
                refreshScheduled.set(false);
            }
        });
    }

    public List<ProductEntity> getSnapshot() {
        List<ProductEntity> snapshot = cachedProducts;
        if (snapshot == null || snapshot.isEmpty()) {
            return refreshNow();
        }
        return new ArrayList<>(snapshot);
    }

    public List<ProductEntity> refreshNow() {
        synchronized (refreshLock) {
            try {
                List<ProductEntity> products = RobbieDatabase.getInstance(appContext).productDao().getAllProductsSync();
                List<ProductEntity> snapshot = products != null ? new ArrayList<>(products) : new ArrayList<>();
                cachedProducts = Collections.unmodifiableList(snapshot);
                lastRefreshAtMs = System.currentTimeMillis();
                Log.i(TAG, "Catalog cache refreshed: " + snapshot.size() + " products");
                return new ArrayList<>(snapshot);
            } catch (Exception e) {
                Log.w(TAG, "Could not refresh product catalog cache", e);
                List<ProductEntity> snapshot = cachedProducts;
                return snapshot == null ? new ArrayList<>() : new ArrayList<>(snapshot);
            }
        }
    }

    public long getLastRefreshAtMs() {
        return lastRefreshAtMs;
    }

    public String buildAgentCatalogSummary(String header, int maxProducts, boolean includeGuidance) {
        List<ProductEntity> products = getSnapshot();
        if (products.isEmpty()) {
            return "";
        }
        int limit = Math.min(products.size(), Math.max(maxProducts, 1));
        StringBuilder info = new StringBuilder();
        info.append(header).append(" - ").append(products.size()).append(" productos:\n");
        if (includeGuidance) {
            info.append("Usa este catalogo para buscar y recomendar productos. Si el usuario pregunta por omega 3, fish oil, aceite de pescado, vitaminas, creatina, proteina, defensas, energia o articulaciones, debes responder con productos concretos del catalogo y no solo abrir la pantalla de retail.\n");
        }
        for (int i = 0; i < limit; i++) {
            ProductEntity product = products.get(i);
            info.append("- ").append(product.getName());
            if (product.getPrice() > 0) {
                info.append(" ($").append(String.format("%.2f", product.getPrice())).append(")");
            }
            info.append(" [").append(product.getCategory()).append("]");
            if (product.getBrand() != null && !product.getBrand().isEmpty()) {
                info.append(" marca: ").append(product.getBrand());
            }
            info.append("\n");
        }
        if (products.size() > limit) {
            info.append("... y ").append(products.size() - limit).append(" productos mas\n");
        }
        return info.toString();
    }
}
