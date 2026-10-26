# Debugging and limits

## Commands

Client commands, available to everyone in a world:

| Command | Effect |
| --- | --- |
| `/jointed debug [on\|off]` | Draw bones (blue), joints (yellow crosses), the target (red cross) and the pole (green) for every limb and chain. With no argument it toggles. |
| `/jointed demo <tentacle\|aim\|feet>` | Start a built-in demo. Add `stop` to end it, for example `/jointed demo aim stop`. |
| `/jointed stats` | Number of rig registrations, tracked entities and world chains. |

Demos are stopped when you leave the world.

## Config

`config/jointed-client.toml`:

| Key | Default | Meaning |
| --- | --- | --- |
| `enabled` | true | Master switch for all IK on this client. |
| `maxDistance` | 48 | Entities farther than this from the camera are not solved. |
| `maxIterations` | 24 | Upper bound on solver iterations per limb per frame. |
| `globalWeight` | 1.0 | Multiplier on every limb weight, 0 turns the effect off. |

## Troubleshooting

1. `/jointed stats` should show your registration. If it does not, the rig was never registered (client setup not run) or a JSON file failed, check the log for `Skipping Jointed rig`.
2. Is the entity within `maxDistance`, and is `enabled` true?
3. Turn on `/jointed debug on`. If you see bones but no red target cross, your provider returns `null`. Remember `WORLD` and `ENTITY` targets are skipped when the entity is drawn in a GUI!
4. Check the log for `Jointed limb '<rig id>#<limb name>' disabled`. That names a problem: a part that was not found, or a part that is not a direct child of the previous one.
5. If the log has Mixin warnings about `LivingEntityRenderer` or `PlayerRenderer`, the hook did not apply. The injections are set to fail quietly rather than crash :) , so the symptom is simply no IK.
6. Another mod that replaces an entity's renderer without calling the vanilla render method bypasses the hook.

**The limb points the wrong way or stretches.** Check `tipOffset` (the end of the last bone in the last part's frame, in pixels) and the order of `parts` (root first).

**The elbow or knee flips.** Give the target a pole and a hinge constraint on the bending joint.

**It jitters.** Add `smoothing` (try 0.05 to 0.15), raise `tolerance`, or check that your provider returns a stable target each frame. A provider that returns `null` on some frames makes the limb fade in and out, see `fade`.

**The limb only reaches part of the way.** The target is farther than the limb can stretch. Check `SolveResult.reachable()` in an `IKSolveEvent.Post` handler.

**Performance.** Reduce `maxDistance` and `maxIterations`, prefer two-bone and FABRIK over Jacobian, and avoid ray casts in providers for many entities.

**My renderer draws in the wrong place.** For entity limbs the node space has +Y pointing down. Use the sample's `tangent`, `normal` and `binormal` instead of fixed world axes.

## Known limits

Read this before relying on Kinematica.
Things that are most likely to need a tweak:

- **`ModelPart.resetPose()`** is used to read each part's baked rotation. If it does not exist in your mappings, use `restFromInitialPose(false)` on your limbs and remove the call.
- **Renderer layer access.** The entity layer is added with `addLayer` from `EntityRenderersEvent.AddLayers`, which NeoForge exposes publicly.
- **World chain draw stage.** Chains are drawn at `AFTER_ENTITIES` with the event's pose stack. If they appear offset or flicker, that is the place to look.
- **`NodeInstanceRenderer.block` and `.item`** call `renderSingleBlock` and `renderStatic`, check their signatures against your mappings.

**By design:**

- Vanilla limbs are single bones, so they can aim and reach but not bend. A bending limb needs a model with separate parts.
- Everything is client side and visual. Hit boxes, pathfinding and server logic do not see the IK pose but can be made to.
- Rigs apply to `LivingEntity` renderers. Block entity renderers, non-living entities and held items have no automatic hook. Use `BlockChainHost`, `WorldChain` or call a renderer yourself.
- First person aims the arm model only, held items do not move with it.
- Models that are not built from `ModelPart`s (for example GeckoLib rigs) have no built-in binding. Use the core API and write the solved rotations into your own skeleton, see [the last cookbook recipe](cookbook.md#9-using-the-solvers-without-minecraft).
- The vertex helpers in `RenderUtil` write the entity vertex format, which is what the built-in renderers use.
