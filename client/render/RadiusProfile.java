// radius along a chain as a function of t, 0 = root and 1 = tip
package k.jointed.client.render;

@FunctionalInterface
public interface RadiusProfile {
    double radius(double t);

    static RadiusProfile constant(double r) {
        return t -> r;
    }

    static RadiusProfile taper(double root, double tip) {
        return t -> root + (tip - root) * t;
    }

    static RadiusProfile tentacle(double root, double tip) {
        return t -> (root + (tip - root) * t) * (1.0 + 0.25 * Math.sin(Math.PI * Math.min(1, t * 2)) * (1 - t));
    }
}
