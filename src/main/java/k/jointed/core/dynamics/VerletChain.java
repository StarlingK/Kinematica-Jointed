// verlet rope/tail simulation that trails behind a pinned root and can be pulled toward an IK pose
package k.jointed.core.dynamics;

import k.jointed.core.IKChain;
import k.jointed.core.math.V3;
import k.jointed.core.solver.SolverSupport;

public final class VerletChain {

    // optional world collision: returns the corrected position for a point, or the same point if free
    @FunctionalInterface
    public interface Collider {
        V3 resolve(V3 point);
    }

    private final int bones;
    private final V3[] p;
    private final V3[] prev;
    private final double[] len;

    // acceleration in chain units per second squared (default: gravity, -Y)
    public V3 gravity = new V3(0, -9.8, 0);
    // fraction of velocity kept after one second 
    public double damping = 0.1;
    // 0..1: how strongly each step pulls points toward the pose passed to step
    public double stiffness = 0.0;
    // distance-constraint relaxation passes per step
    public int iterations = 8;
    public Collider collider;

    public VerletChain(IKChain chain) {
        this.bones = chain.size();
        this.p = chain.points();
        this.prev = p.clone();
        this.len = new double[bones];
        for (int i = 0; i < bones; i++) len[i] = chain.boneLength(i);
    }

    public void reset(V3[] points) {
        for (int i = 0; i <= bones; i++) {
            p[i] = points[i];
            prev[i] = points[i];
        }
    }

    // root: pinned position of point 0
    // pose: optional desired points (n+1) the chain is pulled toward, may be null
    // tipPin: optional position the last point is pinned to, may be null

    public void step(double dt, V3 root, V3[] pose, V3 tipPin) {
        if (dt <= 0) return;
        double keep = Math.pow(Math.max(0, Math.min(1, damping)), dt);
        V3 g = gravity.scale(dt * dt);
        for (int i = 1; i <= bones; i++) {
            V3 vel = p[i].sub(prev[i]).scale(keep);
            prev[i] = p[i];
            V3 next = p[i].add(vel).add(g);
            if (pose != null && stiffness > 0) next = next.lerp(pose[i], Math.min(1, stiffness));
            p[i] = next;
        }
        p[0] = root;
        prev[0] = root;
        if (tipPin != null) p[bones] = tipPin;

        for (int it = 0; it < iterations; it++) {
            for (int i = 0; i < bones; i++) {
                V3 a = p[i];
                V3 b = p[i + 1];
                V3 d = b.sub(a);
                double dist = d.length();
                if (dist < V3.EPS) continue;
                V3 corr = d.scale((dist - len[i]) / dist);
                boolean aPinned = i == 0;
                boolean bPinned = i == bones - 1 && tipPin != null;
                if (aPinned && bPinned) continue;
                if (aPinned) p[i + 1] = b.sub(corr);
                else if (bPinned) p[i] = a.add(corr);
                else {
                    p[i] = a.add(corr.scale(0.5));
                    p[i + 1] = b.sub(corr.scale(0.5));
                }
            }
            p[0] = root;
            if (tipPin != null) p[bones] = tipPin;
            if (collider != null) {
                for (int i = 1; i <= bones; i++) p[i] = collider.resolve(p[i]);
            }
        }
    }

    public V3[] points() {
        return p.clone();
    }

    // converts simulated points into joint rotations (honouring constraints) on chain
    public void applyTo(IKChain chain) {
        chain.setRoot(p[0], chain.rootRotation());
        V3[] pts = p.clone();
        SolverSupport.forwardPass(chain, pts, bones, true);
    }
}
