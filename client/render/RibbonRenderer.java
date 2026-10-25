// flat two-sided strip along the curve
package k.jointed.client.render;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import k.jointed.core.curve.ChainCurve;
import k.jointed.core.math.V3;
import net.minecraft.client.renderer.LightTexture;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.resources.ResourceLocation;

import java.util.function.Function;

public final class RibbonRenderer implements NodeRenderer {
    private final ResourceLocation texture;
    private final RadiusProfile halfWidth;
    private final int subdivisions;
    private final int colorStart;
    private final int colorEnd;
    private final float vScale;
    private final boolean fullbright;
    private final Function<ResourceLocation, RenderType> renderType;

    public RibbonRenderer(ResourceLocation texture, RadiusProfile halfWidth, int subdivisions, int colorStart,
                          int colorEnd, float vScale, boolean fullbright,
                          Function<ResourceLocation, RenderType> renderType) {
        this.texture = texture;
        this.halfWidth = halfWidth;
        this.subdivisions = Math.max(1, subdivisions);
        this.colorStart = colorStart;
        this.colorEnd = colorEnd;
        this.vScale = vScale;
        this.fullbright = fullbright;
        this.renderType = renderType;
    }

    public RibbonRenderer(ResourceLocation texture, RadiusProfile halfWidth) {
        this(texture, halfWidth, 4, 0xFFFFFFFF, 0xFFFFFFFF, 1f, false, RenderType::entityCutoutNoCull);
    }

    @Override
    public void render(ChainRenderContext ctx) {
        if (ctx.nodeCount() < 2) return;
        ChainCurve.Sample[] s = ctx.samples(subdivisions);
        VertexConsumer vc = ctx.buffers().getBuffer(renderType.apply(texture));
        PoseStack.Pose pose = ctx.poseStack().last();
        int light = fullbright ? LightTexture.FULL_BRIGHT : ctx.light();
        double total = ChainCurve.length(s);

        for (int k = 0; k + 1 < s.length; k++) {
            ChainCurve.Sample a = s[k];
            ChainCurve.Sample b = s[k + 1];
            double wa = halfWidth.radius(a.t);
            double wb = halfWidth.radius(b.t);
            V3 a0 = a.pos.add(a.binormal.scale(-wa));
            V3 a1 = a.pos.add(a.binormal.scale(wa));
            V3 b0 = b.pos.add(b.binormal.scale(-wb));
            V3 b1 = b.pos.add(b.binormal.scale(wb));
            V3 n = a.normal;
            int ca = RenderUtil.lerpColor(colorStart, colorEnd, a.t);
            int cb = RenderUtil.lerpColor(colorStart, colorEnd, b.t);
            float va = (float) (a.t * total) * vScale;
            float vb = (float) (b.t * total) * vScale;
            // drawn twice with flipped normals so it shows from both sides
            RenderUtil.vertex(vc, pose, a0, n, 0, va, ca, light, ctx.overlay());
            RenderUtil.vertex(vc, pose, b0, n, 0, vb, cb, light, ctx.overlay());
            RenderUtil.vertex(vc, pose, b1, n, 1, vb, cb, light, ctx.overlay());
            RenderUtil.vertex(vc, pose, a1, n, 1, va, ca, light, ctx.overlay());
            V3 m = n.negate();
            RenderUtil.vertex(vc, pose, a1, m, 1, va, ca, light, ctx.overlay());
            RenderUtil.vertex(vc, pose, b1, m, 1, vb, cb, light, ctx.overlay());
            RenderUtil.vertex(vc, pose, b0, m, 0, vb, cb, light, ctx.overlay());
            RenderUtil.vertex(vc, pose, a0, m, 0, va, ca, light, ctx.overlay());
        }
    }
}
