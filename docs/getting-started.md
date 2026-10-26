# Getting started

## Requirements

- Minecraft 1.21.1
- NeoForge
- Java 21

## Using Jointed from your mod

Build it (see below) and put the jar in your mod's `libs/` folder.

`build.gradle`:

```groovy
dependencies {
    implementation files('libs/jointed-VERSION.jar')
}
```

`neoforge.mods.toml`:

```toml
[[dependencies.yourmod]]
modId = "jointed"
type = "required"
versionRange = "[0.1.0,)"
ordering = "AFTER"
side = "BOTH"
```

Players need the Jointed jar in their `mods` folder too.

## Which side is which

| Package | Side | Notes |
| --- | --- | --- |
| `k.jointed.core.*` | any | No Minecraft classes. Safe on a dedicated server and in plain unit tests. |
| `k.jointed.api.*` | client | `JointedApi`, limb and rig definitions, targets, events. Call from client-only code. |
| `k.jointed.client.*` | client | Rig runtime, renderers, commands. |

Register rigs and renderers from client setup, for example a `@Mod(dist = Dist.CLIENT)` constructor or `FMLClientSetupEvent`. Never from common code, a dedicated server would crash.

## Your first rig

Make the player's right arm point where they look:

```java
LimbDefinition arm = LimbDefinition.builder("right_arm")
        .parts("right_arm")                          // model part, see Entity rigs for how names are resolved
        .tipOffset(new V3(0, 12, 0))                 // end of the arm in model pixels, 12 for a vanilla arm
        .target(BuiltinTargets.lookAt(10, true))     // where to reach
        .weight(0.9)                                 // 0 = animation only, 1 = full IK
        .smoothing(0.05)                             // seconds, hides jitter
        .build();

JointedApi.registerRig(
        ResourceLocation.fromNamespaceAndPath("yourmod", "aim"),
        e -> e instanceof Player,
        RigDefinition.of(arm));
```

Join a world and look around. If nothing happens, see [Debugging](debugging.md#troubleshooting).

The same thing as JSON, placed at `assets/yourmod/jointed/rigs/aim.json`:

```json
{
  "entity": "minecraft:player",
  "limbs": [
    {
      "name": "right_arm",
      "parts": ["right_arm"],
      "tip_offset": [0, 12, 0],
      "weight": 0.9,
      "smoothing": 0.05,
      "target": { "type": "jointed:look_at", "distance": 10 }
    }
  ]
}
```

JSON rigs reload with the resource packs (F3 + T).

## Try the built-in demos

In a world, run:

```
/jointed demo aim
/jointed demo feet
/jointed demo tentacle
/jointed debug on
```

Each demo has a `stop`, for example `/jointed demo tentacle stop`.

## Building this repository

```
./gradlew build        # compile and package
./gradlew runClient    # start a dev client
./gradlew test         # run the unit checks
```

They can also be run without Gradle, `CoreChecks` has a `main` method.

