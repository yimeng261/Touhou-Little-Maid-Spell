package com.github.yimeng261.maidspell;

import com.github.tartaricacid.touhoulittlemaid.entity.passive.EntityMaid;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraftforge.event.entity.living.LivingHurtEvent;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.Map;
import java.util.function.BiFunction;
import java.util.function.Function;

public class Global {
    public static ArrayList<BiFunction<LivingHurtEvent,EntityMaid,Void>> common_damageProcessors = new ArrayList<>(){{
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

    public static ArrayList<BiFunction<LivingHurtEvent,EntityMaid,Void>> common_hurtProcessors = new ArrayList<>();

    public static Map<String,BiFunction<LivingHurtEvent,EntityMaid,Void>> bauble_damageProcessors = new HashMap<>();

    public static Map<String,BiFunction<LivingHurtEvent,EntityMaid,Void>> bauble_hurtProcessors = new HashMap<>();
}
