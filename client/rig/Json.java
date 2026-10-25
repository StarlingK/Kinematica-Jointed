// tiny helpers for reading optional values from rig JSON with defaults
package k.jointed.client.rig;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import k.jointed.core.math.V3;

final class Json {
    private Json() {}

    static double d(JsonObject o, String key, double def) {
        JsonElement e = o.get(key);
        return e == null || e.isJsonNull() ? def : e.getAsDouble();
    }

    static int i(JsonObject o, String key, int def) {
        JsonElement e = o.get(key);
        return e == null || e.isJsonNull() ? def : e.getAsInt();
    }

    static boolean b(JsonObject o, String key, boolean def) {
        JsonElement e = o.get(key);
        return e == null || e.isJsonNull() ? def : e.getAsBoolean();
    }

    static String s(JsonObject o, String key, String def) {
        JsonElement e = o.get(key);
        return e == null || e.isJsonNull() ? def : e.getAsString();
    }

    static V3 v3(JsonObject o, String key, V3 def) {
        JsonElement e = o.get(key);
        if (e == null || !e.isJsonArray()) return def;
        JsonArray a = e.getAsJsonArray();
        if (a.size() != 3) throw new IllegalArgumentException("'" + key + "' must be [x, y, z]");
        return new V3(a.get(0).getAsDouble(), a.get(1).getAsDouble(), a.get(2).getAsDouble());
    }

    // accepts "#RRGGBB" or "#AARRGGBB"
    static int color(JsonObject o, String key, int def) {
        JsonElement e = o.get(key);
        if (e == null || e.isJsonNull()) return def;
        String s = e.getAsString().replace("#", "");
        long v = Long.parseLong(s, 16);
        return s.length() <= 6 ? (int) (0xFF000000L | v) : (int) v;
    }

    static double[] pair(JsonObject o, String key, double[] def) {
        JsonElement e = o.get(key);
        if (e == null || !e.isJsonArray()) return def;
        JsonArray a = e.getAsJsonArray();
        if (a.size() != 2) throw new IllegalArgumentException("'" + key + "' must be [a, b]");
        return new double[]{a.get(0).getAsDouble(), a.get(1).getAsDouble()};
    }
}
