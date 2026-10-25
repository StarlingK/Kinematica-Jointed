// hooks first-person arm render so registered first-person ik can aim the arm model
package k.jointed.client.mixin;

import com.mojang.blaze3d.vertex.PoseStack;
import k.jointed.client.rig.FirstPersonRig;
import net.minecraft.client.model.PlayerModel;
import net.minecraft.client.model.geom.ModelPart;
import net.minecraft.client.player.AbstractClientPlayer;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.entity.player.PlayerRenderer;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(PlayerRenderer.class)
public abstract class PlayerRendererMixin {
    private static final String HAND =
            "renderHand(Lcom/mojang/blaze3d/vertex/PoseStack;Lnet/minecraft/client/renderer/MultiBufferSource;ILnet/minecraft/client/player/AbstractClientPlayer;Lnet/minecraft/client/model/geom/ModelPart;Lnet/minecraft/client/model/geom/ModelPart;)V";
    private static final String RENDER_PART =
            "Lnet/minecraft/client/model/geom/ModelPart;render(Lcom/mojang/blaze3d/vertex/PoseStack;Lcom/mojang/blaze3d/vertex/VertexConsumer;II)V";

    @Inject(method = HAND, at = @At(value = "INVOKE", target = RENDER_PART, ordinal = 0), require = 0)
    private void jointed$beforeArm(PoseStack poseStack, MultiBufferSource buffer, int light, AbstractClientPlayer player,
                                   ModelPart arm, ModelPart sleeve, CallbackInfo ci) {
        PlayerModel<?> model = ((PlayerRenderer) (Object) this).getModel();
        FirstPersonRig.apply(player, model, arm, poseStack);
    }

    @Inject(method = HAND, at = @At(value = "INVOKE", target = RENDER_PART, ordinal = 1), require = 0)
    private void jointed$beforeSleeve(PoseStack poseStack, MultiBufferSource buffer, int light, AbstractClientPlayer player,
                                      ModelPart arm, ModelPart sleeve, CallbackInfo ci) {
        FirstPersonRig.syncSleeve(arm, sleeve);
    }

    @Inject(method = HAND, at = @At("RETURN"), require = 0)
    private void jointed$afterHand(PoseStack poseStack, MultiBufferSource buffer, int light, AbstractClientPlayer player,
                                   ModelPart arm, ModelPart sleeve, CallbackInfo ci) {
        FirstPersonRig.restore();
    }
}
