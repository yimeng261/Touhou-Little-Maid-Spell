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
 * 指向最近星途终岸的罗盘，复用磁石罗盘的指针逻辑。
 * LodestoneTracked 必须为 false，否则原版会清除并非磁石的结构坐标。
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

    /** 固定物品名；带 ItemStack 的父类方法会返回磁石罗盘名称。 */
    @Override
    public @NotNull String getDescriptionId(@NotNull ItemStack stack) {
        return super.getDescriptionId();
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
