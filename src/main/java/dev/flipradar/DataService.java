package dev.flipradar;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;

import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;

/** Периодически обновляет данные и хранит готовый JSON для дашборда. */
public final class DataService {
    private final HypixelApi api = new HypixelApi();
    private final ScheduledExecutorService scheduler = Executors.newScheduledThreadPool(2, r -> {
        Thread t = new Thread(r, "FlipRadar-data");
        t.setDaemon(true);
        return t;
    });
    private final Map<String, String> names = new HashMap<>();

    volatile String bazaarJson = "{\"updated\":0,\"products\":[]}";
    volatile String auctionsJson = "{\"updated\":0,\"flips\":[]}";
    volatile String lastError = "";

    public void start() {
        scheduler.execute(this::loadNames);
        scheduler.scheduleWithFixedDelay(this::refreshBazaar, 2, 20, TimeUnit.SECONDS);
        scheduler.scheduleWithFixedDelay(this::refreshAuctions, 4, 90, TimeUnit.SECONDS);
    }

    private void loadNames() {
        try {
            JsonArray items = api.items().getAsJsonArray("items");
            for (JsonElement e : items) {
                JsonObject o = e.getAsJsonObject();
                names.put(o.get("id").getAsString(), o.get("name").getAsString());
            }
        } catch (Exception e) {
            lastError = "names: " + e.getMessage();
        }
    }

    private static String pretty(String id) {
        StringBuilder sb = new StringBuilder();
        for (String part : id.split("_")) {
            if (part.isEmpty()) continue;
            if (sb.length() > 0) sb.append(' ');
            sb.append(Character.toUpperCase(part.charAt(0))).append(part.substring(1).toLowerCase());
        }
        return sb.toString();
    }

    private void refreshBazaar() {
        try {
            JsonObject root = api.bazaar();
            JsonObject products = root.getAsJsonObject("products");
            JsonArray arr = new JsonArray();
            for (Map.Entry<String, JsonElement> en : products.entrySet()) {
                JsonObject qs = en.getValue().getAsJsonObject().getAsJsonObject("quick_status");
                double buy = qs.get("buyPrice").getAsDouble();   // мгновенная покупка (мин. sell offer)
                double sell = qs.get("sellPrice").getAsDouble(); // мгновенная продажа (макс. buy order)
                if (buy <= 0 || sell <= 0) continue;
                JsonObject o = new JsonObject();
                o.addProperty("id", en.getKey());
                o.addProperty("name", names.getOrDefault(en.getKey(), pretty(en.getKey())));
                o.addProperty("buy", buy);
                o.addProperty("sell", sell);
                o.addProperty("buyWeek", qs.get("buyMovingWeek").getAsLong());
                o.addProperty("sellWeek", qs.get("sellMovingWeek").getAsLong());
                o.addProperty("buyOrders", qs.get("buyOrders").getAsInt());
                o.addProperty("sellOrders", qs.get("sellOrders").getAsInt());
                arr.add(o);
            }
            JsonObject out = new JsonObject();
            out.addProperty("updated", System.currentTimeMillis());
            out.add("products", arr);
            bazaarJson = out.toString();
        } catch (Exception e) {
            lastError = "bazaar: " + e.getMessage();
        }
    }

    private void refreshAuctions() {
        try {
            JsonArray flips = AuctionFlips.compute(api.allAuctionPages());
            JsonObject out = new JsonObject();
            out.addProperty("updated", System.currentTimeMillis());
            out.add("flips", flips);
            auctionsJson = out.toString();
        } catch (Exception e) {
            lastError = "auctions: " + e.getMessage();
        }
    }

    public String statusJson() {
        JsonObject o = new JsonObject();
        o.addProperty("error", lastError);
        return o.toString();
    }
}
