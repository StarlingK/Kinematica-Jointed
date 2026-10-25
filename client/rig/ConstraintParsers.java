// Parses constraint objects from rig JSON; custom types can be added through JointedApi.
package k.jointed.client.rig;

import com.google.gson.JsonObject;
import k.jointed.Jointed;
import k.jointed.api.JointedApi;
import k.jointed.core.constraint.Constraints;
import k.jointed.core.constraint.JointConstraint;
import k.jointed.core.math.MathUtil;
import k.jointed.core.math.V3;
import net.minecraft.resources.ResourceLocation;

public final class ConstraintParsers {
    private ConstraintParsers() {}

    static void registerBuiltins() {
        reg("none", o -> Constraints.NONE);
        reg("fixed", o -> Constraints.FIXED);
        reg("cone", o -> {
            double[] twist = Json.pair(o, "twist", new double[]{0, 0});
            return Constraints.coneDeg(Json.d(o, "max_swing", 45), twist[0], twist[1]);
        });
        reg("ellipse", o -> {
            double[] sw = Json.pair(o, "swing", new double[]{45, 45});
            double[] twist = Json.pair(o, "twist", new double[]{0, 0});
            return Constraints.ellipse(MathUtil.toRadians(sw[0]), MathUtil.toRadians(sw[0]), MathUtil.toRadians(sw[1]),
                    MathUtil.toRadians(sw[1]), MathUtil.toRadians(twist[0]), MathUtil.toRadians(twist[1]),
                    Json.v3(o, "ref_axis", V3.X));
        });
        reg("hinge", o -> Constraints.hingeDeg(Json.v3(o, "axis", V3.X), Json.d(o, "min", 0), Json.d(o, "max", 140)));
    }

    private static void reg(String name, java.util.function.Function<JsonObject, JointConstraint> f) {
        JointedApi.registerConstraintType(ResourceLocation.fromNamespaceAndPath(Jointed.MOD_ID, name), f);
    }

    // resolves an id string; ids without a namespace belong to Jointed
    static ResourceLocation id(String s) {
        return s.contains(":") ? ResourceLocation.parse(s) : ResourceLocation.fromNamespaceAndPath(Jointed.MOD_ID, s);
    }

    public static JointConstraint parse(JsonObject o) {
        String type = Json.s(o, "type", "none");
        var f = JointedApi.constraintFactory(id(type));
        if (f == null) throw new IllegalArgumentException("unknown constraint type '" + type + "'");
        return f.apply(o);
    }
}
