# Rendering

The solver only produces **node positions**. How those nodes look is a separate, replaceable piece called a `NodeRenderer`. Because every renderer receives the same thing (an array of positions), you can change the texture, the shape, the render type, or the whole look of a chain without touching the IK.

```java
@FunctionalInterface
public interface NodeRenderer {
    void render(ChainRenderContext ctx);
}
```

You can attach a renderer to an entity limb, a world chain or a block entity chain, or call it yourself from your own renderer. This page covers the built-in renderers first, then how to write your own.

## ChainRenderContext

What a renderer is given:

| Member | Meaning |
| --- | --- |
| `points()`, `nodeCount()` | The node positions: joint origins followed by the tip. Do not modify the array, other renderers share it. |
| `poseStack()` | The pose stack, already in the same space as `points()`. Use `poseStack().last()` for the current pose. |
| `buffers()` | The `MultiBufferSource`. Ask it for a buffer with the `RenderType` you want. |
| `light()`, `overlay()` | Packed light and overlay for the thing being drawn. |
| `partialTick()` | Partial tick. |
| `samples(n)` | A smooth curve through the nodes with `n` samples per bone. Cached per `n`, so several renderers on the same chain share the work. |
| `at(t)` | A sample at fraction `t` (0 = root, 1 = tip) of the curve length. |
| `length()` | Curve length. |
| `pushFrame(sample)` / `popFrame()` | Moves the pose stack onto a sample: +Y along the chain, +X = normal, +Z = binormal. Always pair them. |
| `data` | A free-form map for sharing work between renderers of one chain. |

Each `ChainCurve.Sample` has `pos`, `tangent`, `normal`, `binormal`, `t` and `bone`. The frames do not twist along the chain, so a texture or a row of spikes will not spin as the chain bends.

### Where the points are

Which space the points are in depends on who calls the renderer:

| Host | Points are | Notes |
| --- | --- | --- |
| Entity limb (`LimbDefinition.renderer`) | Blocks, in the entity's model space | **+Y points down** in this space. Never use `V3.Y` as "up". Derive directions from the nodes or from the sample frames. |
| `WorldChain` | Blocks, relative to the camera | You do not need to translate anything. |
| `BlockChainHost` | Blocks, relative to the block's corner | The pose stack is already at the block. |
| Your own call | Whatever you pass in | Match the pose stack you pass. |

If you only use positions and sample frames, as all built-in renderers do, you never need to care about the space.

## Built-in renderers

### TubeRenderer

A textured tube along the curve.

```java
NodeRenderer tube = TubeRenderer.builder(ResourceLocation.parse("yourmod:textures/entity/tendril.png"))
        .sides(10)
        .subdivisions(4)
        .radius(RadiusProfile.tentacle(0.09, 0.02))
        .colors(0xFFFFFFFF, 0xFFFF6060)    // ARGB tint from root to tip
        .uvScale(2f, 3f)
        .build();
```

| Builder method | Default | Meaning |
| --- | --- | --- |
| `sides(n)` | 8 | Sides of the tube, minimum 3. |
| `subdivisions(n)` | 4 | Curve samples per bone. Higher is smoother. |
| `radius(profile)` | constant 0.05 | A `RadiusProfile`, radius as a function of `t`. |
| `colors(start, end)` | white, white | ARGB tint blended along the chain. |
| `uvScale(u, v)` | 1, 1 | `u` repeats the texture around the tube, `v` repeats it per block of length. The texture repeats when values go past 1. |
| `caps(bool)` | true | Close both ends with a disc. |
| `fullbright(bool)` | false | Ignore lighting. |
| `twist(radiansPerBlock)` | 0 | Spiral the texture seam around the tube. |
| `renderType(function)` | `entityCutoutNoCull` | Takes the texture and returns the `RenderType` to draw with. |

`RadiusProfile` is a one-method interface: `constant(r)`, `taper(root, tip)`, `tentacle(root, tip)` (thick base, slight bulge, thin tip), or your own lambda `t -> 0.05 + 0.02 * Math.sin(t * 20)`.

### RibbonRenderer

A flat strip, drawn from both sides. Good for blades, whips, tails and capes.

```java
NodeRenderer blade = new RibbonRenderer(texture, RadiusProfile.taper(0.12, 0.01));
```

`RibbonRenderer(texture, halfWidth)` uses the defaults. The full constructor is `(texture, halfWidth, subdivisions, colorStart, colorEnd, vScale, fullbright, renderType)`. The width is spread along the binormal.

### NodeInstanceRenderer

Draws something at every node, or at evenly spaced points along the curve. The pose stack is already moved onto the spot when your code runs.

```java
// a baked block at every joint
NodeRenderer joints = NodeInstanceRenderer.atNodes(
        NodeInstanceRenderer.block(Blocks.IRON_BLOCK.defaultBlockState(), 0.12));

// 6 items spread along the curve
NodeRenderer charms = NodeInstanceRenderer.evenly(6,
        NodeInstanceRenderer.item(new ItemStack(Items.AMETHYST_SHARD), 0.2));

// anything you like
NodeRenderer custom = NodeInstanceRenderer.atNodes((ctx, index, t) -> {
    VertexConsumer vc = ctx.buffers().getBuffer(RenderType.entityCutoutNoCull(JOINT_TEXTURE));
    RenderUtil.cube(vc, ctx.poseStack().last(), V3.ZERO, 0.06, 0xFFFFFFFF, ctx.light(), ctx.overlay());
});
```

The callback gets the instance `index` and `t` (0 to 1 along the chain). In the callback, +Y runs along the chain.

### CompositeRenderer

Runs several renderers in order on the same chain.

```java
NodeRenderer look = CompositeRenderer.of(tube, joints, spikes);
```

### DebugRenderer

Draws bones, joints, a target and a pole as lines. It is what `/jointed debug` uses: `DebugRenderer.chain(stack, buffers, points, target, pole)`.

## Different textures, different shapes

Because a renderer is just a function of the node positions, swapping looks is a one-line change.

**A different texture:** build another `TubeRenderer` with another texture. Use a different one per limb, per entity type or per state.

**A different render type** (translucent, glowing, custom shader): pass a function to `renderType`.

```java
TubeRenderer.builder(texture)
        .renderType(RenderType::entityTranslucent)   // or RenderType::eyes for a glow, or your own
        .colors(0x80FFFFFF, 0x00FFFFFF)              // fade out towards the tip
        .build();
```

**A different shape:** write a `NodeRenderer`. The next section shows how.

**Changing the look while the game runs:** `WorldChain.renderer` is a public field, assign a new renderer at any time. For limbs, define the renderer to read your own state:

```java
NodeRenderer reactive = ctx -> (state.isAngry() ? angryTube : calmTube).render(ctx);
```

## Writing your own renderer

A renderer asks for a buffer and writes vertices. `RenderUtil` does the vertex formatting for entity-style render types:

| Helper | What it does |
| --- | --- |
| `vertex(vc, pose, position, normal, u, v, argb, light, overlay)` | One vertex. |
| `quad(vc, pose, a, b, c, d, normal, argb, light, overlay)` | Four vertices with UVs at the corners of the texture. Repeat a corner to make a triangle. |
| `cube(vc, pose, centre, halfSize, argb, light, overlay)` | An axis-aligned cube. |
| `argb(a, r, g, b)`, `lerpColor(c0, c1, t)` | Colors. |

Vertex order matters for lighting normals only if the render type culls faces. The default render type does not cull, so orientation mistakes show up as shading, not as missing faces.

### Example: a row of spikes

Spikes point away from the chain, so they use the sample's `normal`, which is correct in every host.

```java
public final class SpikeRenderer implements NodeRenderer {
    private final ResourceLocation texture;
    private final int count;
    private final double length, halfBase;

    public SpikeRenderer(ResourceLocation texture, int count, double length, double halfBase) {
        this.texture = texture;
        this.count = count;
        this.length = length;
        this.halfBase = halfBase;
    }

    @Override
    public void render(ChainRenderContext ctx) {
        if (ctx.nodeCount() < 2) return;
        VertexConsumer vc = ctx.buffers().getBuffer(RenderType.entityCutoutNoCull(texture));
        PoseStack.Pose pose = ctx.poseStack().last();

        for (int i = 0; i < count; i++) {
            ChainCurve.Sample s = ctx.at((i + 0.5) / count);
            V3 tip = s.pos.add(s.normal.scale(length));
            // square base around the chain, in the plane of tangent and binormal
            V3 a = s.pos.add(s.tangent.scale(halfBase)).add(s.binormal.scale(halfBase));
            V3 b = s.pos.add(s.tangent.scale(-halfBase)).add(s.binormal.scale(halfBase));
            V3 c = s.pos.add(s.tangent.scale(-halfBase)).add(s.binormal.scale(-halfBase));
            V3 d = s.pos.add(s.tangent.scale(halfBase)).add(s.binormal.scale(-halfBase));

            // four triangles, each is a quad whose last two corners are the tip
            face(vc, pose, ctx, a, b, tip);
            face(vc, pose, ctx, b, c, tip);
            face(vc, pose, ctx, c, d, tip);
            face(vc, pose, ctx, d, a, tip);
        }
    }

    private static void face(VertexConsumer vc, PoseStack.Pose pose, ChainRenderContext ctx, V3 p, V3 q, V3 tip) {
        V3 normal = q.sub(p).cross(tip.sub(p)).normalize();
        RenderUtil.quad(vc, pose, p, q, tip, tip, normal, 0xFFFFFFFF, ctx.light(), ctx.overlay());
    }
}
```

Use it like any other renderer, or combine it:

```java
CompositeRenderer.of(tube, new SpikeRenderer(spikeTexture, 12, 0.18, 0.04));
```

### Example: something at fixed spots, in its own frame

When your shape is easier to describe in a local frame (+Y along the chain), use `pushFrame`:

```java
ChainCurve.Sample s = ctx.at(0.5);
ctx.pushFrame(s);
// the pose stack is now at the sample, +Y along the chain, +X normal, +Z binormal
VertexConsumer vc = ctx.buffers().getBuffer(RenderType.entityCutoutNoCull(texture));
RenderUtil.cube(vc, ctx.poseStack().last(), V3.ZERO, 0.05, 0xFFFFFFFF, ctx.light(), ctx.overlay());
ctx.popFrame();
```

### Tips

- Ask for the buffer once per render call, not once per quad.
- `ctx.samples(n)` and `ctx.at(t)` are cached, calling them repeatedly is cheap. Avoid allocating in loops you run many times per frame.
- Overlapping translucent shapes are not depth sorted against each other.
- A renderer may run for many entities per frame. Keep state in the renderer's fields only if it is genuinely shared. Per-chain data belongs in `ctx.data` or in the host object.

## Attaching a renderer

**Entity limb:** `LimbDefinition.builder(...).renderer(r)` or `"renderer"` in JSON. Jointed adds a render layer to every living entity renderer that draws it after the model, through the solved nodes (what you see includes the fade and smoothing).

**World chain:** the second argument of `new WorldChain(chain, renderer)`, see [World and block chains](world-and-block-chains.md).

**Block entity:** the second argument of `new BlockChainHost(chain, renderer)`.

**Anywhere else:** build the context yourself.

```java
V3[] nodes = myChain.points();
myRenderer.render(new ChainRenderContext(poseStack, buffers, nodes, partialTick, packedLight, packedOverlay));
```

This is how you reuse the renderers in an item renderer, a custom entity renderer, or an effect.

## JSON renderers

Limb JSON can include a `"renderer"` object. Built-in types:

| Type | Keys |
| --- | --- |
| `jointed:tube` | `texture` (required), `sides` (8), `subdivisions` (4), `radius` (a number or `[root, tip]`, default 0.05), `color_start`, `color_end` (`"#RRGGBB"` or `"#AARRGGBB"`), `caps` (true), `fullbright` (false) |
| `jointed:ribbon` | `texture` (required), `width` (a number or `[root, tip]`, default `[0.1, 0.02]`), `subdivisions` (4), `color_start`, `color_end`, `fullbright` |

Radius and width are in blocks.

Register your own type so packs can use it:

```java
JointedApi.registerRendererType(
        ResourceLocation.fromNamespaceAndPath("yourmod", "spikes"),
        json -> new SpikeRenderer(
                ResourceLocation.parse(json.get("texture").getAsString()),
                json.has("count") ? json.get("count").getAsInt() : 12, 0.18, 0.04));
```

```json
"renderer": { "type": "yourmod:spikes", "texture": "yourmod:textures/entity/spike.png", "count": 16 }
```

Register before resources load (client setup). Unknown types make the rig file fail to load, with the reason in the log.
