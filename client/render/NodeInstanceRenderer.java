// draws a block, item or custom callback at every node or at evenly spaced points along the chain
package k.jointed.client.render;

import com.mojang.blaze3d.vertex.PoseStack;
import k.jointed.core.curve.ChainCurve;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.block.BlockRenderDispatcher;
import net.minecraft.world.item.ItemDisplayContext;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.state.BlockState;
import org.joml.Quaternionf;

public final class NodeInstanceRenderer implements NodeRenderer {

    @FunctionalInterface
    public interface Instance {
        void draw(ChainRenderContext ctx, int index, double t);
    }

    public enum Placement {NODES, EVEN}

    private final Instance instance;
    private final Placement placement;
    private final int count;
    private final double scale;

    private NodeInstanceRenderer(Instance instance, Placement placement, int count, double scale) {
        this.instance = instance;
        this.placement = placement;
        this.count = count;
        this.scale = scale;
    }

    public static NodeInstanceRenderer atNodes(Instance instance) {
        return new NodeInstanceRenderer(instance, Placement.NODES, 0, 1);
    }

    public static NodeInstanceRenderer evenly(int count, Instance instance) {
        return new NodeInstanceRenderer(instance, Placement.EVEN, count, 1);
    }

    public static Instance block(BlockState state, double size) {
        return (ctx, i, t) -> {
            PoseStack ps = ctx.poseStack();
            ps.scale((float) size, (float) size, (float) size);
            ps.translate(-0.5, -0.5, -0.5);
            BlockRenderDispatcher d = Minecraft.getInstance().getBlockRenderer();
            d.renderSingleBlock(state, ps, ctx.buffers(), ctx.light(), ctx.overlay());
        };
    }

    public static Instance item(ItemStack stack, double size) {
        return (ctx, i, t) -> {
            PoseStack ps = ctx.poseStack();
            ps.scale((float) size, (float) size, (float) size);
            Minecraft.getInstance().getItemRenderer().renderStatic(stack, ItemDisplayContext.FIXED, ctx.light(),
                    ctx.overlay(), ps, ctx.buffers(), Minecraft.getInstance().level, 0);
        };
    }

    @Override
    public void render(ChainRenderContext ctx) {
        if (ctx.nodeCount() < 2) return;
        if (placement == Placement.NODES) {
            ChainCurve.Sample[] s = ctx.samples(1);
            int n = ctx.nodeCount();
            for (int i = 0; i < n; i++) {
                // with 1 subdivision, sample i is exactly node i
                ctx.pushFrame(s[Math.min(i, s.length - 1)]);
                instance.draw(ctx, i, n == 1 ? 0 : i / (double) (n - 1));
                ctx.popFrame();
            }
        } else {
            for (int i = 0; i < count; i++) {
                double t = count == 1 ? 0.5 : i / (double) (count - 1);
                ctx.pushFrame(ctx.at(t));
                instance.draw(ctx, i, t);
                ctx.popFrame();
            }
        }
    }
}
