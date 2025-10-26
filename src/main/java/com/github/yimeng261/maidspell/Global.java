package com.github.yimeng261.maidspell;

import com.github.tartaricacid.touhoulittlemaid.entity.passive.EntityMaid;
import com.github.yimeng261.maidspell.api.AbstractSpellData;
import com.github.yimeng261.maidspell.utils.DataItem;
import com.mojang.logging.LogUtils;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import net.neoforged.neoforge.event.entity.living.LivingDamageEvent;
import net.neoforged.neoforge.event.entity.living.LivingDeathEvent;
import net.neoforged.neoforge.event.entity.living.LivingIncomingDamageEvent;
import net.neoforged.neoforge.event.entity.living.MobEffectEvent;
import org.slf4j.Logger;

import java.util.*;
import java.util.function.BiFunction;
import java.util.function.Function;

public class Global {

    public static final Logger LOGGER = LogUtils.getLogger();

    public static ArrayList<BiFunction<LivingIncomingDamageEvent, EntityMaid,Void>> common_damageProcessors = new ArrayList<>(){{
        add((event,maid)->{
            LivingEntity entity = event.getEntity();
            if(entity instanceof EntityMaid){
                event.setCanceled(true);
            }else if(entity instanceof Player){
                event.setCanceled(true);
            }
            return null;
        });
    }};


    public static ArrayList<BiFunction<LivingIncomingDamageEvent,Player,Void>> player_hurtProcessors_pre = new ArrayList<>();

    public static final HashMap<UUID,HashMap<UUID,EntityMaid>> maidInfos = new HashMap<>();

    public static final Set<EntityMaid> maidList = new HashSet<>();

    public static ArrayList<BiFunction<LivingIncomingDamageEvent,EntityMaid,Void>> common_hurtProcessors = new ArrayList<>();

    public static ArrayList<Function<AbstractSpellData.CoolDown, Void>> common_coolDownProcessors = new ArrayList<>();

    public static Map<String,BiFunction<LivingDamageEvent.Post,EntityMaid,Void>> bauble_damageProcessors_aft = new HashMap<>();

    public static Map<String,BiFunction<LivingIncomingDamageEvent,EntityMaid,Void>> bauble_damageProcessors_pre = new HashMap<>();

    public static Map<String,BiFunction<LivingIncomingDamageEvent,EntityMaid,Void>> bauble_commonHurtProcessors_pre = new HashMap<>();

    public static Map<String,Function<DataItem,Void>> bauble_hurtProcessors_pre = new HashMap<>();

    public static Map<String,Function<DataItem,Void>> bauble_hurtProcessors_final = new HashMap<>();

    public static Map<String, Function<AbstractSpellData.CoolDown, Void>> bauble_coolDownProcessors = new HashMap<>();

    public static Map<String,BiFunction<MobEffectEvent.Added,EntityMaid,Void>> bauble_effectAddedProcessors = new HashMap<>();

    public static Map<String,BiFunction<LivingDeathEvent,EntityMaid,Void>> bauble_deathProcessors = new HashMap<>();

}
