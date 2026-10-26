// textured tube along the smooth curve through the nodes
package k.jointed.client.render;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import k.jointed.core.curve.ChainCurve;
import k.jointed.core.math.V3;
import net.minecraft.client.renderer.LightTexture;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.resources.ResourceLocation;

import java.util.function.Function;

public final class TubeRenderer implements NodeRenderer {
    private final ResourceLocation texture;
    private final int sides;
    private final int subdivisions;
    private final RadiusProfile radius;
    private final int colorStart;
    private final int colorEnd;
    private final float uScale;
    private final float vScale;
    private final boolean caps;
    private final boolean fullbright;
    private final double twistPerBlock;
    private final Function<ResourceLocation, RenderType> renderType;

    private TubeRenderer(Builder b) {
        this.texture = b.texture;
        this.sides = b.sides;
        this.subdivisions = b.subdivisions;
        this.radius = b.radius;
        this.colorStart = b.colorStart;
        this.colorEnd = b.colorEnd;
        this.uScale = b.uScale;
        this.vScale = b.vScale;
        this.caps = b.caps;
        this.fullbright = b.fullbright;
        this.twistPerBlock = b.twistPerBlock;
        this.renderType = b.renderType;
    }

    public static Builder builder(ResourceLocation texture) {
        return new Builder(texture);
    }

    public static final class Builder {
        private final ResourceLocation texture;
        private int sides = 8;
        private int subdivisions = 4;
        private RadiusProfile radius = RadiusProfile.constant(0.05);
        private int colorStart = 0xFFFFFFFF;
        private int colorEnd = 0xFFFFFFFF;
        private float uScale = 1f;
        private float vScale = 1f;
        private boolean caps = true;
        private boolean fullbright = false;
        private double twistPerBlock = 0;
        private Function<ResourceLocation, RenderType> renderType = RenderType::entityCutoutNoCull;

        private Builder(ResourceLocation texture) {
            this.texture = texture;
        }

        public Builder sides(int v) {
            sides = Math.max(3, v);
            return this;
        }

        public Builder subdivisions(int v) {
            subdivisions = Math.max(1, v);
            return this;
        }

        public Builder radius(RadiusProfile v) {
            radius = v;
            return this;
        }

        public Builder colors(int start, int end) {
            colorStart = start;
            colorEnd = end;
            return this;
        }

        // u repeats the texture around the tube, v repeats it per block of length
        public Builder uvScale(float u, float v) {
            uScale = u;
            vScale = v;
            return this;
        }

        public Builder caps(boolean v) {
            caps = v;
            return this;
        }

        public Builder fullbright(boolean v) {
            fullbright = v;
            return this;
        }

        public Builder twist(double radiansPerBlock) {
            twistPerBlock = radiansPerBlock;
            return this;
        }

        public Builder renderType(Function<ResourceLocation, RenderType> v) {
            renderType = v;
            return this;
        }

        public TubeRenderer build() {
            return new TubeRenderer(this);
        }
    }

    @Override
    public void render(ChainRenderContext ctx) {
        if (ctx.nodeCount() < 2) return;
        ChainCurve.Sample[] s = ctx.samples(subdivisions);
        VertexConsumer vc = ctx.buffers().getBuffer(renderType.apply(texture));
        PoseStack.Pose pose = ctx.poseStack().last();
        int light = fullbright ? LightTexture.FULL_BRIGHT : ctx.light();
        int overlay = ctx.overlay();

        double totalLength = ChainCurve.length(s);
        V3[][] ring = new V3[s.length][sides + 1];
        V3[][] nrm = new V3[s.length][sides + 1];
        double arc = 0;
        for (int k = 0; k < s.length; k++) {
            if (k > 0) arc += s[k].pos.distance(s[k - 1].pos);
            double r = radius.radius(s[k].t);
            double twist = twistPerBlock * arc;
            for (int j = 0; j <= sides; j++) {
                double phi = Math.PI * 2 * j / sides + twist;
                V3 dir = s[k].normal.scale(Math.cos(phi)).add(s[k].binormal.scale(Math.sin(phi)));
                nrm[k][j] = dir;
                ring[k][j] = s[k].pos.add(dir.scale(r));
            }
        }

        for (int k = 0; k + 1 < s.length; k++) {
            int c0 = RenderUtil.lerpColor(colorStart, colorEnd, s[k].t);
            int c1 = RenderUtil.lerpColor(colorStart, colorEnd, s[k + 1].t);
            float v0 = (float) (s[k].t * totalLength) * vScale;
            float v1 = (float) (s[k + 1].t * totalLength) * vScale;
            for (int j = 0; j < sides; j++) {
                float u0 = uScale * j / sides;
                float u1 = uScale * (j + 1) / sides;
                RenderUtil.vertex(vc, pose, ring[k][j], nrm[k][j], u0, v0, c0, light, overlay);
                RenderUtil.vertex(vc, pose, ring[k + 1][j], nrm[k + 1][j], u0, v1, c1, light, overlay);
                RenderUtil.vertex(vc, pose, ring[k + 1][j + 1], nrm[k + 1][j + 1], u1, v1, c1, light, overlay);
                RenderUtil.vertex(vc, pose, ring[k][j + 1], nrm[k][j + 1], u1, v0, c0, light, overlay);
            }
        }

        if (caps) {
            cap(vc, pose, s[0], ring[0], s[0].tangent.negate(), colorStart, light, overlay);
            int last = s.length - 1;
            cap(vc, pose, s[last], ring[last], s[last].tangent, colorEnd, light, overlay);
        }
    }

    // fan of degenerate quads from the centre to the ring
    private void cap(VertexConsumer vc, PoseStack.Pose pose, ChainCurve.Sample end, V3[] ring, V3 normal, int color,
                     int light, int overlay) {
        for (int j = 0; j < sides; j++) {
            RenderUtil.vertex(vc, pose, end.pos, normal, 0.5f, 0.5f, color, light, overlay);
            RenderUtil.vertex(vc, pose, ring[j], normal, 0, 0, color, light, overlay);
            RenderUtil.vertex(vc, pose, ring[j + 1], normal, 1, 0, color, light, overlay);
            RenderUtil.vertex(vc, pose, end.pos, normal, 0.5f, 0.5f, color, light, overlay);
        }
    }
}
