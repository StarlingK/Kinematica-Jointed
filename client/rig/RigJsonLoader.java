// reload listener that turns assets/<ns>/jointed/rigs/*.json files into rig registrations
package k.jointed.client.rig;

import com.google.gson.Gson;
import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import k.jointed.Jointed;
import k.jointed.api.*;
import k.jointed.core.constraint.JointConstraint;
import k.jointed.core.math.MathUtil;
import k.jointed.core.math.V3;
import k.jointed.core.solver.SolverSettings;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.packs.resources.ResourceManager;
import net.minecraft.server.packs.resources.SimpleJsonResourceReloadListener;
import net.minecraft.util.profiling.ProfilerFiller;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.LivingEntity;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.function.Predicate;

public final class RigJsonLoader extends SimpleJsonResourceReloadListener {
    private static final Gson GSON = new Gson();

    public RigJsonLoader() {
        super(GSON, "jointed/rigs");
    }

    @Override
    protected void apply(Map<ResourceLocation, JsonElement> files, ResourceManager manager, ProfilerFiller profiler) {
        RigManager.removeJsonRigs();
        int loaded = 0;
        for (Map.Entry<ResourceLocation, JsonElement> e : files.entrySet()) {
            try {
                RigManager.register(parse(e.getKey(), e.getValue().getAsJsonObject()));
                loaded++;
            } catch (Exception ex) {
                Jointed.LOGGER.error("Skipping Jointed rig {}: {}", e.getKey(), ex.getMessage());
            }
        }
        Jointed.LOGGER.info("Loaded {} Jointed rig file(s)", loaded);
    }

    static RigManager.Registration parse(ResourceLocation id, JsonObject root) {
        Predicate<LivingEntity> filter = parseFilter(root.get("entity"));
        JsonArray limbArr = root.getAsJsonArray("limbs");
        if (limbArr == null || limbArr.isEmpty()) throw new IllegalArgumentException("'limbs' is missing or empty");
        List<LimbDefinition> limbs = new ArrayList<>();
        for (JsonElement el : limbArr) limbs.add(parseLimb(el.getAsJsonObject()));
        return new RigManager.Registration(id, filter, new RigDefinition(limbs), true);
    }

    private static Predicate<LivingEntity> parseFilter(JsonElement el) {
        if (el == null || (el.isJsonPrimitive() && el.getAsString().equals("*"))) return e -> true;
        Set<EntityType<?>> types = new HashSet<>();
        List<JsonElement> items = new ArrayList<>();
        if (el.isJsonArray()) el.getAsJsonArray().forEach(items::add);
        else items.add(el);
        for (JsonElement item : items) {
            ResourceLocation rl = ResourceLocation.parse(item.getAsString());
            EntityType<?> type = BuiltInRegistries.ENTITY_TYPE.getOptional(rl)
                    .orElseThrow(() -> new IllegalArgumentException("unknown entity type " + rl));
            types.add(type);
        }
        return e -> types.contains(e.getType());
    }

    private static LimbDefinition parseLimb(JsonObject o) {
        String name = Json.s(o, "name", null);
        if (name == null) throw new IllegalArgumentException("limb without 'name'");
        LimbDefinition.Builder b = LimbDefinition.builder(name);

        JsonArray parts = o.getAsJsonArray("parts");
        if (parts == null || parts.isEmpty()) throw new IllegalArgumentException("limb '" + name + "' has no 'parts'");
        for (JsonElement p : parts) b.parts(p.getAsString());
        b.tipOffset(Json.v3(o, "tip_offset", new V3(0, 12, 0)));
        b.solver(SolverChoice.parse(Json.s(o, "solver", "auto")));
        b.weight(Json.d(o, "weight", 1.0));
        b.smoothing(Json.d(o, "smoothing", 0.0));
        b.fade(Json.d(o, "fade", 0.08));
        b.restFromInitialPose(!Json.s(o, "rest", "initial").equalsIgnoreCase("zero"));

        SolverSettings s = new SolverSettings().tolerance(0.01);
        JsonObject so = o.has("settings") ? o.getAsJsonObject("settings") : new JsonObject();
        s.maxIterations = Json.i(so, "iterations", s.maxIterations);
        s.tolerance = Json.d(so, "tolerance", s.tolerance);
        s.damping = Json.d(so, "damping", s.damping);
        s.restBias = Json.d(so, "rest_bias", s.restBias);
        s.softness = Json.d(so, "softness", s.softness);
        s.maxStepAngle = MathUtil.toRadians(Json.d(so, "max_step_degrees", MathUtil.toDegrees(s.maxStepAngle)));
        b.settings(s);

        if (o.has("constraints")) {
            List<JointConstraint> cs = new ArrayList<>();
            for (JsonElement c : o.getAsJsonArray("constraints")) cs.add(ConstraintParsers.parse(c.getAsJsonObject()));
            b.constraints(cs.toArray(new JointConstraint[0]));
        }

        JsonObject t = o.getAsJsonObject("target");
        if (t == null) throw new IllegalArgumentException("limb '" + name + "' has no 'target'");
        ResourceLocation tid = ConstraintParsers.id(Json.s(t, "type", ""));
        var factory = JointedApi.targetProviderFactory(tid);
        if (factory == null) throw new IllegalArgumentException("unknown target type '" + tid + "'");
        b.target(factory.apply(t));
        return b.build();
    }
}
