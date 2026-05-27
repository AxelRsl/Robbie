package com.robbie.platform.retail;

import android.os.SystemClock;
import android.util.Log;

import com.robbie.data.local.entity.ProductEntity;

import java.text.Normalizer;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Fast, tag-aware product search using category/subcategory/name/brand matching
 * with controlled synonym expansion and require-all-tokens scoring.
 *
 * Performance keys vs the old implementation:
 *   1. Normalize once per product at preload time (cached by ID).
 *   2. No per-search fingerprint re-computation.
 *   3. Controlled synonym map instead of explosive alias expansion.
 *   4. Require-all-tokens scoring so multi-word queries return intersections,
 *      not unions that match nearly every product.
 *   5. Hard result limit (default 20).
 */
public final class ProductSemanticMatcher {
    private static final String TAG = "ProductSemanticMatcher";
    private static final int DEFAULT_LIMIT = 20;

    // Cached normalized product data keyed by product ID
    private static final Map<String, NormalizedProduct> CACHE = new ConcurrentHashMap<>();

    private static final Set<String> STOPWORDS = new HashSet<>(Arrays.asList(
        "de", "del", "la", "las", "el", "los", "un", "una", "unos", "unas",
        "y", "o", "a", "al", "por", "para", "con", "en", "que", "me", "mi",
        "tu", "tus", "su", "sus", "lo", "se", "es", "son", "hay", "algo",
        "tiene", "tienes", "quiero", "necesito", "busco", "dame", "das",
        "recomienda", "recomiendame", "muestra", "muestrame", "ver"
    ));

    // Synonym groups: each entry maps a token to the full group of equivalents
    private static final Map<String, List<String>> SYNONYMS = new HashMap<>();
    static {
        addSynonyms("proteina", "proteinas", "protein", "whey", "suero");
        addSynonyms("snack", "snacks", "botana", "botanas", "barra", "barras",
                     "barrita", "barritas", "colacion", "colaciones");
        addSynonyms("omega", "omegas", "omega3", "dha", "epa");
        addSynonyms("vitamina", "vitaminas");
        addSynonyms("creatina", "creatine");
        addSynonyms("energia", "energy", "energetico");
        addSynonyms("colageno", "collagen", "colagena");
        addSynonyms("articulaciones", "movilidad", "cartilago", "huesos");
        addSynonyms("inmunidad", "defensas", "inmune");
        addSynonyms("adelgazar", "bajar peso", "perdida peso", "dieta", "lean");
        addSynonyms("musculo", "muscular", "masa muscular", "fuerza");
        addSynonyms("probiotico", "probioticos", "digestion", "digestivo");
        addSynonyms("hierro", "iron");
        addSynonyms("calcio", "calcium");
        addSynonyms("magnesio", "magnesium");
        addSynonyms("zinc", "cinc");
        addSynonyms("melatonina", "dormir", "sueno", "sleep");
        addSynonyms("biotina", "biotin", "cabello", "pelo", "unas");
    }

    private static void addSynonyms(String... words) {
        List<String> group = Arrays.asList(words);
        for (String word : words) {
            SYNONYMS.put(word, group);
        }
    }

    private ProductSemanticMatcher() {}

    // ==================== Public API ====================

    /**
     * Search products by query. Returns up to {@code limit} best matches
     * (default 20 if limit <= 0). Uses require-all-tokens scoring so
     * multi-word queries return intersections, not broad unions.
     */
    public static List<ProductEntity> search(List<ProductEntity> products, String query, int limit) {
        if (products == null || products.isEmpty()) return new ArrayList<>();

        int effectiveLimit = limit > 0 ? limit : DEFAULT_LIMIT;
        List<String> tokens = tokenize(query);
        if (tokens.isEmpty()) return new ArrayList<>();

        List<ScoredProduct> scored = new ArrayList<>();
        for (ProductEntity product : products) {
            NormalizedProduct np = getOrBuild(product);
            int score = computeScore(np, tokens);
            if (score > 0) {
                scored.add(new ScoredProduct(product, score));
            }
        }

        scored.sort(Comparator
            .comparingInt(ScoredProduct::getScore).reversed()
            .thenComparing(sp -> sp.product.getInStock() ? 0 : 1)
            .thenComparing(sp -> sp.product.getName()));

        List<ProductEntity> results = new ArrayList<>();
        int max = Math.min(effectiveLimit, scored.size());
        for (int i = 0; i < max; i++) {
            results.add(scored.get(i).product);
        }
        return results;
    }

    /**
     * Pre-build the normalized index for all products so searches are fast.
     * Called from ProductCatalogCache after loading products from DB.
     */
    public static void preload(List<ProductEntity> products) {
        if (products == null || products.isEmpty()) return;
        long start = SystemClock.elapsedRealtime();
        for (ProductEntity p : products) {
            getOrBuild(p);
        }
        Log.i(TAG, "Preloaded index for " + products.size() + " products in " +
              (SystemClock.elapsedRealtime() - start) + "ms");
    }

    /**
     * Return derived tags for a product (kept for backward compatibility).
     */
    public static List<String> deriveTags(ProductEntity product) {
        if (product == null) return new ArrayList<>();
        NormalizedProduct np = getOrBuild(product);
        List<String> tags = new ArrayList<>();
        if (!np.category.isEmpty()) tags.add(np.category);
        if (!np.subcategory.isEmpty()) tags.add(np.subcategory);
        if (!np.brand.isEmpty()) tags.add(np.brand);
        tags.addAll(np.entityTags);
        return tags;
    }

    // ==================== Scoring ====================

    /**
     * Scores a product against query tokens using a require-all-tokens strategy.
     * Products matching ALL tokens rank much higher than partial matches.
     */
    private static int computeScore(NormalizedProduct np, List<String> tokens) {
        int matchedCount = 0;
        int fieldScore = 0;

        for (String token : tokens) {
            int best = matchTokenScore(np, token);
            if (best > 0) {
                matchedCount++;
                fieldScore += best;
            }
        }

        if (matchedCount == 0) return 0;

        int score = fieldScore;

        // Big bonus when ALL query tokens match (intersection behavior)
        if (matchedCount == tokens.size() && tokens.size() > 1) {
            score += 100;
        }

        // Penalize partial matches proportionally
        if (matchedCount < tokens.size()) {
            score = score * matchedCount / tokens.size();
        }

        if (np.product.getInStock()) score += 5;

        return score;
    }

    /**
     * Tries to match a single token against all product fields.
     * First tries direct match, then tries each synonym.
     * Returns the best field score, or 0 if no match.
     */
    private static int matchTokenScore(NormalizedProduct np, String token) {
        int direct = matchDirect(np, token);
        if (direct > 0) return direct;

        List<String> syns = SYNONYMS.get(token);
        if (syns != null) {
            int best = 0;
            for (String syn : syns) {
                if (syn.equals(token)) continue;
                int s = matchDirect(np, syn);
                if (s > best) best = s;
            }
            return best;
        }
        return 0;
    }

    /**
     * Checks if a token directly matches any product field.
     * Field weights: subcategory(50) > category(40) > name(35) > tags(20) > brand(15)
     */
    private static int matchDirect(NormalizedProduct np, String token) {
        int score = 0;
        if (np.subcategory.contains(token)) score += 50;
        if (np.category.contains(token))    score += 40;
        if (np.name.contains(token))        score += 35;
        if (np.brand.contains(token))       score += 15;
        for (String tag : np.entityTags) {
            if (tag.contains(token)) { score += 20; break; }
        }
        return score;
    }

    // ==================== Indexing ====================

    private static NormalizedProduct getOrBuild(ProductEntity product) {
        String id = product.getId();
        if (id == null || id.isEmpty()) {
            return buildNormalized(product);
        }
        NormalizedProduct cached = CACHE.get(id);
        if (cached != null) return cached;

        NormalizedProduct np = buildNormalized(product);
        CACHE.put(id, np);
        return np;
    }

    private static NormalizedProduct buildNormalized(ProductEntity product) {
        List<String> normTags = new ArrayList<>();
        if (product.getTags() != null) {
            for (String t : product.getTags()) {
                String n = normalize(t);
                if (!n.isEmpty()) normTags.add(n);
            }
        }
        return new NormalizedProduct(
            product,
            normalize(product.getName()),
            normalize(product.getCategory()),
            normalize(product.getSubcategory()),
            normalize(product.getBrand()),
            normTags
        );
    }

    // ==================== Text utilities ====================

    private static List<String> tokenize(String query) {
        String normalized = normalize(query);
        if (normalized.isEmpty()) return new ArrayList<>();
        String[] parts = normalized.split("\\s+");
        List<String> tokens = new ArrayList<>();
        for (String part : parts) {
            if (part.length() >= 2 && !STOPWORDS.contains(part)) {
                tokens.add(part);
            }
        }
        return tokens;
    }

    private static String normalize(String value) {
        if (value == null || value.isEmpty()) return "";
        return Normalizer.normalize(value, Normalizer.Form.NFD)
            .replaceAll("\\p{M}+", "")
            .toLowerCase(Locale.ROOT)
            .replaceAll("[^a-z0-9\\s]", " ")
            .replaceAll("\\s+", " ")
            .trim();
    }

    // ==================== Inner classes ====================

    private static final class NormalizedProduct {
        final ProductEntity product;
        final String name;
        final String category;
        final String subcategory;
        final String brand;
        final List<String> entityTags;

        NormalizedProduct(ProductEntity product, String name, String category,
                         String subcategory, String brand, List<String> entityTags) {
            this.product = product;
            this.name = name;
            this.category = category;
            this.subcategory = subcategory;
            this.brand = brand;
            this.entityTags = entityTags;
        }
    }

    private static final class ScoredProduct {
        final ProductEntity product;
        final int score;

        ScoredProduct(ProductEntity product, int score) {
            this.product = product;
            this.score = score;
        }

        int getScore() { return score; }
    }
}
