// everything a TargetProvider needs
package k.jointed.client.rig;

import k.jointed.api.IKController;
import k.jointed.api.TargetSpace;
import k.jointed.core.math.MathUtil;
import k.jointed.core.math.Quat;
import k.jointed.core.math.V3;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.level.ClipContext;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.HitResult;
import net.minecraft.world.phys.Vec3;

import java.util.Optional;

public final class TargetContext {
    public final LivingEntity entity;
    public final float partialTick;
    public final String limbName;
    private final ModelSpace space;
    private final IKController controller;
    V3 rootModel = V3.ZERO;
    V3 animatedTipModel = V3.ZERO;

    TargetContext(LivingEntity entity, float partialTick, String limbName, ModelSpace space, IKController controller) {
        this.entity = entity;
        this.partialTick = partialTick;
        this.limbName = limbName;
        this.space = space;
        this.controller = controller;
    }

    public IKController controller() {
        return controller;
    }

    public Level level() {
        return entity.level();
    }

    public boolean isFirstPerson() {
        return space.firstPerson;
    }

    public boolean worldAvailable() {
        return space.worldValid;
    }

    public V3 entityPosition() {
        Vec3 p = entity.getPosition(partialTick);
        return new V3(p.x, p.y, p.z);
    }

    public double bodyYaw() {
        return Math.toRadians(net.minecraft.util.Mth.rotLerp(partialTick, entity.yBodyRotO, entity.yBodyRot));
    }

    public V3 eyePosition() {
        if (space.firstPerson) {
            Vec3 c = net.minecraft.client.Minecraft.getInstance().gameRenderer.getMainCamera().getPosition();
            return new V3(c.x, c.y, c.z);
        }
        Vec3 p = entity.getEyePosition(partialTick);
        return new V3(p.x, p.y, p.z);
    }

    public V3 lookVector() {
        if (space.firstPerson) {
            var l = net.minecraft.client.Minecraft.getInstance().gameRenderer.getMainCamera().getLookVector();
            return new V3(l.x(), l.y(), l.z());
        }
        Vec3 v = entity.getViewVector(partialTick);
        return new V3(v.x, v.y, v.z);
    }

    public V3 entityToWorld(V3 local) {
        return entityPosition().add(Quat.fromAxisAngle(V3.Y, -bodyYaw()).rotate(local));
    }

    public V3 limbRootWorld() {
        return space.worldValid ? space.modelToWorld(rootModel) : null;
    }

    public V3 animatedTipWorld() {
        return space.worldValid ? space.modelToWorld(animatedTipModel) : null;
    }

    public V3 modelToWorld(V3 px) {
        return space.modelToWorld(px);
    }

    public V3 worldToModel(V3 w) {
        return space.worldToModel(w);
    }

    public V3 toModel(V3 p, TargetSpace from) {
        switch (from) {
            case MODEL:
                return p;
            case WORLD:
                return space.worldValid ? space.worldToModel(p) : null;
            case ENTITY:
                return space.worldValid && !space.firstPerson ? space.worldToModel(entityToWorld(p)) : null;
            case VIEW:
                return space.firstPerson ? space.viewToModel(p) : null;
            default:
                return null;
        }
    }

    public Optional<V3> raycastBlocks(V3 from, V3 to) {
        BlockHitResult hit = level().clip(new ClipContext(new Vec3(from.x, from.y, from.z), new Vec3(to.x, to.y, to.z),
                ClipContext.Block.COLLIDER, ClipContext.Fluid.NONE, entity));
        if (hit.getType() == HitResult.Type.MISS) return Optional.empty();
        Vec3 l = hit.getLocation();
        return Optional.of(new V3(l.x, l.y, l.z));
    }

    public static double clamp(double v, double lo, double hi) {
        return MathUtil.clamp(v, lo, hi);
    }
}
