// jacobian damped-least-squares solver: general, supports several goals, tip orientation and rest-pose bias
package k.jointed.core.solver;

import k.jointed.core.IKChain;
import k.jointed.core.math.Quat;
import k.jointed.core.math.V3;

public final class JacobianSolver implements IKSolver {
    public static final JacobianSolver INSTANCE = new JacobianSolver();

    @Override
    public String id() {
        return "jacobian";
    }

    @Override
    public SolveResult solve(IKChain chain, SolverSettings s, IKGoal... goals) {
        int n = chain.size();
        int g = goals.length;
        IKGoal oriGoal = null;
        for (IKGoal x : goals) {
            if (x.orientation != null && x.orientationWeight > 0) {
                oriGoal = x;
                break;
            }
        }
        int rows = 3 * g + (oriGoal != null ? 3 : 0);
        int cols = 3 * n;
        double lambda = s.damping * Math.max(chain.reach(), 1e-6);
        final double baseLambda2 = lambda * lambda;
        double lambda2 = baseLambda2;
        int rejected = 0;

        int it = 0;
        double err = maxError(chain, goals);
        double oriErr = oriGoal == null ? 0 : orientationError(chain, oriGoal).length();
        while ((err > s.tolerance || oriErr > 1e-3) && it < s.maxIterations) {
            chain.updateFK();
            double[][] J = new double[rows][cols];
            double[] e = new double[rows];

            for (int k = 0; k < g; k++) {
                IKGoal goal = goals[k];
                int m = SolverSupport.activeBones(chain, goal);
                V3 pm = chain.pointPosition(m);
                V3 delta = goal.position.sub(pm);
                double w = goal.weight;
                e[3 * k] = delta.x * w;
                e[3 * k + 1] = delta.y * w;
                e[3 * k + 2] = delta.z * w;
                for (int i = 0; i < m; i++) {
                    V3 r = pm.sub(chain.jointPosition(i));
                    // axis x r for the three world axes
                    setCol(J, 3 * k, 3 * i, 0, V3.X.cross(r), w);
                    setCol(J, 3 * k, 3 * i, 1, V3.Y.cross(r), w);
                    setCol(J, 3 * k, 3 * i, 2, V3.Z.cross(r), w);
                }
            }
            if (oriGoal != null) {
                int oj = oriGoal.orientationJoint < 0 ? n - 1 : Math.min(oriGoal.orientationJoint, n - 1);
                double w = oriGoal.orientationWeight;
                V3 oe = orientationError(chain, oriGoal);
                int base = 3 * g;
                e[base] = oe.x * w;
                e[base + 1] = oe.y * w;
                e[base + 2] = oe.z * w;
                for (int i = 0; i <= oj; i++) {
                    J[base][3 * i] = w;
                    J[base + 1][3 * i + 1] = w;
                    J[base + 2][3 * i + 2] = w;
                }
            }

            double[][] A = new double[rows][rows];
            for (int a = 0; a < rows; a++) {
                for (int b = a; b < rows; b++) {
                    double sum = 0;
                    for (int c = 0; c < cols; c++) sum += J[a][c] * J[b][c];
                    A[a][b] = sum;
                    A[b][a] = sum;
                }
                A[a][a] += lambda2;
            }

            double[] y = SolverSupport.solveLinear(copy(A), e.clone());
            double[] dTheta = new double[cols];
            for (int c = 0; c < cols; c++) {
                double sum = 0;
                for (int r = 0; r < rows; r++) sum += J[r][c] * y[r];
                dTheta[c] = sum;
            }

            if (s.restBias > 0) {
                // secondary task in the null space of the primary one: drift joints back towards rest
                double[] v = new double[cols];
                for (int i = 0; i < n; i++) {
                    V3 rv = chain.delta(i).log();
                    V3 world = chain.parentFrame(i).mul(chain.rest(i)).rotate(rv.negate());
                    v[3 * i] = world.x;
                    v[3 * i + 1] = world.y;
                    v[3 * i + 2] = world.z;
                }
                double[] jv = new double[rows];
                for (int r = 0; r < rows; r++) {
                    double sum = 0;
                    for (int c = 0; c < cols; c++) sum += J[r][c] * v[c];
                    jv[r] = sum;
                }
                double[] z = SolverSupport.solveLinear(copy(A), jv);
                for (int c = 0; c < cols; c++) {
                    double sum = 0;
                    for (int r = 0; r < rows; r++) sum += J[r][c] * z[r];
                    dTheta[c] += s.restBias * (v[c] - sum);
                }
            }

            Quat[] saved = chain.copyLocals();
            applyStep(chain, dTheta, s.maxStepAngle);
            double newErr = maxError(chain, goals);
            double newOri = oriGoal == null ? 0 : orientationError(chain, oriGoal).length();
            it++;
            if (newErr + newOri > err + oriErr + 1e-12) {
                // the step made things worse, undo it and damp 
                chain.setLocals(saved);
                lambda2 *= 4;
                if (++rejected > 10) break;
                continue;
            }
            boolean stalled = (err + oriErr) - (newErr + newOri) < 1e-12;
            err = newErr;
            oriErr = newOri;
            lambda2 = Math.max(baseLambda2, lambda2 * 0.5);
            if (stalled) break;
        }

        for (IKGoal goal : goals) {
            SolverSupport.applyPole(chain, goal, SolverSupport.activeBones(chain, goal));
        }
        double finalErr = maxError(chain, goals);
        boolean reachable = true;
        for (IKGoal goal : goals) {
            int m = SolverSupport.activeBones(chain, goal);
            if (goal.position.distance(chain.rootPosition()) > chain.reach(m) + 1e-9) reachable = false;
        }
        return new SolveResult(it, finalErr, finalErr <= s.tolerance, reachable);
    }

    private static void setCol(double[][] J, int row, int jointCol, int axis, V3 v, double w) {
        J[row][jointCol + axis] = v.x * w;
        J[row + 1][jointCol + axis] = v.y * w;
        J[row + 2][jointCol + axis] = v.z * w;
    }

    private static double[][] copy(double[][] a) {
        double[][] c = new double[a.length][];
        for (int i = 0; i < a.length; i++) c[i] = a[i].clone();
        return c;
    }

    private static double maxError(IKChain chain, IKGoal[] goals) {
        double e = 0;
        for (IKGoal goal : goals) e = Math.max(e, SolverSupport.error(chain, goal));
        return e;
    }

    // world-space rotation vector that would rotate current orientation onto goal orientation
    private static V3 orientationError(IKChain chain, IKGoal goal) {
        int n = chain.size();
        int oj = goal.orientationJoint < 0 ? n - 1 : Math.min(goal.orientationJoint, n - 1);
        Quat cur = chain.jointFrame(oj);
        return goal.orientation.mul(cur.inverse()).normalize().log();
    }

    private static void applyStep(IKChain chain, double[] dTheta, double maxStep) {
        int n = chain.size();
        chain.updateFK();
        Quat[] oldFrames = new Quat[n];
        for (int i = 0; i < n; i++) oldFrames[i] = chain.jointFrame(i);

        double maxNorm = 0;
        V3[] steps = new V3[n];
        for (int i = 0; i < n; i++) {
            double w = Math.min(1.0, chain.jointWeight(i));
            if (chain.constraint(i).isFixed()) w = 0;
            steps[i] = new V3(dTheta[3 * i], dTheta[3 * i + 1], dTheta[3 * i + 2]).scale(w);
            maxNorm = Math.max(maxNorm, steps[i].length());
        }
        double scale = maxNorm > maxStep ? maxStep / maxNorm : 1.0;

        Quat parent = chain.rootRotation();
        for (int i = 0; i < n; i++) {
            Quat q = Quat.exp(steps[i].scale(scale));
            chain.setLocal(i, parent.inverse().mul(q).mul(oldFrames[i]));
            chain.constrainJoint(i);
            parent = parent.mul(chain.local(i)).normalize();
        }
    }
}
