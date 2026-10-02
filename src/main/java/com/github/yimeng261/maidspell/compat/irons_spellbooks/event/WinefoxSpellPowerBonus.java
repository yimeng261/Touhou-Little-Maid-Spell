package com.github.yimeng261.maidspell.compat.irons_spellbooks.event;

import com.github.yimeng261.maidspell.MaidSpellMod;
import com.github.yimeng261.maidspell.compat.irons_spellbooks.entity.winefox.MagicalWinefoxBossEntity;
import io.redspace.ironsspellbooks.api.registry.AttributeRegistry;
import net.minecraft.core.Holder;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.entity.ai.attributes.Attribute;
import net.minecraft.world.entity.ai.attributes.AttributeInstance;
import net.minecraft.world.entity.ai.attributes.AttributeModifier;
import net.neoforged.neoforge.event.entity.EntityJoinLevelEvent;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.neoforge.registries.DeferredHolder;

import java.util.List;

/**
 * 在实体入世时按当前配置添加临时法强倍率，兼顾新生成实体和旧存档。
 * 配置尚未加载时不能在 {@code createAttributes} 中读取；临时修饰符也不会写入 NBT。
 * 同时调整通用及各学派法强，保留学派之间原有的相对差异。
 * 由铁魔法兼容入口注册，避免缺少铁魔法时加载此类。
 */
public final class WinefoxSpellPowerBonus {

    /** 固定 ID 使重算倍率时可以替换旧修饰符。 */
    private static final ResourceLocation SPELL_POWER_MODIFIER_ID =
            ResourceLocation.fromNamespaceAndPath(MaidSpellMod.MOD_ID, "winefox_spell_power_multiplier");

    /** 覆盖全部学派，以免将来调整招式时漏掉倍率。 */
    private static final List<DeferredHolder<Attribute, Attribute>> SCHOOL_POWER_ATTRIBUTES = List.of(
            AttributeRegistry.FIRE_SPELL_POWER,
            AttributeRegistry.ICE_SPELL_POWER,
            AttributeRegistry.LIGHTNING_SPELL_POWER,
            AttributeRegistry.HOLY_SPELL_POWER,
            AttributeRegistry.ENDER_SPELL_POWER,
            AttributeRegistry.BLOOD_SPELL_POWER,
            AttributeRegistry.EVOCATION_SPELL_POWER,
            AttributeRegistry.NATURE_SPELL_POWER,
            AttributeRegistry.ELDRITCH_SPELL_POWER
    );

    private WinefoxSpellPowerBonus() {
    }

    /** 两端都应用倍率，避免客户端同步前短暂使用旧值。 */
    @SubscribeEvent
    public static void onEntityJoinLevel(EntityJoinLevelEvent event) {
        if (event.getEntity() instanceof MagicalWinefoxBossEntity winefox) {
            apply(winefox);
        }
    }

    /** 幂等地重算倍率；默认值 1.0 只移除旧修饰符。 */
    public static void apply(MagicalWinefoxBossEntity boss) {
        double multiplier = boss.maidspell$spellPowerMultiplier();
        double amount = multiplier - 1.0D;

        applyTo(boss, AttributeRegistry.SPELL_POWER, amount);
        for (DeferredHolder<Attribute, Attribute> attribute : SCHOOL_POWER_ATTRIBUTES) {
            applyTo(boss, attribute, amount);
        }
    }

    private static void applyTo(MagicalWinefoxBossEntity boss, Holder<Attribute> attribute, double amount) {
        AttributeInstance instance = boss.getAttribute(attribute);
        if (instance == null) {
            // 其它模组可能移除该属性。
            return;
        }
        instance.removeModifier(SPELL_POWER_MODIFIER_ID);
        if (amount != 0.0D) {
            instance.addTransientModifier(new AttributeModifier(SPELL_POWER_MODIFIER_ID,
                    amount, AttributeModifier.Operation.ADD_MULTIPLIED_TOTAL));
        }
    }
}
