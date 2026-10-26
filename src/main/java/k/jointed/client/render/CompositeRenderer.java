// runs several node renderers in order on the same chain
package k.jointed.client.render;

import java.util.List;

public record CompositeRenderer(List<NodeRenderer> parts) implements NodeRenderer {
    public CompositeRenderer {
        parts = List.copyOf(parts);
    }

    public static CompositeRenderer of(NodeRenderer... renderers) {
        return new CompositeRenderer(List.of(renderers));
    }

    @Override
    public void render(ChainRenderContext ctx) {
        for (NodeRenderer r : parts) r.render(ctx);
    }
}
