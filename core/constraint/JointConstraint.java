// interface for limiting a joint's rotation relative to its rest pose.
package k.jointed.core.constraint;

import k.jointed.core.math.Quat;
import k.jointed.core.math.V3;

// limits the rotation of one joint
// actual local rotation is restRotation * delta, where constraints modify delta (identity = rest pose)
// axes are expressed in the joint's rest frame (parent frame rotated by rest rotation)
// implementations must be stateless and thread-safe. single abstract method supports lambdas: JointConstraint noTwist = (delta, boneAxis) -> delta.swing(boneAxis);

@FunctionalInterface
public interface JointConstraint {

// delta: rotation relative to rest (unit quaternion)
// boneAxis: unit direction of the bone driven by this joint, in the joint's own frame (the twist axis)
// returns the closest allowed rotation

    Quat constrain(Quat delta, V3 boneAxis);

    // true if this joint can never move; solvers skip such joints when they distribute rotation
    default boolean isFixed() {
        return false;
    }
}
