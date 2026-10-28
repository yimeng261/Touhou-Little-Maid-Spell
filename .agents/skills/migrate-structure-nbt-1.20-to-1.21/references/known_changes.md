# Schema changes handled by `migrate.py`

This is the canonical inventory of every transformation the script applies. If you hit a runtime error and what you see in the SNBT dump is on this list, the script handled it; if not, you've found a new edge case and need to extend `migrate.py`.

## Top-level

- `DataVersion: <old>` → `DataVersion: 3955` (1.21.1)

## Block palette (`palette[]` / `palettes[][]`)

| Block ID | Change |
|---|---|
| `minecraft:grass` | renamed → `minecraft:short_grass` (1.20.3) |
| `minecraft:wither_skeleton_skull` | added `Properties.powered = "false"` |
| `irons_spellbooks:scroll_forge` | added `Properties.waterlogged = "false"` |
| `goety:cursed_cage` | removed obsolete `Properties.powered` |

Other palette entries pass through unchanged. Mojang's own DataFixer would normally apply some of these, but a structure with `DataVersion: 3955` doesn't trigger DFU at all, so the script applies them explicitly.

## Block-entity NBT (in `blocks[i].nbt`)

- **`ForgeData` → `NeoForgeData`** (rename, preserving content). This is critical: `maid_bed.ForgeData.BedColor: 13` becomes `maid_bed.NeoForgeData.BedColor: 13` — without this, all coloured maid beds revert to a default colour at load.
- All ItemStack-shaped compounds inside (recursively, via `migrate_items_anywhere`) are migrated to 1.21 component format.
- All FluidStack-shaped compounds inside (`{FluidName, Amount, Tag?}` → `{id, amount, components?}`) are migrated.
- For `minecraft:mob_spawner`: the nested `SpawnData.entity` and every `SpawnPotentials[i].data.entity` recurse through `migrate_entity_nbt`.
- After all the above, a final scrub strips any leftover `forge:`-prefixed keys.

## Entity NBT (in `entities[i].nbt`)

Dropped entirely:

- `ForgeData`, `ForgeCaps`, `CitadelData`, `CanUpdate`.

Renamed as-is: `forge:spawn_type` → `neoforge:spawn_type` (NeoForge `Mob` reads it back as a `MobSpawnType` name).

Renames:

- `ModelId` → `model_id` (Touhou Little Maid).
- `ActiveEffects` → `active_effects` (full schema rebuild — see below).
- `Attributes` → `attributes` (full schema rebuild — see below).

`MaidSchedulePos.{Work, Sleep, Idle}` — each old `{X, Y, Z}` Compound becomes an `IntArray [I; x, y, z]`. This is the BlockPos format change; missing it crashes `SchedulePos.save` with NPE.

Inventories:

- Bare-list inventories (`HandItems`, `ArmorItems`, `Items`, `Inventory`) — items in place migrated.
- Wrapped inventories (`MaidInventory`, `MaidBaubleInventory`, `MaidHideInventory`, `MaidTaskInventory`) — `{Size, Items}` wrapper kept (or created if it was a bare list); `Items` recursed.
- Items in `HandItems`/`ArmorItems` whose id is in `BLACKLIST_EQUIPPED` (currently goety cursed_paladin set) are replaced with `{}`. Items in chests are unaffected.

Recursion: `Passengers[]` and any nested ItemStack/FluidStack/BlockPos shape via `migrate_items_anywhere`.

## Attribute migration (1.20 → 1.21)

Old shape: `{Name: String, Base: Double, Modifiers?: [{Name, Operation:Int, Amount, UUID}]}`.
New shape: `{id: String, base: Double, modifiers?: [{id: String, operation: String, amount: Double}]}`.

Attribute ID renames:

| Old ID | New ID |
|---|---|
| `forge:entity_gravity` | `minecraft:generic.gravity` |
| `forge:swim_speed` | `neoforge:swim_speed` |
| `forge:nametag_distance` | `neoforge:nametag_distance` |

`forge:step_height_addition` is special: it was *added* to the entity's own step height, while
`minecraft:generic.step_height` is absolute (0.6 for most mobs). Entity attributes with base 0 are
dropped so the type default applies; a non-zero base becomes `0.6 + base` with a warning (check the
entity type's real default). Item modifiers are additive on both sides and are just renamed.
`minecraft:generic.water_movement_efficiency` is **not** the swim-speed equivalent (default 0.0,
depth-strider-like), so never map `forge:swim_speed` 1.0 onto it.

Any attribute under a namespace in `ATTRIBUTE_NAMESPACE_DROP` (currently `aces_spell_utils`) is dropped.

Attribute IDs that are dropped (no 1.21 equivalent):

| Old ID | Why |
|---|---|
| `caelus:fall_flying` | Caelus mod isn't in NeoForge 1.21 deps and `minecraft:generic.fall_flying` does NOT exist (a tempting wrong rename — generates an `Ignoring unknown attribute 'minecraft:generic.fall_flying'` warning). |

Modifier `Operation: Int` → `operation: String` enum: `0 → add_value`, `1 → add_multiplied_base`, `2 → add_multiplied_total`. Modifier `Name → id` (kept verbatim if it has a colon, otherwise mapped via `ATTRIBUTE_MODIFIER_NAME_RENAMES` for known cases like `"Random spawn bonus" → "minecraft:random_spawn_bonus"`, otherwise prefixed with a legacy namespace fallback).

## Effect migration (1.20 → 1.21)

Old shape: `{forge:id?: String, Id: Int, Ambient, ShowParticles, ShowIcon, Duration, Amplifier, CurativeItems?, HiddenEffect?}`.
New shape: `{id: String, duration, amplifier, ambient?, show_particles?, show_icon?, hidden_effect?}`.

ID resolution: prefer `forge:id` (string); else look up `Id` in the vanilla numeric→namespaced table (resistance is 11, regeneration 10, etc., 33 entries through `darkness`); else drop the entry (mod-only effect with no string id).

`CurativeItems` is dropped (Forge-only; in 1.21 NeoForge added `neoforge:cures: List[String]` instead, but reconstructing it is not worthwhile for migration).

## Item migration (anywhere a stack appears)

Old shape: `{id: String, Count: Byte, Slot?: Byte, tag?: Compound, ForgeCaps?: Compound}`.
New shape: `{id: String, count: Int, Slot?: Byte, components?: Compound}`.

`Count: Byte` → `count: Int`. `ForgeCaps` on the item is **dropped** (mod cap state on individual items, e.g. `goety:focus_bag.ForgeCaps.Parent.Items` — the item-as-container's contents — is recoverable from `tag.cap` which we preserve).

`tag` → `components` translation:

| Old key in `tag` | New key in `components` |
|---|---|
| `Damage: Int` | `minecraft:damage: Int` |
| `RepairCost: Int` | `minecraft:repair_cost: Int` |
| `CustomModelData: Int` | `minecraft:custom_model_data` |
| `Unbreakable: Byte` | `minecraft:unbreakable` |
| `Enchantments: [{id, lvl}]` | `minecraft:enchantments: {levels: {<id>: <int>}}` |
| `StoredEnchantments: [...]` | `minecraft:stored_enchantments: {levels: {...}}` |
| `display.Name` | `minecraft:custom_name` |
| `display.Lore` | `minecraft:lore` |
| `display.color: Int` (leather dye) | `minecraft:dyed_color: {rgb: Int}` |
| `display.<other>` | preserved under `minecraft:custom_data.display` |
| `pages` (writable_book) | `minecraft:writable_book_content: {pages: [{raw: <text>}, ...]}` |
| `pages` + `title`/`author`/`generation`/`resolved` (written_book) | `minecraft:written_book_content: {pages, title, author, generation, resolved}` |
| `AttributeModifiers: [{...}]` | `minecraft:attribute_modifiers: {modifiers: [{type, id, amount, operation, slot?}]}` — drops UUID, collapses `minecraft:generic.x` → `minecraft:x` |
| `BlockEntityTag: {...}` (shulker boxes, signs, command blocks) | `minecraft:block_entity_data: {...}` — contents recurse through block-entity migration |
| `EntityTag: {...}` (spawn eggs, mob buckets) | `minecraft:entity_data: {...}` — contents recurse through entity migration |
| `Potion: <id>` and/or `CustomPotionEffects: [...]` | `minecraft:potion_contents: {potion?, custom_effects?}` |
| `Trim: {material, pattern}` | `minecraft:trim: {material, pattern}` (direct rename) |
| `irons_spellbooks:imbued_spell: String` | `irons_spellbooks:spell_container` (locked=1, level=1) — UNLESS a `spell_container` already exists, in which case the imbued one is silently dropped. The level=1 conversion path emits a stderr `[warn]` line — audit affected items. |
| `<namespaced:key>` | passed through unchanged into `components` |
| Any other unrecognised vanilla key | bundled under `minecraft:custom_data` so nothing is lost |

## FluidStack migration

Old: `{FluidName: String, Amount: Int, Tag?: Compound}`.
New: `{id: String, amount: Int, components?: Compound}`.

Detected wherever a Compound has both `FluidName` and `Amount` keys. Tag is run through `migrate_item_tag` to turn it into components (most fluid-tag content is mod-namespaced and passes through unchanged).

## Books

Already implied by the item table above, but worth calling out:

```
tag: { pages: ["page1", "page2"] }
↓
components: { "minecraft:writable_book_content": { pages: [{raw: "page1"}, {raw: "page2"}] } }
```

Detection: `tag` has a `pages` list AND no `title`/`author` ⇒ writable_book; with title/author ⇒ written_book. The script doesn't distinguish by item id — works for any item that uses the book-pages convention.

## Render-crash blacklist

Items that are valid 1.21 ItemStacks but whose models crash when *rendered on a worn entity*:

- `goety:cursed_paladin_helmet`, `_chestplate`, `_leggings`, `_boots` — Goety 3.0.13 references model `goety:cursed_paladin_armor#outer_armor`, which the mod fails to register with `EntityModelSet`. Equipped on a humanoid renderer, this crashes. Same items in inventories/chests are fine (2D icon).

The script blanks these in `HandItems`/`ArmorItems` only — items in `Items` lists (chest contents) are preserved. To extend: add the item id to `BLACKLIST_EQUIPPED` near the top of `migrate.py`.

## Known limitations / not yet handled

The script's test corpus didn't include these cases, so they are *not* migrated. If you encounter them, extend `migrate.py` and verify with snbt-diff:

- **Banner block-entity `Patterns`** — 1.20.5 changed the schema from `Patterns: [{Pattern: "<short_code>", Color: <int>}]` to `patterns: [{pattern: "<namespaced_id>", color: "<color_name>"}]`. Map short codes (`bs`, `b`, `cs`, `mc`, etc.) to their `minecraft:<full_name>` ids; map color ints (0=white .. 15=black) to color names. If your structures use named/dyed banners, this is needed for them to render correctly.
- **`tag.SkullOwner: Compound`** on player-head items — 1.21 component is `minecraft:profile`. Currently falls into `custom_data` (mods that read from there work; vanilla skin lookup won't).
- **`tag.HideFlags: Int`** — 1.21 hides tooltip sections via per-component `show_in_tooltip` booleans plus a global `minecraft:hide_tooltip` flag. The bitmask doesn't translate cleanly; falls into `custom_data`.
- **Entity-on-armor-stand `Pose` Compound** — vanilla format unchanged, no migration needed; called out only because it *looks* like something that might have changed.
- **`forge_explosion_resistance` / other entity-attribute-modifier UUIDs lacking a `Name`** — the script falls back to a `legacy:<slugified-name>` namespace. This is "best-effort": the modifier loads but loses its source-mod identity. Real fix needs a UUID → modifier-id table, which is mod-specific.
- **Goety entity NBT internal fields** — the script's recursive scrub strips Forge keys but doesn't translate goety-specific 1.20→1.21 schema changes (none observed yet, but mod authors do break things). If a goety mob loads with weird default state, suspect this.
- **Hybrid block-entity NBT that overloads FluidStack fields** — e.g. `goety:haunted_jug` stores `Amount` and `FluidName` at the block-entity NBT root alongside its own `id`. The fluid-stack migration deliberately refuses to touch these (migrating them would clobber the block-entity `id`). Goety needs its own internal DataFixer for these blocks; for now, the fluid contents may render incorrectly until that runs (the structure file still loads).

When you add coverage for any of these, please also extend `evals/evals.json` with a fixture that exercises the case, so future regressions are caught by the eval suite.
