// first-person arm ik: aims the arm model at a target while the hand is drawn in the view
package k.jointed.client.rig;

import com.mojang.blaze3d.vertex.PoseStack;
import k.jointed.api.IKTarget;
import k.jointed.api.TargetProvider;
import k.jointed.client.ClientConfig;
import k.jointed.core.IKChain;
import k.jointed.core.math.MathUtil;
import k.jointed.core.math.Quat;
import k.jointed.core.math.V3;
import k.jointed.core.solver.FabrikSolver;
import net.minecraft.client.Camera;
import net.minecraft.client.Minecraft;
import net.minecraft.client.model.PlayerModel;
import net.minecraft.client.model.geom.ModelPart;
import net.minecraft.client.player.AbstractClientPlayer;
import net.minecraft.world.entity.HumanoidArm;

import java.util.EnumMap;
import java.util.Map;

public final class FirstPersonRig {
    private FirstPersonRig() {}

    public record Arm(TargetProvider target, double reachPixels, double weight) {}

    private static final Map<HumanoidArm, Arm> ARMS = new EnumMap<>(HumanoidArm.class);
    private static ModelPart savedPart;
    private static final float[] saved = new float[3];

    public static void register(HumanoidArm arm, Arm def) {
        ARMS.put(arm, def);
    }

    public static void unregister(HumanoidArm arm) {
        ARMS.remove(arm);
    }

    // called just before the arm model is drawn in the first-person view
    public static void apply(AbstractClientPlayer player, PlayerModel<?> model, ModelPart armPart, PoseStack stack) {
        if (ARMS.isEmpty() || !ClientConfig.ENABLED.get()) return;
        HumanoidArm which = armPart == model.rightArm ? HumanoidArm.RIGHT : HumanoidArm.LEFT;
        Arm def = ARMS.get(which);
        if (def == null) return;
        try {
            Camera cam = Minecraft.getInstance().gameRenderer.getMainCamera();
            ModelSpace space = ModelSpace.forFirstPerson(stack, cam.getPosition(), cam.rotation());
            TargetContext ctx = new TargetContext(player, 1.0f, "first_person_" + which.name().toLowerCase(), space,
                    RigManager.controller(player));
            ctx.rootModel = new V3(armPart.x, armPart.y, armPart.z);
            ctx.animatedTipModel = ctx.rootModel.add(0, def.reachPixels(), 0);

            IKTarget target = def.target().resolve(ctx);
            if (target == null) return;
            V3 pos = ctx.toModel(target.position(), target.space());
            if (pos == null) return;

            IKChain chain = IKChain.builder().root(ctx.rootModel).bone(new V3(0, def.reachPixels(), 0)).build();
            chain.setLocal(0, Quat.fromEulerZYX(armPart.xRot, armPart.yRot, armPart.zRot));
            Quat anim = chain.local(0);
            FabrikSolver.INSTANCE.solve(chain, new k.jointed.core.solver.SolverSettings().tolerance(0.01),
                    k.jointed.core.solver.IKGoal.tip(pos));
            double w = MathUtil.clamp(def.weight() * target.weight() * ClientConfig.GLOBAL_WEIGHT.get(), 0, 1);
            Quat result = Quat.slerp(anim, chain.local(0), w);

            savedPart = armPart;
            saved[0] = armPart.xRot;
            saved[1] = armPart.yRot;
            saved[2] = armPart.zRot;
            double[] e = result.toEulerZYX();
            armPart.xRot = (float) e[0];
            armPart.yRot = (float) e[1];
            armPart.zRot = (float) e[2];
        } catch (Throwable t) {
            k.jointed.Jointed.LOGGER.error("Jointed first-person arm failed", t);
            ARMS.remove(which);
            restore();
        }
    }

    // vanilla resets the sleeve's x rotation to 0; copy the ik pose over it
    public static void syncSleeve(ModelPart arm, ModelPart sleeve) {
        if (savedPart == arm) sleeve.copyFrom(arm);
    }

    public static void restore() {
        if (savedPart == null) return;
        savedPart.xRot = saved[0];
        savedPart.yRot = saved[1];
        savedPart.zRot = saved[2];
        savedPart = null;
    }
}
