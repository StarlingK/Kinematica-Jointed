package k.jointed.client.render;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import k.jointed.core.math.V3;
import net.minecraft.client.renderer.MultiBufferSource;

public final class DebugRenderer {
    private DebugRenderer() {}

    public static final int BONE_FILL = 0x66B4BCCC;
    public static final int BONE_EDGE = 0xFF1C2230;
    public static final int JOINT = 0xFFFFA030;
    public static final int TARGET = 0xFFFF8020;
    public static final int POLE = 0xFF60D0A0;

    private static final V3 LIGHT = new V3(0.3, 0.8, 0.5).normalize();

    public static void line(VertexConsumer vc, PoseStack.Pose pose, V3 a, V3 b, int argb) {
        V3 n = b.sub(a).normalizeOr(V3.Y);
        vc.addVertex(pose, (float) a.x, (float) a.y, (float) a.z).setColor(argb)
                .setNormal(pose, (float) n.x, (float) n.y, (float) n.z);
        vc.addVertex(pose, (float) b.x, (float) b.y, (float) b.z).setColor(argb)
                .setNormal(pose, (float) n.x, (float) n.y, (float) n.z);
    }

    public static void cross(VertexConsumer vc, PoseStack.Pose pose, V3 p, double size, int argb) {
        line(vc, pose, p.add(-size, 0, 0), p.add(size, 0, 0), argb);
        line(vc, pose, p.add(0, -size, 0), p.add(0, size, 0), argb);
        line(vc, pose, p.add(0, 0, -size), p.add(0, 0, size), argb);
    }

    private static void tri(VertexConsumer vc, PoseStack.Pose pose, V3 a, V3 b, V3 c, int argb) {
        double shade = 0.6 + 0.4 * Math.abs(b.sub(a).cross(c.sub(a)).normalizeOr(V3.Y).dot(LIGHT));
        int r = (int) (((argb >> 16) & 0xFF) * shade);
        int g = (int) (((argb >> 8) & 0xFF) * shade);
        int bl = (int) ((argb & 0xFF) * shade);
        int col = (argb & 0xFF000000) | (r << 16) | (g << 8) | bl;
        for (V3 v : new V3[]{a, b, c}) vc.addVertex(pose, (float) v.x, (float) v.y, (float) v.z).setColor(col);
    }

    private static V3[] waist(V3 head, V3 tail) {
        V3 d = tail.sub(head);
        double len = d.length();
        if (len < 1e-6) return null;
        V3 dir = d.scale(1.0 / len);
        V3 u = dir.cross(Math.abs(dir.y) > 0.95 ? V3.X : V3.Y).normalize();
        V3 v = dir.cross(u);
        double w = len * 0.1;
        V3 mid = head.add(dir.scale(w));
        return new V3[]{mid.add(u.scale(w)), mid.add(v.scale(w)), mid.sub(u.scale(w)), mid.sub(v.scale(w))};
    }

    // head and tail are the points of the octahedron, the waist sits a tenth of the way along like blender's
    private static void boneFaces(VertexConsumer faces, PoseStack.Pose pose, V3 head, V3 tail) {
        V3[] c = waist(head, tail);
        if (c == null) return;
        for (int i = 0; i < 4; i++) {
            V3 n = c[(i + 1) % 4];
            tri(faces, pose, head, c[i], n, BONE_FILL);
            tri(faces, pose, tail, n, c[i], BONE_FILL);
        }
    }

    private static void boneEdges(VertexConsumer lines, PoseStack.Pose pose, V3 head, V3 tail) {
        V3[] c = waist(head, tail);
        if (c == null) return;
        for (int i = 0; i < 4; i++) {
            line(lines, pose, head, c[i], BONE_EDGE);
            line(lines, pose, c[i], tail, BONE_EDGE);
            line(lines, pose, c[i], c[(i + 1) % 4], BONE_EDGE);
        }
    }

    private static void ring(VertexConsumer vc, PoseStack.Pose pose, V3 p, V3 a, V3 b, double r, int argb) {
        int n = 12;
        V3 prev = p.add(a.scale(r));
        for (int i = 1; i <= n; i++) {
            double t = Math.PI * 2 * i / n;
            V3 next = p.add(a.scale(Math.cos(t) * r)).add(b.scale(Math.sin(t) * r));
            line(vc, pose, prev, next, argb);
            prev = next;
        }
    }

    private static void joint(VertexConsumer vc, PoseStack.Pose pose, V3 p, double r, int argb) {
        ring(vc, pose, p, V3.X, V3.Y, r, argb);
        ring(vc, pose, p, V3.Y, V3.Z, r, argb);
        ring(vc, pose, p, V3.X, V3.Z, r, argb);
    }

    private static void dashed(VertexConsumer vc, PoseStack.Pose pose, V3 a, V3 b, int argb) {
        double len = a.distance(b);
        double step = 0.04;
        for (double t = 0; t < len; t += step * 2) {
            line(vc, pose, a.lerp(b, t / len), a.lerp(b, Math.min(t + step, len) / len), argb);
        }
    }

    public static void chain(PoseStack stack, MultiBufferSource buffers, V3[] points, V3 target, V3 pole) {
        PoseStack.Pose pose = stack.last();
        // the buffer source ends the previous render type's buffer when another is requested, so finish faces first
        VertexConsumer faces = buffers.getBuffer(DebugTypes.FACES);
        for (int i = 0; i + 1 < points.length; i++) boneFaces(faces, pose, points[i], points[i + 1]);
        VertexConsumer lines = buffers.getBuffer(DebugTypes.LINES);
        for (int i = 0; i + 1 < points.length; i++) boneEdges(lines, pose, points[i], points[i + 1]);
        for (V3 p : points) joint(lines, pose, p, 0.025, JOINT);
        if (target != null) {
            cross(lines, pose, target, 0.07, TARGET);
            joint(lines, pose, target, 0.045, TARGET);
            dashed(lines, pose, points[points.length - 1], target, TARGET);
        }
        if (pole != null) {
            double s = 0.04;
            V3[] o = {pole.add(s, 0, 0), pole.add(0, s, 0), pole.add(0, 0, s), pole.add(-s, 0, 0), pole.add(0, -s, 0), pole.add(0, 0, -s)};
            for (int i = 0; i < 3; i++) {
                for (int j = 3; j < 6; j++) if (j - 3 != i) line(lines, pose, o[i], o[j], POLE);
            }
            for (int i = 0; i < 3; i++) for (int j = i + 1; j < 3; j++) { line(lines, pose, o[i], o[j], POLE); line(lines, pose, o[i + 3], o[j + 3], POLE); }
            dashed(lines, pose, points[points.length / 2], pole, POLE);
        }
    }
}