// which solver a limb uses; AUTO picks two-bone for two-bone limbs and FABRIK otherwise.
package k.jointed.api;

import k.jointed.core.IKChain;
import k.jointed.core.solver.*;

public enum SolverChoice {
    AUTO, TWO_BONE, FABRIK, CCD, JACOBIAN;

    public IKSolver resolve(IKChain chain, IKGoal goal) {
        return switch (this) {
            case AUTO -> Solvers.auto(chain, goal);
            case TWO_BONE -> TwoBoneSolver.INSTANCE;
            case FABRIK -> FabrikSolver.INSTANCE;
            case CCD -> CcdSolver.INSTANCE;
            case JACOBIAN -> JacobianSolver.INSTANCE;
        };
    }

    public static SolverChoice parse(String s) {
        return valueOf(s.trim().toUpperCase(java.util.Locale.ROOT));
    }
}
