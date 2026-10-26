// shared solver helpers: position-to-rotation pass, pole, orientation, linear solve
package k.jointed.core.solver;

import k.jointed.core.IKChain;
import k.jointed.core.math.MathUtil;
import k.jointed.core.math.Quat;
import k.jointed.core.math.V3;

// shared building blocks for solvers; also useful on its own for custom solvers
public final class SolverSupport {
    private SolverSupport() {}

    // number of bones a solver should move for goal (goal's point index, clamped to chain)
    public static int activeBones(IKChain chain, IKGoal goal) {
        int n = chain.size();
        if (goal.point == IKGoal.TIP) return n;
        return Math.max(1, Math.min(goal.point, n));
    }

    // rebuilds joint rotations (swing-only relative to each joint's rest rotation, applying joint constraints) from desired joint positions, walking from root
    // p[i] is desired position of point i; p is updated in place so each point follows previous bone's (possibly clamped) rotation and bone lengths are preserved exactly
    // m: number of bones to process; bones beyond m keep their local rotation

    public static void forwardPass(IKChain chain, V3[] P, int m, boolean applyConstraints) {
        Quat parent = chain.rootRotation();
        for (int i = 0; i < m; i++) {
            Quat restRot = chain.rest(i);
            Quat base = parent.mul(restRot);
            V3 ax = chain.boneAxis(i);
            V3 d = P[i + 1].sub(P[i]);
            Quat swing;
            if (d.isTiny()) {
                swing = Quat.IDENTITY;
            } else {
                swing = Quat.fromTo(ax, base.inverse().rotate(d.normalize()));
            }
            if (applyConstraints) swing = chain.constraint(i).constrain(swing, ax);
            Quat local = restRot.mul(swing);
            chain.setLocal(i, local);
            Quat frame = parent.mul(chain.local(i)).normalize();
            P[i + 1] = P[i].add(frame.rotate(chain.bone(i)));
            parent = frame;
        }
    }

    // rotates whole chain about root-to-end axis so its bend points towards goal.pole
    public static void applyPole(IKChain chain, IKGoal goal, int m) {
        if (goal.pole == null || goal.poleWeight <= 0 || m < 2) return;
        V3 root = chain.rootPosition();
        V3 end = chain.pointPosition(m);
        V3 axisV = end.sub(root);
        if (axisV.isTiny()) return;
        V3 ax = axisV.normalize();

        double best = 1e-6;
        V3 bendPerp = null;
        for (int k = 1; k < m; k++) {
            V3 perp = chain.jointPosition(k).sub(root).rejectFrom(ax);
            double l = perp.length();
            if (l > best) {
                best = l;
                bendPerp = perp;
            }
        }
        if (bendPerp == null) return; // chain is straight, bend direction undefined
        V3 poleDir = goal.pole.sub(root).rejectFrom(ax);
        if (poleDir.isTiny()) return;

        double angle = bendPerp.signedAngleTo(poleDir, ax) * MathUtil.clamp(goal.poleWeight, 0, 1);
        if (Math.abs(angle) < 1e-7) return;
        Quat rot = Quat.fromAxisAngle(ax, angle);
        Quat f0 = chain.jointFrame(0);
        Quat newLocal = chain.rootRotation().inverse().mul(rot).mul(f0);
        chain.setLocal(0, newLocal);
        chain.constrainJoint(0);
    }

    // blends goal's orientation into chosen joint (default: last one)
    public static void alignOrientation(IKChain chain, IKGoal goal) {
        if (goal.orientation == null || goal.orientationWeight <= 0) return;
        int j = goal.orientationJoint < 0 ? chain.size() - 1 : Math.min(goal.orientationJoint, chain.size() - 1);
        Quat parent = chain.parentFrame(j);
        Quat targetLocal = parent.inverse().mul(goal.orientation).normalize();
        Quat blended = Quat.slerp(chain.local(j), targetLocal, MathUtil.clamp(goal.orientationWeight, 0, 1));
        chain.setLocal(j, blended);
        chain.constrainJoint(j);
    }

    // distance from goal's chain point to its target
    public static double error(IKChain chain, IKGoal goal) {
        int m = activeBones(chain, goal);
        return chain.pointPosition(m).distance(goal.position);
    }

    // solves dense linear system a x = b in place with partial pivoting; returns x (may alias b)
    public static double[] solveLinear(double[][] a, double[] b) {
        int n = b.length;
        for (int col = 0; col < n; col++) {
            int piv = col;
            double max = Math.abs(a[col][col]);
            for (int r = col + 1; r < n; r++) {
                double v = Math.abs(a[r][col]);
                if (v > max) {
                    max = v;
                    piv = r;
                }
            }
            if (max < 1e-14) {
                a[col][col] += 1e-12;
                max = Math.abs(a[col][col]);
            }
            if (piv != col) {
                double[] tr = a[piv];
                a[piv] = a[col];
                a[col] = tr;
                double tb = b[piv];
                b[piv] = b[col];
                b[col] = tb;
            }
            for (int r = col + 1; r < n; r++) {
                double f = a[r][col] / a[col][col];
                if (f == 0) continue;
                for (int c = col; c < n; c++) a[r][c] -= f * a[col][c];
                b[r] -= f * b[col];
            }
        }
        for (int r = n - 1; r >= 0; r--) {
            double s = b[r];
            for (int c = r + 1; c < n; c++) s -= a[r][c] * b[c];
            b[r] = s / a[r][r];
        }
        return b;
    }
}
