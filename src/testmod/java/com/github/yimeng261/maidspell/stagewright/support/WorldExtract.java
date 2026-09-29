package com.github.yimeng261.maidspell.stagewright.support;

import com.github.yimeng261.maidspell.stagewright.data.StructureSnapshots;
import net.minecraft.core.BlockPos;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.NumericTag;
import net.minecraft.nbt.Tag;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.ExperienceOrb;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.levelgen.structure.BoundingBox;
import net.minecraft.world.phys.AABB;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.TreeSet;

/**
 * 从已放置的结构里读出与 {@link StructureSnapshots} 同一规则的内容（规则见 /tmp 生成脚本与快照类注释）。
 */
public final class WorldExtract {
    /** 生成 finalize 时会随机给装备的实体，装备不进签名。 */
    private static final Set<String> RANDOM_GEAR = Set.of("minecraft:fox");

    private WorldExtract() {
    }

    /** 本模组模板对应的拼图片（原版补充片不参与快照比对）。 */
    public static List<StructureStage.Piece> ownPieces(StructureStage.Placed placed) {
        return placed.pieces().stream().filter(p -> StructureSnapshots.TEMPLATES.containsKey(p.template())).toList();
    }

    /** 已放置的本模组模板在快照里的预期内容合集。 */
    public static StructureSnapshots.Template expected(StructureStage.Placed placed) {
        Set<String> special = new TreeSet<>();
        List<String> containers = new ArrayList<>();
        List<String> entities = new ArrayList<>();
        List<Map<String, String>> maids = new ArrayList<>();
        for (StructureStage.Piece piece : ownPieces(placed)) {
            StructureSnapshots.Template template = StructureSnapshots.TEMPLATES.get(piece.template());
            special.addAll(template.specialBlocks());
            containers.addAll(template.containers());
            entities.addAll(template.entities());
            maids.addAll(template.maids());
        }
        return new StructureSnapshots.Template(List.copyOf(special), containers, entities, maids);
    }

    public static Set<String> specialBlocks(ServerLevel level, StructureStage.Placed placed) {
        // 先收集出现过的方块种类，最后再换算成 ID，避免逐格构造字符串
        Set<Block> seen = new HashSet<>();
        BlockPos.MutableBlockPos cursor = new BlockPos.MutableBlockPos();
        for (StructureStage.Piece piece : ownPieces(placed)) {
            BoundingBox box = piece.extent();
            for (int x = box.minX(); x <= box.maxX(); x++) {
                for (int y = box.minY(); y <= box.maxY(); y++) {
                    for (int z = box.minZ(); z <= box.maxZ(); z++) {
                        seen.add(level.getBlockState(cursor.set(x, y, z)).getBlock());
                    }
                }
            }
        }
        Set<String> out = new TreeSet<>();
        for (Block block : seen) {
            ResourceLocation id = BuiltInRegistries.BLOCK.getKey(block);
            if (!id.getNamespace().equals("minecraft")) {
                out.add(id.toString());
            }
        }
        return out;
    }

    public static List<String> containers(ServerLevel level, StructureStage.Placed placed) {
        Map<BlockPos, BlockEntity> found = new LinkedHashMap<>();
        for (StructureStage.Piece piece : ownPieces(placed)) {
            BoundingBox box = piece.extent();
            for (int cx = box.minX() >> 4; cx <= box.maxX() >> 4; cx++) {
                for (int cz = box.minZ() >> 4; cz <= box.maxZ() >> 4; cz++) {
                    level.getChunk(cx, cz).getBlockEntities().forEach((pos, be) -> {
                        if (box.isInside(pos)) {
                            found.put(pos, be);
                        }
                    });
                }
            }
        }
        List<String> out = new ArrayList<>();
        found.forEach((pos, be) -> {
            String id = BuiltInRegistries.BLOCK.getKey(be.getBlockState().getBlock()).toString();
            String entry = container(id, be.saveWithoutMetadata(level.registryAccess()));
            if (entry != null) {
                out.add(entry);
            }
        });
        return out;
    }

    public static List<Entity> entities(ServerLevel level, StructureStage.Placed placed) {
        Set<Entity> out = new LinkedHashSet<>();
        for (StructureStage.Piece piece : ownPieces(placed)) {
            AABB box = AABB.of(piece.extent()).inflate(0.5);
            out.addAll(level.getEntities((Entity) null, box, e -> !(e instanceof Player)
                    && !(e instanceof ItemEntity) && !(e instanceof ExperienceOrb)));
        }
        return new ArrayList<>(out);
    }

    public static CompoundTag nbtOf(Entity entity) {
        CompoundTag tag = entity.saveWithoutId(new CompoundTag());
        tag.putString("id", BuiltInRegistries.ENTITY_TYPE.getKey(entity.getType()).toString());
        return tag;
    }

    // ---- 以下与生成脚本逐条对应 ----

    public static String container(String block, CompoundTag nbt) {
        if (nbt.contains("LootTable")) {
            return block + "|loot=" + nbt.getString("LootTable");
        }
        if (block.equals("minecraft:spawner")) {
            return block + "|spawn=" + nbt.getCompound("SpawnData").getCompound("entity").getString("id");
        }
        String model = findString(nbt, "model_id");
        if (model != null) {
            return block + "|model=" + model;
        }
        List<String> items = new ArrayList<>();
        stacks(nbt, items);
        if (!items.isEmpty()) {
            return block + "|items=" + String.join(",", sorted(items));
        }
        return null;
    }

    public static String entity(CompoundTag nbt) {
        StringBuilder sig = new StringBuilder(nbt.getString("id"));
        if (nbt.contains("Item") && !nbt.getCompound("Item").getString("id").isEmpty()) {
            sig.append("|item=").append(nbt.getCompound("Item").getString("id"));
        }
        List<String> gear = new ArrayList<>();
        if (!RANDOM_GEAR.contains(nbt.getString("id"))) {
            for (String key : List.of("HandItems", "ArmorItems")) {
                ListTag list = nbt.getList(key, Tag.TAG_COMPOUND);
                for (int i = 0; i < list.size(); i++) {
                    String id = list.getCompound(i).getString("id");
                    if (!id.isEmpty()) {
                        gear.add(id);
                    }
                }
            }
        }
        if (!gear.isEmpty()) {
            sig.append("|gear=").append(String.join(",", sorted(gear)));
        }
        if (nbt.contains("variant")) {
            sig.append("|variant=").append(nbt.getString("variant"));
        }
        return sig.toString();
    }

    public static Map<String, String> maid(CompoundTag n) {
        String blue = "";
        ListTag baubles = n.getCompound("MaidBaubleInventory").getList("Items", Tag.TAG_COMPOUND);
        for (int i = 0; i < baubles.size(); i++) {
            ListTag ids = baubles.getCompound(i).getCompound("components")
                    .getList("touhou_little_maid_spell:spell_ids", Tag.TAG_STRING);
            if (!ids.isEmpty()) {
                List<String> spells = new ArrayList<>();
                for (int j = 0; j < ids.size(); j++) {
                    spells.add(ids.getString(j));
                }
                blue = String.join(",", spells);
            }
        }
        List<String> effects = new ArrayList<>();
        ListTag active = n.getList("active_effects", Tag.TAG_COMPOUND);
        for (int i = 0; i < active.size(); i++) {
            effects.add(active.getCompound(i).getString("id"));
        }
        Map<String, String> f = new LinkedHashMap<>();
        f.put("model_id", n.getString("model_id"));
        f.put("favorability", numeric(n, "MaidFavorability"));
        f.put("sitting", numeric(n, "Sitting"));
        f.put("task", n.getString("MaidTask"));
        f.put("structureSpawn", numeric(n, "StructureSpawn"));
        f.put("backpack", n.getString("MaidBackpackType"));
        f.put("scheduleMode", n.getString("MaidScheduleMode"));
        f.put("hand", ids(n.getList("HandItems", Tag.TAG_COMPOUND)));
        f.put("armor", ids(n.getList("ArmorItems", Tag.TAG_COMPOUND)));
        f.put("inventory", slots(n.getCompound("MaidInventory")));
        f.put("baubles", slots(n.getCompound("MaidBaubleInventory")));
        f.put("blueNoteSpells", blue);
        f.put("effects", String.join(",", sorted(effects)));
        return f;
    }

    private static String numeric(CompoundTag tag, String key) {
        return tag.get(key) instanceof NumericTag number ? String.valueOf(number.getAsLong()) : "0";
    }

    private static String ids(ListTag list) {
        List<String> ids = new ArrayList<>();
        for (int i = 0; i < list.size(); i++) {
            ids.add(list.getCompound(i).getString("id"));
        }
        return String.join(",", ids);
    }

    private static String slots(CompoundTag inventory) {
        ListTag items = inventory.getList("Items", Tag.TAG_COMPOUND);
        List<CompoundTag> stacks = new ArrayList<>();
        for (int i = 0; i < items.size(); i++) {
            stacks.add(items.getCompound(i));
        }
        stacks.sort((a, b) -> Integer.compare(a.getInt("Slot"), b.getInt("Slot")));
        List<String> out = new ArrayList<>();
        for (CompoundTag stack : stacks) {
            out.add(stack.getInt("Slot") + ":" + stack.getString("id"));
        }
        return String.join(",", out);
    }

    private static void stacks(Tag tag, List<String> out) {
        if (tag instanceof CompoundTag compound) {
            if (compound.contains("id", Tag.TAG_STRING) && (compound.contains("count") || compound.contains("Count"))) {
                long count = compound.get(compound.contains("count") ? "count" : "Count") instanceof NumericTag number
                        ? number.getAsLong() : 0;
                if (!compound.getString("id").equals("minecraft:air") && count > 0) {
                    out.add(compound.getString("id") + "*" + count);
                }
                return;
            }
            for (String key : compound.getAllKeys()) {
                stacks(compound.get(key), out);
            }
        } else if (tag instanceof ListTag list) {
            for (Tag element : list) {
                stacks(element, out);
            }
        }
    }

    private static String findString(Tag tag, String key) {
        if (tag instanceof CompoundTag compound) {
            if (compound.contains(key, Tag.TAG_STRING)) {
                return compound.getString(key);
            }
            for (String name : compound.getAllKeys()) {
                String found = findString(compound.get(name), key);
                if (found != null) {
                    return found;
                }
            }
        } else if (tag instanceof ListTag list) {
            for (Tag element : list) {
                String found = findString(element, key);
                if (found != null) {
                    return found;
                }
            }
        }
        return null;
    }

    private static List<String> sorted(List<String> values) {
        return values.stream().sorted().toList();
    }
}
