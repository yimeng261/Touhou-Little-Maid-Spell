package com.github.yimeng261.maidspell.mixin.iss;

import com.github.yimeng261.maidspell.client.animation.MagicCastingAnimateState;
import com.github.yimeng261.maidspell.client.spell.CastingAnimateStateAccessor;
import io.redspace.ironsspellbooks.capabilities.magic.SyncedSpellData;
import io.redspace.ironsspellbooks.player.ClientMagicData;
import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * 设置同步状态
 *
 * @author Gardel &lt;gardel741@outlook.com&gt;
 * @since 2026-01-03 14:20
 */
@Mixin(value = ClientMagicData.class, remap = false)
public class ClientMagicDataMixin {
    /**
     * <b>必须是 HEAD，不能是 TAIL。</b>
     *
     * <p>对铁魔法自己的施法怪（{@code IMagicEntity}，万法酒狐就是），
     * {@code handleAbstractCastingMobSyncedData} 方法体里会先调
     * {@code AbstractSpellCastingMob.setSyncedSpellData(syncedSpellData)}，
     * 那边把<b>包里这个对象</b>直接装进 {@code MagicData}，然后对瞬发法术接着走：
     *
     * <pre>
     * setSyncedSpellData -> castComplete() -> MagicData.resetCastingState()
     *                    -> getSyncedData().setIsCasting(false, "", 0, ...)
     * </pre>
     *
     * <p>最后那一句改的正是<b>我们马上要读的那个对象</b>。挂在 TAIL 上时，
     * {@code updateState} 拿到的已经是被清成 {@code isCasting=false, spellId=""} 的空壳，
     * 于是走「两边都是 none」的提前返回，相位永远停在 {@code NONE}，
     * {@code ISSCastingAnimationProvider} 什么也建不出来。她 22 条施法里 17 条是瞬发。
     *
     * <p>普通女仆不受影响，两个注入点等价：女仆不是 {@code IMagicEntity}，
     * 上面那条分支压根不进，包里的对象没人动。
     *
     * <p>放在 HEAD 安全：{@code updateState} 只读传进来的 {@code syncedSpellData}
     * 和 {@code caster.level().isClientSide}，不依赖铁魔法那边装没装好。
     */
    @Inject(method = "handleAbstractCastingMobSyncedData", at = @At(value = "HEAD"))
    private static void afterHandleAbstractCastingMobSyncedData(int entityId, SyncedSpellData syncedSpellData, CallbackInfo ci) {
        ClientLevel level = Minecraft.getInstance().level;
        if (level != null) {
            Entity entity = level.getEntity(entityId);
            // 原先这里还要求 entity instanceof EntityMaid。万法酒狐自己实现了
            // CastingAnimateStateAccessor（她不是女仆，mixin 挂不上去），那条判断会把她挡在门外。
            if (entity instanceof CastingAnimateStateAccessor animateStateAccessor
                    && entity instanceof LivingEntity caster) {
                MagicCastingAnimateState magicCastingAnimateState = animateStateAccessor.maidspell$getCastingAnimateState();
                if (magicCastingAnimateState != null) {
                    magicCastingAnimateState.updateState(caster, syncedSpellData);
                }
            }
        }
    }
}
