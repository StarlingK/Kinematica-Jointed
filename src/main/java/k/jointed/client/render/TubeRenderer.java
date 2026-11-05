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
        VertexConsumer vc = ctx.buffers().getBuffer(RenderUtil.type(renderType, texture));
        PoseStack.Pose pose = ctx.poseStack().last();
        int light = fullbright ? LightTexture.FULL_BRIGHT : ctx.light();
        int overlay = ctx.overlay();

        double totalLength = ChainCurve.length(s);
        int stride = (sides + 1) * 3;
        double[] prevPos = new double[stride], curPos = new double[stride];
        double[] prevNrm = new double[stride], curNrm = new double[stride];
        double[] firstPos = new double[stride];
        double arc = 0;
        for (int k = 0; k < s.length; k++) {
            if (k > 0) arc += s[k].pos.distance(s[k - 1].pos);
            double r = radius.radius(s[k].t);
            double twist = twistPerBlock * arc;
            V3 nm = s[k].normal, bn = s[k].binormal, sp = s[k].pos;
            for (int j = 0; j <= sides; j++) {
                double phi = Math.PI * 2 * j / sides + twist;
                double cs = Math.cos(phi), sn = Math.sin(phi);
                double dx = nm.x * cs + bn.x * sn;
                double dy = nm.y * cs + bn.y * sn;
                double dz = nm.z * cs + bn.z * sn;
                int o = j * 3;
                curNrm[o] = dx;
                curNrm[o + 1] = dy;
                curNrm[o + 2] = dz;
                curPos[o] = sp.x + dx * r;
                curPos[o + 1] = sp.y + dy * r;
                curPos[o + 2] = sp.z + dz * r;
            }
            if (k == 0) {
                System.arraycopy(curPos, 0, firstPos, 0, stride);
            } else {
                int c0 = RenderUtil.ghost(RenderUtil.lerpColor(colorStart, colorEnd, s[k - 1].t));
                int c1 = RenderUtil.ghost(RenderUtil.lerpColor(colorStart, colorEnd, s[k].t));
                float v0 = (float) (s[k - 1].t * totalLength) * vScale;
                float v1 = (float) (s[k].t * totalLength) * vScale;
                for (int j = 0; j < sides; j++) {
                    float u0 = uScale * j / sides;
                    float u1 = uScale * (j + 1) / sides;
                    int a = j * 3, b = a + 3;
                    RenderUtil.vertex(vc, pose, prevPos[a], prevPos[a + 1], prevPos[a + 2], prevNrm[a], prevNrm[a + 1], prevNrm[a + 2], u0, v0, c0, light, overlay);
                    RenderUtil.vertex(vc, pose, curPos[a], curPos[a + 1], curPos[a + 2], curNrm[a], curNrm[a + 1], curNrm[a + 2], u0, v1, c1, light, overlay);
                    RenderUtil.vertex(vc, pose, curPos[b], curPos[b + 1], curPos[b + 2], curNrm[b], curNrm[b + 1], curNrm[b + 2], u1, v1, c1, light, overlay);
                    RenderUtil.vertex(vc, pose, prevPos[b], prevPos[b + 1], prevPos[b + 2], prevNrm[b], prevNrm[b + 1], prevNrm[b + 2], u1, v0, c0, light, overlay);
                }
            }
            double[] t = prevPos; prevPos = curPos; curPos = t;
            t = prevNrm; prevNrm = curNrm; curNrm = t;
        }

        if (caps) {
            cap(vc, pose, s[0], firstPos, s[0].tangent.negate(), RenderUtil.ghost(colorStart), light, overlay);
            int last = s.length - 1;
            cap(vc, pose, s[last], prevPos, s[last].tangent, RenderUtil.ghost(colorEnd), light, overlay);
        }
    }

    // fan of degenerate quads from the centre to the ring
    private void cap(VertexConsumer vc, PoseStack.Pose pose, ChainCurve.Sample end, double[] ring, V3 normal, int color,
                     int light, int overlay) {
        V3 c = end.pos;
        for (int j = 0; j < sides; j++) {
            int a = j * 3, b = a + 3;
            RenderUtil.vertex(vc, pose, c.x, c.y, c.z, normal.x, normal.y, normal.z, 0.5f, 0.5f, color, light, overlay);
            RenderUtil.vertex(vc, pose, ring[a], ring[a + 1], ring[a + 2], normal.x, normal.y, normal.z, 0, 0, color, light, overlay);
            RenderUtil.vertex(vc, pose, ring[b], ring[b + 1], ring[b + 2], normal.x, normal.y, normal.z, 1, 0, color, light, overlay);
            RenderUtil.vertex(vc, pose, c.x, c.y, c.z, normal.x, normal.y, normal.z, 0.5f, 0.5f, color, light, overlay);
        }
    }
}
