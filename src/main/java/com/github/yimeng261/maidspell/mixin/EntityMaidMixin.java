package com.github.yimeng261.maidspell.mixin;

import com.github.tartaricacid.touhoulittlemaid.entity.passive.EntityMaid;
import com.github.tartaricacid.touhoulittlemaid.inventory.handler.BaubleItemHandler;
import com.github.yimeng261.maidspell.inventory.MaidAwareBaubleItemHandler;
import com.github.yimeng261.maidspell.inventory.SpellBookAwareMaidBackpackHandler;
import com.mojang.logging.LogUtils;

import net.minecraft.world.entity.EntityType;
import net.minecraft.world.level.Level;
import net.minecraftforge.items.ItemStackHandler;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

import java.lang.reflect.Field;

/**
 * EntityMaid的Mixin，用于:
 * 1. 替换女仆背包处理器为支持法术书变化监听的版本
 * 2. 替换女仆饰品处理器为支持女仆实体关联的版本
 */
@Mixin(EntityMaid.class)
public class EntityMaidMixin {

    /**
     * 在构造函数完成后替换字段值
     */
    @Inject(method = "<init>(Lnet/minecraft/world/entity/EntityType;Lnet/minecraft/world/level/Level;)V", 
            at = @At("TAIL"))
    private void replaceHandlers(EntityType<EntityMaid> type, Level world, CallbackInfo ci) {
        try {
            LogUtils.getLogger().info("Replacing maid handlers in constructor");
            
            // 替换背包处理器
            Field maidInvField = EntityMaid.class.getDeclaredField("maidInv");
            maidInvField.setAccessible(true);
            SpellBookAwareMaidBackpackHandler newMaidInv = new SpellBookAwareMaidBackpackHandler(36, (EntityMaid)(Object)this);
            maidInvField.set(this, newMaidInv);
            LogUtils.getLogger().info("Successfully replaced maidInv with SpellBookAwareMaidBackpackHandler");
            
            // 替换饰品处理器
            Field maidBaubleField = EntityMaid.class.getDeclaredField("maidBauble");
            maidBaubleField.setAccessible(true);
            MaidAwareBaubleItemHandler newMaidBauble = new MaidAwareBaubleItemHandler(9, (EntityMaid)(Object)this);
            maidBaubleField.set(this, newMaidBauble);
            LogUtils.getLogger().info("Successfully replaced maidBauble with MaidAwareBaubleItemHandler");
            
        } catch (Exception e) {
            LogUtils.getLogger().error("Failed to replace maid handlers", e);
        }
    }
} 