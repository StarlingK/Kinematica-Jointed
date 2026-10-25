// immutable description of one ik limb bound to a chain of model parts
package k.jointed.api;

import k.jointed.core.constraint.JointConstraint;
import k.jointed.core.math.V3;
import k.jointed.core.solver.SolverSettings;

import java.util.ArrayList;
import java.util.List;

// one ik limb: chain of modelparts (root to tip, each a direct child of the previous), its solver, and driver
// part references: dotted paths from the model's top level ("body.right_arm") or a bare name ("right_arm") searched for
// vanilla humanoid parts: addressable by snake_case field names (head, body, right_arm, left_arm, right_leg, left_leg)

public final class LimbDefinition {
    public final String name;
    public final List<String> parts;
    // end of the last bone in the last part's frame in model pixels like (0, 12, 0) for a vanilla arm
    public final V3 tipOffset;
    public final SolverChoice solver;
    public final SolverSettings settings;
    // one per part or empty for none
    public final List<JointConstraint> constraints;
    public final TargetProvider target;
    public final double weight;
    // seconds smooths solved rotations over time 0 = off
    public final double smoothing;
    public final double fade;
    // true constraints are measured from the part's initial baked rotation false from zero rotation
    public final boolean restFromInitialPose;

    private LimbDefinition(Builder b) {
        if (b.parts.isEmpty()) throw new IllegalArgumentException("limb '" + b.name + "' has no parts");
        if (b.target == null) throw new IllegalArgumentException("limb '" + b.name + "' has no target provider");
        if (!b.constraints.isEmpty() && b.constraints.size() != b.parts.size()) {
            throw new IllegalArgumentException("limb '" + b.name + "': constraints must match parts one to one");
        }
        this.name = b.name;
        this.parts = List.copyOf(b.parts);
        this.tipOffset = b.tipOffset;
        this.solver = b.solver;
        this.settings = b.settings;
        this.constraints = List.copyOf(b.constraints);
        this.target = b.target;
        this.weight = b.weight;
        this.smoothing = b.smoothing;
        this.fade = b.fade;
        this.restFromInitialPose = b.restFromInitialPose;
    }

    public static Builder builder(String name) {
        return new Builder(name);
    }

    public static final class Builder {
        private final String name;
        private final List<String> parts = new ArrayList<>();
        private V3 tipOffset = new V3(0, 12, 0);
        private SolverChoice solver = SolverChoice.AUTO;
        private SolverSettings settings = new SolverSettings().tolerance(0.01);
        private final List<JointConstraint> constraints = new ArrayList<>();
        private TargetProvider target;
        private double weight = 1.0;
        private double smoothing = 0.0;
        private double fade = 0.08;
        private boolean restFromInitialPose = true;

        private Builder(String name) {
            this.name = name;
        }

        public Builder parts(String... paths) {
            parts.addAll(List.of(paths));
            return this;
        }

        public Builder tipOffset(V3 v) {
            this.tipOffset = v;
            return this;
        }

        public Builder solver(SolverChoice s) {
            this.solver = s;
            return this;
        }

        public Builder settings(SolverSettings s) {
            this.settings = s;
            return this;
        }

        public Builder constraints(JointConstraint... c) {
            constraints.addAll(List.of(c));
            return this;
        }

        public Builder target(TargetProvider t) {
            this.target = t;
            return this;
        }

        public Builder weight(double w) {
            this.weight = w;
            return this;
        }

        public Builder smoothing(double seconds) {
            this.smoothing = seconds;
            return this;
        }

        public Builder fade(double seconds) {
            this.fade = seconds;
            return this;
        }

        public Builder restFromInitialPose(boolean v) {
            this.restFromInitialPose = v;
            return this;
        }

        public LimbDefinition build() {
            return new LimbDefinition(this);
        }
    }
}
