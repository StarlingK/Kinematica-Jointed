# World and block chains

Not every chain belongs to an entity model. Tentacles, ropes, cranes and weapons can live on their own. Jointed gives two hosts for them. Both are **client-side visuals**, they are not saved or synced, so drive them from state your mod already syncs (an entity, a block entity, a packet).

Both use `ProceduralChain` ([Core API](core-api.md#proceduralchain)) for the solving and a `NodeRenderer` ([Rendering](rendering.md)) for the look.

## World chains

A `WorldChain` lives at absolute world coordinates. Jointed ticks it every client tick and draws it every frame until you remove it.

```java
IKChain chain = IKChain.straight(10, 0.25, V3.Y, Constraints.coneDeg(35));
ProceduralChain pc = new ProceduralChain(chain);
VerletChain physics = pc.enableDynamics();
physics.stiffness = 0.12;                       // follow the IK pose loosely
physics.gravity = new V3(0, -3, 0);
pc.settings().iterations(12).tolerance(0.01);

NodeRenderer renderer = TubeRenderer.builder(texture)
        .radius(RadiusProfile.tentacle(0.09, 0.02))
        .build();

WorldChain tentacle = new WorldChain(pc, renderer).onTick(c -> {
    LocalPlayer p = Minecraft.getInstance().player;
    if (p == null) return;
    Vec3 eye = p.getEyePosition();
    Vec3 aim = eye.add(p.getLookAngle().scale(5));
    c.chain.root(new V3(eye.x, eye.y - 0.4, eye.z), Quat.IDENTITY);
    c.chain.goal(IKGoal.tip(new V3(aim.x, aim.y, aim.z)));
});

JointedApi.addWorldChain(tentacle);
// later
tentacle.remove();            // or JointedApi.removeWorldChain(tentacle)
```

- `onTick` runs before each tick. Set the root and the goal there.
- If you move the root a long way in one step (teleport), call `chain.snap()` so the drawing does not streak.
- If `onTick` or the renderer throws, the chain is removed and the error is logged. One broken chain does not take the others down.
- All world chains are cleared when the player leaves the world.
- `WorldChain.renderer` is a public field, replace it to change the look.

World chains draw after entities. With `/jointed debug on` they also get bone and joint lines.

## Block entity chains

`BlockChainHost` owns a chain for a block entity and draws it inside your block entity renderer. It ticks the chain from the level's game time, so it keeps correct speed regardless of frame rate, and it does not tick while the game is paused.

Coordinates are **block local**: the pose stack is already at the block's corner, so a root of `(0.5, 1.0, 0.5)` is the middle of the top face.

```java
public class CraneBlockEntity extends BlockEntity {
    private final BlockChainHost host;

    public CraneBlockEntity(BlockPos pos, BlockState state) {
        super(ModBlockEntities.CRANE.get(), pos, state);
        IKChain chain = IKChain.straight(6, 0.6, V3.Y, Constraints.coneDeg(40));
        chain.setRootPosition(new V3(0.5, 1.0, 0.5));
        ProceduralChain pc = new ProceduralChain(chain);
        host = new BlockChainHost(pc, TubeRenderer.builder(TEXTURE).radius(RadiusProfile.constant(0.06)).build());
        host.onTick = c -> c.target(new V3(0.5 + reach, 1.5, 0.5));   // reach: your synced field
    }

    public BlockChainHost chainHost() {
        return host;
    }
}

public class CraneRenderer implements BlockEntityRenderer<CraneBlockEntity> {
    @Override
    public void render(CraneBlockEntity be, float partial, PoseStack stack, MultiBufferSource buffers,
                       int light, int overlay) {
        be.chainHost().render(be.getLevel(), stack, buffers, partial, light, overlay);
    }

    @Override
    public AABB getRenderBoundingBox(CraneBlockEntity be) {
        // make sure the block entity is still drawn when the block itself is off screen
        return new AABB(be.getBlockPos()).inflate(4);
    }
}
```

Notes:

- At most 4 ticks are run in one frame, so a long pause does not cause a burst of simulation.
- Create the host once per block entity (a field), not per frame.
- The block entity class is loaded on servers too. If you need to keep it server-safe, create the host lazily from the renderer instead of in the constructor.
- The host also draws debug lines when debug drawing is on.
