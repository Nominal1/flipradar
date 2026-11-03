package dev.flipradar;

import com.google.gson.JsonObject;
import com.google.gson.JsonParser;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.Callable;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;

/** Тонкая обёртка над публичным API Hypixel (ключ не нужен для этих эндпоинтов). */
public final class HypixelApi {
    private static final String BASE = "https://api.hypixel.net/v2";

    private final HttpClient http = HttpClient.newBuilder()
            .connectTimeout(Duration.ofSeconds(10))
            .build();

    public JsonObject get(String path) throws Exception {
        Exception last = null;
        for (int attempt = 0; attempt < 2; attempt++) {
            try {
                HttpRequest req = HttpRequest.newBuilder(URI.create(BASE + path))
                        .timeout(Duration.ofSeconds(30))
                        .header("User-Agent", "FlipRadar/1.0 (Fabric client mod)")
                        .GET().build();
                HttpResponse<String> res = http.send(req, HttpResponse.BodyHandlers.ofString());
                if (res.statusCode() != 200) {
                    throw new IllegalStateException("HTTP " + res.statusCode() + " для " + path);
                }
                return JsonParser.parseString(res.body()).getAsJsonObject();
            } catch (Exception e) {
                last = e;
                Thread.sleep(400);
            }
        }
        throw last;
    }

    public JsonObject bazaar() throws Exception {
        return get("/skyblock/bazaar");
    }

    public JsonObject items() throws Exception {
        return get("/resources/skyblock/items");
    }

    /** Скачивает все страницы активных аукционов (4 параллельных запроса). */
    public List<JsonObject> allAuctionPages() throws Exception {
        JsonObject first = get("/skyblock/auctions?page=0");
        int total = first.has("totalPages") ? first.get("totalPages").getAsInt() : 1;
        List<JsonObject> pages = new ArrayList<>();
        pages.add(first);
        if (total <= 1) return pages;

        ExecutorService pool = Executors.newFixedThreadPool(4);
        try {
            List<Future<JsonObject>> futures = new ArrayList<>();
            for (int p = 1; p < total; p++) {
                final int page = p;
                Callable<JsonObject> task = () -> get("/skyblock/auctions?page=" + page);
                futures.add(pool.submit(task));
            }
            for (Future<JsonObject> f : futures) pages.add(f.get());
        } finally {
            pool.shutdownNow();
        }
        return pages;
    }
}
