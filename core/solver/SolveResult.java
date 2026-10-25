package k.jointed.core.solver;

// outcome of a solve
// iterations: iterations performed
// error: remaining distance between goal point and target (chain units)
// converged: error <= tolerance
// reachable: false if target is farther than chain can stretch (solver extended towards it)

public record SolveResult(int iterations, double error, boolean converged, boolean reachable) {
}
