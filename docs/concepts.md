# Concepts

If you already know IK, skip to [Core API](core-api.md). If not, this page should explain everything in an easy to understand way, hopefully.

## Forward and inverse kinematics

A **chain** is a row of **bones** joined at **joints**, like a shoulder, an elbow and a wrist.

- **Forward kinematics:** you choose each joint's rotation, the hand ends up wherever it ends up. This is what normal animation does.
- **Inverse kinematics (IK):** you choose where the hand should be (the **target**), the solver works out the joint rotations.

IK is what keeps a foot on a stair step or lets a tentacle follow a point.

Words used throughout the docs:

| Word | Meaning |
| --- | --- |
| Root | Where the chain is fixed. The shoulder of an arm. |
| Tip | The free end of the last bone. The hand. (Also called the end effector). |
| Target | The position the tip should reach. |
| Reach | Total length of all bones. A target farther away than this cannot be reached, the chain stretches towards it. |
| Pole | A point the middle of the chain should bend towards. It decides which way an elbow or knee points. Without one, a two-bone chain could bend in any direction around the root-to-target line. |
| Node | A position along the chain. A chain with `n` bones has `n + 1` nodes: `n` joint origins plus the tip. Renderers receive these. |
| Constraint | A limit on one joint, for example an elbow that only bends one way. |
| Swing and twist | Any rotation splits into a swing (tilting the bone) and a twist (spinning around the bone's own axis). Cone limits restrict the swing, a hinge allows only a single axis. |

## Picking a solver

| Solver | Good for | Notes |
| --- | --- | --- |
| `TwoBoneSolver` | Arms and legs | Supports a pole and a soft reach (eases into full extension instead of snapping straight). For anything other than two bones it falls back to FABRIK. |
| `FabrikSolver` | Tails, tentacles, spines, ropes, long chains | Constraints are applied on every pass. Never adds twist. |
| `CcdSolver` | Chains that should stay close to their animated pose | Works with rotations directly so hinge and twist limits behave 'naturally'. Needs many iterations when the target is near the limit of reach. |
| `JacobianSolver` | Several targets at once, pulling joints back to rest | Most general and most expensive. Takes more than one `IKGoal`. |

`SolverChoice.AUTO` (the default for rigs) uses the two-bone solver for two-bone limbs and FABRIK for everything else.

All solvers start from the chain's current pose. Solving every frame therefore gives smooth motion instead of a guess every time.

## Coordinate conventions

Kinematica: Jointed's chains don't about units.

| Host | Unit | Origin | Notes |
| --- | --- | --- | --- |
| Entity limb (model parts) | Model pixels (1/16 block) | The limb root's parent part | Y points down |
| Entity limb as seen by a renderer | Blocks | Model space of the entity | The render layer's pose stack is in model space, so +Y is down here too. |
| World chain | Blocks | Absolute world coordinates | Renderers receive camera-relative positions. |
| Block entity chain | Blocks | The block's corner | The pose stack is already at the block. |

### Target spaces

Targets handed to a rig say which space their position is in (`TargetSpace`):

| Space | Meaning |
| --- | --- |
| `WORLD` | Absolute world position in blocks. |
| `ENTITY` | Blocks relative to the entity's feet, rotated with its body yaw. +Z is forward, +X is the entity's left, +Y is up. |
| `MODEL` | Raw model pixels. Always available. (Even when an entity is drawn in a GUI.) |
| `VIEW` | First person only. Blocks relative to the camera in eye space. |

`WORLD` and `ENTITY` targets are skipped when an entity is drawn outside the world (inventory screen, for example). The limb then stays on its animation.

### Rotation conventions

- `Quat` is an immutable unit quaternion. `a.mul(b)` applies `b` first, then `a`.
- `Quat.fromEulerZYX(x, y, z)` matches the rotation order of Minecraft's `ModelPart`, so rotations from the solver can be written straight to `xRot`, `yRot`, `zRot` with `toEulerZYX()`. (hehe euler, signalis)
- A joint's local rotation is `rest * delta`. Constraints work on `delta`, the deviation from rest.

