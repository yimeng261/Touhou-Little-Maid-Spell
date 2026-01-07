package com.github.yimeng261.maidspell.mixin;

import com.github.tartaricacid.touhoulittlemaid.entity.passive.EntityMaid;
import com.github.tartaricacid.touhoulittlemaid.entity.task.TaskFeedOwner;
import com.github.tartaricacid.touhoulittlemaid.util.ItemsUtil;
import com.github.yimeng261.maidspell.Config;
import com.github.yimeng261.maidspell.item.MaidSpellItems;
import com.github.yimeng261.maidspell.item.bauble.fragrantIngenuity.FragrantIngenuityBauble;
import com.mojang.logging.LogUtils;
import net.minecraft.world.effect.MobEffect;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import org.slf4j.Logger;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/**
 * Mixin用于在女仆喂食主人时触发馥郁巧思饰品效果
 * 注入到 TaskFeedOwner.feed() 方法，在喂食完成后为主人添加随机的1级正面buff
 */
@Mixin(value = TaskFeedOwner.class, remap = false)
public class TaskFeedOwnerMixin {
    private static final Logger LOGGER = LogUtils.getLogger();
    
    /**
     * 注入到 TaskFeedOwner.feed() 方法的末尾
     * 这个方法是真正执行喂食逻辑的地方
     * 在 finishUsingItem 完成后触发馥郁巧思效果
     */
    @Inject(
        method = "feed(Lnet/minecraft/world/item/ItemStack;Lnet/minecraft/world/entity/player/Player;)Lnet/minecraft/world/item/ItemStack;",
        at = @At("RETURN"),
        remap = false
    )
    private void onFeedOwner(ItemStack stack, Player owner, CallbackInfoReturnable<ItemStack> cir) {
        // 通过 owner 获取女仆
        // 注意：这里需要找到正在喂食的女仆
        // 由于我们在 feed 方法中，无法直接获取 maid 实例
        // 需要通过 owner 附近的女仆来判断
        
        if (!(owner.level() instanceof net.minecraft.server.level.ServerLevel serverLevel)) {
            return;
        }
        
        // 查找附近正在喂食的女仆
        EntityMaid feedingMaid = null;
        for (EntityMaid maid : serverLevel.getEntitiesOfClass(EntityMaid.class, 
                owner.getBoundingBox().inflate(7.0D))) {
            if (maid.getOwner() == owner && ItemsUtil.hasBaubleItemInMaid(maid, MaidSpellItems.FRAGRANT_INGENUITY.get())) {
                feedingMaid = maid;
                break;
            }
        }
        
        if (feedingMaid == null) {
            return;
        }
        
        if (FragrantIngenuityBauble.POSITIVE_EFFECTS.isEmpty()) {
            LOGGER.warn("[FragrantIngenuity] No positive effects available!");
            return;
        }
        
        // 随机选择一个正面buff
        MobEffect randomEffect = FragrantIngenuityBauble.POSITIVE_EFFECTS.get(
            owner.getRandom().nextInt(FragrantIngenuityBauble.POSITIVE_EFFECTS.size())
        );
        
        // 持续时间：从配置中读取
        int duration = Config.fragrantIngenuityBuffDuration;
        
        // 添加buff（1级，即amplifier = 0）
        owner.addEffect(new MobEffectInstance(randomEffect, duration, 0));
        
    }
}

