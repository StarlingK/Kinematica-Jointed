// plug-in point for custom chain rendering, gets the node positions and draws whatever it wants
package k.jointed.client.render;

@FunctionalInterface
public interface NodeRenderer {
    void render(ChainRenderContext ctx);
}
