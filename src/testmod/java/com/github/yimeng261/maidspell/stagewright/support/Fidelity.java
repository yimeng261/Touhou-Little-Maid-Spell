package com.github.yimeng261.maidspell.stagewright.support;

import net.magicterra.stagewright.scene.SceneContext;
import net.minecraft.core.BlockPos;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.StringTag;
import net.minecraft.nbt.NbtUtils;
import net.minecraft.nbt.Tag;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.levelgen.structure.BoundingBox;
import net.minecraft.world.level.levelgen.structure.templatesystem.StructureTemplate;
import net.minecraft.world.phys.AABB;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.function.BiPredicate;

/**
 * 通用保真度比对：放置后的世界与放置前按处理器算出的模板内容逐项对照。
 */
public final class Fidelity {
    /**
     * 不参与方块实体比对的键：坐标/类型、放置时随机的战利品种子、1.20 Forge 能力与持久数据残留
     * （NeoForge 只读 NeoForgeData；模板里仅有无人读取的 BedColor）、Goety 基座每 tick 更新的时间戳。
     */
    private static final Set<String> IGNORED_BE_KEYS = Set.of("x", "y", "z", "id", "LootTableSeed", "ForgeCaps",
            "ForgeData", "lastChangeTime");
    /** 失败信息里 NBT 值截断长度。 */
    private static final int VALUE_LIMIT = 240;
    /** 失败信息里每类问题最多列出的位置数。 */
    private static final int SAMPLE = 3;

    private Fidelity() {
    }

    /**
     * 每个模板方块位置上的方块种类一致（不比较连接等形状属性）。
     * 与其它拼图片包围盒在水平面上重叠的位置可能被相邻片覆盖（贴地投影会改变 Y），不参与比对。
     * 贴地投影的拼图片按放置时的实时 WORLD_SURFACE 高度图逐列落地，场地原点附近偶有一列高出一格，
     * 这类片允许方块上下偏一格。
     */
    public static void blocks(SceneContext ctx, ServerLevel level, StructureStage.Placed placed) {
        Grouped mismatches = new Grouped();
        int checked = 0;
        for (StructureStage.Piece piece : WorldExtract.ownPieces(placed)) {
            BoundingBox extent = piece.extent();
            // 只留下水平面上与本片内容范围重叠的其它片
            List<BoundingBox> others = placed.allBoxes().stream()
                    .filter(box -> !box.equals(piece.box()) && box.intersects(extent.minX(), extent.minZ(), extent.maxX(), extent.maxZ()))
                    .toList();
            for (StructureTemplate.StructureBlockInfo info : piece.blocks()) {
                int x = info.pos().getX();
                int z = info.pos().getZ();
                if (overlapsAny(others, x, z)) {
                    continue;
                }
                Block expected = info.state().getBlock();
                Block actual = level.getBlockState(info.pos()).getBlock();
                checked++;
                if (expected == actual || (piece.terrainMatching()
                        && (level.getBlockState(info.pos().above()).is(expected) || level.getBlockState(info.pos().below()).is(expected)))) {
                    continue;
                }
                mismatches.add(piece.template() + ": " + id(expected) + " → " + id(actual), info.pos());
            }
        }
        ctx.record("blocksChecked", checked);
        ctx.check(mismatches.lines()).as("与模板不一致的方块").isEmpty();
    }

    private static boolean overlapsAny(List<BoundingBox> boxes, int x, int z) {
        for (BoundingBox box : boxes) {
            if (box.intersects(x, z, x, z)) {
                return true;
            }
        }
        return false;
    }

    /**
     * 模板里带 NBT 的方块：世界中要有方块实体，且模板 NBT 是其保存结果的子集。
     * @param known 已知缺陷（模板, 键）不在这里报告，由对应的已知缺陷场景覆盖
     */
    public static void blockEntities(SceneContext ctx, ServerLevel level, StructureStage.Placed placed,
                                     BiPredicate<String, String> known) {
        Grouped mismatches = new Grouped();
        int checked = 0;
        for (StructureStage.Piece piece : WorldExtract.ownPieces(placed)) {
            for (StructureTemplate.StructureBlockInfo info : piece.blocks()) {
                if (info.nbt() == null) {
                    continue;
                }
                checked++;
                String block = id(info.state().getBlock());
                BlockEntity be = level.getBlockEntity(info.pos());
                if (be == null) {
                    mismatches.add(piece.template() + ": " + block + " 缺少方块实体", info.pos());
                    continue;
                }
                CompoundTag actual = be.saveWithoutMetadata(level.registryAccess());
                for (String key : info.nbt().getAllKeys()) {
                    if (IGNORED_BE_KEYS.contains(key) || known.test(piece.template(), block + "#" + key)) {
                        continue;
                    }
                    if (!sameValue(level, info.nbt().get(key), actual.get(key))) {
                        String group = piece.template() + ": " + block + " 的 " + key + " 未保留";
                        mismatches.add(group, info.pos());
                        mismatches.detail(group, "期望 " + clip(info.nbt().get(key)) + " 实际 " + clip(actual.get(key)));
                    }
                }
            }
        }
        ctx.record("blockEntitiesChecked", checked);
        ctx.check(mismatches.lines()).as("与模板不一致的方块实体数据").isEmpty();
    }

    /** 模板里的每个实体在预期位置 1.5 格内有同类型实体。 */
    public static void entities(SceneContext ctx, ServerLevel level, StructureStage.Placed placed) {
        Grouped missing = new Grouped();
        for (StructureStage.Piece piece : WorldExtract.ownPieces(placed)) {
            for (StructureTemplate.StructureEntityInfo info : piece.entities()) {
                String type = info.nbt.getString("id");
                List<Entity> near = level.getEntities((Entity) null, new AABB(info.pos, info.pos).inflate(1.5),
                        e -> BuiltInRegistries.ENTITY_TYPE.getKey(e.getType()).toString().equals(type));
                if (near.isEmpty()) {
                    missing.add(piece.template() + ": " + type, BlockPos.containing(info.pos));
                }
            }
        }
        ctx.check(missing.lines()).as("没有出现在模板位置的实体").isEmpty();
    }

    /**
     * 模板值是否被保留。先按 NBT 子集比较，再容忍几类不影响含义的格式差异：
     * 空列表/空复合/空物品等价于没有；全是砖块的陶罐纹饰等价于没有；
     * 物品先经 ItemStack 编解码规范化（去掉默认组件、统一文本格式）再比较，
     * 只有 Count 没有 tag 的 1.20 旧格式物品视同当前格式；带 tag 的旧物品不规范化，数据丢失仍会报出来；
     * 告示牌文本按文本组件比较（{"text":""} 与 "" 等价）。
     */
    private static boolean sameValue(ServerLevel level, Tag expected, Tag actual) {
        if (NbtUtils.compareNbt(expected, actual, true)) {
            return true;
        }
        if (isEmpty(expected) && (actual == null || isEmpty(actual))) {
            return true;
        }
        Tag normalizedExpected = normalize(level, expected);
        Tag normalizedActual = normalize(level, actual);
        return NbtUtils.compareNbt(normalizedExpected, normalizedActual, true);
    }

    private static boolean isEmpty(Tag tag) {
        if (tag instanceof ListTag list) {
            return list.isEmpty() || list.stream().allMatch(e -> e instanceof StringTag s && s.getAsString().equals("minecraft:brick"));
        }
        if (tag instanceof CompoundTag compound) {
            return compound.isEmpty() || (compound.getString("id").equals("minecraft:air")
                    || (compound.contains("Count") && compound.getByte("Count") == 0));
        }
        return false;
    }

    private static Tag normalize(ServerLevel level, Tag tag) {
        if (tag instanceof CompoundTag compound) {
            if (compound.contains("id", Tag.TAG_STRING) && !compound.contains("tag")
                    && (compound.contains("count") || compound.contains("Count"))) {
                CompoundTag stack = compound.copy();
                if (stack.contains("Count")) {
                    stack.putInt("count", stack.getByte("Count"));
                    stack.remove("Count");
                }
                return ItemStack.parse(level.registryAccess(), stack)
                        .map(parsed -> parsed.save(level.registryAccess()))
                        .orElse(compound);
            }
            CompoundTag copy = new CompoundTag();
            for (String key : compound.getAllKeys()) {
                copy.put(key, key.equals("messages") && compound.get(key) instanceof ListTag lines
                        ? normalizeText(level, lines)
                        : normalize(level, compound.get(key)));
            }
            return copy;
        }
        if (tag instanceof ListTag list) {
            ListTag copy = new ListTag();
            list.forEach(element -> copy.add(normalize(level, element)));
            return copy;
        }
        return tag;
    }

    /** 告示牌每行是 JSON 文本组件，解析后按规范形式重新序列化。 */
    private static ListTag normalizeText(ServerLevel level, ListTag lines) {
        ListTag copy = new ListTag();
        for (Tag line : lines) {
            if (line instanceof StringTag string) {
                try {
                    Component component = Component.Serializer.fromJson(string.getAsString(), level.registryAccess());
                    copy.add(StringTag.valueOf(component == null ? string.getAsString()
                            : Component.Serializer.toJson(component, level.registryAccess())));
                    continue;
                } catch (RuntimeException ignored) {
                    // 不是合法 JSON 文本组件，按原样比较
                }
            }
            copy.add(line);
        }
        return copy;
    }

    private static String clip(Tag tag) {
        String text = tag == null ? "<无>" : tag.toString();
        return text.length() > VALUE_LIMIT ? text.substring(0, VALUE_LIMIT) + "…" : text;
    }

    private static String id(Block block) {
        return BuiltInRegistries.BLOCK.getKey(block).toString();
    }

    /** 按问题分组计数，并保留少量位置样本。 */
    private static final class Grouped {
        private final Map<String, List<BlockPos>> groups = new LinkedHashMap<>();
        private final Map<String, Integer> counts = new LinkedHashMap<>();
        private final Map<String, String> details = new LinkedHashMap<>();

        void detail(String key, String detail) {
            details.putIfAbsent(key, detail);
        }

        void add(String key, BlockPos pos) {
            counts.merge(key, 1, Integer::sum);
            List<BlockPos> sample = groups.computeIfAbsent(key, k -> new ArrayList<>());
            if (sample.size() < SAMPLE) {
                sample.add(pos.immutable());
            }
        }

        List<String> lines() {
            List<String> lines = new ArrayList<>();
            counts.forEach((key, count) -> lines.add(key + " ×" + count + " @" + groups.get(key)
                    + (details.containsKey(key) ? " — " + details.get(key) : "")));
            return lines;
        }
    }
}
