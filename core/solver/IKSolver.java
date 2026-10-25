// common interface implemented by every ik solver
package k.jointed.core.solver;

import k.jointed.core.IKChain;

// moves an ikChain to satisfy one or more ikGoals, honouring each joint's constraint
// solvers modify the chain's local rotations in place and start from its current pose
// result is temporally coherent when a chain is solved every frame
// solvers are stateless and may be shared

public interface IKSolver {

    // goals: at least one. solvers other than jacobianSolver use goals[0] only
    SolveResult solve(IKChain chain, SolverSettings settings, IKGoal... goals);

    default SolveResult solve(IKChain chain, IKGoal goal) {
        return solve(chain, new SolverSettings(), goal);
    }

    // identifier ("fabrik", "ccd", etc)
    String id();
}
