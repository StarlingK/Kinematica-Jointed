// entry points called by the mixins; guards against config, distance and any exception
package k.jointed.client;

import com.mojang.blaze3d.vertex.PoseStack;
import k.jointed.Jointed;
import k.jointed.client.rig.RigInstance;
import k.jointed.client.rig.RigManager;
import net.minecraft.client.Minecraft;
import net.minecraft.client.model.EntityModel;
import net.minecraft.world.entity.LivingEntity;

public final class ClientHooks {
    private ClientHooks() {}

    private static boolean warned;

    public static void afterSetupAnim(LivingEntity entity, EntityModel<?> model, PoseStack stack, float partialTick) {
        if (!ClientConfig.ENABLED.get()) return;
        try {
            double max = ClientConfig.MAX_DISTANCE.get();
            var cam = Minecraft.getInstance().gameRenderer.getMainCamera().getPosition();
            if (entity.distanceToSqr(cam) > max * max) return;
            RigInstance inst = RigManager.instanceFor(entity, model);
            if (inst != null) inst.apply(model, stack, partialTick);
        } catch (Throwable t) {
            if (!warned) {
                warned = true;
                Jointed.LOGGER.error("Jointed hook failed", t);
            }
        }
    }

    public static void afterRender(LivingEntity entity, EntityModel<?> model) {
        try {
            RigInstance inst = RigManager.peek(entity);
            if (inst != null) inst.restore(model);
        } catch (Throwable t) {
            if (!warned) {
                warned = true;
                Jointed.LOGGER.error("Jointed restore failed", t);
            }
        }
    }
}
