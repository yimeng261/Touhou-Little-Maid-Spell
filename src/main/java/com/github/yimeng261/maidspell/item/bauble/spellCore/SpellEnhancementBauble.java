package com.github.yimeng261.maidspell.item.bauble.spellCore;

import com.github.tartaricacid.touhoulittlemaid.api.bauble.IMaidBauble;
import com.github.tartaricacid.touhoulittlemaid.entity.passive.EntityMaid;
import com.github.yimeng261.maidspell.MaidSpellMod;
import com.mojang.logging.LogUtils;
import io.redspace.ironsspellbooks.IronsSpellbooks;
import net.minecraft.core.Holder;
import net.minecraft.core.Registry;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.ai.attributes.Attribute;
import net.minecraft.world.entity.ai.attributes.AttributeInstance;
import net.minecraft.world.entity.ai.attributes.AttributeModifier;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.neoforged.neoforge.registries.RegisterEvent;
import org.slf4j.Logger;

import java.util.ArrayList;
import java.util.List;

/**
 * 法术强化饰品实现
 * 根据主人的铁魔法属性为女仆提供相应的属性加成
 * 使用注册表查找，完全避免反射
 * 优化：女仆始终保有modifier，只更新数值
 */
public class SpellEnhancementBauble implements IMaidBauble {

    // 支持的属性列表
    private static final List<AttributeConfig> ATTRIBUTES = new ArrayList<>();
    private static final Logger LOGGER = LogUtils.getLogger();

    /**
     * 通过注册表初始化属性，无需反射
     */
    public static void initializeAttributes(RegisterEvent event) {
        // 铁魔法属性的ResourceLocation列表
        Registry<Attribute> registry = event.getRegistry(Registries.ATTRIBUTE);
        if (registry == null) {
            return;
        }
        for (var entry : registry.entrySet()) {
            ResourceLocation id = entry.getKey().location();
            Attribute attribute = entry.getValue();
            if (IronsSpellbooks.MODID.equals(id.getNamespace())) {
                double defaultValue = attribute.getDefaultValue();
                ATTRIBUTES.add(new AttributeConfig(registry.wrapAsHolder(attribute), defaultValue, ResourceLocation.fromNamespaceAndPath(MaidSpellMod.MOD_ID, id.getPath())));
            }
        }
    }

    @Override
    public void onTick(EntityMaid maid, ItemStack baubleItem) {
        // 只在服务端执行，每40tick（2秒）更新一次
        if (maid.level().isClientSide || maid.tickCount % 40 != 0) {
            return;
        }

        // 获取女仆的主人
        LivingEntity owner = maid.getOwner();
        if (!(owner instanceof Player player)) {
            return;
        }

        // 更新女仆的属性加成
        updateMaidEnhancements(maid, player);
    }

    /**
     * 更新女仆的属性加成，优化版本
     */
    private void updateMaidEnhancements(EntityMaid maid, Player player) {
        for (AttributeConfig config : ATTRIBUTES) {
            double bonus = 0.0;
            if (player != null) {
                // 计算玩家属性加成（超过默认值的部分）
                double playerValue = player.getAttributeValue(config.attribute);
                bonus = Math.max(0, playerValue - config.defaultValue);
            }

            AttributeInstance maidAttr=maid.getAttribute(config.attribute);
            AttributeModifier modifier = new AttributeModifier(config.renamedId, bonus, AttributeModifier.Operation.ADD_VALUE);
            if(maidAttr == null) {
                return;
            }
            if (config.renamedId != null) {
                maidAttr.removeModifier(config.renamedId);
            }
            config.renamedId = modifier.id();
            maidAttr.addTransientModifier(modifier);
        }
    }


    /**
     * 清理女仆的所有属性修饰符
     */
    private void clearAllEnhancements(EntityMaid maid) {
        for (AttributeConfig config : ATTRIBUTES) {
            AttributeInstance maidAttr = maid.getAttribute(config.attribute);
            if (maidAttr != null && config.renamedId != null) {
                maidAttr.removeModifier(config.renamedId);
                LOGGER.debug("Removed modifier {} for attribute {} from maid {}",
                    config.renamedId, config.attribute.getKey(), maid.getName().getString());
            }
        }
    }

    /**
     * 属性配置类
     */
    private static class AttributeConfig {
        final Holder<Attribute> attribute;
        final double defaultValue;
        ResourceLocation renamedId;


        AttributeConfig(Holder<Attribute> attribute, double defaultValue, ResourceLocation renamedId) {
            this.attribute = attribute;
            this.defaultValue = defaultValue;
            this.renamedId = renamedId;
        }
    }
}