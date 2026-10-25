// frame-rate independent smoothing of a chain's joint rotations (hide solver jitter, until i find a better way this will do)
package k.jointed.core.dynamics;

import k.jointed.core.IKChain;
import k.jointed.core.math.MathUtil;
import k.jointed.core.math.Quat;

public final class PoseSmoother {
    private Quat[] prev;

// blends current rotations with previous smoothed ones using a half-life duration in seconds
    public void smooth(IKChain chain, double dt, double halfLife) {
        Quat[] cur = chain.copyLocals();
        if (prev == null || prev.length != cur.length || halfLife <= 0) {
            prev = cur;
            return;
        }
        double f = MathUtil.halfLifeFactor(dt, halfLife);
        for (int i = 0; i < cur.length; i++) cur[i] = Quat.slerp(prev[i], cur[i], f);
        chain.setLocals(cur);
        prev = cur;
    }

    public void reset() {
        prev = null;
    }
}
