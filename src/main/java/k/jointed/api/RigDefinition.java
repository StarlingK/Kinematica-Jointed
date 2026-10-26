// a set of limbs that are registered together for matching entities
package k.jointed.api;

import java.util.List;

public record RigDefinition(List<LimbDefinition> limbs) {
    public RigDefinition {
        limbs = List.copyOf(limbs);
    }

    public static RigDefinition of(LimbDefinition... limbs) {
        return new RigDefinition(List.of(limbs));
    }
}
