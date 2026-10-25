// built-in target providers: look_at, ground (foot planting), fixed and controller
package k.jointed.client.rig;

import com.google.gson.JsonObject;
import k.jointed.Jointed;
import k.jointed.api.IKTarget;
import k.jointed.api.JointedApi;
import k.jointed.api.TargetProvider;
import k.jointed.api.TargetSpace;
import k.jointed.core.math.Quat;
import k.jointed.core.math.V3;
import net.minecraft.resources.ResourceLocation;

import java.util.Locale;
import java.util.Optional;

public final class BuiltinTargets {
    private BuiltinTargets() {}

    public static final ResourceLocation LOOK_AT = id("look_at");
    public static final ResourceLocation GROUND = id("ground");
    public static final ResourceLocation FIXED = id("fixed");
    public static final ResourceLocation CONTROLLER = id("controller");

    private static ResourceLocation id(String path) {
        return ResourceLocation.fromNamespaceAndPath(Jointed.MOD_ID, path);
    }

    public static void registerAll() {
        JointedApi.registerTargetProvider(LOOK_AT, p -> lookAt(Json.d(p, "distance", 16), Json.b(p, "raycast", true)));
        JointedApi.registerTargetProvider(FIXED, p -> fixed(
                TargetSpace.valueOf(Json.s(p, "space", "entity").toUpperCase(Locale.ROOT)),
                Json.v3(p, "position", V3.ZERO)));
        JointedApi.registerTargetProvider(GROUND, p -> ground(
                Json.d(p, "max_up", 0.6), Json.d(p, "max_down", 0.6), Json.d(p, "foot_height", 0.0),
                Json.d(p, "plant_distance", 0.15), Json.d(p, "pole_forward", 0.0)));
        JointedApi.registerTargetProvider(CONTROLLER, p -> controller());
        ConstraintParsers.registerBuiltins();
    }

    // reach towards where the entity looks
    // first block hit if raycast, else distance blocks ahead
    public static TargetProvider lookAt(double distance, boolean raycast) {
        return ctx -> {
            if (!ctx.worldAvailable()) return null;
            V3 eye = ctx.eyePosition();
            V3 end = eye.add(ctx.lookVector().scale(distance));
            V3 t = end;
            if (raycast) {
                Optional<V3> hit = ctx.raycastBlocks(eye, end);
                if (hit.isPresent()) t = hit.get();
            }
            return IKTarget.world(t);
        };
    }

    // a constant point in the given space
    // blocks for WORLD/ENTITY/VIEW, pixels for MODEL

    public static TargetProvider fixed(TargetSpace space, V3 position) {
        IKTarget t = new IKTarget(position, space, null, 1.0);
        return ctx -> t;
    }

    // targets set through k.jointed.api.IKController#setTarget
    public static TargetProvider controller() {
        return ctx -> ctx.controller().target(ctx.limbName).orElse(null);
    }

// foot planting: lifts tip if below surface
// snaps down if within plantDistance above surface
// else keeps animation (does not lower body)
// footHeight sets tip height above surface
// poleForward pushes knee/elbow forward

    public static TargetProvider ground(double maxUp, double maxDown, double footHeight, double plantDistance,
                                        double poleForward) {
        return ctx -> {
            V3 tip = ctx.animatedTipWorld();
            if (tip == null) return null;
            Optional<V3> hit = ctx.raycastBlocks(tip.add(0, maxUp, 0), tip.add(0, -maxDown, 0));
            if (hit.isEmpty()) return null;
            double groundY = hit.get().y + footHeight;
            double dy = tip.y - groundY;
            if (dy > plantDistance) return null;
            IKTarget t = IKTarget.world(new V3(tip.x, groundY, tip.z));
            if (poleForward != 0) {
                V3 root = ctx.limbRootWorld();
                if (root != null) {
                    V3 forward = Quat.fromAxisAngle(V3.Y, -ctx.bodyYaw()).rotate(V3.Z);
                    t = t.withPole(root.add(forward.scale(poleForward)));
                }
            }
            return t;
        };
    }
}
