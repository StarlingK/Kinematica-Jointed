// immutable description of what a solver should reach: position, optional orientation and pole
package k.jointed.core.solver;

import k.jointed.core.math.Quat;
import k.jointed.core.math.V3;

// what a solver should achieve. immutable; use with* methods to deribe
// position / point: world position the chain point should reach. TIP (default) means the tip of the whole chain
// a smaller point (e.g. wrist of an arm with a hand bone) moves only the first point bones; later bones follow 
// orientation (optional): world orientation for joint orientationJoint (default: last joint), applied after position solve
// pole (optional): world point the middle of the chain should bend towards (knee/elbow direction) waaa

public final class IKGoal {
    public static final int TIP = -1;

    public final V3 position;
    public final int point;
    public final double weight;
    public final Quat orientation;
    public final int orientationJoint;
    public final double orientationWeight;
    public final V3 pole;
    public final double poleWeight;

    private IKGoal(V3 position, int point, double weight, Quat orientation, int orientationJoint,
                   double orientationWeight, V3 pole, double poleWeight) {
        this.position = position;
        this.point = point;
        this.weight = weight;
        this.orientation = orientation;
        this.orientationJoint = orientationJoint;
        this.orientationWeight = orientationWeight;
        this.pole = pole;
        this.poleWeight = poleWeight;
    }

    public static IKGoal tip(V3 position) {
        return new IKGoal(position, TIP, 1.0, null, -1, 1.0, null, 1.0);
    }

    public static IKGoal point(int point, V3 position) {
        return new IKGoal(position, point, 1.0, null, -1, 1.0, null, 1.0);
    }

    public IKGoal withPosition(V3 p) {
        return new IKGoal(p, point, weight, orientation, orientationJoint, orientationWeight, pole, poleWeight);
    }

    public IKGoal withWeight(double w) {
        return new IKGoal(position, point, w, orientation, orientationJoint, orientationWeight, pole, poleWeight);
    }

    public IKGoal withOrientation(Quat q) {
        return new IKGoal(position, point, weight, q, orientationJoint, orientationWeight, pole, poleWeight);
    }

    public IKGoal withOrientation(Quat q, int joint, double weight) {
        return new IKGoal(position, point, this.weight, q, joint, weight, pole, poleWeight);
    }

    public IKGoal withPole(V3 pole) {
        return new IKGoal(position, point, weight, orientation, orientationJoint, orientationWeight, pole, poleWeight);
    }

    public IKGoal withPole(V3 pole, double weight) {
        return new IKGoal(position, point, this.weight, orientation, orientationJoint, orientationWeight, pole, weight);
    }
}
