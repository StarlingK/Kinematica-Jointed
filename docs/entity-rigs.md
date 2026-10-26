# Entity rigs

A **rig** bends limbs of an entity model at render time. You describe each limb, say where it should reach, and Jointed does the rest. This page is for Java; the JSON format is documented at the end and is equivalent.

Everything here is client side.

## How it works

1. Vanilla renders an entity: it positions the model (`setupAnim`), then draws it.
2. Jointed hooks in right after `setupAnim`. For each limb that applies to the entity it reads the animated pose, asks the limb's target provider where to reach, solves, and writes the result into the model parts.
3. The model is drawn with the IK pose. Layers (including Jointed's own renderer layer) are drawn.
4. When the entity has finished rendering, the original animated rotations are restored, so nothing leaks into the next entity or the next frame.

If a limb has no target (provider returns `null`) it fades back to its animated pose instead of snapping.

## Registering a rig

```java
JointedApi.registerRig(id, filter, rig);        // filter is a Predicate<LivingEntity>
JointedApi.registerRig(id, entityType, rig);    // shortcut for one entity type
JointedApi.unregisterRig(id);                   // true if it existed
```

Registering the same id again replaces the old rig. A `RigDefinition` is a list of limbs: `RigDefinition.of(limbA, limbB)`.

## Limbs

```java
LimbDefinition limb = LimbDefinition.builder("left_leg")
        .parts("left_leg")
        .target(BuiltinTargets.ground(0.6, 0.6, 0.0, 0.15, 0.0))
        .build();
```

| Builder method | Default | Meaning |
| --- | --- | --- |
| `builder(name)` | required | Name of the limb. Must be unique within the entity, used by the controller and events. |
| `parts(paths...)` | required | Model parts from root to tip. |
| `tipOffset(v3)` | `(0, 12, 0)` | End of the last bone, in the last part's frame, in model pixels. |
| `target(provider)` | required | Where to reach. |
| `solver(choice)` | `AUTO` | `AUTO`, `TWO_BONE`, `FABRIK`, `CCD`, `JACOBIAN`. |
| `settings(solverSettings)` | tolerance 0.01 | See [Core API](core-api.md#solversettings). The config's iteration cap also applies. |
| `constraints(c...)` | none | One per part, in order. |
| `weight(w)` | 1 | 0 to 1, strength of the IK. |
| `smoothing(seconds)` | 0 | Half-life smoothing of the solved rotations. |
| `fade(seconds)` | 0.08 | Half-life of the fade when the target appears or disappears. |
| `restFromInitialPose(bool)` | true | Constraints measured from the rotation baked into the part when the model was created. `false` measures from zero. |
| `renderer(nodeRenderer)` | none | Draws something through the solved nodes, see [Rendering](rendering.md). |

### Model parts

A part reference is a dotted path from the model's top level (`"body.right_arm"`) or a bare name that is searched for (`"right_arm"`).

- For most models (humanoids, quadrupeds, ...) the top level is every `ModelPart` field of the model class, named by field name and by snake_case. Vanilla humanoids give you `head`, `body`, `right_arm`, `left_arm`, `right_leg`, `left_leg`, `hat`.
- For `HierarchicalModel`s (many newer and modded entities) the top level is `root`. Bare names are searched through the whole tree, so `"left_front_leg"` usually just works.
- Each part after the first must be a **direct child** of the part before it. If it is not, or a part is missing, the limb is disabled and an error is logged once.

How the chain is built from the parts:

- Root is the first part's pivot, in its parent's frame.
- The bone of every part except the last is the next part's pivot offset (what you see in the model definition).
- The last bone is `tipOffset`.

**Vanilla limbs are one bone.** A vanilla arm or leg is a single part 12 pixels long with no elbow or knee, so a vanilla limb can aim and reach but cannot bend. Real bending needs a model with separate upper and lower parts, for example a custom model, or a modded entity with leg segments.

For players, parts are kept in synx with the parts you move.

## Targets

A `TargetProvider` is called once per limb per frame on the render thread:

```java
TargetProvider provider = ctx -> {
    V3 hit = ...;
    return hit == null ? null : IKTarget.world(hit).withPole(somePoint).withWeight(1.0);
};
```

Return `null` to leave the limb on its animation. It runs every frame.

### IKTarget

`IKTarget(position, space, pole, weight)` with factories `world`, `entity`, `model`, `view`, and `withPole(p)`, `withWeight(w)`. The pole is in the same space as the position. For the meaning of the spaces see [Concepts](concepts.md#target-spaces).

### TargetContext

| Member | Meaning |
| --- | --- |
| `entity`, `partialTick`, `limbName` | What is being solved. |
| `level()` | The level. |
| `controller()` | The entity's `IKController`. |
| `worldAvailable()` | `false` when drawn outside the world (GUI). |
| `isFirstPerson()` | True for first-person arm rigs. |
| `entityPosition()`, `eyePosition()`, `lookVector()`, `bodyYaw()` | Interpolated entity data. In first person, eye and look come from the camera. |
| `limbRootWorld()`, `animatedTipWorld()` | Where the animation put the limb's root and tip, in world space (null if unavailable). |
| `entityToWorld(local)` | Entity space to world. |
| `modelToWorld(px)`, `worldToModel(w)`, `toModel(p, space)` | Conversions. |
| `raycastBlocks(from, to)` | First block hit on a segment, as `Optional<V3>`. |

### Built-in target providers

Available from Java as `BuiltinTargets.xxx(...)` and from JSON as `jointed:xxx`.

| Provider | Parameters | Behavior |
| --- | --- | --- |
| `lookAt(distance, raycast)` | `distance` (default 16), `raycast` (default true) | Reaches where the entity looks. With `raycast` the first block hit, otherwise a point `distance` blocks ahead. |
| `ground(maxUp, maxDown, footHeight, plantDistance, poleForward)` | defaults 0.6, 0.6, 0, 0.15, 0 | Foot planting. Casts down around the animated tip. If the tip is below the surface it is lifted onto it, if it is within `plantDistance` above the surface it is snapped down. Otherwise the limb keeps its animation. `poleForward` makes the knee bend towards a point that far in front of the limb root. It does not move the body. |
| `fixed(space, position)` | space (default `entity`), position | A constant point. |
| `controller()` | none | Uses targets set from code with `IKController`. |

## IKController

Steer a limb from your own code.

```java
IKController c = JointedApi.controller(entity);
c.setTarget("right_arm", IKTarget.world(leverPos).withPole(elbowHint));
c.setWeight("right_arm", 0.8);       // multiplied with the limb's own weight
c.clearTarget("right_arm");
c.setEnabled(false);                 // turns off every limb on this entity
```

The key is the limb **name** you gave in `LimbDefinition.builder(name)`. Limbs only read it if their provider is `BuiltinTargets.controller()` (or your provider reads `ctx.controller()`).

## Events

Posted on `NeoForge.EVENT_BUS`, client side, on the render thread.

| Event | Use |
| --- | --- |
| `IKSolveEvent.Pre` | Cancellable. `getTarget()` / `setTarget(...)` to change the target, set `null` to disable the limb for the frame. Fired even when the provider returned `null`, so a handler can supply a target. |
| `IKSolveEvent.Post` | `getChain()` and `getResult()` after solving, before the result is written to the model. The chain is in limb space (model pixels). |

Both have `getEntity()` and `getLimbName()`.

## First-person arms

```java
JointedApi.registerFirstPersonArm(HumanoidArm.RIGHT, ctx -> IKTarget.view(new V3(0.3, -0.2, -0.8)), 12, 1.0);
JointedApi.unregisterFirstPersonArm(HumanoidArm.RIGHT);
```

Parameters: target provider, arm length in pixels (12 for vanilla), weight. It aims the arm model only, held items are not moved. `VIEW` space is camera eye space: +X right, +Y up, forward is -Z.

## Extending JSON

JSON rigs can use your own pieces if you register them (client side, before resources reload):

```java
JointedApi.registerTargetProvider(id, json -> provider);      // "target": {"type": "yourmod:thing", ...}
JointedApi.registerConstraintType(id, json -> constraint);    // "constraints": [{"type": "yourmod:thing", ...}]
JointedApi.registerRendererType(id, json -> nodeRenderer);    // "renderer": {"type": "yourmod:thing", ...}
```

Ids written without a namespace in JSON belong to Jointed (`"cone"` means `jointed:cone`). The factory receives the whole JSON object so it can read its own parameters.

## JSON reference

Files live at `assets/<namespace>/jointed/rigs/<name>.json`. The rig id is `<namespace>:<name>`. Rigs reload with resource packs. A file with an error is skipped and the reason is logged.

```json
{
  "entity": "minecraft:player",
  "limbs": [
    {
      "name": "right_arm",
      "parts": ["right_arm"],
      "tip_offset": [0, 12, 0],
      "solver": "auto",
      "weight": 1.0,
      "smoothing": 0.05,
      "fade": 0.08,
      "rest": "initial",
      "settings": { "iterations": 16, "tolerance": 0.01 },
      "constraints": [ { "type": "cone", "max_swing": 80 } ],
      "target": { "type": "jointed:look_at", "distance": 10, "raycast": true },
      "renderer": { "type": "jointed:tube", "texture": "minecraft:textures/block/stone.png" }
    }
  ]
}
```

### Top level

| Key | Meaning |
| --- | --- |
| `entity` | `"*"` for all living entities, an entity type id, or an array of ids. Default `"*"`. |
| `limbs` | Array of limbs, required. |

### Limb

| Key | Default | Meaning |
| --- | --- | --- |
| `name` | required | Limb name. |
| `parts` | required | Array of part paths. |
| `tip_offset` | `[0, 12, 0]` | Tip offset in pixels. |
| `solver` | `"auto"` | `auto`, `two_bone`, `fabrik`, `ccd`, `jacobian`. |
| `weight`, `smoothing`, `fade` | 1, 0, 0.08 | As in Java. |
| `rest` | `"initial"` | `"initial"` or `"zero"`. |
| `settings` | see below | Solver settings. |
| `constraints` | none | One object per part. |
| `target` | required | Target provider object. |
| `renderer` | none | Renderer object, see [Rendering](rendering.md#json-renderers). |

`settings` keys: `iterations`, `tolerance`, `damping`, `rest_bias`, `softness`, `max_step_degrees`.

### Constraint types

| Type | Keys |
| --- | --- |
| `none`, `fixed` | none |
| `cone` | `max_swing` (degrees, default 45), `twist` `[min, max]` degrees (default `[0, 0]`) |
| `ellipse` | `swing` `[a, b]` degrees (default `[45, 45]`), `twist` `[min, max]`, `ref_axis` `[x, y, z]` (default `[1, 0, 0]`) |
| `hinge` | `axis` `[x, y, z]` (default `[1, 0, 0]`), `min`, `max` degrees (default 0 and 140) |

### Target types

| Type | Keys |
| --- | --- |
| `jointed:look_at` | `distance`, `raycast` |
| `jointed:ground` | `max_up`, `max_down`, `foot_height`, `plant_distance`, `pole_forward` |
| `jointed:fixed` | `space` (`world`, `entity`, `model`, `view`), `position` `[x, y, z]` |
| `jointed:controller` | none |
