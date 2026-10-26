// registry of rig registrations plus the per-entity runtime state (client thread only)
package k.jointed.client.rig;

import k.jointed.api.IKController;
import k.jointed.api.LimbDefinition;
import k.jointed.api.RigDefinition;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.entity.LivingEntity;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.WeakHashMap;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.function.Predicate;

public final class RigManager {
    private RigManager() {}

    public record Registration(ResourceLocation id, Predicate<LivingEntity> filter, RigDefinition rig, boolean fromJson) {}

    private static final List<Registration> REGISTRATIONS = new CopyOnWriteArrayList<>();
    private static final Map<LivingEntity, RigInstance> INSTANCES = new WeakHashMap<>();
    private static final Map<LivingEntity, IKController> CONTROLLERS = new WeakHashMap<>();
    private static volatile int version;

    public static int version() {
        return version;
    }

    public static void register(Registration r) {
        REGISTRATIONS.removeIf(x -> x.id().equals(r.id()));
        REGISTRATIONS.add(r);
        version++;
    }

    public static boolean unregister(ResourceLocation id) {
        boolean removed = REGISTRATIONS.removeIf(x -> x.id().equals(id));
        if (removed) version++;
        return removed;
    }

    public static void removeJsonRigs() {
        if (REGISTRATIONS.removeIf(Registration::fromJson)) version++;
    }

    public static List<Registration> registrations() {
        return List.copyOf(REGISTRATIONS);
    }

    public static IKController controller(LivingEntity e) {
        return CONTROLLERS.computeIfAbsent(e, x -> new IKController());
    }

    static List<LimbRuntime> createLimbs(LivingEntity e) {
        List<LimbRuntime> out = new ArrayList<>();
        for (Registration r : REGISTRATIONS) {
            boolean match;
            try {
                match = r.filter().test(e);
            } catch (RuntimeException ex) {
                match = false;
            }
            if (!match) continue;
            for (LimbDefinition l : r.rig().limbs()) out.add(new LimbRuntime(r.id() + "#" + l.name, l));
        }
        return out;
    }

    public static RigInstance instanceFor(LivingEntity e, net.minecraft.client.model.EntityModel<?> model) {
        if (REGISTRATIONS.isEmpty()) return null;
        RigInstance inst = INSTANCES.computeIfAbsent(e, x -> new RigInstance(x, controller(x)));
        return inst.hasLimbs(model) ? inst : null;
    }

    public static RigInstance peek(LivingEntity e) {
        return INSTANCES.get(e);
    }

    public static int trackedEntities() {
        return INSTANCES.size();
    }
}
