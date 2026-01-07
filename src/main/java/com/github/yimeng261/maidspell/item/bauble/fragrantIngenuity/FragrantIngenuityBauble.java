package com.github.yimeng261.maidspell.item.bauble.fragrantIngenuity;

import com.github.tartaricacid.touhoulittlemaid.api.bauble.IMaidBauble;
import com.github.tartaricacid.touhoulittlemaid.api.event.MaidAfterEatEvent;
import com.github.tartaricacid.touhoulittlemaid.entity.passive.EntityMaid;
import com.github.tartaricacid.touhoulittlemaid.util.ItemsUtil;
import com.github.yimeng261.maidspell.Config;
import com.mojang.logging.LogUtils;

import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.world.effect.MobEffect;
import net.minecraftforge.common.MinecraftForge;
import net.minecraftforge.event.server.ServerStartedEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;

import java.util.ArrayList;
import java.util.List;

import org.slf4j.Logger;

/**
 * 馥郁巧思 Fragrant Ingenuity 饰品扩展行为
 * 女仆进食额外增加好感，女仆喂食主人会赋予随机的1级正面buff
 * 
 * 喂食主人的buff效果通过MaidFeedOwnerTaskMixin实现
 */
public class FragrantIngenuityBauble implements IMaidBauble {
    public static final Logger LOGGER = LogUtils.getLogger();
    
    // 可用的正面buff列表
    public static final List<MobEffect> POSITIVE_EFFECTS = new ArrayList<>();
    
    @SubscribeEvent
    public void onServerStarted(ServerStartedEvent event) {
        POSITIVE_EFFECTS.clear();
        BuiltInRegistries.MOB_EFFECT.entrySet().forEach(entry -> {
            if(entry.getValue().isBeneficial()){
                POSITIVE_EFFECTS.add(entry.getValue());
            }
        });
    }

    public FragrantIngenuityBauble() {
        MinecraftForge.EVENT_BUS.register(this);
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

