package com.github.yimeng261.maidspell.stagewright.support;

import com.github.tartaricacid.touhoulittlemaid.entity.passive.EntityMaid;
import net.magicterra.stagewright.scene.SceneContext;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.ResourceKey;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.damagesource.DamageType;
import net.minecraft.world.damagesource.DamageTypes;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.level.Level;

import java.util.LinkedHashMap;
import java.util.Map;

/** 按指定的伤害来源打一下并量出实际扣血，用来判定"打不打得到"。 */
public final class Hits {
    /** 试探伤害：足够看出扣血，又远低于任何测试目标的生命。 */
    public static final float PROBE = 2;

    private Hits() {
    }

    /** 伤害来源：direct 是直接造成伤害的实体（弹射物、法术实体），causing 是记账的实体（施法者），都可为 null。 */
    public static DamageSource source(Level level, ResourceKey<DamageType> type, Entity direct, Entity causing) {
        return new DamageSource(level.registryAccess().registryOrThrow(Registries.DAMAGE_TYPE).getHolderOrThrow(type), direct, causing);
    }

    public static DamageSource magic(Entity direct, Entity causing) {
        Entity anchor = direct != null ? direct : causing;
        return source(anchor.level(), DamageTypes.MAGIC, direct, causing);
    }

    /**
     * 先把目标回满、清受击间隔，再用 source 打 amount，返回实际扣掉的生命（被取消或免疫时为 0）。
     * 女仆走本模组的写血旁路回满，玩家和其他生物直接回满。
     */
    public static float dealt(LivingEntity target, DamageSource source, float amount) {
        restore(target);
        float before = target.getHealth();
        target.hurt(source, amount);
        float after = target.getHealth();
        restore(target);
        return before - after;
    }

    public static boolean lands(LivingEntity target, DamageSource source) {
        return dealt(target, source, PROBE) > 0;
    }

    /** 每个目标是否被 source 打到，按名字排好，便于一次比对整张表。 */
    public static Map<String, Boolean> matrix(Map<String, LivingEntity> targets, DamageSource source) {
        Map<String, Boolean> result = new LinkedHashMap<>();
        targets.forEach((name, target) -> {
            if (target != null) {
                result.put(name, lands(target, source));
            }
        });
        return result;
    }

    /** 期望表：列出的名字取 value，场景据此与 {@link #matrix} 比对。 */
    public static Map<String, Boolean> expect(Map<String, LivingEntity> targets, boolean value) {
        Map<String, Boolean> result = new LinkedHashMap<>();
        targets.forEach((name, target) -> {
            if (target != null) {
                result.put(name, value);
            }
        });
        return result;
    }

    public static void restore(LivingEntity target) {
        target.invulnerableTime = 0;
        target.hurtTime = 0;
        if (target instanceof EntityMaid maid) {
            Actors.setMaidHealth(maid, maid.getMaxHealth());
        } else {
            target.setHealth(target.getMaxHealth());
        }
    }

    /** 记录并断言 source 打 targets 的结果全为 expected。 */
    public static void expectAll(SceneContext ctx, String label, Map<String, LivingEntity> targets, DamageSource source, boolean expected) {
        ctx.check(matrix(targets, source)).as(label).isEqualTo(expect(targets, expected));
    }
}
