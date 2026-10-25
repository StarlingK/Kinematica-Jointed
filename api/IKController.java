// per entity runtime handle for overriding or disabling ik from code
package k.jointed.api;

import java.util.HashMap;
import java.util.Map;
import java.util.Optional;

// runtime rig control for one entity via JointedApi controller targets are read by jointed controller client thread only

public final class IKController {
    private boolean enabled = true;
    private final Map<String, IKTarget> targets = new HashMap<>();
    private final Map<String, Double> weights = new HashMap<>();

    public boolean enabled() {
        return enabled;
    }

  // master switch: when false no limb on this entity is solved
    public void setEnabled(boolean enabled) {
        this.enabled = enabled;
    }

    public void setTarget(String limb, IKTarget target) {
        targets.put(limb, target);
    }

    public void clearTarget(String limb) {
        targets.remove(limb);
    }

    public Optional<IKTarget> target(String limb) {
        return Optional.ofNullable(targets.get(limb));
    }

    // multiplier (0..1) applied on top of the limb's own weight
    public void setWeight(String limb, double weight) {
        weights.put(limb, weight);
    }

    public double weight(String limb) {
        return weights.getOrDefault(limb, 1.0);
    }

    public void clearAll() {
        targets.clear();
        weights.clear();
    }
}
