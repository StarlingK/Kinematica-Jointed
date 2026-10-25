// everything a node renderer needs: node positions, buffers, light and a smooth curve through the nodes
package k.jointed.client.render;

import com.mojang.blaze3d.vertex.PoseStack;
import k.jointed.core.curve.ChainCurve;
import k.jointed.core.math.Quat;
import k.jointed.core.math.V3;
import net.minecraft.client.renderer.MultiBufferSource;
import org.joml.Quaternionf;

import java.util.HashMap;
import java.util.Map;

public final class ChainRenderContext {
    private final PoseStack poseStack;
    private final MultiBufferSource buffers;
    private final V3[] points;
    private final float partialTick;
    private final int light;
    private final int overlay;
    private final Map<Integer, ChainCurve.Sample[]> curves = new HashMap<>();

    public final Map<String, Object> data = new HashMap<>();

    public ChainRenderContext(PoseStack poseStack, MultiBufferSource buffers, V3[] points, float partialTick,
                              int light, int overlay) {
        this.poseStack = poseStack;
        this.buffers = buffers;
        this.points = points;
        this.partialTick = partialTick;
        this.light = light;
        this.overlay = overlay;
    }

    public PoseStack poseStack() {
        return poseStack;
    }

    public MultiBufferSource buffers() {
        return buffers;
    }

    // joint origins followed by the tip, in the current pose-stack space
    public V3[] points() {
        return points;
    }

    public int nodeCount() {
        return points.length;
    }

    public float partialTick() {
        return partialTick;
    }

    public int light() {
        return light;
    }

    public int overlay() {
        return overlay;
    }

    public ChainCurve.Sample[] samples(int subdivisions) {
        return curves.computeIfAbsent(subdivisions, s -> ChainCurve.sample(points, s, V3.Z));
    }

    // t is the fraction of the curve length, 0 = root and 1 = tip
    public ChainCurve.Sample at(double t) {
        return ChainCurve.at(samples(8), t);
    }

    public double length() {
        return ChainCurve.length(samples(8));
    }

    // moves the pose stack onto the sample with +Y along the chain, call popFrame() after
    public void pushFrame(ChainCurve.Sample s) {
        poseStack.pushPose();
        poseStack.translate(s.pos.x, s.pos.y, s.pos.z);
        Quat q = ChainCurve.frameRotation(s);
        poseStack.mulPose(new Quaternionf((float) q.x, (float) q.y, (float) q.z, (float) q.w));
    }

    public void popFrame() {
        poseStack.popPose();
    }
}
