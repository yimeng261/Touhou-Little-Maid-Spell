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
import java.util.HashMap;
import java.util.UUID;

/**
 * 法术强化饰品实现
 * 根据主人的铁魔法属性为女仆提供相应的属性加成
 * 使用注册表查找，完全避免反射
 * 优化：女仆始终保有modifier，只更新数值
 */
public class SpellEnhancementBauble implements IMaidBauble {

    // 支持的属性列表
    private static final ArrayList<AttributeConfig> ATTRIBUTES = new ArrayList<>();

    private static final HashMap<UUID, HashMap<String,Double>> playerAttributeValues = new HashMap<>();

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
                ATTRIBUTES.add(new AttributeConfig(attribute, defaultValue,attribute.getDescriptionId().replace("attribute.irons_spellbooks.", "")));
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
    
    /**
     * 更新女仆的属性加成
     */
    private void updateMaidEnhancements(EntityMaid maid, ServerPlayer player) {
        for (AttributeConfig config : ATTRIBUTES) {
            double playerValue = player.getAttributeValue(config.attribute);
            HashMap<String, Double> attributeValues = playerAttributeValues.computeIfAbsent(player.getUUID(), k -> new HashMap<>());

            if (attributeValues.get(config.attributeName) == null || attributeValues.get(config.attributeName) != playerValue) {
                attributeValues.put(config.attributeName, playerValue);
            } else {
                continue;
            }

            double bonus = Math.max(0, playerValue - config.defaultValue);

            AttributeInstance maidAttr = maid.getAttribute(config.attribute);
            UUID uuid = new UUID(MaidSpellMod.MOD_ID.hashCode(), config.attribute.getDescriptionId().hashCode());
            AttributeModifier modifier = new AttributeModifier(uuid, "yimeng" + config.attributeName, bonus, AttributeModifier.Operation.ADDITION);
            if (maidAttr == null) {
                return;
            }
            maidAttr.removeModifier(uuid);
            maidAttr.addTransientModifier(modifier);
        }
    }



    /**
     * 属性配置类
     */
    private static class AttributeConfig {
        final Attribute attribute;
        final double defaultValue;
        String attributeName;


        AttributeConfig(Attribute attribute, double defaultValue, String attributeName) {
            this.attribute = attribute;
            this.defaultValue = defaultValue;
            this.attributeName = attributeName;
        }
    }
} 