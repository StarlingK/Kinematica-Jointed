// mutable serial bone chain with forward kinematics, rest poses and per-joint constraints
package k.jointed.core;

import k.jointed.core.constraint.Constraints;
import k.jointed.core.constraint.JointConstraint;
import k.jointed.core.math.Quat;
import k.jointed.core.math.V3;

import java.util.ArrayList;
import java.util.List;

// a serial chain of n bones/joints
// joint i sits at pos[i] with frame F_i = F_(i-1) * local[i], where F_(-1) is the rootRotation()
// bone i is the vector bone[i] in joint i's frame, ending at joint i+1 (or the tip for the last bone):
//   pos[i+1] = pos[i] + F_i * bone[i]
//   tip      = pos[n-1] + F_(n-1) * bone[n-1]
// points are the n+1 positions: pos[0..n-1] and tip (point n is the tip)
// matches nested minecraft ModelPart composition (child pivot is in parent frame), but is unit-agnostic
// each joint has a rest rotation and a JointConstraint limiting deviation from it
// instances are mutable and not thread-safe

public final class IKChain {
    private final int n;
    private final V3[] bone;
    private final double[] length;
    private final V3[] axis;
    private final Quat[] rest;
    private final JointConstraint[] constraint;
    private final double[] jointWeight;
    private final Quat[] local;

    private V3 rootPos = V3.ZERO;
    private Quat rootRot = Quat.IDENTITY;

    private final V3[] pos;
    private final Quat[] frame;
    private V3 tip = V3.ZERO;
    private boolean dirty = true;

    private IKChain(List<V3> bones, List<JointConstraint> constraints, List<Quat> rests, V3 rootPos, Quat rootRot) {
        this.n = bones.size();
        if (n == 0) throw new IllegalArgumentException("A chain needs at least one bone");
        bone = new V3[n];
        length = new double[n];
        axis = new V3[n];
        rest = new Quat[n];
        constraint = new JointConstraint[n];
        jointWeight = new double[n];
        local = new Quat[n];
        pos = new V3[n];
        frame = new Quat[n];
        for (int i = 0; i < n; i++) {
            setBoneInternal(i, bones.get(i));
            rest[i] = rests.get(i).normalize();
            constraint[i] = constraints.get(i);
            jointWeight[i] = 1.0;
            local[i] = rest[i];
        }
        this.rootPos = rootPos;
        this.rootRot = rootRot;
    }

    private void setBoneInternal(int i, V3 v) {
        bone[i] = v;
        length[i] = v.length();
        axis[i] = v.normalizeOr(V3.Y);
    }

    // construction

    public static Builder builder() {
        return new Builder();
    }

  // straight chain of equal bones pointing along direction, i hate commenting framework
    public static IKChain straight(int bones, double boneLength, V3 direction, JointConstraint constraint) {
        Builder b = builder();
        V3 v = direction.normalize().scale(boneLength);
        for (int i = 0; i < bones; i++) b.bone(v, constraint);
        return b.build();
    }

    public static IKChain straight(int bones, double boneLength, V3 direction) {
        return straight(bones, boneLength, direction, Constraints.NONE);
    }

    public static final class Builder {
        private final List<V3> bones = new ArrayList<>();
        private final List<JointConstraint> constraints = new ArrayList<>();
        private final List<Quat> rests = new ArrayList<>();
        private V3 rootPos = V3.ZERO;
        private Quat rootRot = Quat.IDENTITY;

        public Builder root(V3 position, Quat rotation) {
            this.rootPos = position;
            this.rootRot = rotation;
            return this;
        }

        public Builder root(V3 position) {
            return root(position, Quat.IDENTITY);
        }

        // adds a bone with its vector in its own joint frame, no constraint, identity rest rotation
        public Builder bone(V3 vector) {
            return bone(vector, Constraints.NONE, Quat.IDENTITY);
        }

        public Builder bone(V3 vector, JointConstraint constraint) {
            return bone(vector, constraint, Quat.IDENTITY);
        }

        public Builder bone(V3 vector, JointConstraint constraint, Quat restRotation) {
            bones.add(vector);
            constraints.add(constraint == null ? Constraints.NONE : constraint);
            rests.add(restRotation == null ? Quat.IDENTITY : restRotation);
            return this;
        }

        public IKChain build() {
            return new IKChain(bones, constraints, rests, rootPos, rootRot);
        }
    }

    //  structure accessors

    // number of bones (== number of joints).
    public int size() {
        return n;
    }

    public V3 rootPosition() {
        return rootPos;
    }

    public Quat rootRotation() {
        return rootRot;
    }

    public void setRoot(V3 position, Quat rotation) {
        this.rootPos = position;
        this.rootRot = rotation;
        dirty = true;
    }

    public void setRootPosition(V3 position) {
        setRoot(position, rootRot);
    }

    public V3 bone(int i) {
        return bone[i];
    }

    public void setBone(int i, V3 vector) {
        setBoneInternal(i, vector);
        dirty = true;
    }

    public double boneLength(int i) {
        return length[i];
    }

   // unit bone direction in the joint's own frame.
    public V3 boneAxis(int i) {
        return axis[i];
    }

    public Quat rest(int i) {
        return rest[i];
    }

    public void setRest(int i, Quat q) {
        rest[i] = q.normalize();
    }

    public JointConstraint constraint(int i) {
        return constraint[i];
    }

    public void setConstraint(int i, JointConstraint c) {
        constraint[i] = c == null ? Constraints.NONE : c;
    }

    // relative participation of joint i in iterative solvers (0 = frozen, 1 = default)
    public double jointWeight(int i) {
        return jointWeight[i];
    }

    public void setJointWeight(int i, double w) {
        jointWeight[i] = Math.max(0, w);
    }

  // sum of all bone lengths: the maximum distance the tip can reach from the root
    public double reach() {
        double r = 0;
        for (double l : length) r += l;
        return r;
    }

    // sum of the first m bone lengths
    public double reach(int m) {
        double r = 0;
        for (int i = 0; i < m; i++) r += length[i];
        return r;
    }

    // pose accessors

    public Quat local(int i) {
        return local[i];
    }

    public void setLocal(int i, Quat q) {
        local[i] = q.normalize();
        dirty = true;
    }

  // deviation of joint i from its rest rotation: rest^-1 * local
    public Quat delta(int i) {
        return rest[i].inverse().mul(local[i]).normalize();
    }

    public void setDelta(int i, Quat delta) {
        setLocal(i, rest[i].mul(delta));
    }

  // applies the joint constraint of joint i to its current rotation
    public void constrainJoint(int i) {
        Quat d = delta(i);
        Quat c = constraint[i].constrain(d, axis[i]);
        local[i] = rest[i].mul(c).normalize();
        dirty = true;
    }

    public void constrainAll() {
        for (int i = 0; i < n; i++) constrainJoint(i);
    }

    // true if every joint currently satisfies its constraint within eps (rotation-distance)
    public boolean satisfiesConstraints(double eps) {
        for (int i = 0; i < n; i++) {
            Quat d = delta(i);
            Quat c = constraint[i].constrain(d, axis[i]);
            if (!d.approxEquals(c, eps)) return false;
        }
        return true;
    }

    public void resetToRest() {
        for (int i = 0; i < n; i++) local[i] = rest[i];
        dirty = true;
    }

    public Quat[] copyLocals() {
        return local.clone();
    }

    public void setLocals(Quat[] q) {
        if (q.length != n) throw new IllegalArgumentException("expected " + n + " rotations, got " + q.length);
        for (int i = 0; i < n; i++) local[i] = q[i].normalize();
        dirty = true;
    }

    // forward kinematics(the fun part)

    // recomputes cached world data if a setter invalidated it. called lazily by the getters below
    public void updateFK() {
        if (!dirty) return;
        Quat f = rootRot;
        V3 p = rootPos;
        for (int i = 0; i < n; i++) {
            pos[i] = p;
            f = f.mul(local[i]).normalize();
            frame[i] = f;
            p = p.add(f.rotate(bone[i]));
        }
        tip = p;
        dirty = false;
    }

// world position of joint origin i (0..n-1)
    public V3 jointPosition(int i) {
        updateFK();
        return pos[i];
    }

    // world frame of joint i
    public Quat jointFrame(int i) {
        updateFK();
        return frame[i];
    }

    // frame of joint i's parent (the root rotation for joint 0)
    public Quat parentFrame(int i) {
        if (i == 0) return rootRot;
        updateFK();
        return frame[i - 1];
    }

    public V3 tipPosition() {
        updateFK();
        return tip;
    }

    // position of point m: joint origin for m < n, the tip for m == n
    public V3 pointPosition(int m) {
        updateFK();
        return m >= n ? tip : pos[m];
    }

    // all n+1 points (joint origins followed by the tip)
    public V3[] points() {
        updateFK();
        V3[] out = new V3[n + 1];
        System.arraycopy(pos, 0, out, 0, n);
        out[n] = tip;
        return out;
    }

    /** Current world direction of bone {@code i} (unit). */
    public V3 boneDirection(int i) {
        updateFK();
        return frame[i].rotate(axis[i]);
    }

    // returns a deep copy of this chain, retaining the root pose, bones, constraints, and joint states

    public IKChain copy() {
        Builder b = builder().root(rootPos, rootRot);
        for (int i = 0; i < n; i++) b.bone(bone[i], constraint[i], rest[i]);
        IKChain c = b.build();
        for (int i = 0; i < n; i++) {
            c.local[i] = local[i];
            c.jointWeight[i] = jointWeight[i];
        }
        c.dirty = true;
        return c;
    }
  // returns a string summary after updating forward kinematics (FK)
    @Override
    public String toString() {
        updateFK();
        StringBuilder sb = new StringBuilder("IKChain[").append(n).append(" bones, reach=")
                .append(String.format("%.3f", reach())).append(", tip=").append(tip).append(']');
        return sb.toString();
    }
}
