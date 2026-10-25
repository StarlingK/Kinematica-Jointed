// ticks and draws all world chains, chain positions are absolute world coordinates
package k.jointed.client.render;

import com.mojang.blaze3d.vertex.PoseStack;
import k.jointed.Jointed;
import k.jointed.core.math.V3;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.LevelRenderer;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.texture.OverlayTexture;
import net.minecraft.core.BlockPos;
import net.minecraft.world.phys.Vec3;
import net.neoforged.neoforge.client.event.ClientTickEvent;
import net.neoforged.neoforge.client.event.RenderLevelStageEvent;

import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;

public final class WorldChains {
    private WorldChains() {}

    private static final List<WorldChain> CHAINS = new CopyOnWriteArrayList<>();

    public static void add(WorldChain c) {
        CHAINS.add(c);
    }

    public static void remove(WorldChain c) {
        CHAINS.remove(c);
    }

    public static void clear() {
        CHAINS.clear();
    }

    public static int count() {
        return CHAINS.size();
    }

    public static void onClientTick(ClientTickEvent.Post event) {
        if (Minecraft.getInstance().level == null) return;
        for (WorldChain c : CHAINS) {
            if (c.isRemoved()) {
                CHAINS.remove(c);
                continue;
            }
            try {
                if (c.onTick != null) c.onTick.accept(c);
                c.chain.tick(0.05);
            } catch (Throwable t) {
                Jointed.LOGGER.error("world chain failed and was removed", t);
                CHAINS.remove(c);
            }
        }
    }

    public static void onRenderLevel(RenderLevelStageEvent event) {
        if (event.getStage() != RenderLevelStageEvent.Stage.AFTER_ENTITIES || CHAINS.isEmpty()) return;
        Minecraft mc = Minecraft.getInstance();
        if (mc.level == null) return;
        float partial = event.getPartialTick().getGameTimeDeltaPartialTick(false);
        Vec3 cam = event.getCamera().getPosition();
        PoseStack stack = event.getPoseStack();
        MultiBufferSource.BufferSource buffers = mc.renderBuffers().bufferSource();

        for (WorldChain c : CHAINS) {
            try {
                V3[] world = c.chain.points(partial);
                // camera relative so the pose stack needs no translation
                V3[] pts = new V3[world.length];
                for (int i = 0; i < world.length; i++) pts[i] = world[i].add(-cam.x, -cam.y, -cam.z);
                int light = LevelRenderer.getLightColor(mc.level, BlockPos.containing(world[0].x, world[0].y, world[0].z));
                if (c.renderer != null) {
                    c.renderer.render(new ChainRenderContext(stack, buffers, pts, partial, light, OverlayTexture.NO_OVERLAY));
                }
                if (DebugState.enabled) DebugRenderer.chain(stack, buffers, pts, null, null);
            } catch (Throwable t) {
                Jointed.LOGGER.error("world chain render failed and was removed", t);
                CHAINS.remove(c);
            }
        }
        buffers.endBatch();
    }
}
