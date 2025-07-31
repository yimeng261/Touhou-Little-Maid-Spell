package com.github.yimeng261.maidspell.event;

import com.github.yimeng261.maidspell.Config;
import net.minecraftforge.event.entity.living.LivingHurtEvent;

import java.lang.reflect.Method;
import java.util.ArrayList;
import java.util.function.Function;

public class Global {
    public static final ArrayList<Function<LivingHurtEvent,Void>> damageProcess = new ArrayList<>(){{
        add(event -> {
            event.setAmount((float) (event.getAmount() * Config.spellDamageMultiplier));
            return null;
        });
    }};
}
