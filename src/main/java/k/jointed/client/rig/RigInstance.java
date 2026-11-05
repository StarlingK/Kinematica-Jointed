// all limb runtimes for one entity; rebuilt when rig registrations or the model instance change
package k.jointed.client.rig;

import com.mojang.blaze3d.vertex.PoseStack;
import k.jointed.api.IKController;
import net.minecraft.client.Minecraft;
import net.minecraft.client.model.EntityModel;
import net.minecraft.world.entity.LivingEntity;

import java.lang.ref.WeakReference;
import java.util.List;

public final class RigInstance {
    private int version = -1;
    private WeakReference<EntityModel<?>> model = new WeakReference<>(null);
    private List<LimbRuntime> limbs = List.of();
    private boolean applied;

    RigInstance() {}

    public List<LimbRuntime> limbs() {
        return limbs;
    }

    private void ensure(LivingEntity entity, EntityModel<?> m) {
        int v = RigManager.version();
        if (v != version || model.get() != m) {
            limbs = RigManager.createLimbs(entity);
            version = v;
            model = new WeakReference<>(m);
        }
    }

    public boolean hasLimbs(LivingEntity entity, EntityModel<?> m) {
        ensure(entity, m);
        return !limbs.isEmpty();
    }

    public void markStale() {
        for (LimbRuntime l : limbs) l.markStale();
    }

    public void apply(LivingEntity entity, EntityModel<?> m, PoseStack stack, float partial) {
        ensure(entity, m);
        if (limbs.isEmpty()) return;
        IKController controller = RigManager.controller(entity);
        if (!controller.enabled()) {
            markStale();
            return;
        }
        var camera = Minecraft.getInstance().gameRenderer.getMainCamera().getPosition();
        ModelSpace space = ModelSpace.forEntity(entity, partial, stack, camera);
        boolean any = false;
        for (LimbRuntime l : limbs) any |= l.apply(entity, m, space, partial, controller);
        if (any) {
            ModelAdapters.sync(m);
            applied = true;
        }
    }

    public void restore(EntityModel<?> m) {
        if (!applied) return;
        for (LimbRuntime l : limbs) l.restore();
        ModelAdapters.sync(m);
        applied = false;
    }
}
