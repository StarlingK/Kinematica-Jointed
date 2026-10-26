// a chain that lives in the world instead of on a model, for tentacles, ropes, cranes, anything free standing
package k.jointed.client.render;

import k.jointed.core.ProceduralChain;

import java.util.function.Consumer;

public final class WorldChain {
    public final ProceduralChain chain;
    public NodeRenderer renderer;
    // runs before every tick, set the root and the target here
    public Consumer<WorldChain> onTick;
    private boolean removed;

    public WorldChain(ProceduralChain chain, NodeRenderer renderer) {
        this.chain = chain;
        this.renderer = renderer;
    }

    public WorldChain onTick(Consumer<WorldChain> onTick) {
        this.onTick = onTick;
        return this;
    }

    public void remove() {
        removed = true;
    }

    public boolean isRemoved() {
        return removed;
    }
}
