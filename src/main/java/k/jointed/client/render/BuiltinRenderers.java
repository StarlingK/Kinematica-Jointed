// json renderer types: tube and ribbon
package k.jointed.client.render;

import com.google.gson.JsonObject;
import k.jointed.Jointed;
import k.jointed.api.JointedApi;
import net.minecraft.resources.ResourceLocation;

public final class BuiltinRenderers {
    private BuiltinRenderers() {}

    public static void registerAll() {
        JointedApi.registerRendererType(id("tube"), o -> {
            double[] r = radii(o, "radius", new double[]{0.05, 0.05});
            return TubeRenderer.builder(texture(o))
                    .sides(intOf(o, "sides", 8))
                    .subdivisions(intOf(o, "subdivisions", 4))
                    .radius(RadiusProfile.taper(r[0], r[1]))
                    .colors(color(o, "color_start"), color(o, "color_end"))
                    .caps(o.has("caps") ? o.get("caps").getAsBoolean() : true)
                    .fullbright(o.has("fullbright") && o.get("fullbright").getAsBoolean())
                    .build();
        });
        JointedApi.registerRendererType(id("ribbon"), o -> {
            double[] w = radii(o, "width", new double[]{0.1, 0.02});
            return new RibbonRenderer(texture(o), RadiusProfile.taper(w[0], w[1]), intOf(o, "subdivisions", 4),
                    color(o, "color_start"), color(o, "color_end"), 1f,
                    o.has("fullbright") && o.get("fullbright").getAsBoolean(),
                    net.minecraft.client.renderer.RenderType::entityCutoutNoCull);
        });
    }

    private static ResourceLocation id(String path) {
        return ResourceLocation.fromNamespaceAndPath(Jointed.MOD_ID, path);
    }

    private static ResourceLocation texture(JsonObject o) {
        if (!o.has("texture")) throw new IllegalArgumentException("renderer needs a 'texture'");
        return ResourceLocation.parse(o.get("texture").getAsString());
    }

    private static int intOf(JsonObject o, String key, int def) {
        return o.has(key) ? o.get(key).getAsInt() : def;
    }

    // "#RRGGBB" or "#AARRGGBB", white when missing
    private static int color(JsonObject o, String key) {
        if (!o.has(key)) return 0xFFFFFFFF;
        String s = o.get(key).getAsString().replace("#", "");
        long v = Long.parseLong(s, 16);
        return s.length() <= 6 ? (int) (0xFF000000L | v) : (int) v;
    }

    // a single number or [root, tip]
    private static double[] radii(JsonObject o, String key, double[] def) {
        if (!o.has(key)) return def;
        var e = o.get(key);
        if (e.isJsonArray()) return new double[]{e.getAsJsonArray().get(0).getAsDouble(), e.getAsJsonArray().get(1).getAsDouble()};
        double v = e.getAsDouble();
        return new double[]{v, v};
    }
}
