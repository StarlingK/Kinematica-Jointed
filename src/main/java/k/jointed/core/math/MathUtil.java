// small math helpers: clamp, lerp, angle wrapping, and smoothing factors
package k.jointed.core.math;

public final class MathUtil {
    private MathUtil() {}

    public static final double PI = Math.PI;
    public static final double TAU = Math.PI * 2.0;
    public static final double DEG2RAD = Math.PI / 180.0;
    public static final double RAD2DEG = 180.0 / Math.PI;

    public static double clamp(double v, double lo, double hi) {
        return v < lo ? lo : (v > hi ? hi : v);
    }

    public static double lerp(double a, double b, double t) {
        return a + (b - a) * t;
    }

    public static double smoothstep(double t) {
        t = clamp(t, 0, 1);
        return t * t * (3 - 2 * t);
    }

    // wraps an angle in radians to (-pi, pi]
    public static double wrapRadians(double a) {
        a = a % TAU;
        if (a > PI) a -= TAU;
        else if (a <= -PI) a += TAU;
        return a;
    }

    public static double toRadians(double deg) {
        return deg * DEG2RAD;
    }

    public static double toDegrees(double rad) {
        return rad * RAD2DEG;
    }

    // frame-rate independent exponential smoothing factor for a given half-life (same unit as dt)
    public static double halfLifeFactor(double dt, double halfLife) {
        if (halfLife <= 0) return 1.0;
        return 1.0 - Math.pow(0.5, dt / halfLife);
    }
}
