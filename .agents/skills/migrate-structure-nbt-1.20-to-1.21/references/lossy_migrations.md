# What gets dropped (and why)

Some 1.20 NBT data has no clean 1.21 equivalent. Half-migrating it is worse than dropping it — it leaves the file in a state where MC silently swallows the data or, worse, errors at load time. Below is everything the script intentionally discards, with the reasoning.

## Entity-level drops

### `ForgeData` (entity scope)

Entities in 1.20 carry a `ForgeData` Compound for arbitrary mod-attached data. In 1.21 NeoForge the equivalent is `neoforge:attachments` with a totally different on-disk shape (each attachment serialised by its own codec). The 1.20 contents are usually one of:

- Empty (`{}`) — the most common case; just noise.
- A stale UUID like `PetsTarget: <uuid>` — references an entity that won't exist in the freshly-generated world. Worthless to preserve.

So we drop. Mods that need attachment data on freshly-spawned entities will reinitialise on next load via their codec's `default()` method.

### `ForgeCaps` (entity scope)

Same reasoning as `ForgeData` but for capabilities. 1.20 stored multiple namespaces in here (`curios:inventory`, `goety:misc`, `ebwizardry:containment_data`, etc.). 1.21 mods migrated to data attachments and don't read `ForgeCaps` anymore. Reinitialised at spawn.

The notable potentially-lossy case: a maid's curio slots stored their items here. In our case the maid's *visible* equipment lives in `MaidBaubleInventory` (preserved separately), so this loss is largely cosmetic. If you spot a mod that *only* stored items in `ForgeCaps` and you need them, you'd need a custom translator before running this script.

### `CitadelData`, `CanUpdate`

- `CitadelData` — Citadel mod's per-entity state, only meaningful while Citadel runs.
- `CanUpdate` — internal flag, not part of any 1.21 schema.

`forge:spawn_type` is not dropped: it is renamed to `neoforge:spawn_type`, which NeoForge `Mob` saves and loads under that exact key. Structure entities are not re-spawned through `finalizeSpawn`, so dropping it would lose values like `SPAWN_EGG`.

### `caelus:fall_flying` attribute

Caelus is the mod that ported elytra-style flight to attribute form in 1.20. It isn't in our 1.21 NeoForge dep set, and `minecraft:generic.fall_flying` is **not** a vanilla attribute (despite the suggestive name — adding it as a rename produces `Ignoring unknown attribute 'minecraft:generic.fall_flying'` at load).

The right call: drop. If a mod brings back something equivalent later, add a real rename.

## Item-level drops

### `ForgeCaps` on individual items

E.g. `goety:focus_bag.ForgeCaps.Parent.Items` (the bag's contents stored as a capability). Mod-specific, no 1.21 component. We drop, but `tag.cap` (the same bag's mirrored content) goes into `components.minecraft:custom_data.cap` so the data is *near* recoverable — whether the mod still reads it depends on its own codec.

### `irons_spellbooks:imbued_spell` (string)

Iron's Spellbooks 1.21 renamed the imbue mechanism from a separate component to "a `spell_container` with `locked: 1`". The old `imbued_spell: "irons_spellbooks:foo"` is converted to a fresh `spell_container` with `level: 1` if no container already exists; otherwise it's silently dropped (the existing container already represents the spell, often at a higher level).

In practice: imbued weapons keep their spell. The level may be wrong if the imbued spell was the only signal of a non-default level — but in our test data, every imbued weapon also had a `spell_container` with the correct level.

### Unrecognised non-namespaced `tag` keys

Anything in `tag` that isn't on the known-vanilla translation list and doesn't have a colon in the key (i.e. no `mod:` prefix) gets bundled under `components.minecraft:custom_data`. This is *not* lossy in the data-bytes sense — the data is still on the item — but the mod's component codec probably won't read it from there. So it's effectively-lossy unless the mod explicitly looks under `custom_data`. Document any cases you find.

## Equipped-armour blacklist

Currently the only entry is goety's cursed paladin set (helmet/chestplate/leggings/boots). When equipped, these crash the renderer because Goety 1.21 forgot to register the geckolib `outer_armor` layer. The script blanks the `HandItems`/`ArmorItems` slot — same item in chest contents is fine.

If a future Goety release fixes the registration, remove the four IDs from `BLACKLIST_EQUIPPED` and re-run the migration on a fresh copy of the 1.20 source.

## What is *not* lossy (commonly mistaken)

- **`Tags: [...]` on entities** — 1.21 still supports this. Don't drop. (Earlier drafts of the script did drop it; that was wrong.)
- **`Brain` data** — pass-through unchanged. Memories will be re-derived on first tick.
- **Block-entity `ForgeData`** — *renamed* to `NeoForgeData`, not dropped. Critical for things like maid_bed colour.
- **Custom names with JSON component strings** — passed through to `minecraft:custom_name` as-is. Vanilla parses them fine.

## Verification: counting losses

The migration prints nothing on lossy drops by design (a 1.20 source might have hundreds of empty `ForgeData: {}` entries — useless noise). To audit a concrete file, snbt-dump before and after, then `diff` and look at the deletion lines:

```
diff before.snbt after.snbt | grep '^<' | grep -E 'ForgeData|ForgeCaps|forge:|caelus:|imbued_spell' | sort | uniq -c
```

That gives you "this many entries of each kind were removed" for sanity checking.
