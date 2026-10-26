// small vertex helpers shared by the built-in renderers
package k.jointed.client.render;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import k.jointed.core.math.V3;

public final class RenderUtil {
    private RenderUtil() {}

    public static void vertex(VertexConsumer vc, PoseStack.Pose pose, V3 p, V3 normal, float u, float v, int argb,
                              int light, int overlay) {
        vc.addVertex(pose, (float) p.x, (float) p.y, (float) p.z)
                .setColor(argb)
                .setUv(u, v)
                .setOverlay(overlay)
                .setLight(light)
                .setNormal(pose, (float) normal.x, (float) normal.y, (float) normal.z);
    }

    // triangles are done by repeating a corner
    public static void quad(VertexConsumer vc, PoseStack.Pose pose, V3 a, V3 b, V3 c, V3 d, V3 normal, int argb,
                            int light, int overlay) {
        vertex(vc, pose, a, normal, 0, 0, argb, light, overlay);
        vertex(vc, pose, b, normal, 1, 0, argb, light, overlay);
        vertex(vc, pose, c, normal, 1, 1, argb, light, overlay);
        vertex(vc, pose, d, normal, 0, 1, argb, light, overlay);
    }

    public static void cube(VertexConsumer vc, PoseStack.Pose pose, V3 c, double h, int argb, int light, int overlay) {
        V3[] v = new V3[8];
        for (int i = 0; i < 8; i++) {
            v[i] = c.add((i & 1) == 0 ? -h : h, (i & 2) == 0 ? -h : h, (i & 4) == 0 ? -h : h);
        }
        quad(vc, pose, v[0], v[1], v[3], v[2], V3.NEG_Z, argb, light, overlay);
        quad(vc, pose, v[4], v[6], v[7], v[5], V3.Z, argb, light, overlay);
        quad(vc, pose, v[0], v[2], v[6], v[4], V3.NEG_X, argb, light, overlay);
        quad(vc, pose, v[1], v[5], v[7], v[3], V3.X, argb, light, overlay);
        quad(vc, pose, v[0], v[4], v[5], v[1], V3.NEG_Y, argb, light, overlay);
        quad(vc, pose, v[2], v[3], v[7], v[6], V3.Y, argb, light, overlay);
    }

    public static int argb(int a, int r, int g, int b) {
        return (a & 255) << 24 | (r & 255) << 16 | (g & 255) << 8 | (b & 255);
    }

    public static int lerpColor(int c0, int c1, double t) {
        int a = (int) Math.round(((c0 >>> 24) & 255) * (1 - t) + ((c1 >>> 24) & 255) * t);
        int r = (int) Math.round(((c0 >> 16) & 255) * (1 - t) + ((c1 >> 16) & 255) * t);
        int g = (int) Math.round(((c0 >> 8) & 255) * (1 - t) + ((c1 >> 8) & 255) * t);
        int b = (int) Math.round((c0 & 255) * (1 - t) + (c1 & 255) * t);
        return argb(a, r, g, b);
    }


    public static int ghost(int color) {
        if (!DebugState.enabled) return color;
        int a = (int) (((color >>> 24) & 255) * 0.35);
        return (a << 24) | (color & 0xFFFFFF);
    }

    public static net.minecraft.client.renderer.RenderType type(java.util.function.Function<net.minecraft.resources.ResourceLocation, net.minecraft.client.renderer.RenderType> normal, net.minecraft.resources.ResourceLocation texture) {
        return DebugState.enabled ? net.minecraft.client.renderer.RenderType.entityTranslucent(texture) : normal.apply(texture);
    }
}
