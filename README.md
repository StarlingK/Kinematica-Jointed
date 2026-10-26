# Kinematica: Jointed
Kinematics framework and core mod for Minecraft.

This was originally going to be part of a Tokyo Ghoul mod for minecraft, that used IK Kagunes. 

I have decided to release the framework publicly, as there are way more talented people than me out there and they could make better things with this.

## What it does: 

Jointed solves **inverse kinematics** for chains of bones and lets other mods build on top of it.
You just give it a chain and a target and it works out most of everything else.

- It has four solvers; two-bone, FABRIK, CCD and Jacobian.
- It has joint constraints; cone, elliptical cone, hinge, fixed, or write your own(recommended for some use cases).
- It has entity rigs; bend limbs of vanilla and modded entity models at render time. Rigs can be written in Java or JSON for ease of use.
- It has tentacles, ropes and cranes in the world, with optional Verlet physics.
- It has block entities; a helper that ticks and draws a chain inside a block entity renderer. 
- It has pluggable rendering; the solver only produces node positions. How those nodes look (textures, shapes, models, render types) is a `NodeRenderer` you can replace or write yourself.
- It comes with debug tools; `/jointed debug` draws bones, joints, targets and poles. `/jointed demo` runs built-in examples.

# Hooks use Mixin plus NeoForge events. No JS coremod is needed.

## Quick start guide: 

```java
//makes the player's right arm point where they look
LimbDefinition arm = LimbDefinition.builder("right_arm")
        .parts("right_arm")
        .target(BuiltinTargets.lookAt(10, true))
        .build();
JointedApi.registerRig(
        ResourceLocation.fromNamespaceAndPath("yourmod", "aim"),
        e -> e instanceof Player,
        RigDefinition.of(arm));
```

# Or with no Java, in `assets/yourmod/jointed/rigs/aim.json`:

```json
{
  "entity": "minecraft:player",
  "limbs": [
    { "name": "right_arm", "parts": ["right_arm"], "target": { "type": "jointed:look_at", "distance": 10 } }
  ]
}
```

## Documentation

| Topic | Description |
| :--- | :--- |
| [Getting started](docs/getting-started.md) | Adding Jointed to your mod, first rig, building this repository |
| [Concepts](docs/concepts.md) | IK explained for people who have never used it before, coordinate spaces |
| [Core API](docs/core-api.md) | Chains, goals, solvers, constraints, physics, curves |
| [Entity rigs](docs/entity-rigs.md) | Rigs, limbs, targets, events, first person, full JSON reference |
| [Rendering](docs/rendering.md) | Custom textures, shapes and render types for the nodes; or writing your own renderer |
| [World and block chains](docs/world-and-block-chains.md) | Free-standing chains and block entity chains |
| [Cookbook](docs/cookbook.md) | Worked examples |
| [Debugging and limits](docs/debugging.md) | Commands, config, troubleshooting, known limits |
