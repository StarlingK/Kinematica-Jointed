// a resolved ik target position optional pole space and blend weight
package k.jointed.api;

import k.jointed.core.math.V3;

// a resolved ik target: position, optional pole, space, and blend weight
public record IKTarget(V3 position, TargetSpace space, V3 pole, double weight) {

    public static IKTarget world(V3 position) {
        return new IKTarget(position, TargetSpace.WORLD, null, 1.0);
    }

    public static IKTarget entity(V3 position) {
        return new IKTarget(position, TargetSpace.ENTITY, null, 1.0);
    }

    public static IKTarget model(V3 position) {
        return new IKTarget(position, TargetSpace.MODEL, null, 1.0);
    }

    public static IKTarget view(V3 position) {
        return new IKTarget(position, TargetSpace.VIEW, null, 1.0);
    }

    public IKTarget withPole(V3 pole) {
        return new IKTarget(position, space, pole, weight);
    }

    public IKTarget withWeight(double weight) {
        return new IKTarget(position, space, pole, weight);
    }
}
