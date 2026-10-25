// analytic two-bone solver: exact, no iteration, with pole vector and soft reach (arms and legs)
package k.jointed.core.solver;

import k.jointed.core.IKChain;
import k.jointed.core.math.MathUtil;
import k.jointed.core.math.V3;

public final class TwoBoneSolver implements IKSolver {
    public static final TwoBoneSolver INSTANCE = new TwoBoneSolver();

    @Override
    public String id() {
        return "two_bone";
    }

    @Override
    public SolveResult solve(IKChain chain, SolverSettings s, IKGoal... goals) {
        IKGoal g = goals[0];
        int m = SolverSupport.activeBones(chain, g);
        if (m != 2) return FabrikSolver.INSTANCE.solve(chain, s, goals); // not a two-bone problem, lol

        V3 root = chain.rootPosition();
        V3 target = g.position;
        double l1 = chain.boneLength(0);
        double l2 = chain.boneLength(1);
        V3 d = target.sub(root);
        double dist = d.length();
        double maxReach = l1 + l2;
        double minReach = Math.abs(l1 - l2);
        boolean reachable = dist <= maxReach + 1e-9;

        V3 dir = dist < V3.EPS ? chain.jointPosition(1).sub(root).normalizeOr(V3.Y) : d.scale(1.0 / dist);
        double eff = MathUtil.clamp(dist, minReach + 1e-6, Math.max(minReach + 1e-6, maxReach - 1e-6));
        if (s.softness > 0 && maxReach > 1e-9) {
            double start = maxReach * (1.0 - MathUtil.clamp(s.softness, 0, 0.99));
            if (eff > start) {
                double span = maxReach - start;
                eff = start + span * (1.0 - Math.exp(-(eff - start) / span));
            }
        }

        V3 elbow;
        if (l1 < V3.EPS || eff < V3.EPS) {
            elbow = root.add(dir.scale(l1));
        } else {
            double a = MathUtil.clamp((l1 * l1 + eff * eff - l2 * l2) / (2 * l1 * eff), -1, 1);
            double h = l1 * Math.sqrt(Math.max(0, 1 - a * a));
            V3 base = root.add(dir.scale(l1 * a));

            V3 bend = null;
            if (g.pole != null) {
                V3 pv = g.pole.sub(root).rejectFrom(dir);
                if (!pv.isTiny()) bend = pv.normalize();
            }
            if (bend == null) {
                V3 cur = chain.jointPosition(1).sub(root).rejectFrom(dir);
                if (!cur.isTiny()) bend = cur.normalize();
            }
            if (bend == null) bend = dir.anyPerpendicular();
            elbow = base.add(bend.scale(h));
        }

        V3[] P = {root, elbow, root.add(dir.scale(eff))};
        SolverSupport.forwardPass(chain, P, 2, true);
        SolverSupport.alignOrientation(chain, g);
        double err = SolverSupport.error(chain, g);
        return new SolveResult(1, err, err <= s.tolerance, reachable);
    }
}
