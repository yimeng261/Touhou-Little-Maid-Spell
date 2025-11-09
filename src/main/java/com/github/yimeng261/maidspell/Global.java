package com.github.yimeng261.maidspell;

import com.github.tartaricacid.touhoulittlemaid.entity.passive.EntityMaid;
import com.github.yimeng261.maidspell.api.IMaidSpellData;
import com.github.yimeng261.maidspell.utils.DataItem;
import com.mojang.logging.LogUtils;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Item;
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

    public static ArrayList<BiFunction<LivingIncomingDamageEvent, EntityMaid,Void>> commonDamageCalc = new ArrayList<>();

    public static ArrayList<BiFunction<LivingIncomingDamageEvent,Player,Void>> playerHurtCalcAft = new ArrayList<>();

    public static final HashMap<UUID,HashMap<UUID,EntityMaid>> maidInfos = new HashMap<>();

    public static final Set<EntityMaid> maidList = new HashSet<>();

    public static ArrayList<BiFunction<LivingIncomingDamageEvent,EntityMaid,Void>> commonHurtCalc = new ArrayList<>();

    public static ArrayList<Function<IMaidSpellData.CoolDown, Void>> commonCoolDownCalc = new ArrayList<>();

    public static Map<Item,BiFunction<LivingDamageEvent.Post,EntityMaid,Void>> baubleDamageCalcAft = new HashMap<>();

    public static Map<Item,BiFunction<LivingIncomingDamageEvent,EntityMaid,Void>> baubleDamageCalcPre = new HashMap<>();

    public static Map<Item,BiFunction<LivingIncomingDamageEvent,EntityMaid,Void>> baubleCommonHurtCalcPre = new HashMap<>();

    public static Map<Item,Function<DataItem,Void>> baubleHurtCalcPre = new HashMap<>();

    public static Map<Item,Function<DataItem,Void>> baubleHurtCalcFinal = new HashMap<>();

    public static Map<Item, Function<IMaidSpellData.CoolDown, Void>> baubleCoolDownCalc = new HashMap<>();

    public static Map<Item,BiFunction<MobEffectEvent.Added,EntityMaid,Void>> baubleEffectAddedCalc = new HashMap<>();

    public static Map<Item,BiFunction<LivingDeathEvent,EntityMaid,Void>> baubleDeathCalc = new HashMap<>();


    public static void resetCommonDamageCalc() {
        commonDamageCalc.clear();
        commonDamageCalc.add((event, maid) -> {
            LivingEntity entity = event.getEntity();
            if (entity instanceof EntityMaid) {
                event.setCanceled(true);
            } else if (entity instanceof Player) {
                event.setCanceled(true);
            }
            return null;
        });
        commonDamageCalc.add((hurtEvent, maid)->{
            if(maid.getTask().getUid().toString().startsWith("maidspell")) {
                hurtEvent.setAmount((float) (hurtEvent.getAmount()*Config.spellDamageMultiplier));
            }
            return null;
        });
    }

    public static void resetCommonCoolDownCalc() {
        commonCoolDownCalc.clear();
        commonCoolDownCalc.add((coolDown -> {
            coolDown.cooldownticks= (int)(coolDown.cooldownticks*Config.coolDownMultiplier);
            return null;
        }));
    }
}
