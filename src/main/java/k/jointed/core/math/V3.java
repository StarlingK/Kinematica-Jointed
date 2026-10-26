// immutable 3d double vector
package k.jointed.core.math;

public final class V3 {
    public static final V3 ZERO = new V3(0, 0, 0);
    public static final V3 X = new V3(1, 0, 0);
    public static final V3 Y = new V3(0, 1, 0);
    public static final V3 Z = new V3(0, 0, 1);
    public static final V3 NEG_X = new V3(-1, 0, 0);
    public static final V3 NEG_Y = new V3(0, -1, 0);
    public static final V3 NEG_Z = new V3(0, 0, -1);

    public static final double EPS = 1e-9;

    public final double x, y, z;

    public V3(double x, double y, double z) {
        this.x = x;
        this.y = y;
        this.z = z;
    }

    public static V3 of(double x, double y, double z) {
        return new V3(x, y, z);
    }

    public V3 add(V3 o) {
        return new V3(x + o.x, y + o.y, z + o.z);
    }

    public V3 add(double dx, double dy, double dz) {
        return new V3(x + dx, y + dy, z + dz);
    }

    public V3 sub(V3 o) {
        return new V3(x - o.x, y - o.y, z - o.z);
    }

    public V3 scale(double s) {
        return new V3(x * s, y * s, z * s);
    }

    public V3 negate() {
        return new V3(-x, -y, -z);
    }

    public double dot(V3 o) {
        return x * o.x + y * o.y + z * o.z;
    }

    public V3 cross(V3 o) {
        return new V3(y * o.z - z * o.y, z * o.x - x * o.z, x * o.y - y * o.x);
    }

    public double lengthSq() {
        return x * x + y * y + z * z;
    }

    public double length() {
        return Math.sqrt(lengthSq());
    }

    public double distance(V3 o) {
        return sub(o).length();
    }

    public double distanceSq(V3 o) {
        return sub(o).lengthSq();
    }

    public boolean isTiny() {
        return lengthSq() < EPS * EPS;
    }

    // normalized or {@code fallback} if this vector is nearly zero
    // if you do not know what this means:
	// if (l < eps) checks if the vector's length is smaller than this threshold
	// if it is, the code skips the division math and returns the fallback value

    public V3 normalizeOr(V3 fallback) {
        double l = length();
        if (l < EPS) return fallback;
        return new V3(x / l, y / l, z / l);
    }

    // normalized or {@link #Y} if this vector is nearly zero
    public V3 normalize() {
        return normalizeOr(Y);
    }

    public V3 withLength(double len) {
        return normalizeOr(Y).scale(len);
    }

    public V3 lerp(V3 o, double t) {
        return new V3(x + (o.x - x) * t, y + (o.y - y) * t, z + (o.z - z) * t);
    }

    // component of this vector along {@code onto}, which doesn't need to be normalized
    public V3 projectOnto(V3 onto) {
        double d = onto.lengthSq();
        if (d < EPS * EPS) return ZERO;
        return onto.scale(dot(onto) / d);
    }

    // this vector with its component along {@code axis} removed
    public V3 rejectFrom(V3 axis) {
        return sub(projectOnto(axis));
    }

    // unsigned angle in radians between this vector and {@code o}; 0 if either is zero
    public double angleTo(V3 o) {
        double d = length() * o.length();
        if (d < EPS * EPS) return 0;
        return Math.acos(MathUtil.clamp(dot(o) / d, -1, 1));
    }

    // signed angle from this to {@code o} around {@code axis} radians in (-pi, pi]
    public double signedAngleTo(V3 o, V3 axis) {
        V3 a = rejectFrom(axis);
        V3 b = o.rejectFrom(axis);
        if (a.isTiny() || b.isTiny()) return 0;
        double s = a.cross(b).dot(axis.normalize());
        double c = a.dot(b);
        return Math.atan2(s, c);
    }

    // any unit vector perpendicular to this one
    public V3 anyPerpendicular() {
        V3 n = normalize();
        V3 ref = Math.abs(n.x) < 0.9 ? X : Y;
        return n.cross(ref).normalize();
    }

    public boolean approxEquals(V3 o, double eps) {
        return Math.abs(x - o.x) <= eps && Math.abs(y - o.y) <= eps && Math.abs(z - o.z) <= eps;
    }

    public boolean isFinite() {
        return Double.isFinite(x) && Double.isFinite(y) && Double.isFinite(z);
    }

    @Override
    public boolean equals(Object o) {
        return o instanceof V3 v && v.x == x && v.y == y && v.z == z;
    }

    @Override
    public int hashCode() {
        return Double.hashCode(x) * 31 * 31 + Double.hashCode(y) * 31 + Double.hashCode(z);
    }

    @Override
    public String toString() {
        return String.format("(%.4f, %.4f, %.4f)", x, y, z);
    }
}
