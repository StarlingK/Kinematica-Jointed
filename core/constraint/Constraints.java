// built-in joint constraints (cone, ellipse, hinge, fixed) and factory methods.
package k.jointed.core.constraint;

import k.jointed.core.math.MathUtil;
import k.jointed.core.math.Quat;
import k.jointed.core.math.V3;

// factory and built-in JointConstraint implementations. methods with '*Deg' use degrees
public final class Constraints {
    private Constraints() {}

    public static final JointConstraint NONE = new JointConstraint() {
        @Override
        public Quat constrain(Quat delta, V3 boneAxis) {
            return delta;
        }

        @Override
        public String toString() {
            return "Unconstrained";
        }
    };

    public static final JointConstraint FIXED = new JointConstraint() {
        @Override
        public Quat constrain(Quat delta, V3 boneAxis) {
            return Quat.IDENTITY;
        }

        @Override
        public boolean isFixed() {
            return true;
        }

        @Override
        public String toString() {
            return "Fixed";
        }
    };

    // factories

    // circular swing cone around the rest bone direction, plus an optional twist range about its axi.
    public static JointConstraint cone(double maxSwingRad, double twistMinRad, double twistMaxRad) {
        return new Cone(maxSwingRad, twistMinRad, twistMaxRad);
    }

    // circular swing cone with twist locked to zero
    public static JointConstraint coneDeg(double maxSwingDeg) {
        return new Cone(MathUtil.toRadians(maxSwingDeg), 0, 0);
    }

    public static JointConstraint coneDeg(double maxSwingDeg, double twistMinDeg, double twistMaxDeg) {
        return new Cone(MathUtil.toRadians(maxSwingDeg), MathUtil.toRadians(twistMinDeg), MathUtil.toRadians(twistMaxDeg));
    }

  // elliptical swing limits (like a shoulder: more range forward/back than sideways) plus twist range
  // swing is limited as a rotation vector about the two axes perpendicular to the bone: refAxis
  // (projected perpendicular to the bone) and bone x refAxis.

    public static JointConstraint ellipse(double aPos, double aNeg, double bPos, double bNeg,
                                          double twistMinRad, double twistMaxRad, V3 refAxis) {
        return new Ellipse(aPos, aNeg, bPos, bNeg, twistMinRad, twistMaxRad, refAxis);
    }

    public static JointConstraint ellipseDeg(double aDeg, double bDeg, double twistMinDeg, double twistMaxDeg) {
        return new Ellipse(MathUtil.toRadians(aDeg), MathUtil.toRadians(aDeg), MathUtil.toRadians(bDeg),
                MathUtil.toRadians(bDeg), MathUtil.toRadians(twistMinDeg), MathUtil.toRadians(twistMaxDeg), V3.X);
    }

    // single-axis hinge (elbow/knee/finger). angle range is in radians, measured about axis
    public static JointConstraint hinge(V3 axis, double minRad, double maxRad) {
        return new Hinge(axis, minRad, maxRad);
    }

    public static JointConstraint hingeDeg(V3 axis, double minDeg, double maxDeg) {
        return new Hinge(axis, MathUtil.toRadians(minDeg), MathUtil.toRadians(maxDeg));
    }

    // applies all constraints in order.
    public static JointConstraint all(JointConstraint... constraints) {
        return new Composite(constraints.clone());
    }

    //  implementations

    public record Cone(double maxSwing, double twistMin, double twistMax) implements JointConstraint {
        public Cone {
            if (maxSwing < 0) throw new IllegalArgumentException("maxSwing < 0");
            if (twistMin > twistMax) throw new IllegalArgumentException("twistMin > twistMax");
        }

        @Override
        public Quat constrain(Quat delta, V3 boneAxis) {
            Quat q = delta.w < 0 ? delta.negate() : delta;
            Quat twist = q.twist(boneAxis);
            Quat swing = q.mul(twist.conjugate()).normalize();

            double t = MathUtil.clamp(twist.angleAbout(boneAxis), twistMin, twistMax);
            Quat twistC = Quat.fromAxisAngle(boneAxis, t);

            double sa = swing.angle();
            Quat swingC = swing;
            if (sa > maxSwing) {
                V3 rv = swing.log();
                swingC = rv.isTiny() ? Quat.IDENTITY : Quat.exp(rv.withLength(maxSwing));
            }
            return swingC.mul(twistC).normalize();
        }
    }

    public record Ellipse(double aPos, double aNeg, double bPos, double bNeg,
                          double twistMin, double twistMax, V3 refAxis) implements JointConstraint {
        public Ellipse {
            if (aPos < 0 || aNeg < 0 || bPos < 0 || bNeg < 0) throw new IllegalArgumentException("negative swing limit");
            if (twistMin > twistMax) throw new IllegalArgumentException("twistMin > twistMax");
        }

        @Override
        public Quat constrain(Quat delta, V3 boneAxis) {
            Quat q = delta.w < 0 ? delta.negate() : delta;
            Quat twist = q.twist(boneAxis);
            Quat swing = q.mul(twist.conjugate()).normalize();

            double t = MathUtil.clamp(twist.angleAbout(boneAxis), twistMin, twistMax);
            Quat twistC = Quat.fromAxisAngle(boneAxis, t);

            V3 a = refAxis.rejectFrom(boneAxis).normalizeOr(boneAxis.anyPerpendicular());
            V3 b = boneAxis.cross(a).normalize();
            V3 rv = swing.log();
            double sa = rv.dot(a);
            double sb = rv.dot(b);
            double la = sa >= 0 ? aPos : aNeg;
            double lb = sb >= 0 ? bPos : bNeg;
            double k;
            if (la < 1e-12 && lb < 1e-12) {
                k = (Math.abs(sa) > 1e-12 || Math.abs(sb) > 1e-12) ? Double.POSITIVE_INFINITY : 0;
            } else {
                double ea = la < 1e-12 ? (Math.abs(sa) > 1e-12 ? Double.POSITIVE_INFINITY : 0) : sa / la;
                double eb = lb < 1e-12 ? (Math.abs(sb) > 1e-12 ? Double.POSITIVE_INFINITY : 0) : sb / lb;
                k = ea * ea + eb * eb;
            }
            Quat swingC = swing;
            if (k > 1.0) {
                if (Double.isInfinite(k)) {
                    // one axis has a zero limit: zero out that component, clamp the other
                    double na = la < 1e-12 ? 0 : MathUtil.clamp(sa, -aNeg, aPos);
                    double nb = lb < 1e-12 ? 0 : MathUtil.clamp(sb, -bNeg, bPos);
                    swingC = Quat.exp(a.scale(na).add(b.scale(nb)));
                } else {
                    double s = 1.0 / Math.sqrt(k);
                    swingC = Quat.exp(a.scale(sa * s).add(b.scale(sb * s)));
                }
            }
            return swingC.mul(twistC).normalize();
        }
    }

    public record Hinge(V3 axis, double min, double max) implements JointConstraint {
        public Hinge {
            if (axis.isTiny()) throw new IllegalArgumentException("hinge axis is zero");
            axis = axis.normalize();
            if (min > max) throw new IllegalArgumentException("min > max");
        }

        @Override
        public Quat constrain(Quat delta, V3 boneAxis) {
            double angle = MathUtil.clamp(delta.angleAbout(axis), min, max);
            return Quat.fromAxisAngle(axis, angle);
        }
    }

    public record Composite(JointConstraint[] constraints) implements JointConstraint {
        @Override
        public Quat constrain(Quat delta, V3 boneAxis) {
            Quat q = delta;
            for (JointConstraint c : constraints) q = c.constrain(q, boneAxis);
            return q;
        }

        @Override
        public boolean isFixed() {
            for (JointConstraint c : constraints) if (c.isFixed()) return true;
            return false;
        }
    }
}
