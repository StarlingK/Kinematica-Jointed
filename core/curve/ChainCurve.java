// smooth Catmull-Rom curve through chain points with twist-free frames, used for rendering and attachments
package k.jointed.core.curve;

import k.jointed.core.math.Quat;
import k.jointed.core.math.V3;

import java.util.ArrayList;
import java.util.List;

public final class ChainCurve {
    private ChainCurve() {}

    // one point on the curve. frame is right-handed: normal x tangent = binormal
    public static final class Sample {
        public final V3 pos;
        public final V3 tangent;
        public final V3 normal;
        public final V3 binormal;
        public final double t;
    // index of the chain bone this sample lies on.
        public final int bone;

        Sample(V3 pos, V3 tangent, V3 normal, double t, int bone) {
            this.pos = pos;
            this.tangent = tangent;
            this.normal = normal;
            this.binormal = normal.cross(tangent).normalize();
            this.t = t;
            this.bone = bone;
        }
    }

// points: chain points (at least 2)
// subdivisions: samples per bone (at least 1)
// referenceUp: preferred direction for the first normal (may be null)

    public static Sample[] sample(V3[] points, int subdivisions, V3 referenceUp) {
        if (points.length < 2) throw new IllegalArgumentException("need at least 2 points");
        int segs = points.length - 1;
        int sub = Math.max(1, subdivisions);
        List<V3> pos = new ArrayList<>(segs * sub + 1);
        List<Integer> boneOf = new ArrayList<>();
        for (int i = 0; i < segs; i++) {
            V3 p0 = points[Math.max(i - 1, 0)];
            V3 p1 = points[i];
            V3 p2 = points[i + 1];
            V3 p3 = points[Math.min(i + 2, segs)];
            for (int k = 0; k < sub; k++) {
                double u = k / (double) sub;
                pos.add(catmull(p0, p1, p2, p3, u));
                boneOf.add(i);
            }
        }
        pos.add(points[segs]);
        boneOf.add(segs - 1);

        int count = pos.size();
        double[] dist = new double[count];
        for (int i = 1; i < count; i++) dist[i] = dist[i - 1] + pos.get(i).distance(pos.get(i - 1));
        double total = dist[count - 1];

        V3[] tan = new V3[count];
        for (int i = 0; i < count; i++) {
            V3 a = pos.get(Math.max(i - 1, 0));
            V3 b = pos.get(Math.min(i + 1, count - 1));
            tan[i] = b.sub(a).normalizeOr(i > 0 ? tan[i - 1] : V3.Y);
        }

        V3 up = referenceUp == null ? V3.Z : referenceUp;
        V3 n = up.rejectFrom(tan[0]);
        if (n.isTiny()) n = tan[0].anyPerpendicular();
        n = n.normalize();

        Sample[] out = new Sample[count];
        for (int i = 0; i < count; i++) {
            if (i > 0) {
                n = Quat.fromTo(tan[i - 1], tan[i]).rotate(n).rejectFrom(tan[i]);
                n = n.normalizeOr(tan[i].anyPerpendicular());
            }
            double t = total < V3.EPS ? 0 : dist[i] / total;
            out[i] = new Sample(pos.get(i), tan[i], n, t, boneOf.get(i));
        }
        return out;
    }

    // total arc length of the sampled curve.    public static double length(Sample[] s) {
        double l = 0;
        for (int i = 1; i < s.length; i++) l += s[i].pos.distance(s[i - 1].pos);
        return l;
    }

    // interpolated sample at length fraction t (0..1)
    public static Sample at(Sample[] s, double t) {
        if (t <= 0) return s[0];
        if (t >= 1) return s[s.length - 1];
        int lo = 0, hi = s.length - 1;
        while (hi - lo > 1) {
            int mid = (lo + hi) >>> 1;
            if (s[mid].t <= t) lo = mid;
            else hi = mid;
        }
        Sample a = s[lo], b = s[hi];
        double span = b.t - a.t;
        double f = span < 1e-12 ? 0 : (t - a.t) / span;
        V3 p = a.pos.lerp(b.pos, f);
        V3 tg = a.tangent.lerp(b.tangent, f).normalizeOr(a.tangent);
        V3 nm = a.normal.lerp(b.normal, f).rejectFrom(tg).normalizeOr(tg.anyPerpendicular());
        return new Sample(p, tg, nm, t, f < 0.5 ? a.bone : b.bone);
    }

  // orientation whose local X/Y/Z axes are the sample's normal/tangent/binormal
    public static Quat frameRotation(Sample s) {
        return Quat.fromBasis(s.normal, s.tangent, s.binormal);
    }

    private static V3 catmull(V3 p0, V3 p1, V3 p2, V3 p3, double u) {
        double u2 = u * u, u3 = u2 * u;
        double x = 0.5 * ((2 * p1.x) + (-p0.x + p2.x) * u + (2 * p0.x - 5 * p1.x + 4 * p2.x - p3.x) * u2 + (-p0.x + 3 * p1.x - 3 * p2.x + p3.x) * u3);
        double y = 0.5 * ((2 * p1.y) + (-p0.y + p2.y) * u + (2 * p0.y - 5 * p1.y + 4 * p2.y - p3.y) * u2 + (-p0.y + 3 * p1.y - 3 * p2.y + p3.y) * u3);
        double z = 0.5 * ((2 * p1.z) + (-p0.z + p2.z) * u + (2 * p0.z - 5 * p1.z + 4 * p2.z - p3.z) * u2 + (-p0.z + 3 * p1.z - 3 * p2.z + p3.z) * u3);
        return new V3(x, y, z);
    }
}
