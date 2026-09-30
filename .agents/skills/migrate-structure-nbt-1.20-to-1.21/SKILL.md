---
name: migrate-structure-nbt-1.20-to-1.21
description: Mechanically rewrite Minecraft structure NBT files (`*.nbt` under `data/<modid>/structure[s]/`) from 1.20.1 Forge format to 1.21.x NeoForge format — bumps DataVersion, applies block/item/entity schema changes, strips Forge-only fields. Use this skill whenever a Minecraft mod is being ported from 1.20 to 1.21 and structure templates need to be brought along, OR when post-port runtime errors appear that match the canonical 1.20→1.21 NBT breakage signatures: SchedulePos NPE, `No key amount in MapLike[{Amount:..., FluidName:...}]`, `No component with type 'irons_spellbooks:imbued_spell'`, `Ignoring unknown attribute 'forge:...'` or `'caelus:fall_flying'`, lost lectern/item-frame book pages, wrong maid-bed colour, missing `count`/`components` on items, or `No model for layer ... #outer_armor`. Trigger even when the user describes only the symptom without naming the version pair, or asks to "fix old structure files for 1.21" / "convert .nbt to NeoForge format".
---

# Migrate Structure NBT 1.20.1 (Forge) → 1.21.1 (NeoForge)

This skill mechanically rewrites `*.nbt` structure files (the kind found under `data/<modid>/structure[s]/`, used by `StructureTemplate`) from 1.20.1 Forge format to 1.21.1 NeoForge format. It bumps `DataVersion` to **3955**, applies all the schema changes Mojang and NeoForge baked between 1.20.1 and 1.21.1, and strips Forge-only fields that 1.21 NeoForge doesn't recognise.

## When to use

The most common trigger is "I copied my 1.20 mod's structure NBT files into the 1.21 branch and now the world generates broken or crashes." Symptoms map cleanly to schema gaps:

| Runtime symptom | Underlying schema change |
|---|---|
| NPE in `SchedulePos.save` / `NbtUtils.writeBlockPos(null)` | BlockPos went from `{X,Y,Z}` Compound to `IntArray` |
| `Tried to load invalid fluid: 'No key amount in MapLike[{Amount:..., FluidName:...}]'` | FluidStack went from `{FluidName, Amount, Tag}` to `{id, amount, components}` |
| `No component with type: 'irons_spellbooks:imbued_spell'` | Iron's Spellbooks 1.21 dropped `imbued_spell` component; replaced by a locked `spell_container` |
| `Ignoring unknown attribute 'forge:step_height_addition'` etc. | Forge attribute IDs absorbed into vanilla `minecraft:generic.*` or `neoforge:*`; step height addition is additive, see `known_changes.md` |
| `Ignoring unknown attribute 'caelus:fall_flying'` | Caelus is gone in 1.21; no equivalent — drop the entry |
| Maid bed colour wrong, item-frame book pages empty, lectern empty | `tag` → `components` not done; `pages` needs `minecraft:writable_book_content` wrapper; block-entity `ForgeData` needs renaming to `NeoForgeData` |
| `No model for layer goety:cursed_paladin_armor#outer_armor` | Goety 1.21 forgot to register that geckolib layer; only safe to leave that armour in chests, not equipped |

## How to run it

The migration script is `scripts/migrate.py`. Sole dependency is `nbtlib` (any 2.x). One-time setup (the `/tmp/nbtcmp` path is the one already provisioned by the project's working-directory convention; pick any path you like if you don't have it):

```bash
python3 -m venv /tmp/nbtcmp && /tmp/nbtcmp/bin/pip install -q nbtlib
PY=/tmp/nbtcmp/bin/python   # use this to invoke the scripts
```

The two canonical workflows:

```bash
# A) Migrate a 1.20 directory tree into a fresh 1.21 location (recommended — keeps the original).
#    Note the 1.21 datapack convention: `structures/` becomes `structure/` (singular).
$PY scripts/migrate.py \
    --source /path/to/1.20/data/<modid>/structures \
    --dest   /path/to/1.21/data/<modid>/structure \
    --verbose

# B) Migrate in place. --source can be either a directory (recurses *.nbt) or a single file.
$PY scripts/migrate.py --source /path/to/dir
$PY scripts/migrate.py --source /path/to/foo.nbt
```

Useful flags:

- `--target {1.21, 1.21.1, ..., 1.21.8}` — sets DataVersion. Defaults to **1.21.1 (3955)**, which is the only version this script has been actually calibrated against. Other 1.21.x targets just change the DataVersion stamp; the rest of the schema is unchanged between them. For a different 1.21 patch use `--data-version <n>`.
- `--dry-run` — runs every transform in memory but writes nothing. Useful for "did anything blow up parsing this corpus?" sanity checks.
- `--dest` rules: with single-file `--source`, `--dest` may be either a directory (output goes to `DEST/source.nbt`) or an explicit `*.nbt` file path (rename allowed). With directory `--source`, `--dest` must be a directory; the layout is mirrored.

Idempotency: the script is safe to run multiple times. Already-migrated palette entries no-op, items already in `count`/`components` form pass through, DataVersion is re-stamped. Re-running after extending the script is the normal way to apply newly added rules.

## Verification workflow

The mechanical migration almost always succeeds; when it doesn't, the failure mode is "the script missed an edge case for mod-specific data." Always verify before committing — text-diffing the SNBT-serialised forms is the fastest way to see exactly what changed:

```bash
# 1. Dump SNBT BEFORE migrating (1.20 source files).
$PY scripts/snbt_dump.py --source <1.20-dir> --out /tmp/snbt_before

# 2. Migrate.
$PY scripts/migrate.py --source <1.20-dir> --dest <1.21-dir>

# 3. Dump SNBT AFTER.
$PY scripts/snbt_dump.py --source <1.21-dir> --out /tmp/snbt_after

# 4. Diff. Skim for surprises — wrong renames, untouched 1.20 keys, etc.
diff -r /tmp/snbt_before /tmp/snbt_after | less
```

Then run this single grep on the migrated tree — it captures every signature that means "something was left in the old format":

```bash
grep -rPE '(?<!Neo)ForgeData|(?<!Neo)ForgeCaps|forge:|caelus:|FluidName|^ +Count: [0-9]+b|^ +tag: \{|^ +ModelId|^ +Attributes:|^ +ActiveEffects:' /tmp/snbt_after/
```

The negative lookbehinds avoid false-matching `NeoForgeData` / `NeoForgeCaps` (which are valid 1.21 keys).

What the matches mean and how to react:

- **Empty output** — clean migration, ship it.
- **`forge:`, `caelus:`, `ForgeData`, `ForgeCaps` matches** — the recursive scrub didn't reach this path. Check whether the offending compound is inside a container shape the script doesn't know about (e.g. a new mod's custom inventory wrapper). If so, you've found a genuinely new edge case — extend `migrate.py` (see `references/known_changes.md` for the right place to add a rule).
- **`Count: <n>b` or `tag: {`** — an item escaped `migrate_items_anywhere`. Almost certainly the item lives inside a path the recursive walker stops at; trace where in the diff and report.
- **`FluidName`** — same idea but for fluid stacks.
- **`ModelId`, `Attributes:`, `ActiveEffects:`** — top-level entity keys not migrated. If the entity NBT is inside something other than `entities[i].nbt` / `blocks[i].nbt.SpawnData.entity` / `blocks[i].nbt.SpawnPotentials[i].data.entity` / `Passengers[i]`, the script doesn't see it.

For a runtime sanity-check after migration (delete the old test world first — old chunks persist 1.20 NBT regardless of structure files), see **`references/runtime_checklist.md`**.

## When the migration is lossy (deliberate)

Some 1.20 NBT data has no clean 1.21 equivalent and gets dropped instead of half-migrated:

- **Entity `ForgeCaps`** — mod capability data (curios slots state, ebwizardry minion state, goety:misc shake counters). 1.21 mods reinitialise their own attachments at load time, so dropping this is correct; the entity spawns with default mod state.
- **Entity `ForgeData` non-empty content** (e.g. `PetsTarget: <UUID>`) — the UUID won't resolve in a fresh world anyway.
- **Entity `CitadelData`, `CanUpdate`** — mod-specific or internal, no 1.21 counterpart.
- **`caelus:fall_flying` attribute entries** — Caelus mod is gone in our 1.21 deps and `minecraft:generic.fall_flying` is NOT a vanilla attribute (a tempting but wrong rename). Just drop.
- **Items in `HandItems`/`ArmorItems` of an entity if the item id is on the render-blacklist** (currently `goety:cursed_paladin_*` set) — the item is replaced with `{}` (empty slot). Same item in chest contents is preserved (chests render 2D icons, no model lookup).

Block-entity `ForgeData` is treated differently: it's **renamed** to `NeoForgeData`, preserving any data inside (like maid_bed `BedColor: 13`). Read `references/lossy_migrations.md` for the full list and reasoning.

Easy-to-miss cases the script handles (all verified against 1.21.1 jars):

- **Attribute ids keep the `generic.` prefix.** 1.21.1 still registers `minecraft:generic.armor`; the prefix only went away in 1.21.2. Item modifiers must not strip it.
- **Explicitly empty `AttributeModifiers: []`** meant "no modifiers" in 1.20. NeoForge 1.21 treats an empty `minecraft:attribute_modifiers` as absent and falls back to the item's defaults, so the script writes one zero-amount `minecraft:generic.armor` placeholder and prints a `[warn]`.
- **Touhou Little Maid chairs** (`touhou_little_maid:chair`): `MountedHeight` / `TameableCanRide` / `OwnerUUID` become `mounted_height` / `tameable_can_ride` / `owner_uuid`. The old keys are silently ignored, which loses the seat height and owner.

Not handled (fix by hand with nbtlib when you see it in the diff): a Goety `cursed_cage` holding `item: {id: "minecraft:air", Count: 0b}` should become `item: {}`. Otherwise 1.21 logs `Item must not be minecraft:air`.

## What it does in detail

For an exhaustive list of the transformations grouped by area (block palette, entity NBT, item NBT, fluid stacks, mob_spawner SpawnData/SpawnPotentials), see **`references/known_changes.md`**.

For the runtime checklist (delete-the-world-save advice, expected-noise warnings, what a clean migration log should look like), see **`references/runtime_checklist.md`**.

## When to update the script

If you discover a new schema change that's not handled — e.g. a new mod releasing a 1.21 update that renames one of its NBT keys — the right move is:

1. Find a small example NBT that demonstrates the issue (one before, one after if you have a 1.21 reference).
2. Add the rule to the relevant section of `migrate.py`. The script is structured by domain: `migrate_palette_entry`, `migrate_item_tag`, `migrate_attribute`, `migrate_effect`, `migrate_entity_nbt`, `migrate_block_entity_nbt`, plus the recursive walker `migrate_items_anywhere` for item-stack-shaped or fluid-stack-shaped or BlockPos-shaped compounds nested anywhere.
3. Re-run on a test corpus and snbt-diff to verify the rule fires only where intended.

The script's structure is intentionally heuristic-shape-based (`_looks_like_old_item_stack`, `_looks_like_old_block_pos`, `_looks_like_old_fluid_stack`) so new contexts (mod containers we've never heard of) get migrated automatically as long as the inner data has the standard shape.
