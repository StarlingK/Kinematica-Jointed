// finds ModelParts in any EntityModel by dotted path or name and lists a part's ancestors
package k.jointed.client.rig;

import k.jointed.client.mixin.ModelPartAccessor;
import net.minecraft.client.model.EntityModel;
import net.minecraft.client.model.HierarchicalModel;
import net.minecraft.client.model.geom.ModelPart;

import java.lang.reflect.Field;
import java.lang.reflect.Modifier;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;

public final class ModelPartResolver {
    private ModelPartResolver() {}

    private static final Map<Class<?>, List<Field>> FIELDS = new ConcurrentHashMap<>();

    public static Map<String, ModelPart> children(ModelPart part) {
        return ((ModelPartAccessor) (Object) part).jointed$getChildren();
    }

// parts rendered directly in model space: for HierarchicalModel this is just root
// for other models every ModelPart field registered under both field name and snake_case name

    public static Map<String, ModelPart> topLevel(EntityModel<?> model) {
        Map<String, ModelPart> out = new LinkedHashMap<>();
        if (model instanceof HierarchicalModel<?> h) {
            out.put("root", h.root());
            return out;
        }
        for (Field f : fields(model.getClass())) {
            try {
                Object v = f.get(model);
                if (v instanceof ModelPart p) {
                    out.putIfAbsent(snake(f.getName()), p);
                    out.putIfAbsent(f.getName(), p);
                }
            } catch (IllegalAccessException ignored) {
              
            }
        }
        return out;
    }

    public static ModelPart find(EntityModel<?> model, String path) {
        String[] segs = path.split("\\.");
        Map<String, ModelPart> top = topLevel(model);
        ModelPart cur = top.get(segs[0]);
        if (cur == null) cur = deepFind(top.values(), segs[0], new IdentityHashMap<>());
        if (cur == null) return null;
        for (int i = 1; i < segs.length; i++) {
            cur = children(cur).get(segs[i]);
            if (cur == null) return null;
        }
        return cur;
    }
 
    public static List<ModelPart> ancestors(EntityModel<?> model, ModelPart target) {
        for (ModelPart top : new LinkedHashSet<>(topLevel(model).values())) {
            List<ModelPart> trail = new ArrayList<>();
            if (pathTo(top, target, trail)) {
                trail.remove(trail.size() - 1);
                return trail;
            }
        }
        return List.of();
    }

    public static boolean isDirectChild(ModelPart parent, ModelPart child) {
        for (ModelPart c : children(parent).values()) if (c == child) return true;
        return false;
    }

    private static boolean pathTo(ModelPart cur, ModelPart target, List<ModelPart> trail) {
        trail.add(cur);
        if (cur == target) return true;
        for (ModelPart c : children(cur).values()) if (pathTo(c, target, trail)) return true;
        trail.remove(trail.size() - 1);
        return false;
    }

    private static ModelPart deepFind(Collection<ModelPart> parts, String name, Map<ModelPart, Boolean> seen) {
        for (ModelPart p : parts) {
            if (seen.put(p, Boolean.TRUE) != null) continue;
            ModelPart direct = children(p).get(name);
            if (direct != null) return direct;
        }
        for (ModelPart p : parts) {
            ModelPart r = deepFind(children(p).values(), name, seen);
            if (r != null) return r;
        }
        return null;
    }

    private static List<Field> fields(Class<?> type) {
        return FIELDS.computeIfAbsent(type, t -> {
            List<Field> list = new ArrayList<>();
            for (Class<?> c = t; c != null && c != Object.class; c = c.getSuperclass()) {
                for (Field f : c.getDeclaredFields()) {
                    if (Modifier.isStatic(f.getModifiers()) || f.getType() != ModelPart.class) continue;
                    try {
                        f.setAccessible(true);
                        list.add(f);
                    } catch (RuntimeException ignored) {
                    
                    }
                }
            }
            return list;
        });
    }

    static String snake(String camel) {
        StringBuilder sb = new StringBuilder();
        for (char ch : camel.toCharArray()) {
            if (Character.isUpperCase(ch)) {
                sb.append('_').append(Character.toLowerCase(ch));
            } else {
                sb.append(ch);
            }
        }
        return sb.toString();
    }
}
