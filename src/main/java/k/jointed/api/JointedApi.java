// public client side facade register rigs target providers constraint types and first person arms
package k.jointed.api;

import com.google.gson.JsonObject;
import k.jointed.client.render.DebugState;
import k.jointed.client.render.NodeRenderer;
import k.jointed.client.render.WorldChain;
import k.jointed.client.render.WorldChains;
import k.jointed.client.rig.FirstPersonRig;
import k.jointed.client.rig.RigManager;
import k.jointed.core.constraint.JointConstraint;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.HumanoidArm;
import net.minecraft.world.entity.LivingEntity;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.Function;
import java.util.function.Predicate;

// entry point for other mods everything here is client-only call it from client setup code like FMLClientSetupEvent or a Mod dist = CLIENT constructor never from common code

public final class JointedApi {
    private JointedApi() {}

    private static final Map<ResourceLocation, Function<JsonObject, TargetProvider>> TARGET_FACTORIES = new ConcurrentHashMap<>();
    private static final Map<ResourceLocation, Function<JsonObject, JointConstraint>> CONSTRAINT_FACTORIES = new ConcurrentHashMap<>();
    private static final Map<ResourceLocation, Function<JsonObject, NodeRenderer>> RENDERER_FACTORIES = new ConcurrentHashMap<>();

    // rigs

    // registers or replaces a rig for every entity accepted by filter
    public static void registerRig(ResourceLocation id, Predicate<LivingEntity> filter, RigDefinition rig) {
        RigManager.register(new RigManager.Registration(id, filter, rig, false));
    }

    public static void registerRig(ResourceLocation id, EntityType<?> type, RigDefinition rig) {
        registerRig(id, e -> e.getType() == type, rig);
    }

    // removes a rig registered with registerRig returns true if it existed
    public static boolean unregisterRig(ResourceLocation id) {
        return RigManager.unregister(id);
    }

    // runtime controls enable disable per limb targets and weights for one entity
    public static IKController controller(LivingEntity entity) {
        return RigManager.controller(entity);
    }

    // JSON extension 

    // makes id usable as target type id in rig JSON files
    public static void registerTargetProvider(ResourceLocation id, Function<JsonObject, TargetProvider> factory) {
        TARGET_FACTORIES.put(id, factory);
    }

    public static Function<JsonObject, TargetProvider> targetProviderFactory(ResourceLocation id) {
        return TARGET_FACTORIES.get(id);
    }

    // makes id usable as a constraint type in rig JSON files
    public static void registerConstraintType(ResourceLocation id, Function<JsonObject, JointConstraint> factory) {
        CONSTRAINT_FACTORIES.put(id, factory);
    }

    public static Function<JsonObject, JointConstraint> constraintFactory(ResourceLocation id) {
        return CONSTRAINT_FACTORIES.get(id);
    }

    // makes id usable as a limb renderer type in rig JSON files
    public static void registerRendererType(ResourceLocation id, Function<JsonObject, NodeRenderer> factory) {
        RENDERER_FACTORIES.put(id, factory);
    }

    public static Function<JsonObject, NodeRenderer> rendererFactory(ResourceLocation id) {
        return RENDERER_FACTORIES.get(id);
    }

    // world chains and debug

    // a chain that lives in the world instead of on a model, ticked and drawn until removed
    public static WorldChain addWorldChain(WorldChain chain) {
        WorldChains.add(chain);
        return chain;
    }

    public static void removeWorldChain(WorldChain chain) {
        WorldChains.remove(chain);
    }

    public static void setDebugDrawing(boolean on) {
        DebugState.enabled = on;
    }

    // first person

    // aims the first person arm model with ik target should return VIEW MODEL or WORLD targets the arm is a single bone of reachPixels 12 for vanilla arms only the arm model moves held items are unaffected
    public static void registerFirstPersonArm(HumanoidArm arm, TargetProvider target, double reachPixels, double weight) {
        FirstPersonRig.register(arm, new FirstPersonRig.Arm(target, reachPixels, weight));
    }

    public static void unregisterFirstPersonArm(HumanoidArm arm) {
        FirstPersonRig.unregister(arm);
    }
}
