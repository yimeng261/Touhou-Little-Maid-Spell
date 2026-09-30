#!/usr/bin/env python3
"""Migrate Minecraft 1.20.1 (Forge) structure NBT files to 1.21.1 (NeoForge) format.

Usage:
    python migrate.py --source <dir-or-file> [--dest <dir>] [--dry-run] [--verbose]

If --dest is omitted the migration is in-place (overwrites source files).
If --source is a directory, every *.nbt under it is migrated recursively.
If --source is a single .nbt file, only that file is processed.

Strategy: mechanical Forge→NeoForge fixups + the vanilla schema changes that are
guaranteed to break load if left alone. Bumps DataVersion to 3955 (1.21.1).

See the skill's SKILL.md and references/ for the full list of changes and the
intentionally lossy parts (ForgeCaps drops, render-blacklist, etc.).
"""
from __future__ import annotations

import argparse
import re
import shutil
import sys
from pathlib import Path
from typing import Any

import nbtlib
from nbtlib.tag import Compound, List, String, Int, Double, Byte, IntArray


# DataVersion table — pick whichever patch version of 1.21 you're targeting.
# Override at runtime with --data-version. 3955 (1.21.1) is the default since that's the
# Forge→NeoForge transition target that this script was originally calibrated for.
DATA_VERSION_BY_TARGET = {
    '1.21':   3953,
    '1.21.1': 3955,
    '1.21.2': 4080,
    '1.21.3': 4082,
    '1.21.4': 4189,
    '1.21.5': 4325,
    '1.21.6': 4435,
    '1.21.7': 4438,
    '1.21.8': 4440,
}
DEFAULT_TARGET = '1.21.1'

# Namespace/prefix used to synthesize a 1.21 ResourceLocation for legacy attribute
# modifiers whose 1.20 `Name` was free text (no namespace). 1.21 requires modifier
# `id` to be a valid ResourceLocation. Override per-project via CLI flags.
DEFAULT_LEGACY_NAMESPACE = 'touhou_little_maid_spell'
DEFAULT_LEGACY_PATH_PREFIX = 'legacy_'
LEGACY_NAMESPACE = DEFAULT_LEGACY_NAMESPACE
LEGACY_PATH_PREFIX = DEFAULT_LEGACY_PATH_PREFIX


def _legacy_modifier_id(s: str) -> str:
    """Build a 1.21-valid namespaced modifier id from a free-text 1.20 `Name`."""
    path = s.lower().replace(' ', '_').replace('.', '_').replace(':', '_')
    return f'{LEGACY_NAMESPACE}:{LEGACY_PATH_PREFIX}{path}'


# 1.20 names an effect's attribute modifier "<effect description id> <amplifier>", e.g.
# "effect.minecraft.strength 1". In 1.21 every effect declares its own modifier id and removes
# modifiers by that id when the effect ends, so a migrated modifier has to carry the same id.
EFFECT_MODIFIER_NAME = re.compile(r'^effect\.([a-z0-9_-]+)\.([a-z0-9_./-]+) \d+$')

# Iron's Spellbooks 1.21 effect modifier ids are irons_spellbooks:mobeffect_<suffix>
# (suffixes read from MobEffectRegistry in 3.15.6); a suffix is not always the effect's registry path.
ISS_EFFECT_MODIFIER_SUFFIXES = {
    'antigravity', 'ascension', 'charged', 'chilled', 'fortify', 'haste', 'oakskin', 'rend', 'slow', 'vigor',
}


def _effect_modifier_id(name: str):
    """1.21 modifier id of the effect that produced a 1.20 effect modifier, or None if unknown."""
    match = EFFECT_MODIFIER_NAME.match(name)
    if not match:
        return None
    namespace, path = match.groups()
    if namespace == 'minecraft':
        return f'minecraft:effect.{path}'
    if namespace == 'irons_spellbooks' and path in ISS_EFFECT_MODIFIER_SUFFIXES:
        return f'irons_spellbooks:mobeffect_{path}'
    print(f'  WARN: no known 1.21 modifier id for effect modifier {name!r}; '
          f'using a legacy id, which the effect will not remove when it ends', file=sys.stderr)
    return None

# Vanilla MobEffect numeric ID → namespaced ID (1.20.x, used to recover effect ID
# when only `Id: Int` is present without `forge:id`).
VANILLA_EFFECT_BY_ID = {
    1: 'minecraft:speed', 2: 'minecraft:slowness', 3: 'minecraft:haste',
    4: 'minecraft:mining_fatigue', 5: 'minecraft:strength',
    6: 'minecraft:instant_health', 7: 'minecraft:instant_damage',
    8: 'minecraft:jump_boost', 9: 'minecraft:nausea',
    10: 'minecraft:regeneration', 11: 'minecraft:resistance',
    12: 'minecraft:fire_resistance', 13: 'minecraft:water_breathing',
    14: 'minecraft:invisibility', 15: 'minecraft:blindness',
    16: 'minecraft:night_vision', 17: 'minecraft:hunger',
    18: 'minecraft:weakness', 19: 'minecraft:poison',
    20: 'minecraft:wither', 21: 'minecraft:health_boost',
    22: 'minecraft:absorption', 23: 'minecraft:saturation',
    24: 'minecraft:glowing', 25: 'minecraft:levitation',
    26: 'minecraft:luck', 27: 'minecraft:unluck',
    28: 'minecraft:slow_falling', 29: 'minecraft:conduit_power',
    30: 'minecraft:dolphins_grace', 31: 'minecraft:bad_omen',
    32: 'minecraft:hero_of_the_village', 33: 'minecraft:darkness',
}

# Old AttributeModifier.Operation int → 1.21 string enum
ATTR_MOD_OPERATION = {0: 'add_value', 1: 'add_multiplied_base', 2: 'add_multiplied_total'}

# 1.20 Forge/3rd-party attribute IDs that were absorbed into vanilla in 1.21.
# Anything not in this map is kept as-is (a mod attribute that's still namespaced).
# Only use this for attributes whose value means the same thing on both sides; entity `base`
# values are copied verbatim.
ATTRIBUTE_ID_RENAMES = {
    'forge:entity_gravity': 'minecraft:generic.gravity',   # both absolute, default 0.08
    'forge:swim_speed': 'neoforge:swim_speed',             # NeoForge kept it (default 1.0); NOT water_movement_efficiency (default 0.0, different meaning)
    'forge:nametag_distance': 'neoforge:nametag_distance',
}

# forge:step_height_addition was ADDED to the entity's own step height (base 0 = no change);
# 1.21 minecraft:generic.step_height is the absolute value (0.6 for most mobs). Item modifiers
# stay additive so they can be renamed; an entity base needs the per-type default, which the
# script doesn't know — base 0 drops the entry (entity default applies), anything else is
# kept as 0.6 + base with a warning to check against the entity type's real default.
STEP_HEIGHT_ADDITION = 'forge:step_height_addition'
STEP_HEIGHT = 'minecraft:generic.step_height'
DEFAULT_STEP_HEIGHT = 0.6

# Attribute namespaces whose mods aren't in our 1.21 deps — any attribute under them is dropped.
ATTRIBUTE_NAMESPACE_DROP = {
    'aces_spell_utils',
}

# Banner pattern short codes → 1.21 registry ids (vanilla BannerPatternFormatFix.PATTERN_ID_MAP)
BANNER_PATTERN_IDS = {
    'b': 'base', 'bl': 'square_bottom_left', 'br': 'square_bottom_right', 'tl': 'square_top_left',
    'tr': 'square_top_right', 'bs': 'stripe_bottom', 'ts': 'stripe_top', 'ls': 'stripe_left',
    'rs': 'stripe_right', 'cs': 'stripe_center', 'ms': 'stripe_middle', 'drs': 'stripe_downright',
    'dls': 'stripe_downleft', 'ss': 'small_stripes', 'cr': 'cross', 'sc': 'straight_cross',
    'bt': 'triangle_bottom', 'tt': 'triangle_top', 'bts': 'triangles_bottom', 'tts': 'triangles_top',
    'ld': 'diagonal_left', 'rd': 'diagonal_up_right', 'lud': 'diagonal_up_left', 'rud': 'diagonal_right',
    'mc': 'circle', 'mr': 'rhombus', 'vh': 'half_vertical', 'hh': 'half_horizontal',
    'vhr': 'half_vertical_right', 'hhb': 'half_horizontal_bottom', 'bo': 'border', 'cbo': 'curly_border',
    'gra': 'gradient', 'gru': 'gradient_up', 'bri': 'bricks', 'glb': 'globe', 'cre': 'creeper',
    'sku': 'skull', 'flo': 'flower', 'moj': 'mojang', 'pig': 'piglin',
}

DYE_COLORS = ['white', 'orange', 'magenta', 'light_blue', 'yellow', 'lime', 'pink', 'gray',
              'light_gray', 'cyan', 'purple', 'blue', 'brown', 'green', 'red', 'black']


def migrate_banner_patterns(patterns: List) -> List:
    """1.20 `Patterns: [{Pattern: "gru", Color: 4}]` → 1.21 `patterns: [{pattern: "minecraft:gradient_up", color: "yellow"}]`."""
    out = List[Compound]([])
    for layer in patterns:
        if not isinstance(layer, Compound):
            continue
        code = str(layer.get('Pattern', ''))
        pattern = code if ':' in code else 'minecraft:' + BANNER_PATTERN_IDS.get(code, code)
        color = int(layer.get('Color', 0))
        out.append(Compound({
            'pattern': String(pattern),
            'color': String(DYE_COLORS[color] if 0 <= color < len(DYE_COLORS) else 'white'),
        }))
    return out


# Touhou Little Maid EntityChair save keys, 1.20 → 1.21
CHAIR_KEY_RENAMES = {
    'MountedHeight': 'mounted_height',
    'TameableCanRide': 'tameable_can_ride',
    'OwnerUUID': 'owner_uuid',
}

# Attribute IDs that no longer exist in 1.21 with no equivalent — drop the entry entirely.
ATTRIBUTE_ID_DROP = {
    'caelus:fall_flying',     # Caelus is gone in our 1.21 deps; NeoForge has no straight replacement
    'forge:entity_reach',     # Forge-only; vanilla `minecraft:player.entity_interaction_range` is player-scoped
    'forge:block_reach',      # Same
    'forge:reach_distance',   # Same
}

# 1.20 Forge AttributeModifier names → 1.21 modifier IDs (used when only legacy `Name` is available)
ATTRIBUTE_MODIFIER_NAME_RENAMES = {
    'Random spawn bonus': 'minecraft:random_spawn_bonus',
    # MaidSpellEventHandler re-adds this +1.0 under the new id if missing; a legacy id would stack with it
    'Maid Step Height Addition': 'touhou_little_maid_spell:maid_step_height',
}

# Entity-level keys to drop entirely (Forge-only / no longer recognized in 1.21).
# `Tags` is preserved (hidden_retreat reference shows it's a valid 1.21 entity field).
DROP_ENTITY_KEYS = {
    'ForgeData', 'ForgeCaps',
    'CitadelData',       # Citadel 1.20-only
    'CanUpdate',         # internal, not in 1.21
}

# Entity-level keys renamed as-is. NeoForge Mob reads `neoforge:spawn_type` (MobSpawnType name),
# e.g. an allay saved as SPAWN_EGG must keep that to behave the same after migration.
RENAME_ENTITY_KEYS = {
    'forge:spawn_type': 'neoforge:spawn_type',
}

# Item-stack inventory fields. "wrapped" means 1.21 requires {Size: Int, Items: List};
# "bare" means it stays as List (HandItems/ArmorItems vanilla pattern).
WRAPPED_INVENTORY_FIELDS = {
    'MaidInventory', 'MaidBaubleInventory', 'MaidHideInventory', 'MaidTaskInventory',
}
BARE_INVENTORY_FIELDS = {'HandItems', 'ArmorItems', 'Items', 'Inventory'}

# Items that crash the renderer when *worn* on an entity (e.g. armor whose 1.21 geckolib
# model layer is unregistered). Safe in chests (rendered as 2D icon) — only equipped slots
# trigger the model lookup. We only blank these in ArmorItems / HandItems / equipment slots.
BLACKLIST_EQUIPPED = {
    # goety 3.0.13 references model "goety:cursed_paladin_armor#outer_armor" which is not registered.
    'goety:cursed_paladin_helmet',
    'goety:cursed_paladin_chestplate',
    'goety:cursed_paladin_leggings',
    'goety:cursed_paladin_boots',
}


# ─── Block palette migration ─────────────────────────────────────────────────

def migrate_palette_entry(entry: Compound) -> None:
    name = str(entry.get('Name', ''))
    props: Compound | None = entry.get('Properties')

    if name == 'minecraft:grass':
        entry['Name'] = String('minecraft:short_grass')
        return
    if name == 'minecraft:wither_skeleton_skull':
        if props is None:
            entry['Properties'] = Compound({'powered': String('false')})
        elif 'powered' not in props:
            props['powered'] = String('false')
        return
    if name == 'irons_spellbooks:scroll_forge':
        if props is None:
            entry['Properties'] = Compound({'waterlogged': String('false')})
        elif 'waterlogged' not in props:
            props['waterlogged'] = String('false')
        return
    if name == 'goety:cursed_cage':
        if props is not None and 'powered' in props:
            del props['powered']
            if not props:
                del entry['Properties']
        return


# ─── Item migration ──────────────────────────────────────────────────────────

# Vanilla `tag` keys that map directly to a component identifier. Value type is preserved.
VANILLA_TAG_TO_COMPONENT = {
    'Damage': 'minecraft:damage',
    'RepairCost': 'minecraft:repair_cost',
    'CustomModelData': 'minecraft:custom_model_data',
    'Unbreakable': 'minecraft:unbreakable',
}


def _migrate_enchantments_list(lst: List) -> Compound:
    """1.20 [{id, lvl}] → 1.21 {levels: {<id>: <int lvl>}}"""
    levels = Compound()
    for ench in lst:
        if isinstance(ench, Compound) and 'id' in ench and 'lvl' in ench:
            levels[str(ench['id'])] = Int(int(ench['lvl']))
    return Compound({'levels': levels})


def _pages_to_book_content(pages: List, *, written: bool, extra: Compound | None = None) -> Compound:
    """Convert 1.20 List[String] pages → 1.21 book_content compound: {pages: [{raw}, ...], ...}."""
    new_pages = List[Compound]([])
    for page in pages:
        text = str(page) if isinstance(page, String) else ''
        new_pages.append(Compound({'raw': String(text)}))
    out = Compound({'pages': new_pages})
    if written and extra is not None:
        if 'title' in extra:
            t = extra['title']
            out['title'] = Compound({'raw': String(str(t))}) if not isinstance(t, Compound) else t
            if 'filtered_title' in extra and 'filtered' not in out['title']:
                out['title']['filtered'] = String(str(extra['filtered_title']))
        if 'author' in extra:
            out['author'] = String(str(extra['author']))
        if 'generation' in extra:
            out['generation'] = Int(int(extra['generation']))
        if 'resolved' in extra:
            out['resolved'] = Byte(int(extra['resolved']))
    return out


def _imbued_spell_to_spell_container(spell_id: str) -> Compound:
    """1.20 string `irons_spellbooks:imbued_spell` → 1.21 `irons_spellbooks:spell_container` (locked=1)."""
    return Compound({
        'maxSpells': Int(1),
        'mustEquip': Byte(0),
        'spellWheel': Byte(0),
        'data': List[Compound]([Compound({
            'index': Int(0),
            'id': String(spell_id),
            'locked': Byte(1),
            'level': Int(1),
        })]),
    })


def _migrate_attribute_modifiers_on_item(lst: List) -> Compound:
    """1.20 `tag.AttributeModifiers: [{AttributeName, Name, Amount, Operation, UUID, Slot?}]`
       → 1.21 `minecraft:attribute_modifiers: {modifiers: [{type, id, amount, operation, slot?}]}`.

    Drops the 1.20 UUID — 1.21 identifies modifiers by string id only. The 1.20 `Name`
    becomes the modifier `id`; `AttributeName` becomes `type`. Vanilla ids keep their
    `generic.` / `player.` prefix: 1.21.1 still registers `minecraft:generic.armor` etc.
    (the prefix was only dropped in 1.21.2).

    An explicitly empty 1.20 list meant "this item has no modifiers". NeoForge 1.21 treats an
    empty `attribute_modifiers` component as absent and falls back to the item's defaults, so an
    empty result gets one zero-amount placeholder modifier to keep the "no modifiers" meaning.
    """
    modifiers = List[Compound]([])
    for m in lst:
        if not isinstance(m, Compound):
            continue
        attr_name = str(m.get('AttributeName', '')) or str(m.get('type', ''))
        if not attr_name:
            continue
        # Forge-attribute renames apply to item modifiers too; step height modifiers are additive
        # on both sides.
        if attr_name == STEP_HEIGHT_ADDITION:
            attr_name = STEP_HEIGHT
        attr_name = ATTRIBUTE_ID_RENAMES.get(attr_name, attr_name)
        if attr_name in ATTRIBUTE_ID_DROP or attr_name.split(':', 1)[0] in ATTRIBUTE_NAMESPACE_DROP:
            continue

        # Build a stable id from the original `Name` (or `id`).
        nm = str(m.get('id', '')) or str(m.get('Name', ''))
        if ':' in nm:
            mod_id = nm
        elif nm in ATTRIBUTE_MODIFIER_NAME_RENAMES:
            mod_id = ATTRIBUTE_MODIFIER_NAME_RENAMES[nm]
        elif nm:
            mod_id = _effect_modifier_id(nm) or _legacy_modifier_id(nm)
        else:
            mod_id = _legacy_modifier_id(attr_name)

        out = Compound({
            'type': String(attr_name),
            'id': String(mod_id),
            'amount': Double(float(m.get('Amount', m.get('amount', 0.0)))),
        })
        op = m.get('Operation', m.get('operation'))
        if isinstance(op, (Int, Byte)):
            out['operation'] = String(ATTR_MOD_OPERATION.get(int(op), 'add_value'))
        elif isinstance(op, String):
            out['operation'] = op
        else:
            out['operation'] = String('add_value')
        if 'Slot' in m:
            out['slot'] = String(str(m['Slot']))
        elif 'slot' in m:
            out['slot'] = m['slot']
        modifiers.append(out)
    if len(modifiers) == 0:
        modifiers.append(Compound({
            'type': String('minecraft:generic.armor'),
            'id': String(_legacy_modifier_id('empty_attribute_modifiers')),
            'amount': Double(0.0),
            'operation': String('add_value'),
            'slot': String('any'),
        }))
        print("  [warn] empty AttributeModifiers → zero-amount placeholder modifier "
              "(NeoForge falls back to item defaults on an empty component)", file=sys.stderr)
    return Compound({'modifiers': modifiers})


def _migrate_potion_contents(potion: Any, custom_effects: Any) -> Compound:
    """1.20 `tag.Potion: String` and/or `tag.CustomPotionEffects: [...]`
       → 1.21 `minecraft:potion_contents: {potion?: id, custom_effects?: [...]}`."""
    out = Compound()
    if isinstance(potion, String) and str(potion):
        out['potion'] = potion
    if isinstance(custom_effects, List):
        translated = List[Compound]([])
        for e in custom_effects:
            if isinstance(e, Compound):
                m = migrate_effect(e)
                if m is not None:
                    translated.append(m)
        if len(translated) > 0:
            out['custom_effects'] = translated
    return out


# Namespaced 1.20 tag keys that are real data components in 1.21; any other namespaced key is custom data.
KNOWN_MOD_COMPONENTS = {
    'irons_spellbooks:spell_container',
}


def migrate_item_tag(tag: Compound) -> Compound:
    """Convert a 1.20 item `tag` Compound to a 1.21 `components` Compound."""
    components: dict[str, Any] = {}
    custom_data: dict[str, Any] = {}

    # Detect book context — handled in a single pass below
    is_writable_book_pages = isinstance(tag.get('pages'), List) and 'title' not in tag and 'author' not in tag
    is_written_book_pages = isinstance(tag.get('pages'), List) and ('title' in tag or 'author' in tag)

    # Potion items: Potion (string) and/or CustomPotionEffects (list) merge into one component.
    has_potion = isinstance(tag.get('Potion'), String) or isinstance(tag.get('CustomPotionEffects'), List)

    for k, v in list(tag.items()):
        if k == 'irons_spellbooks:imbued_spell':
            # 1.21 ISB no longer exposes this component; convert to spell_container.
            # If a spell_container is also present, keep it and drop imbued_spell.
            if 'irons_spellbooks:spell_container' not in tag:
                components['irons_spellbooks:spell_container'] = _imbued_spell_to_spell_container(str(v))
                # Loud warning: this is the silent-degrade path — original level isn't recoverable.
                print(f"  [warn] imbued_spell '{v}' → spell_container with level=1 (audit if original was higher)",
                      file=sys.stderr)
            # else: spell_container already covers it — just drop imbued_spell.
            continue
        if k == 'pages' and (is_writable_book_pages or is_written_book_pages):
            # Handled below via the dedicated book content key.
            continue
        if k in ('title', 'filtered_title', 'author', 'generation', 'resolved') and is_written_book_pages:
            continue  # consumed by writable book content
        if k in ('Potion', 'CustomPotionEffects') and has_potion:
            continue  # consumed by minecraft:potion_contents below
        if ':' in k and k in KNOWN_MOD_COMPONENTS:
            # Namespaced key that is a registered 1.21 data component
            components[k] = v
        elif ':' in k:
            # Namespaced custom tag key (e.g. a mod marker): unregistered component types fail
            # DataComponentPatch decoding, so keep it under custom_data.
            custom_data[k] = v
        elif k == 'Enchantments':
            components['minecraft:enchantments'] = _migrate_enchantments_list(v)
        elif k == 'StoredEnchantments':
            components['minecraft:stored_enchantments'] = _migrate_enchantments_list(v)
        elif k == 'AttributeModifiers' and isinstance(v, List):
            components['minecraft:attribute_modifiers'] = _migrate_attribute_modifiers_on_item(v)
        elif k == 'BlockEntityTag' and isinstance(v, Compound):
            # Shulker boxes, signs, command blocks, etc. The contents recurse through
            # block-entity migration so nested items/fluids/Forge keys also get fixed.
            be = Compound(v)
            migrate_block_entity_nbt(be)
            components['minecraft:block_entity_data'] = be
        elif k == 'EntityTag' and isinstance(v, Compound):
            # Spawn eggs, mob-bucket entities. Recurse through entity migration.
            ent = Compound(v)
            migrate_entity_nbt(ent)
            components['minecraft:entity_data'] = ent
        elif k == 'Trim' and isinstance(v, Compound):
            components['minecraft:trim'] = v
        elif k == 'display':
            # {Name?, Lore?, color?, ...} → spread across components
            if isinstance(v, Compound):
                if 'Name' in v:
                    components['minecraft:custom_name'] = v['Name']
                if 'Lore' in v:
                    components['minecraft:lore'] = v['Lore']
                # Leather-armour dye: 1.20 `display.color: Int` → 1.21 `minecraft:dyed_color: {rgb: Int}`.
                if 'color' in v and isinstance(v['color'], Int):
                    components['minecraft:dyed_color'] = Compound({'rgb': v['color']})
                # Anything else under `display` (LocName, MapColor, sub-keys we don't translate)
                # is preserved under custom_data so a mod that reads from there still works.
                leftover = Compound({kk: vv for kk, vv in v.items()
                                     if kk not in ('Name', 'Lore', 'color')})
                if leftover:
                    custom_data['display'] = leftover
        elif k == 'Unbreakable':
            if bool(v):
                unbreakable = Compound()
                if int(tag.get('HideFlags', 0)) & 4:
                    unbreakable['show_in_tooltip'] = Byte(0)
                components['minecraft:unbreakable'] = unbreakable
        elif k in VANILLA_TAG_TO_COMPONENT:
            components[VANILLA_TAG_TO_COMPONENT[k]] = v
        else:
            # Unknown vanilla key — preserve under custom_data so nothing's lost
            custom_data[k] = v

    if is_writable_book_pages:
        components['minecraft:writable_book_content'] = _pages_to_book_content(tag['pages'], written=False)
    elif is_written_book_pages:
        components['minecraft:written_book_content'] = _pages_to_book_content(tag['pages'], written=True, extra=tag)

    if has_potion:
        contents = _migrate_potion_contents(tag.get('Potion'), tag.get('CustomPotionEffects'))
        if len(contents) > 0:
            components['minecraft:potion_contents'] = contents

    if custom_data:
        components['minecraft:custom_data'] = Compound(custom_data)
    return Compound(components)


def migrate_item(item: Compound) -> Compound:
    """Convert a 1.20 ItemStack NBT to 1.21 format."""
    if not isinstance(item, Compound) or len(item) == 0 or 'id' not in item:
        return item  # empty slot or already empty compound

    # Idempotency: if the item is already in 1.21 form (lowercase count, no Count/tag),
    # short-circuit so we don't strip an existing `components` Compound by rebuilding `out`
    # from scratch. The shape-based migrate_items_anywhere already skips these — but
    # migrate_items_in_list (called for HandItems/ArmorItems/Items/Inventory) calls
    # migrate_item unconditionally, and would otherwise drop components on re-runs.
    if 'count' in item and 'Count' not in item and 'tag' not in item:
        return item

    out = Compound()
    out['id'] = item['id']

    # Count: Byte → count: Int
    if 'count' in item:
        out['count'] = item['count']
    elif 'Count' in item:
        out['count'] = Int(int(item['Count']))
    else:
        out['count'] = Int(1)

    if 'Slot' in item:
        out['Slot'] = item['Slot']

    # Drop ForgeCaps that ride on individual items (e.g. goety:focus_bag). Mod rebuilds defaults at load time.
    # tag → components
    if 'tag' in item:
        comps = migrate_item_tag(item['tag'])
        if len(comps) > 0:
            # Defensive: scrub Forge keys that may have been carried via namespaced tags
            _scrub_forge_recursively(comps)
            out['components'] = comps

    return out


def migrate_items_in_list(lst: List) -> List:
    """Map migrate_item over a List."""
    new = List[Compound]([])
    for it in lst:
        new.append(migrate_item(it))
    return new


def migrate_fluid_stacks_in_list(lst: List) -> List:
    new = List[Compound]([])
    for fs in lst:
        if isinstance(fs, Compound) and _looks_like_old_fluid_stack(fs):
            new.append(migrate_fluid_stack(fs))
        else:
            new.append(fs)
    return new


# ─── Effects migration ───────────────────────────────────────────────────────

def migrate_effect(eff: Compound) -> Compound | None:
    """Convert 1.20 ActiveEffects entry to 1.21 active_effects entry. Returns None if unrecoverable."""
    out = Compound()

    # Resolve id: prefer forge:id (string), else look up Id (int) in vanilla table
    eff_id = None
    if 'forge:id' in eff:
        eff_id = str(eff['forge:id'])
    elif 'id' in eff:
        eff_id = str(eff['id'])
    elif 'Id' in eff:
        eff_id = VANILLA_EFFECT_BY_ID.get(int(eff['Id']))

    if not eff_id:
        return None
    out['id'] = String(eff_id)

    if 'Duration' in eff:
        out['duration'] = Int(int(eff['Duration']))
    elif 'duration' in eff:
        out['duration'] = eff['duration']

    if 'Amplifier' in eff:
        out['amplifier'] = Byte(int(eff['Amplifier']))
    elif 'amplifier' in eff:
        out['amplifier'] = eff['amplifier']

    for old, new in [('Ambient', 'ambient'), ('ShowParticles', 'show_particles'), ('ShowIcon', 'show_icon')]:
        if old in eff:
            out[new] = Byte(int(eff[old]))
        elif new in eff:
            out[new] = eff[new]

    if 'HiddenEffect' in eff:
        sub = migrate_effect(eff['HiddenEffect'])
        if sub is not None:
            out['hidden_effect'] = sub

    return out


def migrate_active_effects(lst: List) -> List:
    new = List[Compound]([])
    for eff in lst:
        if isinstance(eff, Compound):
            m = migrate_effect(eff)
            if m is not None:
                new.append(m)
    return new


# ─── Attributes migration ────────────────────────────────────────────────────

def migrate_attribute(attr: Compound) -> Compound | None:
    """1.20 Attribute: {Name: String, Base: Double, Modifiers?: [{Name, Operation, Amount, UUID}]}
       1.21 attribute: {id: String, base: Double, modifiers?: [{id: String, operation: String, amount: Double}]}"""
    out = Compound()

    name = None
    if 'id' in attr:
        name = str(attr['id'])
    elif 'Name' in attr:
        name = str(attr['Name'])
    if not name:
        return None
    if name in ATTRIBUTE_ID_DROP or name.split(':', 1)[0] in ATTRIBUTE_NAMESPACE_DROP:
        return None
    step_addition = name == STEP_HEIGHT_ADDITION
    name = STEP_HEIGHT if step_addition else ATTRIBUTE_ID_RENAMES.get(name, name)
    out['id'] = String(name)

    if 'Base' in attr:
        out['base'] = Double(float(attr['Base']))
    elif 'base' in attr:
        out['base'] = attr['base']
    if step_addition:
        addition = float(out.get('base', 0.0))
        if addition == 0.0 and not (attr.get('Modifiers') or attr.get('modifiers')):
            return None
        out['base'] = Double(DEFAULT_STEP_HEIGHT + addition)
        print(f'  WARN: {STEP_HEIGHT_ADDITION} base {addition} -> {STEP_HEIGHT} base '
              f'{DEFAULT_STEP_HEIGHT + addition}; check the entity type\'s default step height', file=sys.stderr)

    mods_old = attr.get('Modifiers') or attr.get('modifiers')
    if mods_old:
        mods_new = List[Compound]([])
        for m in mods_old:
            if not isinstance(m, Compound):
                continue
            mm = Compound()
            # id: prefer existing string id, fallback to Name, fallback to UUID-derived placeholder
            mid = None
            if 'id' in m:
                mid = str(m['id'])
            elif 'Name' in m:
                # Modifier names like "minecraft:step_height_addition" are reused; Forge stored bare names
                nm = str(m['Name'])
                if ':' in nm:
                    mid = nm
                elif nm in ATTRIBUTE_MODIFIER_NAME_RENAMES:
                    mid = ATTRIBUTE_MODIFIER_NAME_RENAMES[nm]
                else:
                    # Effect modifiers take the effect's own 1.21 id. Otherwise 1.21 still needs a
                    # namespaced id for the free-text Forge `Name`: synthesize a stable per-name id
                    # under the configured legacy namespace (see DEFAULT_LEGACY_NAMESPACE).
                    mid = _effect_modifier_id(nm) or _legacy_modifier_id(nm)
            if not mid:
                continue
            mm['id'] = String(mid)
            if 'Amount' in m:
                mm['amount'] = Double(float(m['Amount']))
            elif 'amount' in m:
                mm['amount'] = m['amount']
            op = m.get('Operation', m.get('operation'))
            if isinstance(op, (Int, Byte)):
                mm['operation'] = String(ATTR_MOD_OPERATION.get(int(op), 'add_value'))
            elif isinstance(op, String):
                mm['operation'] = op
            else:
                mm['operation'] = String('add_value')
            mods_new.append(mm)
        if len(mods_new) > 0:
            out['modifiers'] = mods_new

    return out


def migrate_attributes(lst: List) -> List:
    new = List[Compound]([])
    for a in lst:
        if isinstance(a, Compound):
            m = migrate_attribute(a)
            if m is not None:
                new.append(m)
    return new


# ─── Entity migration ────────────────────────────────────────────────────────

def migrate_entity_nbt(nbt: Compound) -> None:
    # Drop Forge fields and other 1.20-only top-level keys
    for k in list(nbt.keys()):
        if k in DROP_ENTITY_KEYS:
            del nbt[k]
    for old, new in RENAME_ENTITY_KEYS.items():
        if old in nbt:
            if new not in nbt:
                nbt[new] = nbt[old]
            del nbt[old]

    # Rename ModelId → model_id (Touhou Little Maid)
    if 'ModelId' in nbt:
        nbt['model_id'] = nbt['ModelId']
        del nbt['ModelId']

    # Touhou Little Maid chair: 1.21 EntityChair only reads the snake_case keys
    if str(nbt.get('id', '')) == 'touhou_little_maid:chair':
        for old, new in CHAIR_KEY_RENAMES.items():
            if old in nbt and new not in nbt:
                nbt[new] = nbt[old]
                del nbt[old]

    # Attributes → attributes
    if 'Attributes' in nbt:
        nbt['attributes'] = migrate_attributes(nbt['Attributes'])
        del nbt['Attributes']

    # ActiveEffects → active_effects
    if 'ActiveEffects' in nbt:
        nbt['active_effects'] = migrate_active_effects(nbt['ActiveEffects'])
        del nbt['ActiveEffects']

    # Bare-list inventories: in-place item migration
    for k in BARE_INVENTORY_FIELDS:
        if k in nbt and isinstance(nbt[k], List):
            nbt[k] = migrate_items_in_list(nbt[k])

    # Blank out items that are dangerous to render in equipment slots
    # (chest contents — also under BARE_INVENTORY_FIELDS as `Items` — keep the items;
    # only HandItems / ArmorItems are equipment slots on entities)
    for k in ('HandItems', 'ArmorItems'):
        if k in nbt and isinstance(nbt[k], List):
            for i in range(len(nbt[k])):
                item = nbt[k][i]
                if isinstance(item, Compound) and 'id' in item and str(item['id']) in BLACKLIST_EQUIPPED:
                    nbt[k][i] = Compound()

    # Wrapped inventories: may already be Compound{Size, Items} (Forge handler format) OR
    # a bare List depending on how the mod serialized. Handle both.
    for k in WRAPPED_INVENTORY_FIELDS:
        if k not in nbt:
            continue
        v = nbt[k]
        if isinstance(v, Compound) and 'Items' in v and isinstance(v['Items'], List):
            v['Items'] = migrate_items_in_list(v['Items'])
        elif isinstance(v, List):
            items = migrate_items_in_list(v)
            nbt[k] = Compound({'Size': Int(len(items)), 'Items': items})

    # Single Item field (item_frame, glow_item_frame)
    if 'Item' in nbt and isinstance(nbt['Item'], Compound):
        nbt['Item'] = migrate_item(nbt['Item'])

    # Passengers: list of nested entity NBTs
    if 'Passengers' in nbt and isinstance(nbt['Passengers'], List):
        for p in nbt['Passengers']:
            if isinstance(p, Compound):
                migrate_entity_nbt(p)

    # Catch mod-specific item lists nested anywhere (e.g. backpack data, sub-config inventories)
    migrate_items_anywhere(nbt)

    # Recurse into nested compounds (covers things like Brain.memories, etc.) — strip stray Forge data
    handled = {'attributes', 'active_effects', 'model_id', 'Item', 'Passengers'} | BARE_INVENTORY_FIELDS | WRAPPED_INVENTORY_FIELDS
    for k, v in list(nbt.items()):
        if k in handled:
            continue
        _scrub_forge_recursively(v)


def _scrub_forge_recursively(node: Any) -> None:
    """Walk a value and drop ForgeData/ForgeCaps/forge:* anywhere they appear."""
    if isinstance(node, Compound):
        for k in list(node.keys()):
            if k in {'ForgeData', 'ForgeCaps'} or k.startswith('forge:'):
                del node[k]
                continue
            _scrub_forge_recursively(node[k])
    elif isinstance(node, List):
        for v in node:
            _scrub_forge_recursively(v)


def _looks_like_old_item_stack(c: Any) -> bool:
    """Compound with old-format ItemStack signature (has Count: Byte and/or tag)."""
    if not isinstance(c, Compound):
        return False
    if 'id' not in c:
        return False
    return ('Count' in c) or ('tag' in c)


# Marker fields that, alongside `id: String`, distinguish a serialised entity NBT
# from a same-shape Compound that just happens to have an `id` field.
ENTITY_NBT_MARKERS = {
    'Pos', 'UUID', 'Brain', 'Attributes', 'attributes',
    'ModelId', 'model_id', 'HandItems', 'ArmorItems',
}


def _looks_like_entity_nbt(c: Any) -> bool:
    """Compound carrying entity NBT (e.g. nested inside TLM block-entity ExtraData / auto-serial).

    Used to recurse `migrate_entity_nbt` into containers we don't explicitly know about, so
    fields like `ModelId`/`ActiveEffects`/`Attributes` get migrated wherever the entity lives.
    """
    if not isinstance(c, Compound):
        return False
    if 'id' not in c or not isinstance(c['id'], String):
        return False
    return any(m in c for m in ENTITY_NBT_MARKERS)


def _looks_like_old_block_pos(c: Any) -> bool:
    """1.20 BlockPos: {X: Int, Y: Int, Z: Int}. 1.21 expects [I; x, y, z]."""
    if not isinstance(c, Compound):
        return False
    return set(c.keys()) == {'X', 'Y', 'Z'} and all(isinstance(c[k], Int) for k in ('X', 'Y', 'Z'))


def _block_pos_to_int_array(c: Compound) -> IntArray:
    return IntArray([int(c['X']), int(c['Y']), int(c['Z'])])


def _looks_like_old_fluid_stack(c: Any) -> bool:
    """1.20 FluidStack: {FluidName: String, Amount: Int, Tag?: Compound}.
       1.21 FluidStack: {id: String, amount: Int, components?: Compound}.

    Reject Compounds that *also* carry an `id` field — those are usually mod-specific hybrid
    block-entity NBTs (e.g. goety:haunted_jug stores fluid fields alongside its block-entity id
    at the same level). Migrating them as fluids would clobber the block-entity id."""
    if not isinstance(c, Compound):
        return False
    if 'id' in c:
        return False
    return 'FluidName' in c and 'Amount' in c


def migrate_fluid_stack(fs: Compound) -> Compound:
    out = Compound()
    out['id'] = String(str(fs['FluidName']))
    out['amount'] = Int(int(fs['Amount']))
    if 'Tag' in fs and isinstance(fs['Tag'], Compound):
        comps = migrate_item_tag(fs['Tag'])
        if len(comps) > 0:
            out['components'] = comps
    return out


def _list_mostly_items(lst: List) -> bool:
    """True iff the list looks like an inventory: at least one element is an old-format item.
    Tolerates leading placeholders like {_null: 1b} (TLM auto-serial) before the real items."""
    return any(_looks_like_old_item_stack(e) for e in lst)


def _list_mostly_fluids(lst: List) -> bool:
    return any(_looks_like_old_fluid_stack(e) for e in lst)


def migrate_items_anywhere(node: Any) -> Any:
    """Recursively walk and migrate every old-format ItemStack / FluidStack / BlockPos / nested
    entity NBT found anywhere in `node`.

    Catches (per-Compound shape detection — works regardless of the surrounding key/path):
    - Single ItemStack (Book on lectern, buy/buyB/sell on trades, Item on item_frame, etc.)
    - Single FluidStack {FluidName, Amount, Tag} → {id, amount, components}
    - Old BlockPos {X, Y, Z} → IntArray (e.g. MaidSchedulePos.Work/Sleep/Idle)
    - Lists where at least one element is an item / fluid stack — handles mixed lists with
      placeholder entries like TLM's `{_null: 1b}`.
    - Nested entity NBT (e.g. TLM block-entity `ExtraData` / `auto-serial` carrying a
      serialised maid) — recurses into `migrate_entity_nbt`.

    Already-migrated entries are skipped (the per-element migrators are no-ops on new shapes).
    """
    if isinstance(node, Compound):
        for k in list(node.keys()):
            v = node[k]
            if isinstance(v, Compound):
                if _looks_like_old_item_stack(v):
                    node[k] = migrate_item(v)
                    continue
                if _looks_like_old_fluid_stack(v):
                    node[k] = migrate_fluid_stack(v)
                    continue
                if _looks_like_old_block_pos(v):
                    node[k] = _block_pos_to_int_array(v)
                    continue
                if _looks_like_entity_nbt(v):
                    migrate_entity_nbt(v)
                    continue
                migrate_items_anywhere(v)
            elif isinstance(v, List) and len(v) > 0:
                # Look at every element, not just v[0]: lists may have leading placeholders
                # (e.g. TLM auto-serial uses `{_null: 1b}` for empty slots).
                if _list_mostly_items(v):
                    node[k] = migrate_items_in_list(v)
                elif _list_mostly_fluids(v):
                    node[k] = migrate_fluid_stacks_in_list(v)
                else:
                    migrate_items_anywhere(v)
            else:
                migrate_items_anywhere(v)
    elif isinstance(node, List):
        for v in node:
            migrate_items_anywhere(v)
    return node


# ─── Block-entity migration ──────────────────────────────────────────────────

def migrate_block_entity_nbt(nbt: Compound) -> None:
    """Block entities (chests, mob_spawner, item_frame, maid_bed, etc.) live in `blocks[i].nbt`.
    Same flow as entities, except `ForgeData` is RENAMED to `NeoForgeData` (preserves data like
    maid_bed BedColor) instead of being dropped."""
    # Block entities preserve mod-attached data via NeoForgeData (1.21 NeoForge convention).
    if 'ForgeData' in nbt:
        existing = nbt.get('NeoForgeData')
        legacy = nbt['ForgeData']
        del nbt['ForgeData']
        if isinstance(legacy, Compound) and len(legacy) > 0:
            if isinstance(existing, Compound):
                # Merge — newer NeoForgeData wins on conflict
                for k, v in legacy.items():
                    if k not in existing:
                        existing[k] = v
            else:
                nbt['NeoForgeData'] = legacy

    if 'Items' in nbt and isinstance(nbt['Items'], List):
        nbt['Items'] = migrate_items_in_list(nbt['Items'])

    # Banner layers: 1.21 BannerBlockEntity only reads lowercase `patterns`; vanilla DFU won't
    # rewrite them once DataVersion is bumped, so the layers would be silently lost.
    if 'Patterns' in nbt and isinstance(nbt['Patterns'], List):
        nbt['patterns'] = migrate_banner_patterns(nbt['Patterns'])
        del nbt['Patterns']

    # mob_spawner: SpawnData = {entity: <entityNBT>, ...}
    if 'SpawnData' in nbt and isinstance(nbt['SpawnData'], Compound):
        sd = nbt['SpawnData']
        if 'entity' in sd and isinstance(sd['entity'], Compound):
            migrate_entity_nbt(sd['entity'])

    # mob_spawner: SpawnPotentials = [{data: {entity: <entityNBT>}, weight: int}, ...]
    if 'SpawnPotentials' in nbt and isinstance(nbt['SpawnPotentials'], List):
        for entry in nbt['SpawnPotentials']:
            if not isinstance(entry, Compound):
                continue
            if 'data' in entry and isinstance(entry['data'], Compound):
                d = entry['data']
                if 'entity' in d and isinstance(d['entity'], Compound):
                    migrate_entity_nbt(d['entity'])
            elif 'Entity' in entry and isinstance(entry['Entity'], Compound):
                # legacy field name
                migrate_entity_nbt(entry['Entity'])

    # Catch mod-specific inventory containers (goety:pedestal.inventory.Items, etc.)
    migrate_items_anywhere(nbt)

    # Final pass: scrub any remaining Forge-only sub-keys we didn't translate
    _scrub_forge_recursively(nbt)


# ─── Top-level driver ────────────────────────────────────────────────────────

def transform_file(path: Path, *, data_version: int, dry_run: bool = False) -> None:
    f = nbtlib.load(str(path))
    root = f.root if hasattr(f, 'root') else f

    # 1. DataVersion → target
    root['DataVersion'] = Int(data_version)

    # 2. Block palette
    if 'palette' in root:
        for entry in root['palette']:
            migrate_palette_entry(entry)
    if 'palettes' in root:
        for variant in root['palettes']:
            for entry in variant:
                migrate_palette_entry(entry)

    # 3. Block-entity NBT (in blocks[i].nbt)
    if 'blocks' in root:
        for blk in root['blocks']:
            if 'nbt' in blk and isinstance(blk['nbt'], Compound):
                migrate_block_entity_nbt(blk['nbt'])

    # 4. Entities
    if 'entities' in root:
        for ent in root['entities']:
            if 'nbt' in ent and isinstance(ent['nbt'], Compound):
                migrate_entity_nbt(ent['nbt'])

    if not dry_run:
        f.save()


def _iter_nbt_files(source: Path) -> list[Path]:
    if source.is_file():
        if source.suffix != '.nbt':
            raise SystemExit(f"Not an .nbt file: {source}")
        return [source]
    if source.is_dir():
        return sorted(source.rglob('*.nbt'))
    raise SystemExit(f"--source not found: {source}")


def _resolve_dest_pairs(source: Path, sources: list[Path], dest: Path) -> list[tuple[Path, Path]]:
    """Map every input path to the (input, output) pair the user actually wants.

    Resolution rules:
    - --source FILE.nbt + --dest DIR              → DIR/FILE.nbt
    - --source FILE.nbt + --dest OTHER.nbt        → OTHER.nbt (rename allowed)
    - --source FILE.nbt + --dest pre-existing dir → DIR/FILE.nbt
    - --source DIR/      + --dest DIR2/           → mirror tree under DIR2/
    - --source DIR/      + --dest existing FILE   → error (ambiguous)
    """
    if source.is_file():
        # Treat dest as a file iff its name ends in .nbt and it doesn't exist as a directory.
        if dest.suffix == '.nbt' and not dest.is_dir():
            dest.parent.mkdir(parents=True, exist_ok=True)
            return [(source, dest)]
        dest.mkdir(parents=True, exist_ok=True)
        return [(source, dest / source.name)]

    if dest.is_file() or dest.suffix == '.nbt':
        raise SystemExit(f"--source is a directory but --dest looks like a single file: {dest}")
    dest.mkdir(parents=True, exist_ok=True)
    pairs = []
    for src in sources:
        rel = src.relative_to(source)
        tgt = dest / rel
        tgt.parent.mkdir(parents=True, exist_ok=True)
        pairs.append((src, tgt))
    return pairs


def main(argv: list[str] | None = None) -> int:
    parser = argparse.ArgumentParser(
        description=__doc__.split('\n\n')[0],
        formatter_class=argparse.RawDescriptionHelpFormatter,
        epilog=f"Default --target {DEFAULT_TARGET} (DataVersion={DATA_VERSION_BY_TARGET[DEFAULT_TARGET]}). "
               f"Other supported targets: {', '.join(t for t in DATA_VERSION_BY_TARGET if t != DEFAULT_TARGET)}.",
    )
    parser.add_argument('--source', required=True, type=Path,
                        help='Path to a directory of *.nbt files OR a single .nbt file (1.20.1 format).')
    parser.add_argument('--dest', type=Path, default=None,
                        help='Output directory (or, with single-file --source, an output .nbt path). '
                             'If omitted, migrate in-place over --source.')
    parser.add_argument('--target', default=DEFAULT_TARGET, choices=sorted(DATA_VERSION_BY_TARGET.keys()),
                        help='Minecraft target version (sets DataVersion). Defaults to 1.21.1 — the only '
                             'version this script has been calibrated against. Other 1.21.x targets bump '
                             'DataVersion only; the rest of the schema is unchanged.')
    parser.add_argument('--data-version', type=int, default=None,
                        help='Override DataVersion explicitly (advanced; takes precedence over --target).')
    parser.add_argument('--dry-run', action='store_true',
                        help='Parse and run all transforms but do not write output.')
    parser.add_argument('--verbose', action='store_true', help='Print every file as it is migrated.')
    parser.add_argument('--legacy-namespace', default=DEFAULT_LEGACY_NAMESPACE,
                        help=f'Namespace for synthesized legacy attribute-modifier IDs '
                             f'(default: {DEFAULT_LEGACY_NAMESPACE}).')
    parser.add_argument('--legacy-path-prefix', default=DEFAULT_LEGACY_PATH_PREFIX,
                        help=f'Path prefix for synthesized legacy attribute-modifier IDs '
                             f'(default: "{DEFAULT_LEGACY_PATH_PREFIX}").')
    args = parser.parse_args(argv)

    global LEGACY_NAMESPACE, LEGACY_PATH_PREFIX
    LEGACY_NAMESPACE = args.legacy_namespace
    LEGACY_PATH_PREFIX = args.legacy_path_prefix

    data_version = args.data_version or DATA_VERSION_BY_TARGET[args.target]

    sources = _iter_nbt_files(args.source)
    if not sources:
        print(f"No .nbt files found under {args.source}", file=sys.stderr)
        return 1

    # Resolve (in_path → out_path) pairs. With --dest we write to the destination tree;
    # without --dest we migrate in place.
    if args.dest is not None and not args.dry_run:
        pairs = _resolve_dest_pairs(args.source, sources, args.dest)
        # Copy inputs to the destination paths; transform_file will overwrite them.
        for src, tgt in pairs:
            shutil.copy2(src, tgt)
        targets = [tgt for _, tgt in pairs]
    else:
        targets = sources

    for p in targets:
        if args.verbose:
            display = p.name
            if args.dest is not None and args.dest.is_dir():
                try:
                    display = str(p.relative_to(args.dest))
                except ValueError:
                    pass
            print(f"  migrating {display}")
        transform_file(p, data_version=data_version, dry_run=args.dry_run)

    suffix = ' (dry-run, no writes)' if args.dry_run else ''
    print(f"\nMigrated {len(targets)} file{'s' if len(targets) != 1 else ''} → DataVersion {data_version}{suffix}.")
    return 0


if __name__ == '__main__':
    sys.exit(main())
