// lets a block entity own a chain, ticks it from the level clock and draws it inside the block entity renderer
package k.jointed.client.render;

import com.mojang.blaze3d.vertex.PoseStack;
import k.jointed.core.ProceduralChain;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.world.level.Level;

import java.util.function.Consumer;

public final class BlockChainHost {
    private final ProceduralChain chain;
    private final NodeRenderer renderer;
    // runs before every tick, chain positions are in block local space
    public Consumer<ProceduralChain> onTick;
    private long lastTick = Long.MIN_VALUE;

    public BlockChainHost(ProceduralChain chain, NodeRenderer renderer) {
        this.chain = chain;
        this.renderer = renderer;
    }

    public ProceduralChain chain() {
        return chain;
    }

    // call from BlockEntityRenderer.render, the pose stack is already at the block's corner
    public void render(Level level, PoseStack stack, MultiBufferSource buffers, float partial, int light, int overlay) {
        long now = level.getGameTime();
        if (lastTick == Long.MIN_VALUE) {
            lastTick = now;
            chain.snap();
        }
        // capped so a long pause does not run hundreds of ticks in one frame
        long steps = Math.min(4, now - lastTick);
        for (long i = 0; i < steps; i++) {
            if (onTick != null) onTick.accept(chain);
            chain.tick(0.05);
        }
        lastTick = now;

        var points = chain.points(partial);
        renderer.render(new ChainRenderContext(stack, buffers, points, partial, light, overlay));
        if (DebugState.enabled) DebugRenderer.chain(stack, buffers, points, null, null);
    }
}
