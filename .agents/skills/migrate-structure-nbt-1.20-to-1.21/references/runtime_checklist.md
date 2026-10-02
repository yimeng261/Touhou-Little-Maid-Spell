# Runtime checklist after migration

Migrating the NBT files is half the job — you also need to verify the structure actually loads correctly at world-gen time. Here's the routine.

## 1. Delete any test world that already loaded the old NBT

This is the single biggest source of "I migrated everything but I'm still seeing forge:* warnings." Once Minecraft has saved chunks containing entities or block-entities with old-format NBT, those old-format entries are persisted to the region files. Subsequent world loads read region files, not your structure NBT — so updating the structure templates does nothing for already-generated terrain.

Before re-testing:

- Delete `run/saves/<your-test-world>/region/`, OR
- Delete the entire test world, OR
- Use `/locate` in a fresh, unexplored chunk far from where you previously visited.

## 2. Read the world-gen log for these patterns

When you spin up the client and approach an unexplored area where one of your structures spawns, the log should look something like:

```
[Server thread/INFO] [minecraft/LocateCommand]: Locating element <modid>:<structure> took N ms
[c2me-worker-X/DEBUG] [...]: Prevented finalizeSpawn processing for maid in <structure> at BlockPos{...}
```

That's normal. Things to flag as actual problems:

| Log line | Meaning |
|---|---|
| `Saving entity NBT` NPE at `SchedulePos.save:87` | `MaidSchedulePos.{Work,Sleep,Idle}` still in old `{X,Y,Z}` shape. Re-run migration with the latest script. |
| `Tried to load invalid fluid: 'No key amount in MapLike[{Amount:..., FluidName:...}]'` | A FluidStack was missed. Check whether the surrounding block-entity / SpawnData path is one the script's recursion reaches; if not, file a script update. |
| `Ignoring unknown attribute 'forge:<x>'` (during chunk-gen, not on already-spawned entities) | The script's attribute rename map is missing a key. Compare against `references/known_changes.md`. |
| `Ignoring unknown attribute 'minecraft:generic.fall_flying'` | An older version of the script wrongly mapped `caelus:fall_flying`. Re-run with the current script which drops it. |
| `Ignoring unknown attribute 'caelus:<anything>'` (in NewEntity, not from old chunks) | Caelus survived the migration into a place you didn't expect. |
| `Tried to load invalid item: 'No component with type: ...'` | A namespaced `tag` key didn't map to a real 1.21 component. Probably a mod-specific component that was renamed/removed. Add a translator. |
| `No model for layer <mod>:<armor>#outer_armor` | An armour item is equipped on an entity but its geckolib model layer isn't registered. Add the item ID to `BLACKLIST_EQUIPPED` to blank-out worn instances. |
| `Block-attached entity at invalid position: BlockPos{...}` | An item_frame / painting is at a position where the attachment block doesn't exist. Usually means a palette entry was renamed to something with different attachment rules, or the original 1.20 structure had stale art. Investigate per-case. |
| `Empty or non-existent pool: minecraft:` | Jigsaw template_pool referencing the empty string. Not an NBT issue — it's a `worldgen/template_pool/*.json` config pointing somewhere bad. |

## 3. Acceptable noise

These warnings/errors don't block the migration and don't crash anything:

- `c2me-worker-N/DEBUG` lines about anchor_core, finalizeSpawn — these are debug-level diagnostics from Touhou Little Maid Spell's own mixin layer.
- `Unprimed heightmap: WORLD_SURFACE_WG` — c2me chunk-status oddity; does not break gen.
- `JigsawPlacement: Empty or non-existent pool: minecraft:` — annoying but non-fatal; trace to which structure references an empty pool and fix in the worldgen JSON.

## 4. Visual checks per structure

Spawn the structure with `/place template <modid>:<name>` (or `/locate` + travel) and visually verify:

- **Maid beds**: cushion colour matches what was authored. Wrong colour = `ForgeData → NeoForgeData` rename was missed for that block-entity.
- **Lecterns / item_frames with books**: the book has pages when right-clicked. Empty pages = `tag.pages` → `writable_book_content` conversion failed.
- **NPC equipment**: armour and weapons are visible on the right slots. Missing = item migration dropped them, OR the item is on the render-blacklist (check `references/known_changes.md`).
- **Chests**: contents are present when opened. Items with funny tooltips ("(custom data)") are signs of a tag→component fallback to `minecraft:custom_data`.

## 5. Smoke test the build

```bash
./gradlew compileJava processResources
```

Doesn't catch NBT issues directly (compileJava doesn't validate resources), but catches any Java code that still references renamed identifiers.
