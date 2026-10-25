// solver lookup by id, plus an auto choice that picks a sensible solver for a given chain
package k.jointed.core.solver;

import k.jointed.core.IKChain;

import java.util.Locale;
import java.util.Map;

public final class Solvers {
    private Solvers() {}

    private static final Map<String, IKSolver> BY_ID = Map.of(
            "fabrik", FabrikSolver.INSTANCE,
            "ccd", CcdSolver.INSTANCE,
            "jacobian", JacobianSolver.INSTANCE,
            "two_bone", TwoBoneSolver.INSTANCE);

    // returns built-in solver with this id (case-insensitive) or null. "auto" is not handled here, see auto
    public static IKSolver byId(String id) {
        return BY_ID.get(id.toLowerCase(Locale.ROOT));
    }

    // two-bone for two-bone chains, fabrik for everything else, i don't see many instances where you would wanna change this, but you are free to do so
    public static IKSolver auto(IKChain chain, IKGoal goal) {
        return SolverSupport.activeBones(chain, goal) == 2 ? TwoBoneSolver.INSTANCE : FabrikSolver.INSTANCE;
    }
}
