# Feature parity tracking

Target: Minecraft **1.21.10**, Fabric, Java 21. Source baseline: the parent
project's 1.7.10 Forge implementation (279 Java files). The original feature set remains the long-term reference. Current user priority
is ordinary mod-block cutting, render optimization and tool modes. Functional
structures and additional color settings are deferred. A dependency with no
modern equivalent requires a behavioral replacement when its feature is resumed.

The user requires a new mod for new Minecraft worlds. Importing 1.7.10 worlds,
NBT, configurations or old blueprints/schematics is out of scope. New block,
item and data formats may be designed for Fabric without preserving legacy
identifiers or serialization. Persistence of worlds and blueprints created
with the new mod remains required.

## Implemented and checked

- Independent Fabric build definition and lifecycle entry point added. Loom
  1.18.4 dependency resolution, Java compilation, resource expansion and remapped
  JAR packaging passed with Minecraft 1.21.10 / Loader 0.19.5 / API 0.138.4.
- Geometry core: legacy 16-unit scale, overlap, intersection, exact adjacent
  merge, six-slab subtraction, translation, axis rotation and mirroring.
  Its behavioral suite passed 108,463 checks across 1,000 seeded geometry
  cases, both in Gradle and directly on Java 21.
- Registered micro-tile block/entity, validated tile codecs, block/item data
  persistence, selection/collision shapes, exact drops and pick-block data,
  block-entity synchronization and emissive-material light levels.
- Basic hammer/saw/chisel/wrench/color/collision/mallet interactions, survival
  extraction, portable inventory with persistent components and locked bag
  slots, multi-block blueprint copying/placement and exact material payment
  with fractional refunds. These do not yet replace every original tool mode.
- Creative undo/redo with 30 grouped actions, permission/reach checks, conflict
  protection, disconnect/server-stop cleanup and preflight of all edited blocks.
- Typed preview transform requests and R/F bindings; immutable render snapshots,
  textured/tinted cuboid rendering and stack-data-based item rendering.
- Server integration tests and a real client smoke test are included. The
  client test verified world/item tile data and portable menu synchronization;
  generated screenshots were inspected for the stone/glass sample and bag UI.
  Latest local validation passed: 25 custom server tests (26 including the
  runner's own test), the 108,463-check geometry suite, remapped build, and the
  client smoke test including live tint updates and a real C2S preview rotation
  request followed by inventory synchronization. Xvfb logged a missing standard
  cursor warning on shutdown; the test runner exited successfully and the
  screenshots were inspected. Two real multiplayer clients remain untested.
  `.github/workflows/fabric.yml` runs the build, server tests and Xvfb client
  smoke test; the hosted CI job itself has not yet been run.

## Required work and acceptance criteria

| Feature | Legacy source | Remaining implementation and acceptance |
| --- | --- | --- |
| Micro-block storage and placement | `common/blocks`, `common/tileentity`, `common/items/LittleTilePlacementPlan.java` | Registry entries, block entity codecs, collision/selection shapes, server-authoritative edits; place, save, reload and break microtiles on dedicated server |
| Materials and colored blocks | `common/material`, `common/utils/LittleTileBlock*.java` | Block-state identity/properties, tint and material accounting; opaque/translucent materials and survival refunds |
| Mesh rendering and previews | `client/render`, `client/util3d` | Modern render pipeline, culling, OBJ/deformed meshes and previews; verify world/item render, selection, transparency and collision overlay |
| Hammer, saw, wrench, chisel | `common/items/ItemHammer.java`, `ItemLittleSaw.java`, `ItemLittleWrench.java`, `ItemLittleChisel.java` | Port all modes and interaction rules, corner selection, rotation/mirror and survival/creative behavior |
| Other tools | `ItemColorTube.java`, `ItemRubberMallet.java`, `ItemCollisionTool.java` | Tint editing, mallet actions and collision editing; validate permissions and item state persistence |
| Blueprints, multi/partial tiles | `ItemRecipe.java`, `ItemMultiTiles.java`, `ItemPartialTiles.java` | Components/codecs, copy/paste, previews and cross-block placement; save/reload blueprints |
| Container and little bag | `common/bag`, `common/gui/bag`, `ItemTileContainer.java`, `ItemLittleBag.java` | Inventory persistence, material counts, sorting, screens and server sync; no duplication/loss after reconnect |
| Screens and configuration | `common/gui`, `LittleTilesConfig.java`, embedded CreativeCore/N2ConfigApi | Replace legacy ModularUI2/Forge GUI and configuration bindings; validate every mode/setting and dedicated-server class isolation |
| Structures | `common/structure` | Doors, chairs, beds, ladders, fixed structures, axis previews; motion, seating, sleep and climbing with save/reload and multiplayer |
| Undo/redo | `common/history` | Dimension/player-scoped atomic change plans, 30-action limit, conflict handling, redo invalidation and disconnect/server-stop cleanup |
| Network protocol | `common/packet` | Typed Fabric payloads for place/block/rotate/flip/item/cursor/history, bounds and reach/permission validation; two-client sync and malformed-payload tests |
| Pick block, shortcuts and events | `common/events`, `mixin`, `client/ChiselCornerMouseHandler.java` | Modern callbacks/key bindings/mixins; middle-click/NBT preservation, tool shortcuts and interaction precedence |
| Recipes, assets and localization | `LittleTiles.java`, `src/main/resources` | Namespaced lowercase IDs, item models, recipes/tags and language JSON; preserve tool recipes and translated controls |
| Compatibility integrations | `waila`, `NEICompat`, `BogoCompat`, `AngelicaCompat` | Replace old loader-specific integration with suitable modern tooltip/recipe/sorting/render hooks; preserve underlying behavior even when optional mods are absent |

## Porting boundaries

Do not compile 1.7.10 sources against Fabric by creating fake Forge/Minecraft
classes or register inert substitutes for old tools. No legacy-data converter
is required. Keep original source/assets available until every
feature above has a verified replacement. The new artifact must stay visibly
marked as a development port until parity is achieved.

Required release gates: successful remapped build, common code safe on a
dedicated server, successful server startup, integrated-client smoke test,
two-client placement/edit/history synchronization, gameplay checks for every
row above, and save/reload round trips for worlds, items, blueprints and
structures created with the new mod. Legacy-data migration is not a release gate.
The initial network denials and JDK/proxy trust-store setup problems were
resolved for the build. A Minecraft server test run and an Xvfb/software-GL
client test ran successfully for the implemented subset. Functional structures,
complex/deformed meshes, remaining complex-shape tool modes, full placement
previews and optional integrations remain outstanding. Do not claim complete
feature parity or publish this as the completed port.

## Current priority update

- G opens server-validated tool settings: six cut modes, four placement modes,
  grid and dimensions. Region edits are atomic and material accounting uses
  actual additions/removals. Original complex/deformed shape modes remain open.
- Generic ordinary mod-block handling uses registry state identity, actual-world
  full-cube checks and an extensible exclusion tag. Block entities and fluids
  are rejected. A separate foreign-block fixture verifies directional state,
  direct cutting and all six cut modes without shipping fixture assets.
- Fabric emission captures contextual models, tint, layer and vertex attributes.
  Geometry-key caches, exact face subtraction and clipped UVs replace repeated
  scaled per-tile model rendering. 512 cubes: 3,072 to 384 quads and one geometry
  submission for a single render layer. This does not measure FPS or GPU draws.
- Added 256,000 randomized surface checks, adjacency and UV checks, and client
  assertions for emission-only models, multiple layers, emissive faces, contextual
  tint/cache changes and actual settings network synchronization.
- Full opaque groups occlude terrain and block skylight; partial/transparent
  groups do not fully occlude. Surface lighting samples adjacent cells. Exact
  per-microtile light propagation and terrain-style ambient occlusion remain open.
- Production modpacks, Sodium, named tooltip integrations and two real clients
  remain untested. Keep the artifact marked as a development build.

## Hammer and placement preview follow-up

- Added hammer workbench with live inventory material selection, XYZ grid view,
  batch quantity, explicit block cost and exact fractional refund. Server packets
  carry slot references and bounded dimensions/count, never arbitrary material
  or tile data. Stale material, special item data, insufficient stock and
  block-entity source blocks are rejected. Sneak-use harvests whole groups.
- Replaced repeated pairwise compaction with sorted axis groups; render-only
  compaction merges boundary-only model cuboids while leaving detailed models
  and editable source data intact. The dense 512-cube case now emits six quads.
  Geometry-cache keys also include model identity to avoid reusing baked geometry
  after a block-model reload.
- Added green/red/amber translucent placement-volume previews for tile items and
  single/multi-block blueprint pastes. Shared location math prevents drift from
  server placement. Textured ghosts and client prediction of payment/protection
  remain outstanding.
- Added three server regressions for batch volume conservation, malformed/stale
  hammer requests and compaction identity/holes. Client checks cover the hammer
  screen and real manufacturing packet, preview alignment and blocked overlap.
