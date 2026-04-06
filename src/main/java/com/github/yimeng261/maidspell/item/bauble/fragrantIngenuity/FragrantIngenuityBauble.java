package com.github.yimeng261.maidspell.item.bauble.fragrantIngenuity;

import com.github.tartaricacid.touhoulittlemaid.api.bauble.IMaidBauble;
import com.github.tartaricacid.touhoulittlemaid.api.event.MaidAfterEatEvent;
import com.github.tartaricacid.touhoulittlemaid.entity.passive.EntityMaid;
import com.github.tartaricacid.touhoulittlemaid.util.ItemsUtil;
import com.github.yimeng261.maidspell.Config;
import com.mojang.logging.LogUtils;
import net.minecraft.core.Holder;
import net.minecraft.core.Registry;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.core.registries.Registries;
import net.minecraft.world.effect.MobEffect;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.neoforge.common.NeoForge;
import net.neoforged.neoforge.registries.RegisterEvent;
import org.slf4j.Logger;

import java.util.ArrayList;
import java.util.List;

/**
 * 馥郁巧思 Fragrant Ingenuity 饰品扩展行为
 * 女仆进食额外增加好感，女仆喂食主人会赋予随机的1级正面buff
 *
 * 喂食主人的buff效果通过MaidFeedOwnerTaskMixin实现
 */
public class FragrantIngenuityBauble implements IMaidBauble {
    public static final Logger LOGGER = LogUtils.getLogger();

    // 可用的正面buff列表
    public static final List<Holder<MobEffect>> POSITIVE_EFFECTS = new ArrayList<>();

    public FragrantIngenuityBauble() {
        NeoForge.EVENT_BUS.register(this);
    }

    public static void onRegisterEffects(RegisterEvent event) {
        Registry<MobEffect> registry = event.getRegistry(Registries.MOB_EFFECT);
        if (registry == null) {
            return;
        }
        POSITIVE_EFFECTS.clear();
        registry.holders().filter(holder -> holder.value().isBeneficial()).forEach(POSITIVE_EFFECTS::add);
    }

    /**
     * 根据配置中的黑名单重建正面效果列表，在配置加载/重载时调用
     * Rebuild the positive effects list according to the blacklist in config, called on config load/reload
     */
    public static void refreshEffectsList() {
        List<String> blacklist = Config.fragrantIngenuityEffectBlacklist;
        POSITIVE_EFFECTS.clear();
        BuiltInRegistries.MOB_EFFECT.holders()
                .filter(holder -> holder.value().isBeneficial())
                .filter(holder -> {
                    if (blacklist == null || blacklist.isEmpty()) return true;
                    return holder.unwrapKey()
                            .map(key -> !blacklist.contains(key.location().toString()))
                            .orElse(true);
                })
                .forEach(POSITIVE_EFFECTS::add);
    }

    /**
     * 监听女仆进食事件，增加额外好感度
     */
    @SubscribeEvent
    public void afterMaidEat(MaidAfterEatEvent event) {
        EntityMaid maid = event.getMaid();
        if (ItemsUtil.getBaubleSlotInMaid(maid, this) >= 0) {
            // 女仆进食额外增加好感
            maid.getFavorabilityManager().add(Config.fragrantIngenuityFavorabilityGain);
            LOGGER.debug("[FragrantIngenuity] Maid {} gained {} favorability from eating",
                maid.getName().getString(), Config.fragrantIngenuityFavorabilityGain);
        }
    }
}

