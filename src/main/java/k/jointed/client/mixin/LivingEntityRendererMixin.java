// runs the ik rigs right after setupAnim and restores the animated pose after rendering finishes
package k.jointed.client.mixin;

import com.mojang.blaze3d.vertex.PoseStack;
import k.jointed.client.ClientHooks;
import net.minecraft.client.model.EntityModel;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.entity.LivingEntityRenderer;
import net.minecraft.world.entity.LivingEntity;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(LivingEntityRenderer.class)
public abstract class LivingEntityRendererMixin {
    private static final String RENDER =
            "render(Lnet/minecraft/world/entity/LivingEntity;FFLcom/mojang/blaze3d/vertex/PoseStack;Lnet/minecraft/client/renderer/MultiBufferSource;I)V";
    private static final String SETUP_ANIM =
            "Lnet/minecraft/client/model/EntityModel;setupAnim(Lnet/minecraft/world/entity/Entity;FFFFF)V";

    @Inject(method = RENDER, at = @At(value = "INVOKE", target = SETUP_ANIM, shift = At.Shift.AFTER), require = 0)
    private void jointed$afterSetupAnim(LivingEntity entity, float entityYaw, float partialTick, PoseStack poseStack,
                                        MultiBufferSource buffer, int packedLight, CallbackInfo ci) {
        EntityModel<?> model = ((LivingEntityRenderer<?, ?>) (Object) this).getModel();
        ClientHooks.afterSetupAnim(entity, model, poseStack, partialTick);
    }

    @Inject(method = RENDER, at = @At("RETURN"), require = 0)
    private void jointed$afterRender(LivingEntity entity, float entityYaw, float partialTick, PoseStack poseStack,
                                     MultiBufferSource buffer, int packedLight, CallbackInfo ci) {
        EntityModel<?> model = ((LivingEntityRenderer<?, ?>) (Object) this).getModel();
        ClientHooks.afterRender(entity, model);
    }
}
