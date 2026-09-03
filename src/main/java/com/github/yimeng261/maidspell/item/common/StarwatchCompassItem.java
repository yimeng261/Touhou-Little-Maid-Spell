package com.github.yimeng261.maidspell.item.common;

import com.github.yimeng261.maidspell.Global;
import com.github.yimeng261.maidspell.MaidSpellMod;
import net.minecraft.ChatFormatting;
import net.minecraft.core.BlockPos;
import net.minecraft.core.registries.Registries;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.NbtOps;
import net.minecraft.nbt.NbtUtils;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.tags.TagKey;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResultHolder;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.CompassItem;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Rarity;
import net.minecraft.world.item.TooltipFlag;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.levelgen.structure.Structure;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.List;

/**
 * 观星罗盘。守塔人的战利品，在末地右键指向最近的星途终岸。
 *
 * <p><b>为什么继承 {@link CompassItem}：</b>指针朝向那一整套（{@code minecraft:angle} 谓词、
 * 磁石坐标的读写、发光效果）原版已经写好了，只要把 {@code LodestonePos} /
 * {@code LodestoneDimension} 填对，客户端一行代码都不用写。
 * 模型侧的 12 帧 overrides 见 {@code models/item/starwatch_compass.json}，
 * 谓词函数在 {@code MaidSpellClientMod} 里按物品单独注册——{@code angle} 是逐物品注册的，
 * 不是通用谓词，光继承 {@code CompassItem} 拿不到。
 *
 * <p><b>{@code LodestoneTracked} 必须写 false。</b>{@code CompassItem.inventoryTick}
 * 每 tick 都会去查坐标处是不是还有一块真磁石，不是就把 {@code LodestonePos} 抹掉——
 * 而我们指的是一座结构，那儿当然没有磁石。原版给这条留的口子正是
 * {@code LodestoneTracked}：它存在且为 false 时直接 return，不做校验。
 */
public class StarwatchCompassItem extends CompassItem {

    /** 指向的目标结构。 */
    private static final TagKey<Structure> STELLAR_ENDSHORE = TagKey.create(
            Registries.STRUCTURE, new ResourceLocation(MaidSpellMod.MOD_ID, "stellar_endshore"));

    /**
     * 搜索半径（区块）。和原版探险家地图取同一个值。
     *
     * <p>{@code findNearestMapStructure} 是**同步**跑在服务端主线程上的：
     * 半径每翻一倍要试的候选区域翻四倍，每个候选都要拿地形生成器做一次落点校验。
     * 星途终岸的 spacing 是 34，100 已经够扫到三环开外，正常种子第一二环就命中；
     * 而没命中时整条搜索会跑满，全服跟着卡住，所以这个数只能往小了给。
     */
    private static final int SEARCH_RADIUS_CHUNKS = 100;

    /** 右键冷却，防止连点把搜索反复跑起来。 */
    private static final int USE_COOLDOWN_TICKS = 40;

    /** 没找到时的冷却。命中一次就够，落空却要把半径扫满，不能让人两秒一次地扫。 */
    private static final int MISS_COOLDOWN_TICKS = 400;

    public StarwatchCompassItem() {
        super(new Properties().stacksTo(1).rarity(Rarity.RARE));
    }

    @Override
    public @NotNull InteractionResultHolder<ItemStack> use(@NotNull Level level, @NotNull Player player,
                                                           @NotNull InteractionHand hand) {
        ItemStack stack = player.getItemInHand(hand);
        if (!(level instanceof ServerLevel serverLevel)) {
            // 客户端这一侧只负责摆出"用了"的姿势，真正的搜索在服务端。
            return InteractionResultHolder.sidedSuccess(stack, level.isClientSide);
        }
        if (player.getCooldowns().isOnCooldown(this)) {
            return InteractionResultHolder.pass(stack);
        }
        player.getCooldowns().addCooldown(this, USE_COOLDOWN_TICKS);

        if (!level.dimension().equals(Level.END)) {
            player.displayClientMessage(
                    Component.translatable("item.touhou_little_maid_spell.starwatch_compass.wrong_dimension")
                            .withStyle(ChatFormatting.RED), true);
            return InteractionResultHolder.fail(stack);
        }

        BlockPos found = serverLevel.findNearestMapStructure(
                STELLAR_ENDSHORE, player.blockPosition(), SEARCH_RADIUS_CHUNKS, false);
        if (found == null) {
            // 没找到那一次是把整个半径扫满了才得出的结论，而这个结论两秒内不会变。
            // 冷却按未命中另算，免得有人在末地空地上一直点、一直卡服。
            player.getCooldowns().addCooldown(this, MISS_COOLDOWN_TICKS);
            player.displayClientMessage(
                    Component.translatable("item.touhou_little_maid_spell.starwatch_compass.not_found")
                            .withStyle(ChatFormatting.RED), true);
            return InteractionResultHolder.fail(stack);
        }

        CompoundTag tag = stack.getOrCreateTag();
        tag.put(TAG_LODESTONE_POS, NbtUtils.writeBlockPos(found));
        Level.RESOURCE_KEY_CODEC.encodeStart(NbtOps.INSTANCE, level.dimension())
                .resultOrPartial(Global.LOGGER::error)
                .ifPresent(dimension -> tag.put(TAG_LODESTONE_DIMENSION, dimension));
        // 见类注释：这一位为 false，原版才不会因为那里没有磁石而把坐标抹掉。
        tag.putBoolean(TAG_LODESTONE_TRACKED, false);

        player.displayClientMessage(
                Component.translatable("item.touhou_little_maid_spell.starwatch_compass.found",
                                found.getX(), found.getZ())
                        .withStyle(ChatFormatting.LIGHT_PURPLE), true);
        return InteractionResultHolder.success(stack);
    }

    @Override
    public void appendHoverText(@NotNull ItemStack stack, @Nullable Level level,
                                @NotNull List<Component> tooltip, @NotNull TooltipFlag flag) {
        tooltip.add(Component.translatable("item.touhou_little_maid_spell.starwatch_compass.desc")
                .withStyle(ChatFormatting.GRAY));
        CompoundTag tag = stack.getTag();
        if (tag != null && tag.contains(TAG_LODESTONE_POS)) {
            BlockPos target = NbtUtils.readBlockPos(tag.getCompound(TAG_LODESTONE_POS));
            tooltip.add(Component.translatable("item.touhou_little_maid_spell.starwatch_compass.target",
                            target.getX(), target.getZ())
                    .withStyle(ChatFormatting.DARK_AQUA));
        }
    }
}
