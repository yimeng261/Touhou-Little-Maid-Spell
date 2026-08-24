package com.github.yimeng261.maidspell.effect;

import com.github.yimeng261.maidspell.MaidSpellMod;
import net.minecraft.world.effect.MobEffect;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.neoforge.registries.DeferredRegister;
import net.minecraft.core.registries.Registries;
import net.neoforged.neoforge.registries.DeferredHolder;

/**
 * 本模组的药水效果。
 *
 * <p>和 {@code IronsSpellbooksCompatEffects} 分开，是因为那边的注册挂在
 * {@code IronsSpellbooksCompat.init} 上，只在装了铁魔法时才跑；而虚空漫步是
 * {@code staranchor_pearl} 自己带的，那个物品无条件注册（星锚珍珠的战利品表虽然
 * 挂在铁魔法条件下，但物品本身不该因为铁魔法缺席就变成空气）。
 */
public final class MaidSpellEffects {
    public static final DeferredRegister<MobEffect> MOB_EFFECTS =
            DeferredRegister.create(Registries.MOB_EFFECT, MaidSpellMod.MOD_ID);

    /** 虚空漫步：星锚珍珠给的状态，期间免疫虚空伤害。 */
    public static final DeferredHolder<MobEffect, MobEffect> VOID_WALK =
            MOB_EFFECTS.register("void_walk", VoidWalkEffect::new);

    private MaidSpellEffects() {
    }

    public static void register(IEventBus eventBus) {
        MOB_EFFECTS.register(eventBus);
    }
}
