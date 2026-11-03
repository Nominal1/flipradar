package dev.flipradar;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;

import java.util.*;

/**
 * Поиск BIN-флипов: самый дешёвый лот в группе дешевле второго по цене.
 * Группировка по (редкость + название без реформа). Это ПРИБЛИЖЕНИЕ: камни, зачарования,
 * hot potato books и т.п. не учитываются, поэтому каждую сделку нужно проверять глазами.
 */
public final class AuctionFlips {
    // Однозначные реформы. Спорные (Superior, Strong, Wise, Heavy, Light, Perfect...) намеренно
    // не включены: они часть названия предметов (напр. Superior Dragon Armor).
    private static final Set<String> REFORGES = new HashSet<>(List.of(
            "Gentle", "Odd", "Fast", "Fair", "Epic", "Sharp", "Heroic", "Spicy", "Legendary",
            "Dirty", "Fabled", "Suspicious", "Gilded", "Warped", "Withered", "Bulky", "Jaded",
            "Loving", "Necrotic", "Ancient", "Spiked", "Renowned", "Cubic", "Reinforced",
            "Giant", "Hurtful", "Zealous", "Unreal", "Godly", "Demonic", "Smart", "Clean",
            "Titanic", "Mythic", "Pure", "Salty", "Treacherous", "Stained", "Menacing", "Hefty",
            "Soft", "Honored", "Blended", "Astute", "Colossal", "Brilliant", "Magnetic",
            "Fruitful", "Refined", "Submerged", "Moil", "Toil", "Blessed", "Bountiful", "Lush",
            "Fleet", "Stellar", "Heated", "Ambered", "Auspicious", "Fortified", "Bizarre",
            "Itchy", "Ominous", "Pleasant", "Pretty", "Shiny", "Simple", "Strange", "Vivid",
            "Hasty", "Keen", "Rugged", "Unpleasant", "Deadly", "Sturdy", "Grand", "Forceful",
            "Fine", "Neat", "Rapid", "Awkward", "Rich", "Precise", "Spiritual", "Headstrong",
            "Candied", "Waxed", "Rooted", "Mossy", "Dreamy", "Fierce", "Great"));

    /** Настраиваемые константы налогов аукциона. Сверь с актуальной вики Hypixel SkyBlock. */
    static long fees(long sellPrice) {
        double listing = sellPrice < 10_000_000L ? 0.01 : sellPrice < 100_000_000L ? 0.02 : 0.025;
        long claim = sellPrice >= 1_000_000L ? Math.min(Math.round(sellPrice * 0.01), 1_000_000L) : 0L;
        return Math.round(sellPrice * listing) + claim;
    }

    static String normalize(String name) {
        String n = name.trim();
        int sp = n.indexOf(' ');
        if (sp > 0 && REFORGES.contains(n.substring(0, sp))) {
            n = n.substring(sp + 1);
        }
        return n;
    }

    private record Listing(String uuid, String name, String tier, long price) {}

    public static JsonArray compute(List<JsonObject> pages) {
        Map<String, List<Listing>> groups = new HashMap<>();
        for (JsonObject page : pages) {
            JsonArray arr = page.getAsJsonArray("auctions");
            if (arr == null) continue;
            for (JsonElement el : arr) {
                JsonObject a = el.getAsJsonObject();
                if (!a.has("bin") || !a.get("bin").getAsBoolean()) continue;
                String name = a.get("item_name").getAsString();
                if (name.contains("Enchanted Book")) continue; // чары лежат в lore — не различаем
                String tier = a.has("tier") ? a.get("tier").getAsString() : "?";
                long price = a.get("starting_bid").getAsLong();
                String key = tier + "|" + normalize(name);
                groups.computeIfAbsent(key, k -> new ArrayList<>())
                        .add(new Listing(a.get("uuid").getAsString(), name, tier, price));
            }
        }

        List<JsonObject> out = new ArrayList<>();
        for (List<Listing> list : groups.values()) {
            if (list.size() < 2) continue;
            list.sort(Comparator.comparingLong(Listing::price));
            Listing low = list.get(0);
            long second = list.get(1).price();
            long profit = second - low.price() - fees(second);
            double margin = low.price() > 0 ? profit * 100.0 / low.price() : 0;
            if (profit < 50_000 || margin < 3 || margin > 400) continue;

            JsonObject o = new JsonObject();
            o.addProperty("name", low.name());
            o.addProperty("tier", low.tier());
            o.addProperty("uuid", low.uuid());
            o.addProperty("low", low.price());
            o.addProperty("second", second);
            o.addProperty("count", list.size());
            o.addProperty("profit", profit);
            o.addProperty("margin", Math.round(margin * 10) / 10.0);
            out.add(o);
        }
        out.sort((x, y) -> Long.compare(y.get("profit").getAsLong(), x.get("profit").getAsLong()));
        JsonArray res = new JsonArray();
        for (int i = 0; i < Math.min(out.size(), 300); i++) res.add(out.get(i));
        return res;
    }
}
