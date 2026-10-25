// supports ModelPart Euler conversion and swing/twist decomposition
package k.jointed.core.math;

// immutable unit-quaternion rotation (w, x, y, z)
// assumes unit length; call normalize() after multiple multiplications
// a.mul(b) applies b first, then a (i.e., (ab)v == a(bv))
public final class Quat {
    public static final Quat IDENTITY = new Quat(1, 0, 0, 0);

    public final double w, x, y, z;

    public Quat(double w, double x, double y, double z) {
        this.w = w;
        this.x = x;
        this.y = y;
        this.z = z;
    }

    //  factories

    public static Quat fromAxisAngle(V3 axis, double angle) {
        V3 a = axis.normalizeOr(V3.ZERO);
        if (a.isTiny()) return IDENTITY;
        double h = angle * 0.5;
        double s = Math.sin(h);
        return new Quat(Math.cos(h), a.x * s, a.y * s, a.z * s);
    }

   // shortest-arc rotation from direction 'from' to direction 'to'.
    public static Quat fromTo(V3 from, V3 to) {
        V3 a = from.normalizeOr(V3.ZERO);
        V3 b = to.normalizeOr(V3.ZERO);
        if (a.isTiny() || b.isTiny()) return IDENTITY;
        double d = a.dot(b);
        if (d > 1.0 - 1e-12) return IDENTITY;
        if (d < -1.0 + 1e-12) {
            // 180 degrees: pick any perpendicular axis
            V3 axis = a.anyPerpendicular();
            return new Quat(0, axis.x, axis.y, axis.z);
        }
        V3 c = a.cross(b);
        return new Quat(1.0 + d, c.x, c.y, c.z).normalize();
    }

// converts a rotation vector (axis * angle in radians) to a quaternion
    public static Quat exp(V3 rotationVector) {
        double angle = rotationVector.length();
        if (angle < 1e-12) {
            return new Quat(1, rotationVector.x * 0.5, rotationVector.y * 0.5, rotationVector.z * 0.5).normalize();
        }
        return fromAxisAngle(rotationVector.scale(1.0 / angle), angle);
    }

// rotation from a right-handed basis where bx, by, bz are the X, Y, Z column vectors
    public static Quat fromBasis(V3 bx, V3 by, V3 bz) {
        double m00 = bx.x, m10 = bx.y, m20 = bx.z;
        double m01 = by.x, m11 = by.y, m21 = by.z;
        double m02 = bz.x, m12 = bz.y, m22 = bz.z;
        double tr = m00 + m11 + m22;
        double w, x, y, z;
        if (tr > 0) {
            double s = Math.sqrt(tr + 1.0) * 2;
            w = 0.25 * s;
            x = (m21 - m12) / s;
            y = (m02 - m20) / s;
            z = (m10 - m01) / s;
        } else if (m00 > m11 && m00 > m22) {
            double s = Math.sqrt(1.0 + m00 - m11 - m22) * 2;
            w = (m21 - m12) / s;
            x = 0.25 * s;
            y = (m01 + m10) / s;
            z = (m02 + m20) / s;
        } else if (m11 > m22) {
            double s = Math.sqrt(1.0 + m11 - m00 - m22) * 2;
            w = (m02 - m20) / s;
            x = (m01 + m10) / s;
            y = 0.25 * s;
            z = (m12 + m21) / s;
        } else {
            double s = Math.sqrt(1.0 + m22 - m00 - m11) * 2;
            w = (m10 - m01) / s;
            x = (m02 + m20) / s;
            y = (m12 + m21) / s;
            z = 0.25 * s;
        }
        return new Quat(w, x, y, z).normalize();
    }

// minecraft ModelPart rotation order: R = Rz(z) * Ry(y) * Rx(x) (JOML rotationZYX)
    public static Quat fromEulerZYX(double rx, double ry, double rz) {
        Quat qx = fromAxisAngle(V3.X, rx);
        Quat qy = fromAxisAngle(V3.Y, ry);
        Quat qz = fromAxisAngle(V3.Z, rz);
        return qz.mul(qy).mul(qx);
    }

    //  algebra wee

    public Quat mul(Quat o) {
        return new Quat(
                w * o.w - x * o.x - y * o.y - z * o.z,
                w * o.x + x * o.w + y * o.z - z * o.y,
                w * o.y - x * o.z + y * o.w + z * o.x,
                w * o.z + x * o.y - y * o.x + z * o.w);
    }

    public Quat conjugate() {
        return new Quat(w, -x, -y, -z);
    }

    // inverse of a unit quaternion (equivalent to its conjugate)
    public Quat inverse() {
        return conjugate();
    }

    public double dot(Quat o) {
        return w * o.w + x * o.x + y * o.y + z * o.z;
    }

    public double length() {
        return Math.sqrt(w * w + x * x + y * y + z * z);
    }

    public Quat normalize() {
        double l = length();
        if (l < 1e-15) return IDENTITY;
        return new Quat(w / l, x / l, y / l, z / l);
    }

    public Quat negate() {
        return new Quat(-w, -x, -y, -z);
    }

    // same rotation, but flipped to the hemisphere closest to 'ref' (dot product >= 0)
    public Quat sameHemisphereAs(Quat ref) {
        return dot(ref) < 0 ? negate() : this;
    }

    public V3 rotate(V3 v) {
        // v' = v + 2w(q x v) + 2 q x (q x v)
        double qx = x, qy = y, qz = z;
        double tx = 2 * (qy * v.z - qz * v.y);
        double ty = 2 * (qz * v.x - qx * v.z);
        double tz = 2 * (qx * v.y - qy * v.x);
        return new V3(
                v.x + w * tx + (qy * tz - qz * ty),
                v.y + w * ty + (qz * tx - qx * tz),
                v.z + w * tz + (qx * ty - qy * tx));
    }

    public static Quat slerp(Quat a, Quat b, double t) {
        if (t <= 0) return a;
        if (t >= 1) return b;
        Quat bb = b.sameHemisphereAs(a);
        double d = MathUtil.clamp(a.dot(bb), -1, 1);
        if (d > 0.9995) {
            return new Quat(
                    a.w + (bb.w - a.w) * t,
                    a.x + (bb.x - a.x) * t,
                    a.y + (bb.y - a.y) * t,
                    a.z + (bb.z - a.z) * t).normalize();
        }
        double theta = Math.acos(d);
        double sin = Math.sin(theta);
        double wa = Math.sin((1 - t) * theta) / sin;
        double wb = Math.sin(t * theta) / sin;
        return new Quat(
                a.w * wa + bb.w * wb,
                a.x * wa + bb.x * wb,
                a.y * wa + bb.y * wb,
                a.z * wa + bb.z * wb).normalize();
    }

    // decomposition

    // rotation angle in radians, bounded within [0, pi]
    public double angle() {
        double vl = Math.sqrt(x * x + y * y + z * z);
        double ww = w;
        if (ww < 0) ww = -ww; // shortest representation
        return 2.0 * Math.atan2(vl, ww);
    }

    // rotation vector (axis * shortest angle) in radians
    public V3 log() {
        double vl = Math.sqrt(x * x + y * y + z * z);
        if (vl < 1e-12) return new V3(x * 2, y * 2, z * 2).scale(w < 0 ? -1 : 1);
        double sign = w < 0 ? -1 : 1;
        double ww = Math.abs(w);
        double angle = 2.0 * Math.atan2(vl, ww);
        double k = sign * angle / vl;
        return new V3(x * k, y * k, z * k);
    }

    // vector part (x, y, z)
    public V3 vec() {
        return new V3(x, y, z);
    }

    // twist component of this rotation about 'axis' (where this == swing * twist)
    public Quat twist(V3 axis) {
        V3 a = axis.normalizeOr(V3.ZERO);
        if (a.isTiny()) return IDENTITY;
        double d = x * a.x + y * a.y + z * a.z;
        Quat t = new Quat(w, a.x * d, a.y * d, a.z * d);
        double l = t.length();
        if (l < 1e-12) return fromAxisAngle(a, Math.PI); // swing is exactly 180 degrees; twist is arbitrary
        return new Quat(t.w / l, t.x / l, t.y / l, t.z / l);
    }

    /** Swing component of this rotation relative to {@code axis}: {@code this == swing * twist}. */
    public Quat swing(V3 axis) {
        return this.mul(twist(axis).conjugate()).normalize();
    }

    // signed rotation angle in radians (-pi, pi] of this rotation projected onto 'axis'
    public double angleAbout(V3 axis) {
        V3 a = axis.normalizeOr(V3.ZERO);
        if (a.isTiny()) return 0;
        double d = x * a.x + y * a.y + z * a.z;
        return MathUtil.wrapRadians(2.0 * Math.atan2(d, w));
    }

    // euler angles {x, y, z} matching the Minecraft ModelPart rotation order (fromEulerZYX)

    public double[] toEulerZYX() {
        double r20 = 2 * (x * z - w * y);
        double r21 = 2 * (y * z + w * x);
        double r22 = 1 - 2 * (x * x + y * y);
        double r10 = 2 * (x * y + w * z);
        double r00 = 1 - 2 * (y * y + z * z);
        double ry = Math.asin(MathUtil.clamp(-r20, -1, 1));
        double rx, rz;
        if (Math.abs(r20) < 1 - 1e-9) {
            rx = Math.atan2(r21, r22);
            rz = Math.atan2(r10, r00);
        } else {
            // gimbal lock: fix z = 0
            double r11 = 1 - 2 * (x * x + z * z);
            double r12 = 2 * (y * z - w * x);
            rz = 0;
            rx = Math.atan2(-r12, r11);
        }
        return new double[]{rx, ry, rz};
    }

    public boolean isFinite() {
        return Double.isFinite(w) && Double.isFinite(x) && Double.isFinite(y) && Double.isFinite(z);
    }

    public boolean approxEquals(Quat o, double eps) {
        // q and -q are the same rotation
        double d = Math.abs(dot(o));
        return 1.0 - d <= eps;
    }

    @Override
    public boolean equals(Object o) {
        return o instanceof Quat q && q.w == w && q.x == x && q.y == y && q.z == z;
    }

    @Override
    public int hashCode() {
        return ((Double.hashCode(w) * 31 + Double.hashCode(x)) * 31 + Double.hashCode(y)) * 31 + Double.hashCode(z);
    }

    @Override
    public String toString() {
        return String.format("Quat(w=%.4f, x=%.4f, y=%.4f, z=%.4f)", w, x, y, z);
    }
}
