package com.github.yimeng261.maidspell;

import com.github.yimeng261.maidspell.spell.SimplifiedSpellCaster;
import com.github.yimeng261.maidspell.task.SpellCombatFarTask;
import com.github.yimeng261.maidspell.task.SpellCombatMeleeTask;
import com.mojang.logging.LogUtils;
import net.minecraftforge.common.ForgeConfigSpec;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.fml.event.config.ModConfigEvent;
import org.slf4j.Logger;

/**
 * 女仆法术战斗系统配置类
 * 管理所有可配置的参数和设置
 */
@Mod.EventBusSubscriber(modid = MaidSpellMod.MOD_ID, bus = Mod.EventBusSubscriber.Bus.MOD)
public class Config {

    private static final Logger LOGGER = LogUtils.getLogger();
    
    private static final ForgeConfigSpec.Builder BUILDER = new ForgeConfigSpec.Builder();
    
    
    // 战斗相关配置
    private static final ForgeConfigSpec.DoubleValue MAX_SPELL_RANGE = BUILDER
        .comment("最大法术攻击范围 (默认: 24.0)")
        .comment("Maximum spell attack range")
        .defineInRange("maxSpellRange", 24.0, 8.0, 64.0);

        
    private static final ForgeConfigSpec.DoubleValue MELEE_RANGE = BUILDER
        .comment("近战攻击范围 (默认: 2.5)")
        .comment("Melee attack range")
        .defineInRange("meleeRange", 2.5, 1.0, 5.0);

    private static final ForgeConfigSpec.DoubleValue FAR_RANGE = BUILDER
            .comment("远程攻击范围 (默认: 8.5)")
            .comment("Far attack range")
            .defineInRange("farRange", 8.5, 1.0, 20.0);

        
    private static final ForgeConfigSpec.DoubleValue SPELL_DAMAGE_MULTIPLIER = BUILDER
        .comment("女仆伤害倍率 (默认: 1.0，仅在法术战斗任务下生效)")
        .comment("Maid damage multiplier(default:1.0,only effective on spellCombatTask)")
        .defineInRange("maidDamageMultiplier", 1.0, 0, 50.0);

    private static final ForgeConfigSpec.DoubleValue COOLDOWN_MULITIPLIER = BUILDER
            .comment("女仆法术冷却倍率 (默认: 1.0，仅在法术战斗任务下生效)")
            .comment("Maid cooldown multiplier(default:1.0,only effective on spellCombatTask)")
            .defineInRange("maidCooldownMultiplier", 1.0, 0, 50.0);

    public static final ForgeConfigSpec SPEC = BUILDER.build();

    // 缓存的配置值
    public static double maxSpellRange;
    public static double meleeRange;
    public static double farRange;
    public static double spellDamageMultiplier;
    public static double coolDownMultiplier;


    @SubscribeEvent
    static void onLoad(final ModConfigEvent event) {
        // 加载配置值到缓存变量
        maxSpellRange = MAX_SPELL_RANGE.get();
        meleeRange = MELEE_RANGE.get();
        spellDamageMultiplier = SPELL_DAMAGE_MULTIPLIER.get();
        coolDownMultiplier = COOLDOWN_MULITIPLIER.get();
        farRange = FAR_RANGE.get();

        SpellCombatMeleeTask.setSpellRange((float) maxSpellRange);
        SpellCombatFarTask.setSpellRange((float) maxSpellRange);
        SimplifiedSpellCaster.MELEE_RANGE= (float) meleeRange;
        SimplifiedSpellCaster.FAR_RANGE= (float) farRange;

        Global.common_damageProcessors.add((hurtEvent,maid)->{
            if(maid.getTask().getUid().toString().startsWith("maidspell")) {
                hurtEvent.setAmount((float) (hurtEvent.getAmount()*spellDamageMultiplier));
            }
            return null;
        });

        Global.common_coolDownProcessors.add((coolDown -> {
            coolDown.cooldownticks= (int)(coolDown.cooldownticks*coolDownMultiplier);
            return null;
        }));
    }


}
