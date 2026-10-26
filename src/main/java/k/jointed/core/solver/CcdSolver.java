// cyclic coordinate descent solver: rotation-based, refines current pose
package k.jointed.core.solver;

import k.jointed.core.IKChain;
import k.jointed.core.math.Quat;
import k.jointed.core.math.V3;

// cyclic coordinate descent: works directly on joint rotations so twist and joint constraints (hinge/ellipse) behave naturally
// refines current pose, making it good for limbs starting from an animated pose that should deviate as little as possible
// per-joint participation is controlled by IKChain.setJointWeight and per-iteration turn limit by SolverSettings.maxStepAngle

public final class CcdSolver implements IKSolver {
    public static final CcdSolver INSTANCE = new CcdSolver();

    @Override
    public String id() {
        return "ccd";
    }

    @Override
    public SolveResult solve(IKChain chain, SolverSettings s, IKGoal... goals) {
        IKGoal g = goals[0];
        int m = SolverSupport.activeBones(chain, g);
        V3 target = g.position;
        double reach = chain.reach(m);
        boolean reachable = target.distance(chain.rootPosition()) <= reach + 1e-9;

        int it = 0;
        double err = SolverSupport.error(chain, g);
        while (err > s.tolerance && it < s.maxIterations) {
            double before = err;
            for (int i = m - 1; i >= 0; i--) {
                double w = chain.jointWeight(i);
                if (w <= 0 || chain.constraint(i).isFixed()) continue;
                V3 p = chain.jointPosition(i);
                V3 e = chain.pointPosition(m);
                V3 a = e.sub(p);
                V3 b = target.sub(p);
                if (a.isTiny() || b.isTiny()) continue;

                Quat q = Quat.fromTo(a, b);
                double angle = q.angle();
                if (angle < 1e-9) continue;
                double scale = Math.min(1.0, s.maxStepAngle / angle) * Math.min(1.0, w);
                if (scale < 1.0) q = Quat.slerp(Quat.IDENTITY, q, scale);

                Quat frame = chain.jointFrame(i);
                Quat parent = chain.parentFrame(i);
                chain.setLocal(i, parent.inverse().mul(q).mul(frame));
                chain.constrainJoint(i);
            }
            err = SolverSupport.error(chain, g);
            it++;
            if (before - err < 1e-10) break; // stalled (out of reach or contsrained)
        }
        SolverSupport.applyPole(chain, g, m);
        SolverSupport.alignOrientation(chain, g);
        double finalErr = SolverSupport.error(chain, g);
        return new SolveResult(it, finalErr, finalErr <= s.tolerance, reachable);
    }
}
