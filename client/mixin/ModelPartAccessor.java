// mixin accessor exposing ModelPart's private children map so rigs can walk part hierarchies
package k.jointed.client.mixin;

import net.minecraft.client.model.geom.ModelPart;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Accessor;

import java.util.Map;

@Mixin(ModelPart.class)
public interface ModelPartAccessor {
    @Accessor("children")
    Map<String, ModelPart> jointed$getChildren();
}
