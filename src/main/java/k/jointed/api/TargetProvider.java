// supplies an ik target for a limb every frame (client-side only)
package k.jointed.api;

import k.jointed.client.rig.TargetContext;

// computes where a limb should reach. called once per limb per rendered frame on client render thread, so keep it cheap
// return null to leave limb on its animated pose; limb fades out smoothly

@FunctionalInterface
public interface TargetProvider {
    IKTarget resolve(TargetContext ctx);
}
