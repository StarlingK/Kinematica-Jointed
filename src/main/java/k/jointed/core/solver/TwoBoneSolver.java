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
            bend = bestBend(chain, root, dir, eff, base, h, bend, Math.max(s.tolerance, 1e-6));
            elbow = base.add(bend.scale(h));
        }

        V3[] P = {root, elbow, root.add(dir.scale(eff))};
        SolverSupport.forwardPass(chain, P, 2, true);
        SolverSupport.alignOrientation(chain, g);
        double err = SolverSupport.error(chain, g);
        return new SolveResult(1, err, err <= s.tolerance, reachable);
    }

    // the preferred bend (pole, or the current one) can be impossible once joint limits are applied
    // for example a hinge elbow only bends in one plane, so a pole outside that plane would leave the limb mis-aimed
    // when that happens the bend direction is searched for around the root-to-target axis
    // reaching the target wins over following the pole, and among valid bends the one closest to the preferred one is used
    private static V3 bestBend(IKChain chain, V3 root, V3 dir, double eff, V3 base, double h, V3 preferred, double tol) {
        IKChain scratch = chain.copy();
        V3 end = root.add(dir.scale(eff));
        V3 u = preferred;
        V3 v = dir.cross(u).normalize();

        double first = bendError(scratch, root, end, base, h, u, v, 0);
        if (first <= tol) return u;

        int steps = 24;
        double step = MathUtil.TAU / steps;
        double[] e = new double[steps];
        e[0] = first;
        for (int k = 1; k < steps; k++) e[k] = bendError(scratch, root, end, base, h, u, v, k * step);

        double bestPhi = 0, bestErr = Double.MAX_VALUE, bestAbs = Double.MAX_VALUE;
        boolean bestValid = false;
        for (int k = 0; k < steps; k++) {
            if (e[k] > e[(k + steps - 1) % steps] || e[k] > e[(k + 1) % steps]) continue; // only local minimums
            double phi = k * step, err = e[k], span = step;
            // narrow down on the minimum by halving the search distance
            for (int i = 0; i < 20; i++) {
                double lo = bendError(scratch, root, end, base, h, u, v, phi - span);
                double hi = bendError(scratch, root, end, base, h, u, v, phi + span);
                if (lo < err && lo <= hi) {
                    err = lo;
                    phi -= span;
                } else if (hi < err) {
                    err = hi;
                    phi += span;
                }
                span *= 0.5;
            }
            boolean valid = err <= tol;
            double abs = Math.abs(MathUtil.wrapRadians(phi));
            if (valid ? (!bestValid || abs < bestAbs) : (!bestValid && err < bestErr)) {
                bestValid = valid;
                bestPhi = phi;
                bestErr = err;
                bestAbs = abs;
            }
        }
        return u.scale(Math.cos(bestPhi)).add(v.scale(Math.sin(bestPhi)));
    }

    // how far the tip ends up from where it should be after the joint limits are applied to this bend
    private static double bendError(IKChain scratch, V3 root, V3 end, V3 base, double h, V3 u, V3 v, double phi) {
        V3 bend = u.scale(Math.cos(phi)).add(v.scale(Math.sin(phi)));
        V3[] P = {root, base.add(bend.scale(h)), end};
        SolverSupport.forwardPass(scratch, P, 2, true);
        return P[2].distance(end);
    }
}
