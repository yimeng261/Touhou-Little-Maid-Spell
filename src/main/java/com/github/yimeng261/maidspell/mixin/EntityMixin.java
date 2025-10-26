package com.github.yimeng261.maidspell.mixin;


import com.github.tartaricacid.touhoulittlemaid.entity.passive.EntityMaid;
import com.github.yimeng261.maidspell.Global;
import net.minecraft.world.entity.Entity;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(value = Entity.class,remap = false)
public class EntityMixin {
    /**
     * 拦截女仆的remove方法，防止非正常途径移除血量不为0的女仆
     */
    @Inject(method = "remove(Lnet/minecraft/world/entity/Entity$RemovalReason;)V",
            at = @At("HEAD"),
            cancellable = true, remap = true)
    public void onRemove(Entity.RemovalReason reason, CallbackInfo ci) {
        try {
            if((Object)this instanceof EntityMaid maid) {

                // 如果女仆血量为0，允许正常移除
                Global.LOGGER.debug("remove called for {}", maid);
                if (maid.getHealth() <= 0.0f) {
                    return;
                }

                // 检查调用栈，判断是否来自touhou-little-maid模组
                if (!maidSpell$isCallValid()) {
                    Global.LOGGER.debug("Prevented non-TLM removal of maid {} with health {}",
                            maid.getUUID(), maid.getHealth());
                    ci.cancel();
                }
            }

        } catch (Exception e) {
            Global.LOGGER.error("Failed to check maid removal source", e);
        }
    }

    /**
     * 检查调用栈是否来自touhou-little-maid模组
     * @return 如果调用来自TLM模组返回true
     */
    @Unique
    private boolean maidSpell$isCallValid() {
        StackTraceElement[] stackTrace = Thread.currentThread().getStackTrace();
        boolean callFromTouhouLittleMaidMod = false;
        int f=0;
        for(int i=stackTrace.length-10; i>=0; i--) {
            StackTraceElement stackTraceElement = stackTrace[i];
            String className = stackTraceElement.getClassName();
            //Global.LOGGER.debug("className {}", className);
            if(className.endsWith("EntityMaid")) {
                continue;
            }
            if (className.contains("tlm") || className.toLowerCase().contains("maid")) {
                callFromTouhouLittleMaidMod = true;
                break;
            }
        }

        return callFromTouhouLittleMaidMod;
    }
}
