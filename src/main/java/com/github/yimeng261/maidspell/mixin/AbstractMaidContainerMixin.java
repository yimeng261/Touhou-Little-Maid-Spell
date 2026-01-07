package com.github.yimeng261.maidspell.mixin;

import com.github.tartaricacid.touhoulittlemaid.inventory.container.AbstractMaidContainer;
import com.github.tartaricacid.touhoulittlemaid.entity.passive.EntityMaid;
import com.github.yimeng261.maidspell.Global;
import com.github.yimeng261.maidspell.item.MaidSpellItems;
import com.github.yimeng261.maidspell.spell.manager.BaubleStateManager;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.Level;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.Redirect;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

import javax.annotation.Nullable;
import java.util.Map;
import java.util.UUID;

/**
 * Mixin用于移除女仆容器的距离检查限制
 * 允许玩家在任意距离打开女仆背包
 */
@Mixin(value = AbstractMaidContainer.class, remap = false)
public class AbstractMaidContainerMixin {
    
    @Final
    @Shadow
    protected EntityMaid maid;
    
    /**
     * 注入到 stillValid 方法开头，取消原方法并返回我们的逻辑
     */
    @Inject(method = "stillValid", at = @At("HEAD"), cancellable = true, remap = true)
    public void stillValid(Player playerIn, CallbackInfoReturnable<Boolean> cir) {
        if(maid.getMaidBauble().containsItem(MaidSpellItems.ENDER_POCKET.get())){
            boolean isValid = maid.isOwnedBy(playerIn) && !maid.isSleeping() && maid.isAlive();
            cir.setReturnValue(isValid);
        }
    }

    /**
     * 重定向 Level.getEntity(int) 调用
     * 当女仆装备末影腰包且常规方式无法获取时，从Global缓存中查找
     */
    @Redirect(
        method = "<init>",
        at = @At(
                value = "INVOKE",
                target = "Lnet/minecraft/world/level/Level;getEntity(I)Lnet/minecraft/world/entity/Entity;",
                remap = true
        ),
        remap = true
    )
    @Nullable
    private Entity redirectGetEntity(Level level, int entityId) {
        // 首先尝试常规方式获取实体
        Entity entity = level.getEntity(entityId);
        
        // 如果获取成功，直接返回
        if (entity instanceof EntityMaid) {
            return entity;
        }
        
        // 如果常规方式失败，尝试从Global缓存中查找
        // 这种情况通常发生在女仆距离玩家很远，但区块已被加载的情况
        try {
            for(EntityMaid maid : Global.maidList){
                if (maid.getId() == entityId) {
                    if (BaubleStateManager.hasBauble(maid, MaidSpellItems.ENDER_POCKET)) {
                        Global.LOGGER.debug("从Global缓存中找到远距离女仆: {} (ID: {})",
                                maid.getName().getString(), entityId);
                        entity = maid;
                        break;
                    }
                }
            }
        } catch (Exception e) {
            Global.LOGGER.error("从Global缓存中查找女仆时发生错误", e);
        }
        
        // 如果都失败了，返回null（保持原有行为）
        return entity;
    }
}
