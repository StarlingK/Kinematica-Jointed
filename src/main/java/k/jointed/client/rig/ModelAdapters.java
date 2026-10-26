// keeps vanilla's dependent parts in sync after ik moves their base parts
package k.jointed.client.rig;

import net.minecraft.client.model.EntityModel;
import net.minecraft.client.model.HumanoidModel;
import net.minecraft.client.model.PlayerModel;

public final class ModelAdapters {
    private ModelAdapters() {}

    public static void sync(EntityModel<?> model) {
        if (model instanceof PlayerModel<?> pm) {
            pm.leftPants.copyFrom(pm.leftLeg);
            pm.rightPants.copyFrom(pm.rightLeg);
            pm.leftSleeve.copyFrom(pm.leftArm);
            pm.rightSleeve.copyFrom(pm.rightArm);
            pm.jacket.copyFrom(pm.body);
        }
        if (model instanceof HumanoidModel<?> hm) {
            hm.hat.copyFrom(hm.head);
        }
    }
}
