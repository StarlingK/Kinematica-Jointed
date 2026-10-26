// client config: culling distance and solver iteration cap
package k.jointed.client;

import net.neoforged.neoforge.common.ModConfigSpec;

public final class ClientConfig {
    private ClientConfig() {}

    public static final ModConfigSpec SPEC;
    public static final ModConfigSpec.BooleanValue ENABLED;
    public static final ModConfigSpec.DoubleValue MAX_DISTANCE;
    public static final ModConfigSpec.IntValue MAX_ITERATIONS;
    public static final ModConfigSpec.DoubleValue GLOBAL_WEIGHT;

    static {
        ModConfigSpec.Builder b = new ModConfigSpec.Builder();
        b.push("general");
        ENABLED = b.comment("Master switch for all Jointed IK on the client.").define("enabled", true);
        MAX_DISTANCE = b.comment("Entities farther than this many blocks from the camera are not solved.")
                .defineInRange("maxDistance", 48.0, 4.0, 512.0);
        MAX_ITERATIONS = b.comment("Upper bound on solver iterations per limb per frame.")
                .defineInRange("maxIterations", 24, 1, 256);
        GLOBAL_WEIGHT = b.comment("Multiplier applied to every limb weight (0 = off, 1 = as authored).")
                .defineInRange("globalWeight", 1.0, 0.0, 1.0);
        b.pop();
        SPEC = b.build();
    }
}
