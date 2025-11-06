package com.github.yimeng261.maidspell;

import com.github.tartaricacid.touhoulittlemaid.entity.passive.EntityMaid;
import com.github.yimeng261.maidspell.api.IMaidSpellData;
import com.github.yimeng261.maidspell.utils.DataItem;
import com.mojang.logging.LogUtils;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Item;
import net.minecraftforge.event.entity.living.LivingDamageEvent;
import net.minecraftforge.event.entity.living.LivingDeathEvent;
import net.minecraftforge.event.entity.living.LivingHurtEvent;
import net.minecraftforge.event.entity.living.MobEffectEvent;

import org.slf4j.Logger;

import java.util.*;
import java.util.function.BiFunction;
import java.util.function.Function;

public class Global {

    public static final Logger LOGGER = LogUtils.getLogger();

    public static ArrayList<BiFunction<LivingHurtEvent,EntityMaid,Void>> common_damageCalc = new ArrayList<>(){{
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


    public static ArrayList<BiFunction<LivingDamageEvent,Player,Void>> player_hurtCalc_aft = new ArrayList<>();

    public static final HashMap<UUID,HashMap<UUID,EntityMaid>> maidInfos = new HashMap<>();

    public static final Set<EntityMaid> maidList = new HashSet<>();

    public static ArrayList<BiFunction<LivingHurtEvent,EntityMaid,Void>> common_hurtCalc = new ArrayList<>();

    public static ArrayList<Function<IMaidSpellData.CoolDown, Void>> common_coolDownCalc = new ArrayList<>();

    public static Map<Item,BiFunction<LivingDamageEvent,EntityMaid,Void>> bauble_damageCalc_aft = new HashMap<>();

    public static Map<Item,BiFunction<LivingHurtEvent,EntityMaid,Void>> bauble_damageCalc_pre = new HashMap<>();

    public static Map<Item,BiFunction<LivingHurtEvent,EntityMaid,Void>> bauble_commonHurtCalc_pre = new HashMap<>();

    public static Map<Item,Function<DataItem,Void>> bauble_hurtCalc_pre = new HashMap<>();

    public static Map<Item,Function<DataItem,Void>> bauble_hurtCalc_final = new HashMap<>();

    public static Map<Item, Function<IMaidSpellData.CoolDown, Void>> bauble_coolDownCalc = new HashMap<>();

    public static Map<Item,BiFunction<MobEffectEvent.Added,EntityMaid,Void>> bauble_effectAddedCalc = new HashMap<>();

    public static Map<Item,BiFunction<LivingDeathEvent,EntityMaid,Void>> bauble_deathCalc = new HashMap<>();

}
