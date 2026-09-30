package com.github.yimeng261.maidspell.item.common;

import com.github.yimeng261.maidspell.MaidSpellMod;
import net.minecraft.ChatFormatting;
import net.minecraft.core.BlockPos;
import net.minecraft.core.GlobalPos;
import net.minecraft.core.component.DataComponents;
import net.minecraft.core.registries.Registries;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.tags.TagKey;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.InteractionResultHolder;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.CompassItem;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Rarity;
import net.minecraft.world.item.TooltipFlag;
import net.minecraft.world.item.context.UseOnContext;
import net.minecraft.world.item.component.LodestoneTracker;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.levelgen.structure.Structure;
import org.jetbrains.annotations.NotNull;

import java.util.List;
import java.util.Optional;

/**
 * 指向最近星途终岸的罗盘，复用磁石罗盘的指针逻辑。
 * LodestoneTracker 的 tracked 必须为 false，否则原版会清除并非磁石的结构坐标。
 */
public class StarwatchCompassItem extends CompassItem {

    /** 指向的目标结构。 */
    private static final TagKey<Structure> STELLAR_ENDSHORE = TagKey.create(
            Registries.STRUCTURE, ResourceLocation.fromNamespaceAndPath(MaidSpellMod.MOD_ID, "stellar_endshore"));

    /**
     * 搜索半径。random_spread 结构集按格点计，一格是结构集的 spacing 个区块，半径 r 共扫 (2r+1)² 个格点。
     *
     * <p>{@code findNearestMapStructure} 同步跑在服务端主线程上。未命中的格点只做一次存档扫描和群系采样，
     * 代价低；命中尚未生成的区块时，要同步把那个区块生成到结构起点。
     */
    private static final int SEARCH_RADIUS = 4;

    /** 右键冷却，防止连点把搜索反复跑起来。 */
    private static final int USE_COOLDOWN_TICKS = 40;

    /** 没找到时的冷却：结果短时间内不会变，不必反复扫满整个半径。 */
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
                STELLAR_ENDSHORE, player.blockPosition(), SEARCH_RADIUS, false);
        if (found == null) {
            player.getCooldowns().addCooldown(this, MISS_COOLDOWN_TICKS);
            player.displayClientMessage(
                    Component.translatable("item.touhou_little_maid_spell.starwatch_compass.not_found")
                            .withStyle(ChatFormatting.RED), true);
            return InteractionResultHolder.fail(stack);
        }

        // 见类注释：tracked 为 false，原版才不会因为那里没有磁石而把坐标抹掉。
        stack.set(DataComponents.LODESTONE_TRACKER,
                new LodestoneTracker(Optional.of(GlobalPos.of(level.dimension(), found)), false));

        player.displayClientMessage(
                Component.translatable("item.touhou_little_maid_spell.starwatch_compass.found",
                                found.getX(), found.getZ())
                        .withStyle(ChatFormatting.LIGHT_PURPLE), true);
        return InteractionResultHolder.success(stack);
    }

    /** 不认磁石：父类会把指针改成磁石坐标。放行后按普通右键走 {@link #use} 搜索。 */
    @Override
    public @NotNull InteractionResult useOn(@NotNull UseOnContext context) {
        return InteractionResult.PASS;
    }

    /** 固定物品名；带 ItemStack 的父类方法会返回磁石罗盘名称。 */
    @Override
    public @NotNull String getDescriptionId(@NotNull ItemStack stack) {
        return super.getDescriptionId();
    }

    @Override
    public void appendHoverText(@NotNull ItemStack stack, @NotNull TooltipContext context,
                                @NotNull List<Component> tooltip, @NotNull TooltipFlag flag) {
        tooltip.add(Component.translatable("item.touhou_little_maid_spell.starwatch_compass.desc")
                .withStyle(ChatFormatting.GRAY));
        LodestoneTracker tracker = stack.get(DataComponents.LODESTONE_TRACKER);
        if (tracker != null && tracker.target().isPresent()) {
            BlockPos target = tracker.target().get().pos();
            tooltip.add(Component.translatable("item.touhou_little_maid_spell.starwatch_compass.target",
                            target.getX(), target.getZ())
                    .withStyle(ChatFormatting.DARK_AQUA));
        }
    }
}
