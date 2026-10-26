# Cookbook

All client side unless noted. Imports are left out to keep them short.

## 1. Arm that aims where the player looks

```java
LimbDefinition arm = LimbDefinition.builder("right_arm")
        .parts("right_arm")
        .target(BuiltinTargets.lookAt(10, true))
        .weight(0.9)
        .smoothing(0.05)
        .build();
JointedApi.registerRig(id("aim"), e -> e instanceof Player, RigDefinition.of(arm));
```

A vanilla arm is one bone, so it points at the target and does not bend.

## 2. Feet that stay on stairs and slopes

```java
LimbDefinition left = LimbDefinition.builder("left_leg")
        .parts("left_leg")
        .target(BuiltinTargets.ground(0.6, 0.6, 0.0, 0.15, 0.0))
        .build();
LimbDefinition right = LimbDefinition.builder("right_leg")
        .parts("right_leg")
        .target(BuiltinTargets.ground(0.6, 0.6, 0.0, 0.15, 0.0))
        .build();
JointedApi.registerRig(id("feet"), e -> e instanceof Player, RigDefinition.of(left, right));
```

The same in JSON:

```json
{
  "entity": "minecraft:player",
  "limbs": [
    { "name": "left_leg",  "parts": ["left_leg"],  "target": { "type": "jointed:ground" } },
    { "name": "right_leg", "parts": ["right_leg"], "target": { "type": "jointed:ground" } }
  ]
}
```

The legs only react when the animated foot is within reach of a surface below it. The body is not lowered, so this suits small height differences.

## 3. Elbow that bends the right way (custom model)

For a model with `upper_arm` and `lower_arm` parts, where `lower_arm` is a child of `upper_arm`:

```java
LimbDefinition arm = LimbDefinition.builder("arm")
        .parts("upper_arm", "lower_arm")
        .tipOffset(new V3(0, 6, 0))                       // end of the lower arm, in its own frame
        .constraints(
                Constraints.coneDeg(100),                 // shoulder
                Constraints.hingeDeg(V3.X, 0, 150))       // elbow bends on one axis only
        .solver(SolverChoice.TWO_BONE)
        .target(ctx -> {
            V3 tip = ctx.animatedTipWorld();
            if (tip == null) return null;
            V3 hand = ctx.entityToWorld(new V3(0.3, 1.0, 0.6));   // 0.6 blocks in front of the entity
            V3 elbow = ctx.limbRootWorld().add(0, -0.3, -0.5);    // hint for where the elbow should point
            return IKTarget.world(hand).withPole(elbow);
        })
        .build();
```

If the elbow flips, check the hinge axis (it is in the parent part's frame) and give the target a pole.

## 4. Tail that swings with movement

```java
LimbDefinition tail = LimbDefinition.builder("tail")
        .parts("tail1", "tail2", "tail3")
        .tipOffset(new V3(0, 0, 4))
        .solver(SolverChoice.FABRIK)
        .constraints(Constraints.coneDeg(35), Constraints.coneDeg(35), Constraints.coneDeg(35))
        .smoothing(0.12)
        .target(ctx -> {
            V3 tip = ctx.animatedTipWorld();
            if (tip == null) return null;
            Vec3 v = ctx.entity.getDeltaMovement();
            // the tip lags behind the motion
            return IKTarget.world(tip.add(-v.x * 6, -v.y * 3, -v.z * 6));
        })
        .build();
```

## 5. Driving a limb from code

```java
LimbDefinition hand = LimbDefinition.builder("grab")
        .parts("right_arm")
        .target(BuiltinTargets.controller())
        .build();
JointedApi.registerRig(id("grab"), e -> e instanceof Player, RigDefinition.of(hand));

// whenever you want the arm to reach something
IKController c = JointedApi.controller(player);
c.setTarget("grab", IKTarget.world(leverPos));
// and to let go
c.clearTarget("grab");
```

## 6. Overriding a target from an event

```java
@SubscribeEvent
public static void onSolve(IKSolveEvent.Pre event) {
    if (event.getEntity() instanceof Player p && p.isUsingItem() && event.getLimbName().equals("right_arm")) {
        event.setTarget(IKTarget.world(someFocusPoint));
    }
}
```

Register the handler on `NeoForge.EVENT_BUS` from client code. Cancel the event, or set the target to `null`, to switch the limb off for that frame.

## 7. A living tentacle in the world

See [World and block chains](world-and-block-chains.md#world-chains). The built-in demo is exactly this: `/jointed demo tentacle`.

## 8. A blade or kagune on an entity

A limb with a renderer draws through the solved nodes. This one is a spiked ribbon on a custom entity's "wing" bones, following where the entity looks:

```java
NodeRenderer blade = CompositeRenderer.of(
        new RibbonRenderer(BLADE_TEXTURE, RadiusProfile.taper(0.14, 0.01)),
        new SpikeRenderer(SPIKE_TEXTURE, 10, 0.12, 0.03));   // see the Rendering page

LimbDefinition wing = LimbDefinition.builder("wing")
        .parts("wing1", "wing2", "wing3", "wing4")
        .solver(SolverChoice.FABRIK)
        .constraints(Constraints.coneDeg(30), Constraints.coneDeg(30), Constraints.coneDeg(30), Constraints.coneDeg(30))
        .smoothing(0.08)
        .renderer(blade)
        .target(BuiltinTargets.lookAt(8, true))
        .build();
```

For a chain that is not attached to a model at all (a floating blade that trails the entity) use a `WorldChain` and drive its root and goal from `onTick`.

## 9. Using the solvers without Minecraft

`k.jointed.core` needs no game at all. This is the whole IK loop in plain Java:

```java
IKChain arm = IKChain.builder()
        .bone(new V3(0, 1, 0), Constraints.coneDeg(80))
        .bone(new V3(0, 1, 0), Constraints.hingeDeg(V3.Z, 0, 140))
        .build();

SolveResult r = TwoBoneSolver.INSTANCE.solve(arm, new SolverSettings(),
        IKGoal.tip(new V3(0.6, 1.2, 0.2)).withPole(new V3(0, 1, 3)));

System.out.println(r.converged() + " " + arm.tipPosition());
V3[] nodes = arm.points();      // hand these to any renderer
```

Use it to drive bones in a system Jointed has no built-in binding for (see [known limits](debugging.md#known-limits)): solve, then read `arm.local(i)` for each joint and write it into your own skeleton.

