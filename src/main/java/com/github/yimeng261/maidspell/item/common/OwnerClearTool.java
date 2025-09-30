package com.github.yimeng261.maidspell.item.common;

import com.github.tartaricacid.touhoulittlemaid.entity.passive.EntityMaid;
import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.Component;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Rarity;
import net.minecraft.world.item.TooltipFlag;
import net.minecraft.world.level.Level;

import javax.annotation.Nonnull;
import javax.annotation.Nullable;
import java.util.List;

/**
 * 女仆主人清除工具
 * 管理员物品，用于清除女仆的主人
 */
public class OwnerClearTool extends Item {
    
    public OwnerClearTool() {
        super(new Item.Properties()
                .stacksTo(1)
                .rarity(Rarity.EPIC)
        );
    }
    
    @Override
    public InteractionResult interactLivingEntity(@Nonnull ItemStack stack, @Nonnull Player player, @Nonnull LivingEntity target, @Nonnull InteractionHand hand) {
        if (target.level().isClientSide || hand != InteractionHand.MAIN_HAND) {
            return InteractionResult.PASS;
        }
        
        // 只对女仆生效
        if (!(target instanceof EntityMaid maid)) {
            return InteractionResult.PASS;
        }
        
        // 检查女仆是否有主人
        if (!maid.isTame()) {
            player.sendSystemMessage(Component.translatable("item.touhou_little_maid_spell.owner_clear_tool.no_owner")
                    .withStyle(ChatFormatting.YELLOW));
            return InteractionResult.SUCCESS;
        }
        
        // 只有创造模式玩家或OP可以使用
        if (!player.isCreative() && !player.hasPermissions(2)) {
            player.sendSystemMessage(Component.translatable("item.touhou_little_maid_spell.owner_clear_tool.no_permission")
                    .withStyle(ChatFormatting.RED));
            return InteractionResult.SUCCESS;
        }
        
        // 获取原主人名字用于消息显示
        LivingEntity owner = maid.getOwner();
        String ownerName = owner != null ? owner.getName().getString() : "Unknown";
        
        // 清除主人
        maid.setTame(false);
        maid.setOwnerUUID(null);
        
        // 重置女仆状态
        maid.getNavigation().stop();
        maid.setTarget(null);
        
        // 发送成功消息
        player.sendSystemMessage(Component.translatable("item.touhou_little_maid_spell.owner_clear_tool.success",
                maid.getName().getString(), ownerName)
                .withStyle(ChatFormatting.GREEN));
        
        return InteractionResult.SUCCESS;
    }
    
    @Override
    public void appendHoverText(@Nonnull ItemStack stack, @Nullable Level level, @Nonnull List<Component> tooltip, @Nonnull TooltipFlag flag) {
        tooltip.add(Component.translatable("item.touhou_little_maid_spell.owner_clear_tool.tooltip.line1")
                .withStyle(ChatFormatting.GRAY));
        tooltip.add(Component.translatable("item.touhou_little_maid_spell.owner_clear_tool.tooltip.line2")
                .withStyle(ChatFormatting.DARK_RED));
        tooltip.add(Component.translatable("item.touhou_little_maid_spell.owner_clear_tool.tooltip.line3")
                .withStyle(ChatFormatting.GOLD));
    }
}
