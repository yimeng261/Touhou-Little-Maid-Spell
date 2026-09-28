package com.github.yimeng261.maidspell.compat.irons_spellbooks.event;

import com.github.yimeng261.maidspell.compat.irons_spellbooks.entity.winefox.MagicalWinefoxBossEntity;
import io.redspace.ironsspellbooks.api.registry.AttributeRegistry;
import net.minecraft.world.entity.ai.attributes.Attribute;
import net.minecraft.world.entity.ai.attributes.AttributeInstance;
import net.minecraft.world.entity.ai.attributes.AttributeModifier;
import net.minecraftforge.event.entity.EntityJoinLevelEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.registries.RegistryObject;

import java.util.List;
import java.util.UUID;

/**
 * 在实体入世时按当前配置添加临时法强倍率，兼顾新生成实体和旧存档。
 * 配置尚未加载时不能在 {@code createAttributes} 中读取；临时修饰符也不会写入 NBT。
 * 同时调整通用及各学派法强，保留学派之间原有的相对差异。
 * 由铁魔法兼容入口注册，避免缺少铁魔法时加载此类。
 */
public final class WinefoxSpellPowerBonus {

    /** 固定 UUID 使重算倍率时可以替换旧修饰符。 */
    private static final UUID SPELL_POWER_MODIFIER_ID =
            UUID.fromString("d5b1a7c4-3e8f-4a62-9c1d-7f0e2b6a4c93");

    /** 覆盖全部学派，以免将来调整招式时漏掉倍率。 */
    private static final List<RegistryObject<Attribute>> SCHOOL_POWER_ATTRIBUTES = List.of(
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

        applyTo(boss, AttributeRegistry.SPELL_POWER.get(), amount);
        for (RegistryObject<Attribute> attribute : SCHOOL_POWER_ATTRIBUTES) {
            applyTo(boss, attribute.get(), amount);
        }
    }

    private static void applyTo(MagicalWinefoxBossEntity boss, Attribute attribute, double amount) {
        AttributeInstance instance = boss.getAttribute(attribute);
        if (instance == null) {
            // 其它模组可能移除该属性。
            return;
        }
        instance.removeModifier(SPELL_POWER_MODIFIER_ID);
        if (amount != 0.0D) {
            instance.addTransientModifier(new AttributeModifier(SPELL_POWER_MODIFIER_ID,
                    "Winefox spell power multiplier", amount, AttributeModifier.Operation.MULTIPLY_TOTAL));
        }
    }
}
