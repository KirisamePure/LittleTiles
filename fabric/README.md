# Minecraft 1.21.10 Fabric port — in progress

This is a separate development build, **not a feature-complete replacement** for the
1.7.10 Forge mod. Original source, assets, and build files remain in the parent
project as the feature-parity reference. The target is a new mod for new worlds,
retaining the gameplay features. Importing 1.7.10 worlds or old blueprints is
out of scope; legacy identifiers and data formats do not need to be preserved.
Saving and reloading content created with the new mod remains required.

## Build

When opening `fabric/` directly in IntelliJ IDEA, select **Gradle distribution:
Wrapper** and **Gradle JVM: JDK 25** in Settings → Build Tools → Gradle, then
reload the Gradle project. This directory includes its own Gradle 9.7.1 wrapper;
Loom 1.18.4 requires Gradle 9.7 or newer. A locally selected Gradle 9.6 cannot
resolve the plugin even when Java 25 is configured correctly.

From this directory, `./gradlew runClient` launches the Fabric development game;
`./gradlew build` produces the Fabric JAR under `build/libs/`. On Windows use
`gradlew.bat`. The parent project's `runClient` launches legacy Forge 1.7.10.

Use a **JDK 25** for Gradle/Loom (the mod targets Java 21 bytecode) and the
existing Gradle wrapper from **this directory**:

```bash
cd /workspace/LittleTiles/fabric
# In the cloud environment only, source the Java proxy configuration:
source /workspace/.cloud-setup/littletiles-env.sh
export JAVA_HOME=/workspace/toolchains/jdk-25.0.5.0.2
export PATH="$JAVA_HOME/bin:$PATH"
# Use the system CA store, which includes the cloud HTTPS proxy CA:
export JAVA_TOOL_OPTIONS="$JAVA_TOOL_OPTIONS -Djavax.net.ssl.trustStore=/etc/ssl/certs/java/cacerts"
../gradlew --no-daemon --max-workers=2 build
```

Outside this cloud machine, set `JAVA_HOME` to your JDK 25 installation and
omit the cloud-specific proxy/CA commands. The cloud JDK archive was downloaded
from Oracle's HTTPS distribution and checked against its published SHA-256.
Invoking the wrapper from this directory selects this independent Fabric build.
Loader/API coordinates follow the official Fabric example for 1.21.10:
https://github.com/FabricMC/fabric-example-mod/tree/1.21.10
Loom is pinned to release `1.18.4`, confirmed through Fabric Maven metadata.
That Loom release requires Java 25 for the build process; compilation uses
`--release 21` for Minecraft 1.21.10 compatibility.

Dependencies require `maven.fabricmc.net`, the Gradle/Maven/GitHub package
destinations, and Mojang's `piston-meta.mojang.com`, `piston-data.mojang.com`,
`libraries.minecraft.net`, and `resources.download.minecraft.net`. Fabric
version discovery additionally uses `meta.fabricmc.net`.

## Geometry validation without Fabric downloads

The core is an independent module targeting Java 21. It deliberately has no Minecraft,
Forge, CreativeCore, JOML, or downloaded test-framework dependency:

```bash
cd /workspace/LittleTiles/fabric/core
../../gradlew --no-daemon --max-workers=2 check
```

Use the same JDK/proxy environment as above. Alternatively, with JDK 21 or
newer installed, from `fabric/`:

```bash
mkdir -p core/build/offline-tests
javac --release 21 -d core/build/offline-tests \
  core/src/main/java/com/creativemd/littletiles/core/MicroBox.java \
  core/src/geometryTest/java/com/creativemd/littletiles/core/MicroBoxTest.java
java -cp core/build/offline-tests com.creativemd.littletiles.core.MicroBoxTest
```

`MicroBox` ports the 16-unit grid, overlap, adjacent merge and six-slab
subtraction behavior of `LittleTileBox`, with immutable geometry and overflow
checks. It adds platform-independent quarter turns and mirrors. The seeded
suite checks negative coordinates, boundary contacts, exact voxel coverage,
volume conservation, and reversible transforms. These checks do not establish
Minecraft integration or gameplay parity.

See [PORTING.md](PORTING.md) for the remaining features and acceptance checks.

## Implemented gameplay subset

- Hammer: **G** or right-click air opens the cutting workbench. Select an ordinary
  block stack directly from your inventory, set XYZ (1–16 sixteenths) and batch
  quantity (1–64), then Hammer it. The server validates the real input, consumes
  whole blocks and refunds every unused micro-volume. No items are held in a
  temporary GUI slot. A front-view grid previews the chosen dimensions.
  Use on a supported world cube to convert it; use on microtiles to extract a
  cuboid; sneak-use harvests the entire group.
- Chisel/saw: configurable voxel, sized box, slice, two-corner region, whole-cuboid
  extraction and split modes. Hold the tool and press **G** to set grid and dimensions.
- Tile/blueprint placement: normal, fill empty space, overwrite, or reusable stencil
  removal. **G** selects the mode; survival charges/refunds actual edited volume.
- Wrench: rotate a block's group; sneak-use mirrors it. Mallet merges adjacent
  matching cuboids. Color tube uses offhand dye. Collision tool toggles collision.
- Bag/container: right-click opens portable material storage; sneak-right-click
  sorts it. Bag inventory uses persistent item components and vanilla menu sync.
- Blueprint: two right-clicks select corners; the first also captures a single
  block. Sneak-use pastes. Survival pays exact material volume from loose blocks,
  tile items and bags, returning fractional leftovers. Failed placement costs nothing.
- R/F rotate/mirror held tile items or blueprints around the look axis. Commands
  `/littletiles undo` and `/littletiles redo` work in creative, with 30 actions
  and conflict checks. Multi-block pastes undo as one action.

## Ordinary mod blocks and rendering

Material support is namespace independent: ordinary full-cube block states with
model rendering, no fluid and no block entity can be cut directly. Machines,
containers and entity-rendered blocks are excluded. Packs can add exclusions to
`littletiles:uncuttable`. Block-state properties are preserved; rotations also
transform standard axis/facing properties. Complex custom state semantics may
require a dedicated adapter.

The renderer uses Fabric model emission and original textures, render layers,
vertex attributes and contextual tint providers. Textures are cropped rather than
stretched. Cached meshes remove internal/neighbor-covered surfaces and submit
geometry by render layer. The dense 512-cube test reduces 3,072 faces to 384 and
uses one geometry submission for its single layer. Render-only compaction now
reduces those 384 remaining coplanar faces to six while preserving the 512
separately editable cuboids. Detailed models with interior faces are excluded
from this merge optimization. This is a geometry test,
not an FPS benchmark. Custom dynamic models must obey Fabric geometry-key rules.

A separate test mod exercises an emission-only, world-dependent tinted model.
No production third-party modpack, Sodium or tooltip integration has yet been
tested; generic API support is not a guarantee for every mod block.

Current limits: 512 tiles per edit, 32 blocks per selection axis and 512 blocks
per selection region. Functional structures and additional color settings are
deferred at the user's request. Complex/deformed shape tools, textured placement
ghosts, additional selection overlays and remaining original tool modes are still outstanding; this
is not the complete original toolset. Old worlds/blueprints are not imported.

## Integration checks

From `fabric/`, using the JDK/proxy environment above:

```bash
../gradlew --no-daemon --max-workers=2 build runGameTest
```

The `gametest` source set is separate from the released mod. Tests exercise a
real Minecraft server, including codecs, storage/update data, drops, collision,
survival material conservation, placement rejection, bag persistence/slot
locking, blueprint payment/transforms and grouped history/conflict checks.

Client render/menu smoke testing additionally requires a graphical display:

```bash
../gradlew --no-daemon --max-workers=2 runClientGameTest
```

The client test creates a disposable world, waits for synchronized micro-tile
data, captures world/item screenshots, and checks portable inventory menu data.
Screenshots alone do not establish visual feature parity; inspect them for
missing textures, incorrect geometry, tint and transparency.

Latest validation passed 25 custom server tests (26 including the runner),
108,463 geometry checks, 256,000 randomized surface checks plus adjacency/UV
checks, and the real client test. Client checks cover clipping, transparent
interfaces, model/tint cache invalidation, inventory/menu synchronization and
the actual settings packet round trip. Screenshots are generated under
`build/run/clientGameTest/screenshots/`.

## Placement preview

Held tile items show a depth-tested translucent volume and wire outline at the
predicted placement location. Sneak while holding a blueprint to preview its
paste, including multiple blocks. Green means geometry fits; red indicates a
collision/overlap or unsupported target; amber marks stencil removal. R/F
transforms update after the server synchronizes the held item. The preview and
single-block placement share the same locator, including neighboring-block
fallback and inward overwrite/stencil alignment. Fill/stencil show affected
volume. Preview extraction never mutates world or inventory; drawing uses only
immutable snapshots.

The preview predicts geometry locally. The server still checks survival payment,
reach, protection and permissions; a green ghost is not a guarantee that those
server checks will succeed. The current overlay shows volume rather than a
textured material ghost. Additional color settings and functional structures
remain deferred.
