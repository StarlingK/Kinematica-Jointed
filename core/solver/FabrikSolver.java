// FABRIK solver: fast position-based reaching, good for long chains like tails and tentacles.
package k.jointed.core.solver;

import k.jointed.core.IKChain;
import k.jointed.core.math.V3;

// fabrik (forward and backward reaching inverse kinematics): fast, stable, and well suited to long chains (tails, tentacles, ropes, spines, etc)
// works on joint positions; after every backward (tip-to-root) reach, a forward (root-to-tip) pass converts positions back to rotations
// rotations are swing-only relative to each joint's rest rotation (no twist is introduced)
// supports a pole target and a tip orientation! woo!! woo

public final class FabrikSolver implements IKSolver {
    public static final FabrikSolver INSTANCE = new FabrikSolver();

    @Override
    public String id() {
        return "fabrik";
    }

    @Override
    public SolveResult solve(IKChain chain, SolverSettings s, IKGoal... goals) {
        IKGoal g = goals[0];
        int m = SolverSupport.activeBones(chain, g);
        V3 target = g.position;
        V3 root = chain.rootPosition();

        double[] len = new double[m];
        double total = 0;
        for (int i = 0; i < m; i++) {
            len[i] = chain.boneLength(i);
            total += len[i];
        }

        V3[] P = new V3[m + 1];
        for (int i = 0; i <= m; i++) P[i] = chain.pointPosition(i);

        double dist = target.distance(root);
        if (dist > total + 1e-9) {
            // out of reach: stretch straight towards the target
            V3 dir = target.sub(root).normalize();
            P[0] = root;
            for (int i = 0; i < m; i++) P[i + 1] = P[i].add(dir.scale(len[i]));
            SolverSupport.forwardPass(chain, P, m, true);
            SolverSupport.applyPole(chain, g, m);
            SolverSupport.alignOrientation(chain, g);
            return new SolveResult(1, SolverSupport.error(chain, g), false, false);
        }

        double err = P[m].distance(target);
        int it = 0;
        if (err > s.tolerance) {
            for (; it < s.maxIterations; it++) {
                // backward reaching: tip onto target, then pull the chain behind it
                P[m] = target;
                for (int i = m - 1; i >= 0; i--) {
                    V3 dir = P[i].sub(P[i + 1]).normalizeOr(chain.boneDirection(i).negate());
                    P[i] = P[i + 1].add(dir.scale(len[i]));
                }
                // forward reaching: root pinned, constraints applied
                P[0] = root;
                SolverSupport.forwardPass(chain, P, m, true);
                err = P[m].distance(target);
                if (err <= s.tolerance) {
                    it++;
                    break;
                }
            }
        }
        SolverSupport.applyPole(chain, g, m);
        SolverSupport.alignOrientation(chain, g);
        double finalErr = SolverSupport.error(chain, g);
        return new SolveResult(it, finalErr, finalErr <= s.tolerance, true);
    }
}
