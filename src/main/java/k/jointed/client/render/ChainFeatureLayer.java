// render layer added to every living entity renderer, draws each limb's renderer through its solved nodes
package k.jointed.client.render;

import com.mojang.blaze3d.vertex.PoseStack;
import k.jointed.client.rig.LimbRuntime;
import k.jointed.client.rig.RigInstance;
import k.jointed.client.rig.RigManager;
import k.jointed.core.math.V3;
import net.minecraft.client.model.EntityModel;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.entity.LivingEntityRenderer;
import net.minecraft.client.renderer.entity.RenderLayerParent;
import net.minecraft.client.renderer.entity.layers.RenderLayer;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.LivingEntity;
import net.neoforged.neoforge.client.event.EntityRenderersEvent;

public final class ChainFeatureLayer<T extends LivingEntity, M extends EntityModel<T>> extends RenderLayer<T, M> {

    public ChainFeatureLayer(RenderLayerParent<T, M> parent) {
        super(parent);
    }

    @SuppressWarnings({"unchecked", "rawtypes"})
    public static void onAddLayers(EntityRenderersEvent.AddLayers event) {
        for (EntityType<?> type : event.getEntityTypes()) {
            Object r = event.getRenderer((EntityType) type);
            if (r instanceof LivingEntityRenderer lr) lr.addLayer(new ChainFeatureLayer(lr));
        }
        for (var skin : event.getSkins()) {
            Object r = event.getSkin(skin);
            if (r instanceof LivingEntityRenderer lr) lr.addLayer(new ChainFeatureLayer(lr));
        }
    }

    @Override
    public void render(PoseStack stack, MultiBufferSource buffers, int light, T entity, float limbSwing,
                       float limbSwingAmount, float partialTick, float ageInTicks, float netHeadYaw, float headPitch) {
        RigInstance inst = RigManager.peek(entity);
        if (inst == null) return;
        int overlay = LivingEntityRenderer.getOverlayCoords(entity, 0.0F);
        for (LimbRuntime limb : inst.limbs()) {
            V3[] px = limb.renderPoints();
            if (px == null || limb.isBroken()) continue;

            // model pixels to blocks, the layer's pose stack is already in model space
            V3[] pts = new V3[px.length];
            for (int i = 0; i < px.length; i++) pts[i] = px[i].scale(1.0 / 16.0);

            if (limb.def.renderer != null) {
                limb.def.renderer.render(new ChainRenderContext(stack, buffers, pts, partialTick, light, overlay));
            }
            if (DebugState.enabled) {
                V3 t = limb.debugTarget() == null ? null : limb.debugTarget().scale(1.0 / 16.0);
                V3 p = limb.debugPole() == null ? null : limb.debugPole().scale(1.0 / 16.0);
                DebugRenderer.chain(stack, buffers, pts, t, p);
            }
        }
    }
}
