package com.github.yimeng261.maidspell.item.bauble.spellCore;

import com.github.tartaricacid.touhoulittlemaid.api.bauble.IMaidBauble;
import com.github.tartaricacid.touhoulittlemaid.entity.passive.EntityMaid;

import com.github.yimeng261.maidspell.MaidSpellMod;
import com.mojang.logging.LogUtils;

import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.ai.attributes.Attribute;
import net.minecraft.world.entity.ai.attributes.AttributeInstance;
import net.minecraft.world.entity.ai.attributes.AttributeModifier;
import net.minecraft.world.item.ItemStack;
import net.minecraftforge.fml.ModList;
import net.minecraftforge.registries.ForgeRegistries;
import org.slf4j.Logger;

import java.util.ArrayList;
import java.util.UUID;

/**
 * 法术强化饰品实现
 * 根据主人的铁魔法属性为女仆提供相应的属性加成
 * 使用注册表查找，完全避免反射
 * 优化：直接对比女仆当前 modifier 数值，避免玩家级共享缓存导致多女仆串状态
 */
public class SpellEnhancementBauble implements IMaidBauble {

    // 支持的属性列表
    private static final ArrayList<AttributeConfig> ATTRIBUTES = new ArrayList<>();

    @SuppressWarnings("unused")
    private static final Logger LOGGER = LogUtils.getLogger();
    
    static {
        if (ModList.get().isLoaded("irons_spellbooks")) {
            initializeAttributes();
        }
    }

    /**
     * 通过注册表初始化属性，无需反射
     */
    private static void initializeAttributes() {
        // 铁魔法属性的ResourceLocation列表

        ForgeRegistries.ATTRIBUTES.forEach(attribute -> {
            if(attribute.getDescriptionId().startsWith("attribute.irons_spellbooks.")){
                double defaultValue = attribute.getDefaultValue();
                String attributeName = attribute.getDescriptionId().replace("attribute.irons_spellbooks.", "");
                ATTRIBUTES.add(new AttributeConfig(attribute, defaultValue, attributeName));
            }
        });
    }
    
    @Override
    public void onTick(EntityMaid maid, ItemStack baubleItem) {
        // 只在服务端执行，每40tick（2秒）更新一次
        if (maid.level().isClientSide || maid.tickCount % 40 != 0) {
            return;
        }

        LivingEntity owner = maid.getOwner();
        if (owner instanceof ServerPlayer player) {
            updateMaidEnhancements(maid, player);
        }
        
    }

    @Override
    public void onTakeOff(EntityMaid maid, ItemStack baubleItem) {
        for (AttributeConfig config : ATTRIBUTES) {
            AttributeInstance maidAttr = maid.getAttribute(config.attribute);
            if (maidAttr == null) {
                continue;
            }
            maidAttr.removeModifier(config.modifierUuid);
        }
    }
    
    /**
     * 更新女仆的属性加成
     */
    private void updateMaidEnhancements(EntityMaid maid, ServerPlayer player) {
        for (AttributeConfig config : ATTRIBUTES) {
            double playerValue = player.getAttributeValue(config.attribute);
            double bonus = Math.max(0, playerValue - config.defaultValue);
            AttributeInstance maidAttr = maid.getAttribute(config.attribute);
            if (maidAttr == null) {
                continue;
            }

            AttributeModifier currentModifier = maidAttr.getModifier(config.modifierUuid);
            if (bonus <= 0) {
                if (currentModifier != null) {
                    maidAttr.removeModifier(config.modifierUuid);
                }
                continue;
            }

            if (currentModifier != null && Double.compare(currentModifier.getAmount(), bonus) == 0) {
                continue;
            }

            if (currentModifier != null) {
                maidAttr.removeModifier(config.modifierUuid);
            }
            maidAttr.addTransientModifier(new AttributeModifier(
                config.modifierUuid,
                config.modifierName,
                bonus,
                AttributeModifier.Operation.ADDITION
            ));
        }
    }



    /**
     * 属性配置类
     */
    private static class AttributeConfig {
        final Attribute attribute;
        final double defaultValue;
        final String attributeName;
        final UUID modifierUuid;
        final String modifierName;


        AttributeConfig(Attribute attribute, double defaultValue, String attributeName) {
            this.attribute = attribute;
            this.defaultValue = defaultValue;
            this.attributeName = attributeName;
            this.modifierUuid = new UUID(MaidSpellMod.MOD_ID.hashCode(), attribute.getDescriptionId().hashCode());
            this.modifierName = "yimeng" + attributeName;
        }
    }
} 
