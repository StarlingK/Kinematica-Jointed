// runtime state of one limb on one entity: binds model parts, solves each frame, blends and restores
package k.jointed.client.rig;

import k.jointed.Jointed;
import k.jointed.api.IKController;
import k.jointed.api.IKTarget;
import k.jointed.api.LimbDefinition;
import k.jointed.api.event.IKSolveEvent;
import k.jointed.client.ClientConfig;
import k.jointed.core.IKChain;
import k.jointed.core.constraint.Constraints;
import k.jointed.core.dynamics.PoseSmoother;
import k.jointed.core.math.MathUtil;
import k.jointed.core.math.Quat;
import k.jointed.core.math.V3;
import k.jointed.core.solver.IKGoal;
import k.jointed.core.solver.SolveResult;
import k.jointed.core.solver.SolverSettings;
import net.minecraft.client.model.EntityModel;
import net.minecraft.client.model.geom.ModelPart;
import net.minecraft.world.entity.LivingEntity;
import net.neoforged.neoforge.common.NeoForge;

import java.util.List;

public final class LimbRuntime {
    public final String key;
    public final LimbDefinition def;

    private ModelPart[] parts;
    private List<ModelPart> ancestors = List.of();
    private IKChain chain;
    private SolverSettings settings;
    private final PoseSmoother smoother = new PoseSmoother();
    private boolean bound;
    private boolean broken;
    private double fade;
    private long lastNanos;
    private V3 lastPos;
    private V3 lastPole;
    private float[][] saved;
    private boolean applied;

    LimbRuntime(String key, LimbDefinition def) {
        this.key = key;
        this.def = def;
    }

    // the solved chain in limb space (model pixels) from the last frame, or null for debug drawing
    public IKChain chain() {
        return chain;
    }

    public boolean isBroken() {
        return broken;
    }

    private void fail(String why, Throwable t) {
        broken = true;
        if (t != null) Jointed.LOGGER.error("Jointed limb '{}' disabled: {}", key, why, t);
        else Jointed.LOGGER.warn("Jointed limb '{}' disabled: {}", key, why);
    }

    private void bind(EntityModel<?> model) {
        int k = def.parts.size();
        ModelPart[] resolved = new ModelPart[k];
        for (int i = 0; i < k; i++) {
            resolved[i] = ModelPartResolver.find(model, def.parts.get(i));
            if (resolved[i] == null) {
                fail("model part '" + def.parts.get(i) + "' not found in " + model.getClass().getSimpleName(), null);
                return;
            }
        }
        for (int i = 0; i + 1 < k; i++) {
            if (!ModelPartResolver.isDirectChild(resolved[i], resolved[i + 1])) {
                fail("part '" + def.parts.get(i + 1) + "' is not a direct child of '" + def.parts.get(i) + "'", null);
                return;
            }
        }
        parts = resolved;
        ancestors = ModelPartResolver.ancestors(model, resolved[0]);

        IKChain.Builder b = IKChain.builder();
        for (int i = 0; i < k; i++) {
            V3 v = i < k - 1 ? new V3(resolved[i + 1].x, resolved[i + 1].y, resolved[i + 1].z) : def.tipOffset;
            var c = def.constraints.isEmpty() ? Constraints.NONE : def.constraints.get(i);
            Quat rest = def.restFromInitialPose ? initialRotation(resolved[i]) : Quat.IDENTITY;
            b.bone(v, c, rest);
        }
        chain = b.build();
        settings = def.settings.copy();
        saved = new float[k][3];
        bound = true;
    }

    /** Rotation baked into the part at model creation, read without disturbing the current pose. */
    private static Quat initialRotation(ModelPart p) {
        float x = p.x, y = p.y, z = p.z, rx = p.xRot, ry = p.yRot, rz = p.zRot;
        p.resetPose();
        Quat q = Quat.fromEulerZYX(p.xRot, p.yRot, p.zRot);
        p.x = x;
        p.y = y;
        p.z = z;
        p.xRot = rx;
        p.yRot = ry;
        p.zRot = rz;
        return q;
    }

    // returns true if this limb changed the model this frame and must be restored later
    boolean apply(LivingEntity entity, EntityModel<?> model, ModelSpace space, float partial, IKController ctl) {
        if (broken) return false;
        try {
            return applyInternal(entity, model, space, partial, ctl);
        } catch (Throwable t) {
            restore();
            fail("exception while solving", t);
            return false;
        }
    }

    private boolean applyInternal(LivingEntity entity, EntityModel<?> model, ModelSpace space, float partial,
                                  IKController ctl) {
        if (!bound) bind(model);
        if (broken) return false;

        long now = System.nanoTime();
        double dt = lastNanos == 0 ? 1.0 / 60.0 : Math.min(0.1, (now - lastNanos) / 1.0e9);
        lastNanos = now;

        int k = parts.length;
        // frame of the limb root's parent expressed in model pixels
        Quat aRot = Quat.IDENTITY;
        V3 aPos = V3.ZERO;
        for (ModelPart a : ancestors) {
            aPos = aPos.add(aRot.rotate(new V3(a.x, a.y, a.z)));
            aRot = aRot.mul(Quat.fromEulerZYX(a.xRot, a.yRot, a.zRot));
        }

        ModelPart p0 = parts[0];
        chain.setRoot(new V3(p0.x, p0.y, p0.z), Quat.IDENTITY);
        Quat[] anim = new Quat[k];
        for (int i = 0; i < k; i++) {
            if (i < k - 1) chain.setBone(i, new V3(parts[i + 1].x, parts[i + 1].y, parts[i + 1].z));
            else chain.setBone(i, def.tipOffset);
            anim[i] = Quat.fromEulerZYX(parts[i].xRot, parts[i].yRot, parts[i].zRot);
            chain.setLocal(i, anim[i]);
        }

        TargetContext ctx = new TargetContext(entity, partial, def.name, space, ctl);
        ctx.rootModel = aPos.add(aRot.rotate(chain.rootPosition()));
        ctx.animatedTipModel = aPos.add(aRot.rotate(chain.tipPosition()));

        IKTarget target = def.target.resolve(ctx);
        IKSolveEvent.Pre pre = new IKSolveEvent.Pre(entity, def.name, target);
        NeoForge.EVENT_BUS.post(pre);
        target = pre.isCanceled() ? null : pre.getTarget();

        V3 posLimb = null;
        V3 poleLimb = null;
        if (target != null) {
            V3 posModel = ctx.toModel(target.position(), target.space());
            if (posModel == null) {
                target = null;
            } else {
                posLimb = aRot.inverse().rotate(posModel.sub(aPos));
                if (target.pole() != null) {
                    V3 poleModel = ctx.toModel(target.pole(), target.space());
                    if (poleModel != null) poleLimb = aRot.inverse().rotate(poleModel.sub(aPos));
                }
                lastPos = posLimb;
                lastPole = poleLimb;
            }
        }

        double desired = target == null ? 0.0
                : MathUtil.clamp(target.weight() * def.weight * ctl.weight(def.name) * ClientConfig.GLOBAL_WEIGHT.get(), 0, 1);
        fade += (desired - fade) * MathUtil.halfLifeFactor(dt, def.fade);
        if (desired == 0.0 && fade < 0.002) {
            fade = 0;
            smoother.reset();
            return false;
        }
        if (posLimb == null) {
            if (lastPos == null) return false;
            posLimb = lastPos;
            poleLimb = lastPole;
        }

        IKGoal goal = IKGoal.tip(posLimb);
        if (poleLimb != null) goal = goal.withPole(poleLimb);
        settings.maxIterations = Math.min(def.settings.maxIterations, ClientConfig.MAX_ITERATIONS.get());
        SolveResult result = def.solver.resolve(chain, goal).solve(chain, settings, goal);
        NeoForge.EVENT_BUS.post(new IKSolveEvent.Post(entity, def.name, chain, result));
        smoother.smooth(chain, dt, def.smoothing);

        for (int i = 0; i < k; i++) {
            ModelPart p = parts[i];
            saved[i][0] = p.xRot;
            saved[i][1] = p.yRot;
            saved[i][2] = p.zRot;
        }
        for (int i = 0; i < k; i++) {
            Quat l = Quat.slerp(anim[i], chain.local(i), fade);
            double[] e = l.toEulerZYX();
            ModelPart p = parts[i];
            p.xRot = (float) e[0];
            p.yRot = (float) e[1];
            p.zRot = (float) e[2];
        }
        applied = true;
        return true;
    }

    void restore() {
        if (!applied) return;
        for (int i = 0; i < parts.length; i++) {
            parts[i].xRot = saved[i][0];
            parts[i].yRot = saved[i][1];
            parts[i].zRot = saved[i][2];
        }
        applied = false;
    }
}
