# Core API

Everything in `k.jointed.core` is plain Java with no Minecraft classes. Use it on any side, in tests, or in tools.

## IKChain

A serial chain of `n` bones. Joint `i` has a local rotation, bone `i` is a vector written in joint `i`'s frame.

```
frame[i]  = frame[i-1] * local[i]          (frame[-1] is the root rotation)
pos[i+1]  = pos[i] + frame[i] * bone[i]    (pos[0] is the root position)
tip       = pos[n-1] + frame[n-1] * bone[n-1]
```

This is how `ModelPart`s compose, the chain is unit-agnostic.

```java
IKChain arm = IKChain.builder()
        .root(new V3(0, 0, 0))
        .bone(new V3(0, 1, 0), Constraints.coneDeg(80))             // upper arm, 1 unit long
        .bone(new V3(0, 1, 0), Constraints.hingeDeg(V3.Z, 0, 140))  // forearm, elbow bends one way only
        .build();
```

Shortcut for equal bones: `IKChain.straight(bones, boneLength, direction[, constraint])`.

Builder methods: `root(position[, rotation])`, `bone(vector[, constraint[, restRotation]])`, `build()`.

### Reading a chain

| Method | Returns |
| --- | --- |
| `size()` | Number of bones. |
| `points()` | `n + 1` positions: joint origins then the tip. These are the nodes renderers receive. |
| `pointPosition(m)`, `jointPosition(i)`, `tipPosition()` | Single positions. |
| `jointFrame(i)`, `parentFrame(i)` | World frame of a joint and of its parent. |
| `boneDirection(i)`, `boneLength(i)`, `reach()`, `reach(m)` | Direction, length, total length (or of the first `m` bones). |
| `local(i)`, `delta(i)` | Joint rotation, and its deviation from rest. |

### Changing a chain

`setRoot`, `setRootPosition`, `setBone`, `setLocal`, `setDelta`, `setLocals`, `setRest`, `setConstraint`, `resetToRest`, `constrainJoint`, `constrainAll`, `copy`.
`setJointWeight(i, w)` controls how much joint `i` takes part in the iterative solvers (CCD, Jacobian): `0` freezes it, `1` is normal.

Forward kinematics is cached and recomputed lazily after any change, `updateFK()` forces it.

## Goals

`IKGoal` is immutable. Build one with a factory then chain `with...` calls.

```java
IKGoal goal = IKGoal.tip(new V3(0.5, 1.2, 0.3))
        .withPole(new V3(0, 1, 2))                      // bend towards this point
        .withOrientation(Quat.fromAxisAngle(V3.Y, 0.5)) // optional, hand orientation (world)
        .withWeight(1.0);
```

| Factory / method | Meaning |
| --- | --- |
| `IKGoal.tip(pos)` | Make the tip of the whole chain reach `pos`. |
| `IKGoal.point(i, pos)` | Make node `i` reach `pos`. Only the first `i` bones move, the rest follow rigidly. |
| `withPole(p[, weight])` | Bend the middle of the chain towards `p`. |
| `withOrientation(q[, joint, weight])` | Align a joint's world rotation (default: the last joint). Jacobian solves it together with position, the others apply it after the position solve. |

All solvers except `JacobianSolver` use only the first goal you pass.

## Solving

```java
SolveResult r = TwoBoneSolver.INSTANCE.solve(arm, new SolverSettings(), goal);
V3[] nodes = arm.points();
```

`IKSolver.solve(chain, settings, goals...)` modifies the chain in place and starts from its current pose. `solve(chain, goal)` uses default settings.

`SolveResult(iterations, error, converged, reachable)`:

| Field | Meaning |
| --- | --- |
| `iterations` | Iterations used. |
| `error` | Remaining distance between the goal point and the target. |
| `converged` | `error <= tolerance`. |
| `reachable` | `false` if the target is farther than the chain can stretch. The solver still extends the chain towards it. |

### SolverSettings

`copy()` makes an independent copy.

| Field | Default | Used by | Meaning |
| --- | --- | --- | --- |
| `maxIterations` | 20 | FABRIK, CCD, Jacobian | Iteration cap per solve. |
| `tolerance` | 0.001 | all | Stop when the goal point is this close, in chain units. |
| `maxStepAngle` | 0.6 rad | CCD, Jacobian | Largest turn of one joint in one iteration. |
| `damping` | 0.1 | Jacobian | Damping as a fraction of the chain's reach. Higher is steadier near straight poses but slower. |
| `restBias` | 0 | Jacobian | 0 to 1. Pulls joints back to rest where that does not disturb the goal. |
| `softness` | 0 | Two-bone | 0 to 1. Fraction of reach where the limb starts easing into full extension. |

### Solver notes

- **TwoBoneSolver:** exact. Needs exactly two active bones, otherwise it falls back to FABRIK. With a pole, the elbow bends towards it. Without one it keeps its current bend direction. If a joint limit (a hinge, say) makes that bend impossible, it searches for the closest bend that still reaches the target, so reaching wins over following the pole.
- **FabrikSolver:** each iteration pulls the chain from the tip onto the target (backward), then rebuilds rotations from the root outwards (forward) with constraints applied. Rotations are swing only.
- **CcdSolver:** rotates one joint at a time, tip to root, towards the target. Respects joint weights and `maxStepAngle`.
- **JacobianSolver:** uses a damped least squares step, so it stays stable near singular poses. If a step makes the error worse it is undone and the damping raised. It is the only solver that handles several goals or an orientation goal in one go.

Pass an unreachable target and you get a straight chain pointing at it, with `reachable == false`.

### Writing your own solver

Implement `IKSolver` (`id()` and `solve(...)`). `SolverSupport` has the pieces the built-ins use:

- `activeBones(chain, goal)` how many bones the goal moves
- `forwardPass(chain, P, m, applyConstraints)` turns desired node positions `P` into joint rotations, honouring constraints and keeping bone lengths
- `applyPole(chain, goal, m)`, `alignOrientation(chain, goal)`, `error(chain, goal)`
- `solveLinear(a, b)` small dense linear solver

## Constraints

A constraint limits one joint. It receives `delta`, the joint's rotation relative to its rest rotation, and the unit bone axis (the twist axis) in the joint's frame, and returns the closest allowed rotation.

Axes you give a constraint are in the joint's **rest frame**: the parent frame rotated by the joint's rest rotation. For model parts with no baked rotation this is the parent part's frame.

| Factory | Meaning |
| --- | --- |
| `Constraints.NONE` | No limit. |
| `Constraints.FIXED` | The joint cannot move. Solvers skip it. |
| `coneDeg(maxSwing)` | Circular swing limit around the rest bone direction, no twist. |
| `coneDeg(maxSwing, twistMin, twistMax)` | Same plus a twist range around the bone. |
| `ellipseDeg(a, b, twistMin, twistMax)` | Elliptical swing limit, `a` and `b` are the half-angles on the two axes perpendicular to the bone. For separate positive and negative limits use `ellipse(aPos, aNeg, bPos, bNeg, twistMin, twistMax, refAxis)` (radians). |
| `hingeDeg(axis, min, max)` | Single axis rotation within a range, like an elbow or knee. Everything off the axis is removed. |
| `all(c1, c2, ...)` | Apply several in order. |

Each also has a radians version without the `Deg` suffix where it makes sense.

A constraint is one method, so a lambda works:

```java
JointConstraint noTwist = (delta, boneAxis) -> delta.swing(boneAxis);
```

Constraints must be stateless so one instance can be shared between chains.

## ProceduralChain

An `IKChain` plus a goal, a solver, optional physics and tick interpolation. Use it for things that live on their own: tentacles, ropes, cranes.

```java
ProceduralChain tentacle = new ProceduralChain(IKChain.straight(10, 0.25, V3.Y, Constraints.coneDeg(35)));
tentacle.settings().iterations(12).tolerance(0.01);

// each game tick
tentacle.root(rootPos, Quat.IDENTITY);
tentacle.target(targetPos);          // or .goal(IKGoal...) for poles and orientation
tentacle.tick(0.05);                 // seconds per tick

// each frame
V3[] nodes = tentacle.points(partialTick);   // interpolated between the last two ticks
```

- `solver(IKSolver)` picks a solver, `null` (default) chooses automatically.
- `snap()` makes the interpolation jump to the current pose. Call it after teleporting the root.
- `enableDynamics()` adds physics, see below.

## VerletChain (physics)

Position-based rope simulation. `ProceduralChain.enableDynamics()` returns it. The IK pose becomes the shape the simulation is pulled towards, so you get follow-through and sag on top of aiming.

| Field | Default | Meaning |
| --- | --- | --- |
| `gravity` | `(0, -9.8, 0)` | Acceleration in chain units per second squared. |
| `damping` | 0.1 | Fraction of velocity kept after one second. Lower is heavier damping. |
| `stiffness` | 0 | 0 to 1. How strongly each step pulls the points towards the IK pose. **At 0 the simulation ignores the IK target completely** and hangs from the root like a rope. |
| `iterations` | 8 | Length-constraint passes per step. |
| `collider` | none | `Collider.resolve(point)` returns a corrected point, use it to keep the chain out of blocks. |

## ChainCurve

Turns nodes into a smooth curve with twist-free frames. This is what renderers use, see [Rendering](rendering.md).

```java
ChainCurve.Sample[] s = ChainCurve.sample(points, 4, V3.Z);   // 4 samples per bone, preferred "up"
ChainCurve.Sample mid = ChainCurve.at(s, 0.5);                // halfway along the length
Quat frame = ChainCurve.frameRotation(mid);                   // X = normal, Y = tangent, Z = binormal
```

A `Sample` has `pos`, `tangent`, `normal`, `binormal`, `t` (0 to 1 along the length) and `bone` (index of the bone it lies on).

## PoseSmoother

Frame-rate independent smoothing of a chain's joint rotations. `smooth(chain, dt, halfLifeSeconds)` blends the current pose with the previous one. `reset()` forgets history.

## Math

- `V3`: immutable vector with the usual operations plus `rejectFrom`, `angleTo`, `signedAngleTo`, `anyPerpendicular`, `lerp`.
- `Quat`: immutable unit quaternion. `fromAxisAngle`, `fromTo` (shortest arc), `exp`/`log` (rotation vectors), `slerp`, `twist`/`swing` decomposition, `fromEulerZYX`/`toEulerZYX` (Minecraft model part order), `fromBasis`.
- `MathUtil`: clamp, lerp, angle wrapping, `halfLifeFactor`.

## Tests

`CoreChecks` (plain Java, has a `main`) and `CoreTest` (JUnit 5) check the math, every solver, constraints, physics and curves. Run `./gradlew test`.

