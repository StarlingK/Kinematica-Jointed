// everything here should be pretty self xplanatory
package k.jointed.core.solver;

public final class SolverSettings {
    public int maxIterations = 20;
    public double tolerance = 1e-3;
    public double maxStepAngle = 0.6;
    public double damping = 0.1;
    public double restBias = 0.0;

    public double softness = 0.0;

    public SolverSettings iterations(int v) {
        this.maxIterations = v;
        return this;
    }

    public SolverSettings tolerance(double v) {
        this.tolerance = v;
        return this;
    }

    public SolverSettings maxStepAngle(double v) {
        this.maxStepAngle = v;
        return this;
    }

    public SolverSettings damping(double v) {
        this.damping = v;
        return this;
    }

    public SolverSettings restBias(double v) {
        this.restBias = v;
        return this;
    }

    public SolverSettings softness(double v) {
        this.softness = v;
        return this;
    }

    public SolverSettings copy() {
        SolverSettings s = new SolverSettings();
        s.maxIterations = maxIterations;
        s.tolerance = tolerance;
        s.maxStepAngle = maxStepAngle;
        s.damping = damping;
        s.restBias = restBias;
        s.softness = softness;
        return s;
    }
}
