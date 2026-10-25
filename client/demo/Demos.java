// built-in demos for trying the framework without writing a mod: tentacle, aim and feet
package k.jointed.client.demo;

import k.jointed.Jointed;
import k.jointed.api.JointedApi;
import k.jointed.api.LimbDefinition;
import k.jointed.api.RigDefinition;
import k.jointed.client.render.RadiusProfile;
import k.jointed.client.render.TubeRenderer;
import k.jointed.client.render.WorldChain;
import k.jointed.client.rig.BuiltinTargets;
import k.jointed.core.IKChain;
import k.jointed.core.ProceduralChain;
import k.jointed.core.constraint.Constraints;
import k.jointed.core.math.V3;
import k.jointed.core.solver.IKGoal;
import net.minecraft.client.Minecraft;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.phys.Vec3;

import java.util.List;
import java.util.Map;
import java.util.TreeMap;

public final class Demos {
    private Demos() {}

    public static final List<String> NAMES = List.of("tentacle", "aim", "feet");
    private static final Map<String, Runnable> RUNNING = new TreeMap<>();

    public static boolean isRunning(String name) {
        return RUNNING.containsKey(name);
    }

    public static boolean start(String name) {
        if (!NAMES.contains(name)) return false;
        stop(name);
        switch (name) {
            case "tentacle" -> tentacle();
            case "aim" -> aim();
            case "feet" -> feet();
        }
        return true;
    }

    public static boolean stop(String name) {
        Runnable r = RUNNING.remove(name);
        if (r != null) r.run();
        return r != null;
    }

    private static ResourceLocation id(String path) {
        return ResourceLocation.fromNamespaceAndPath(Jointed.MOD_ID, path);
    }

    // a dynamic tentacle that follows the player and reaches where they look
    private static void tentacle() {
        IKChain chain = IKChain.straight(10, 0.25, V3.Y, Constraints.coneDeg(35));
        ProceduralChain pc = new ProceduralChain(chain);
        var dyn = pc.enableDynamics();
        dyn.stiffness = 0.12;
        dyn.gravity = new V3(0, -3.0, 0);
        pc.settings().iterations(12).tolerance(0.01);

        var renderer = TubeRenderer.builder(ResourceLocation.withDefaultNamespace("textures/block/purple_concrete.png"))
                .sides(10)
                .subdivisions(4)
                .radius(RadiusProfile.tentacle(0.09, 0.02))
                .uvScale(1f, 2f)
                .build();

        WorldChain wc = new WorldChain(pc, renderer).onTick(c -> {
            LocalPlayer p = Minecraft.getInstance().player;
            if (p == null) return;
            Vec3 eye = p.getEyePosition();
            Vec3 look = p.getLookAngle();
            // anchored beside the shoulder so it does not sit in front of the camera
            Vec3 side = new Vec3(-look.z, 0, look.x).normalize();
            Vec3 root = eye.add(side.scale(0.5)).add(0, -0.4, 0);
            Vec3 tip = eye.add(look.scale(5));
            c.chain.root(new V3(root.x, root.y, root.z), k.jointed.core.math.Quat.IDENTITY);
            c.chain.goal(IKGoal.tip(new V3(tip.x, tip.y, tip.z)));
        });
        JointedApi.addWorldChain(wc);
        RUNNING.put("tentacle", wc::remove);
    }

    // the player's right arm points where they look
    private static void aim() {
        LimbDefinition arm = LimbDefinition.builder("right_arm")
                .parts("right_arm")
                .tipOffset(new V3(0, 12, 0))
                .target(BuiltinTargets.lookAt(10, true))
                .weight(0.9)
                .smoothing(0.05)
                .build();
        ResourceLocation rid = id("demo_aim");
        JointedApi.registerRig(rid, e -> e instanceof Player, RigDefinition.of(arm));
        RUNNING.put("aim", () -> JointedApi.unregisterRig(rid));
    }

    // both legs plant on whatever surface is under them
    private static void feet() {
        LimbDefinition left = LimbDefinition.builder("left_leg")
                .parts("left_leg")
                .target(BuiltinTargets.ground(0.6, 0.6, 0.0, 0.15, 0.0))
                .build();
        LimbDefinition right = LimbDefinition.builder("right_leg")
                .parts("right_leg")
                .target(BuiltinTargets.ground(0.6, 0.6, 0.0, 0.15, 0.0))
                .build();
        ResourceLocation rid = id("demo_feet");
        JointedApi.registerRig(rid, e -> e instanceof Player, RigDefinition.of(left, right));
        RUNNING.put("feet", () -> JointedApi.unregisterRig(rid));
    }

    public static void stopAll() {
        for (String n : List.copyOf(RUNNING.keySet())) stop(n);
    }
}
