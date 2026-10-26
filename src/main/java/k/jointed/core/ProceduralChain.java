// self-contained animated chain (tentacle, rope, crane arm): IK goal + optional Verlet follow-through, tick-interpolated
package k.jointed.core;

import k.jointed.core.dynamics.VerletChain;
import k.jointed.core.math.Quat;
import k.jointed.core.math.V3;
import k.jointed.core.solver.IKGoal;
import k.jointed.core.solver.IKSolver;
import k.jointed.core.solver.SolverSettings;
import k.jointed.core.solver.Solvers;

public final class ProceduralChain {
    private final IKChain chain;
    private final SolverSettings settings = new SolverSettings();
    private IKSolver solver; // null = auto
    private IKGoal goal;
    private VerletChain dynamics;
    private V3[] prevPoints;
    private V3[] curPoints;

    public ProceduralChain(IKChain chain) {
        this.chain = chain;
        this.curPoints = chain.points();
        this.prevPoints = curPoints;
    }

    public IKChain chain() {
        return chain;
    }

    public SolverSettings settings() {
        return settings;
    }

    // null selects a solver automatically
    public ProceduralChain solver(IKSolver solver) {
        this.solver = solver;
        return this;
    }

    public ProceduralChain goal(IKGoal goal) {
        this.goal = goal;
        return this;
    }

    public ProceduralChain target(V3 tip) {
        this.goal = goal == null ? IKGoal.tip(tip) : goal.withPosition(tip);
        return this;
    }

    public ProceduralChain root(V3 position, Quat rotation) {
        chain.setRoot(position, rotation);
        return this;
    }

    // adds Verlet follow-through, treating the IK pose as the target rest shape for the simulation
    public VerletChain enableDynamics() {
        if (dynamics == null) dynamics = new VerletChain(chain);
        return dynamics;
    }

    public void disableDynamics() {
        dynamics = null;
    }

    public VerletChain dynamics() {
        return dynamics;
    }

  // advances one step (call once per game tick with dt = 0.05, or at any fixed rate)
    public void tick(double dt) {
        prevPoints = curPoints;
        if (goal != null) {
            IKSolver s = solver != null ? solver : Solvers.auto(chain, goal);
            s.solve(chain, settings, goal);
        }
        if (dynamics != null) {
            dynamics.step(dt, chain.rootPosition(), chain.points(), null);
            dynamics.applyTo(chain);
        }
        curPoints = chain.points();
    }

    // points interpolated between the last two ticks (partial in 0..1), for smooth rendering
    public V3[] points(double partial) {
        V3[] out = new V3[curPoints.length];
        int n = Math.min(prevPoints.length, curPoints.length);
        for (int i = 0; i < out.length; i++) out[i] = i < n ? prevPoints[i].lerp(curPoints[i], partial) : curPoints[i];
        return out;
    }

    // snaps the interpolation history to the current pose (use after teleporting the root)
    public void snap() {
        curPoints = chain.points();
        prevPoints = curPoints;
        if (dynamics != null) dynamics.reset(curPoints);
    }
}
