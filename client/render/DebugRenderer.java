// draws bones, joints, target and pole as colored lines
package k.jointed.client.render;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import k.jointed.core.math.V3;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.RenderType;

public final class DebugRenderer {
    private DebugRenderer() {}

    public static final int BONE = 0xFF40E0FF;
    public static final int JOINT = 0xFFFFD040;
    public static final int TARGET = 0xFFFF4060;
    public static final int POLE = 0xFF80FF60;

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

    public static void chain(PoseStack stack, MultiBufferSource buffers, V3[] points, V3 target, V3 pole) {
        VertexConsumer vc = buffers.getBuffer(RenderType.lines());
        PoseStack.Pose pose = stack.last();
        for (int i = 0; i + 1 < points.length; i++) line(vc, pose, points[i], points[i + 1], BONE);
        for (V3 p : points) cross(vc, pose, p, 0.03, JOINT);
        if (target != null) cross(vc, pose, target, 0.06, TARGET);
        if (pole != null) {
            cross(vc, pose, pole, 0.05, POLE);
            line(vc, pose, points[points.length / 2], pole, POLE);
        }
    }
}
