package com.github.yimeng261.maidspell.spell.providers;

import com.github.tartaricacid.touhoulittlemaid.entity.passive.EntityMaid;
import com.github.yimeng261.maidspell.api.ISpellBookProvider;
import com.github.yimeng261.maidspell.spell.data.MaidSlashBladeData;

import com.github.yimeng261.maidspell.spell.SimplifiedSpellCaster;
import mods.flammpfeil.slashblade.registry.SlashArtsRegistry;
import mods.flammpfeil.slashblade.registry.combo.ComboState;
import mods.flammpfeil.slashblade.slasharts.*;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.InteractionHand;

import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.ai.behavior.BehaviorUtils;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.phys.Vec3;

import org.slf4j.Logger;
import com.mojang.logging.LogUtils;

import mods.flammpfeil.slashblade.item.ItemSlashBlade;
import mods.flammpfeil.slashblade.registry.ComboStateRegistry;
import mods.flammpfeil.slashblade.util.AttackManager;
import mods.flammpfeil.slashblade.util.KnockBacks;

import java.util.ArrayList;
import java.util.List;
import java.util.Random;

/**
 * SlashBlade模组的法术提供者
 * 为女仆提供拔刀剑SA使用能力
 * 使用直接技能系统，不依赖原模组状态机
 */
public class SlashBladeProvider extends ISpellBookProvider<MaidSlashBladeData, ResourceLocation> {
    private static final Logger LOGGER = LogUtils.getLogger();
    private static final Random RANDOM = new Random();
    private static final int DASH_COUNT = 3;
    
    /**
     * 冲刺技能位移距离倍数
     * 调整此值可统一控制所有冲刺技能的位移距离
     * 默认 2.8 约等于 7格位移距离
     */
    private static final double DASH_DISTANCE_MULTIPLIER = 4;
    
    /**
     * 战斗中随机跳跃的概率（百分比）
     * 默认 25% 的概率在攻击时触发跳跃
     */
    private static final int RANDOM_JUMP_CHANCE = 15;
    
    /**
     * 跳跃高度倍数
     */
    private static double JUMP_HEIGHT_MULTIPLIER = 1.2;
    
    /**
     * 直接技能枚举 - 使用函数式接口，完全自动化执行
     */
    public enum DirectSkill {
        // ==================== 基础斩击系列 ====================
        BASIC_SLASH("基础斩击", SkillType.BASIC, maid -> {
            float roll = RANDOM.nextInt(60) - 30;
            AttackManager.doSlash(maid, roll, Vec3.ZERO, false, false, 1.0, KnockBacks.cancel);
        }),
        
        HORIZONTAL_SLASH("横斩", SkillType.BASIC, maid -> {
            float pitch = getPitchCorrection(maid);
            AttackManager.doSlash(maid, pitch, Vec3.ZERO, false, false, 1.0, KnockBacks.cancel);
        }),
        
        VERTICAL_SLASH("纵斩", SkillType.BASIC, maid -> {
            float pitch = getPitchCorrection(maid);
            AttackManager.doSlash(maid, -90 + pitch, Vec3.ZERO, false, false, 1.0, KnockBacks.cancel);
        }),
        
        DIAGONAL_SLASH_LEFT("斜斩·左", SkillType.BASIC, maid -> {
            float pitch = getPitchCorrection(maid);
            AttackManager.doSlash(maid, -45 + pitch, Vec3.ZERO, false, false, 1.0, KnockBacks.cancel);
        }),
        
        DIAGONAL_SLASH_RIGHT("斜斩·右", SkillType.BASIC, maid -> {
            float pitch = getPitchCorrection(maid);
            AttackManager.doSlash(maid, 45 + pitch, Vec3.ZERO, false, false, 1.0, KnockBacks.cancel);
        }),
        
        UPWARD_SLASH("上挑斩", SkillType.BASIC, maid -> {
            float pitch = getPitchCorrection(maid);
            // 上挑斩：在当前俯仰角基础上额外上扬
            AttackManager.doSlash(maid, -135 + pitch, Vec3.ZERO, false, false, 1.1, KnockBacks.toss);
        }),
        
        DOWNWARD_SLASH("下劈斩", SkillType.BASIC, maid -> {
            float pitch = getPitchCorrection(maid);
            // 下劈斩：在当前俯仰角基础上额外下压
            AttackManager.doSlash(maid, 90 + pitch, Vec3.ZERO, false, true, 1.2, KnockBacks.smash);
        }),
        
        // ==================== 连续攻击系列 ====================
        RAPID_SLASH("疾走居合", SkillType.COMBO, maid -> {
            // 连续3次斩击，考虑俯仰角
            float pitch = getPitchCorrection(maid);
            for (int i = 0; i < 3; i++) {
                float roll = -45 + 90 * RANDOM.nextFloat() + (i * 180);
                AttackManager.doSlash(maid, roll + pitch, Vec3.ZERO, false, i == 2, 0.44, KnockBacks.cancel);
            }
        }),
        
        MULTI_SLASH("连续斩", SkillType.COMBO, maid -> {
            // 连续5次快速斩击，考虑俯仰角
            float pitch = getPitchCorrection(maid);
            for (int i = 0; i < 5; i++) {
                float roll = -90 + 180 * RANDOM.nextFloat();
                Vec3 offset = new Vec3(
                    RANDOM.nextFloat() - 0.5f,
                    0,
                    RANDOM.nextFloat() - 0.5f
                );
                AttackManager.doSlash(maid, roll + pitch, offset, false, false, 0.24, KnockBacks.cancel);
            }
        }),
        
        CROSS_SLASH("十字斩", SkillType.COMBO, maid -> {
            // 横竖两刀，形成十字，考虑俯仰角
            float pitch = getPitchCorrection(maid);
            AttackManager.doSlash(maid, pitch, Vec3.ZERO, false, false, 0.6, KnockBacks.cancel);
            AttackManager.doSlash(maid, -90 + pitch, Vec3.ZERO, false, true, 0.6, KnockBacks.cancel);
        }),
        
        SPINNING_SLASH("旋风斩", SkillType.COMBO, maid -> {
            // 360度旋转斩击，考虑俯仰角
            float pitch = getPitchCorrection(maid);
            for (int i = 0; i < 4; i++) {
                float roll = i * 90;
                AttackManager.doSlash(maid, roll + pitch, Vec3.ZERO, false, i == 3, 0.35, KnockBacks.cancel);
            }
        }),
        
        RUSHING_COMBO("突进连斩", SkillType.COMBO, maid -> {
            // 快速连击3次，每次带偏移，考虑俯仰角
            float pitch = getPitchCorrection(maid);
            for (int i = 0; i < 3; i++) {
                Vec3 offset = AttackManager.genRushOffset(maid).scale(0.3);
                float roll = -60 + i * 60;
                AttackManager.doSlash(maid, roll + pitch, offset, false, i == 2, 0.5, KnockBacks.cancel);
            }
        }),
        
        PHANTOM_SLASH("幻影斩", SkillType.COMBO, maid -> {
            // 快速的残影斩击，考虑俯仰角
            float pitch = getPitchCorrection(maid);
            for (int i = 0; i < 4; i++) {
                float roll = RANDOM.nextInt(180) - 90;
                Vec3 offset = new Vec3(
                    (RANDOM.nextFloat() - 0.5f) * 0.5,
                    0,
                    (RANDOM.nextFloat() - 0.5f) * 0.5
                );
                AttackManager.doSlash(maid, roll + pitch, offset, true, false, 0.28, KnockBacks.cancel);
            }
        }),
        
        // ==================== 特殊技能系列 ====================
        CIRCLE_SLASH("回旋斩", SkillType.SPECIAL, maid -> {
            // 4个方向的斩击波，形成圆形
            for (int i = 0; i < 4; i++) {
                float yRot = i * 90;
                CircleSlash.doCircleSlashAttack(maid, yRot);
            }
        }),
        
        FULL_CIRCLE_SLASH("全方位回旋斩", SkillType.SPECIAL, maid -> {
            // 8个方向的斩击波，全方位攻击
            for (int i = 0; i < 8; i++) {
                float yRot = i * 45;
                CircleSlash.doCircleSlashAttack(maid, yRot);
            }
        }),
        
        DRIVE_HORIZONTAL("驱动斩·横", SkillType.SPECIAL, maid -> {
            float pitch = getPitchCorrection(maid);
            Drive.doSlash(maid, pitch, 10, Vec3.ZERO, false, 1.5, 2f);
        }),
        
        DRIVE_VERTICAL("驱动斩·纵", SkillType.SPECIAL, maid -> {
            float pitch = getPitchCorrection(maid);
            Drive.doSlash(maid, -90F + pitch, 10, Vec3.ZERO, false, 1.5, 2f);
        }),
        
        DRIVE_CROSS("驱动斩·十字", SkillType.SPECIAL, maid -> {
            // 发射横纵两道驱动斩，考虑俯仰角
            float pitch = getPitchCorrection(maid);
            Drive.doSlash(maid, pitch, 8, Vec3.ZERO, false, 1.2, 2.2f);
            Drive.doSlash(maid, -90F + pitch, 8, Vec3.ZERO, false, 1.2, 2.2f);
        }),
        
        WAVE_EDGE("波刃", SkillType.SPECIAL, maid -> {
            float pitch = getPitchCorrection(maid);
            WaveEdge.doSlash(maid, pitch, 12, Vec3.ZERO, false, 0.8, 1.5f, 2.5f, 3);
        }),
        
        WAVE_EDGE_BURST("波刃爆发", SkillType.SPECIAL, maid -> {
            float pitch = getPitchCorrection(maid);
            WaveEdge.doSlash(maid, pitch, 10, Vec3.ZERO, true, 0.6, 1.8f, 3.0f, 5);
        }),
        
        SAKURA_END("樱花终结", SkillType.SPECIAL, maid -> {
            float pitch = getPitchCorrection(maid);
            SakuraEnd.doSlash(maid, pitch, Vec3.ZERO, false, true, 1.8, KnockBacks.toss);
        }),
        
        JUDGEMENT_CUT("次元斩", SkillType.SPECIAL, JudgementCut::doJudgementCut),
        
        JUDGEMENT_CUT_JUST("次元斩·极", SkillType.SPECIAL, JudgementCut::doJudgementCutJust),
        
        VOID_SLASH("虚空斩", SkillType.SPECIAL, AttackManager::doVoidSlashAttack),
        
        PIERCING_DRIVE("贯穿驱动", SkillType.SPECIAL, maid -> {
            // 发射贯穿性的驱动斩，考虑俯仰角
            float pitch = getPitchCorrection(maid);
            Drive.doSlash(maid, pitch, 15, Vec3.ZERO, true, 2.0, KnockBacks.smash, 2.5f);
        }),
        
        DOUBLE_DRIVE("双重驱动", SkillType.SPECIAL, maid -> {
            // 左右两道驱动斩，考虑俯仰角
            float pitch = getPitchCorrection(maid);
            Vec3 leftOffset = new Vec3(0, 0, -0.5);
            Vec3 rightOffset = new Vec3(0, 0, 0.5);
            Drive.doSlash(maid, pitch, 10, leftOffset, false, 1.3, 2.0f);
            Drive.doSlash(maid, pitch, 10, rightOffset, false, 1.3, 2.0f);
        }),
        
        // ==================== 空中技能系列 ====================
        AIR_SLASH("空中斩", SkillType.AERIAL, maid -> {
            float pitch = getPitchCorrection(maid);
            AttackManager.doSlash(maid, -20 + pitch, Vec3.ZERO, false, false, 0.28, KnockBacks.cancel);
        }),
        
        AIR_SPIN("空中回旋", SkillType.AERIAL, maid -> {
            // 空中旋转攻击，考虑俯仰角
            float pitch = getPitchCorrection(maid);
            for (int i = 0; i < 2; i++) {
                float roll = 180 + 57 + (i * 180);
                AttackManager.doSlash(maid, roll + pitch, Vec3.ZERO, false, false, 0.34, KnockBacks.toss);
            }
        }),
        
        AIR_CROSS("空中十字", SkillType.AERIAL, maid -> {
            // 空中十字斩，考虑俯仰角
            float pitch = getPitchCorrection(maid);
            AttackManager.doSlash(maid, pitch, Vec3.ZERO, false, false, 0.35, KnockBacks.cancel);
            AttackManager.doSlash(maid, -90 + pitch, Vec3.ZERO, false, false, 0.35, KnockBacks.smash);
        }),
        
        AIR_DRIVE("空中驱动", SkillType.AERIAL, maid -> {
            float pitch = getPitchCorrection(maid);
            Drive.doSlash(maid, -30F + pitch, 8, Vec3.ZERO, false, 1.2, 2.0f);
        }),
        
        AIR_SPIRAL("空中螺旋", SkillType.AERIAL, maid -> {
            // 空中螺旋斩击，考虑俯仰角
            float pitch = getPitchCorrection(maid);
            for (int i = 0; i < 3; i++) {
                float roll = i * 120;
                Vec3 offset = new Vec3(
                    Math.cos(i * Math.PI * 2 / 3) * 0.3,
                    -0.2 * i,
                    Math.sin(i * Math.PI * 2 / 3) * 0.3
                );
                AttackManager.doSlash(maid, roll + pitch, offset, false, i == 2, 0.4, KnockBacks.cancel);
            }
        }),
        
        AIR_WAVE("空中波刃", SkillType.AERIAL, maid -> {
            float pitch = getPitchCorrection(maid);
            WaveEdge.doSlash(maid, -20 + pitch, 10, Vec3.ZERO, false, 0.7, 1.5f, 2.2f, 2);
        }),
        
        // ==================== 范围攻击系列 ====================
        AREA_SWEEP("范围横扫", SkillType.SPECIAL, maid -> {
            // 大范围横扫攻击，考虑俯仰角
            float pitch = getPitchCorrection(maid);
            for (int i = 0; i < 3; i++) {
                float roll = -30 + i * 30;
                Vec3 offset = new Vec3(0, 0, (i - 1) * 0.4);
                AttackManager.doSlash(maid, roll + pitch, offset, false, i == 2, 0.8, KnockBacks.cancel);
            }
        }),
        
        OMNISLASH("全方位斩", SkillType.SPECIAL, maid -> {
            // 全方位攻击，考虑俯仰角
            float pitch = getPitchCorrection(maid);
            for (int i = 0; i < 6; i++) {
                float roll = i * 60;
                AttackManager.doSlash(maid, roll + pitch, Vec3.ZERO, true, i == 5, 0.3, KnockBacks.cancel);
            }
        }),
        
        // ==================== 冲刺/突进技能系列 ====================
        DASH_SLASH("冲刺斩", SkillType.COMBO, maid -> {
            // 向前冲刺并斩击
            Vec3 lookAngle = maid.getLookAngle();
            Vec3 dashMotion = new Vec3(lookAngle.x * DASH_DISTANCE_MULTIPLIER, 0.1, lookAngle.z * DASH_DISTANCE_MULTIPLIER);
            maid.setDeltaMovement(maid.getDeltaMovement().add(dashMotion));
            float pitch = getPitchCorrection(maid);
            AttackManager.doSlash(maid, pitch, Vec3.ZERO, false, true, 1.2, KnockBacks.toss);
        }),
        
        RUSH_ASSAULT("突袭斩", SkillType.COMBO, maid -> {
            // 快速突进连击
            Vec3 lookAngle = maid.getLookAngle();
            Vec3 rushMotion = new Vec3(lookAngle.x * DASH_DISTANCE_MULTIPLIER, 0, lookAngle.z * DASH_DISTANCE_MULTIPLIER);
            maid.setDeltaMovement(maid.getDeltaMovement().add(rushMotion));
            
            float pitch = getPitchCorrection(maid);
            for (int i = 0; i < 3; i++) {
                Vec3 offset = AttackManager.genRushOffset(maid).scale(0.4);
                float roll = -60 + i * 60;
                AttackManager.doSlash(maid, roll + pitch, offset, false, i == 2, 0.45, KnockBacks.cancel);
            }
        }),
        
        BLITZ_STRIKE("闪电突击", SkillType.SPECIAL, maid -> {
            // 超快速冲刺攻击
            Vec3 lookAngle = maid.getLookAngle();
            Vec3 blitzMotion = new Vec3(lookAngle.x * DASH_DISTANCE_MULTIPLIER, 0.15, lookAngle.z * DASH_DISTANCE_MULTIPLIER);
            maid.setDeltaMovement(maid.getDeltaMovement().add(blitzMotion));
            
            float pitch = getPitchCorrection(maid);
            // 冲刺时发射一道驱动斩
            Drive.doSlash(maid, pitch, 12, Vec3.ZERO, true, 1.8, 3.0f);
            AttackManager.doSlash(maid, pitch, Vec3.ZERO, false, true, 1.5, KnockBacks.smash);
        }),
        
        CHARGE_SLASH("冲锋斩", SkillType.COMBO, maid -> {
            // 蓄力冲锋斩击
            Vec3 lookAngle = maid.getLookAngle();
            Vec3 chargeMotion = new Vec3(lookAngle.x * DASH_DISTANCE_MULTIPLIER, 0, lookAngle.z * DASH_DISTANCE_MULTIPLIER);
            maid.setDeltaMovement(maid.getDeltaMovement().add(chargeMotion));
            
            float pitch = getPitchCorrection(maid);
            // 冲锋后的强力斩击
            AttackManager.doSlash(maid, -45 + pitch, Vec3.ZERO, false, true, 1.4, KnockBacks.smash);
        }),
        
        SPRINT_COMBO("疾跑连斩", SkillType.COMBO, maid -> {
            // 疾跑状态下的快速连击
            Vec3 lookAngle = maid.getLookAngle();
            Vec3 sprintMotion = new Vec3(lookAngle.x * DASH_DISTANCE_MULTIPLIER, 0, lookAngle.z * DASH_DISTANCE_MULTIPLIER);
            maid.setDeltaMovement(maid.getDeltaMovement().add(sprintMotion));
            
            float pitch = getPitchCorrection(maid);
            for (int i = 0; i < 4; i++) {
                float roll = (i % 2 == 0) ? -30 : 30;
                AttackManager.doSlash(maid, roll + pitch, Vec3.ZERO, true, i == 3, 0.35, KnockBacks.cancel);
            }
        }),
        
        PIERCING_DASH("贯穿冲刺", SkillType.SPECIAL, maid -> {
            // 贯穿性冲刺攻击
            Vec3 lookAngle = maid.getLookAngle();
            Vec3 pierceMotion = new Vec3(lookAngle.x * DASH_DISTANCE_MULTIPLIER, 0.1, lookAngle.z * DASH_DISTANCE_MULTIPLIER);
            maid.setDeltaMovement(maid.getDeltaMovement().add(pierceMotion));
            
            float pitch = getPitchCorrection(maid);
            // 发射多道驱动斩形成贯穿效果
            for (int i = 0; i < 3; i++) {
                Drive.doSlash(maid, pitch, 8, Vec3.ZERO, true, 0.8, 2.8f);
            }
        }),
        
        BACKSTEP_SLASH("后撤斩", SkillType.COMBO, maid -> {
            // 向后撤步同时斩击
            Vec3 lookAngle = maid.getLookAngle();
            Vec3 backMotion = new Vec3(-lookAngle.x * DASH_DISTANCE_MULTIPLIER, 0.2, -lookAngle.z * DASH_DISTANCE_MULTIPLIER);
            maid.setDeltaMovement(maid.getDeltaMovement().add(backMotion));
            
            float pitch = getPitchCorrection(maid);
            // 后撤时的防御性斩击
            AttackManager.doSlash(maid, pitch, Vec3.ZERO, false, false, 0.9, KnockBacks.cancel);
            AttackManager.doSlash(maid, 180 + pitch, Vec3.ZERO, false, true, 0.9, KnockBacks.cancel);
        }),
        
        SLIDE_SLASH("滑步斩", SkillType.COMBO, maid -> {
            // 侧向滑步斩击
            Vec3 lookAngle = maid.getLookAngle();
            Vec3 rightVec = new Vec3(-lookAngle.z, 0, lookAngle.x).normalize();
            Vec3 slideMotion = rightVec.scale((RANDOM.nextBoolean() ? 1 : -1) * DASH_DISTANCE_MULTIPLIER);
            maid.setDeltaMovement(maid.getDeltaMovement().add(slideMotion));
            
            float pitch = getPitchCorrection(maid);
            AttackManager.doSlash(maid, 90 + pitch, Vec3.ZERO, false, true, 1.1, KnockBacks.cancel);
        }),
        
        DASH_DRIVE("冲刺驱动", SkillType.SPECIAL, maid -> {
            // 冲刺并发射驱动斩
            Vec3 lookAngle = maid.getLookAngle();
            Vec3 dashMotion = new Vec3(lookAngle.x * DASH_DISTANCE_MULTIPLIER, 0.1, lookAngle.z * DASH_DISTANCE_MULTIPLIER);
            maid.setDeltaMovement(maid.getDeltaMovement().add(dashMotion));
            
            float pitch = getPitchCorrection(maid);
            // 同时发射横纵两道驱动斩
            Drive.doSlash(maid, pitch, 10, Vec3.ZERO, true, 1.3, 2.5f);
            Drive.doSlash(maid, -90F + pitch, 10, Vec3.ZERO, true, 1.3, 2.5f);
        }),
        
        WHIRLWIND_DASH("旋风冲刺", SkillType.SPECIAL, maid -> {
            // 旋转冲刺攻击
            Vec3 lookAngle = maid.getLookAngle();
            Vec3 whirlMotion = new Vec3(lookAngle.x * DASH_DISTANCE_MULTIPLIER, 0.15, lookAngle.z * DASH_DISTANCE_MULTIPLIER);
            maid.setDeltaMovement(maid.getDeltaMovement().add(whirlMotion));
            
            float pitch = getPitchCorrection(maid);
            // 旋转斩击
            for (int i = 0; i < 3; i++) {
                float roll = i * 120;
                AttackManager.doSlash(maid, roll + pitch, Vec3.ZERO, true, i == 2, 0.5, KnockBacks.cancel);
            }
        }),
        
        // ==================== 跳跃攻击系列 ====================
        JUMP_SLASH("跳跃斩", SkillType.COMBO, maid -> {
            // 跳跃后下劈
            if (maid.onGround()) {
                Vec3 jumpMotion = new Vec3(0, JUMP_HEIGHT_MULTIPLIER, 0);
                maid.setDeltaMovement(maid.getDeltaMovement().add(jumpMotion));
            }
            float pitch = getPitchCorrection(maid);
            AttackManager.doSlash(maid, 90 + pitch, Vec3.ZERO, false, true, 1.3, KnockBacks.smash);
        }),
        
        LEAP_STRIKE("跃击", SkillType.COMBO, maid -> {
            // 跳跃冲刺斩击
            Vec3 lookAngle = maid.getLookAngle();
            if (maid.onGround()) {
                Vec3 leapMotion = new Vec3(
                    lookAngle.x * DASH_DISTANCE_MULTIPLIER * 0.7, 
                    JUMP_HEIGHT_MULTIPLIER, 
                    lookAngle.z * DASH_DISTANCE_MULTIPLIER * 0.7
                );
                maid.setDeltaMovement(maid.getDeltaMovement().add(leapMotion));
            }
            float pitch = getPitchCorrection(maid);
            AttackManager.doSlash(maid, -45 + pitch, Vec3.ZERO, false, true, 1.4, KnockBacks.toss);
        }),
        
        BOUNCE_ATTACK("弹跳攻击", SkillType.COMBO, maid -> {
            // 小跳连击
            if (maid.onGround()) {
                Vec3 bounceMotion = new Vec3(0, JUMP_HEIGHT_MULTIPLIER * 0.6, 0);
                maid.setDeltaMovement(maid.getDeltaMovement().add(bounceMotion));
            }
            float pitch = getPitchCorrection(maid);
            for (int i = 0; i < 2; i++) {
                float roll = i * 180;
                AttackManager.doSlash(maid, roll + pitch, Vec3.ZERO, false, i == 1, 0.6, KnockBacks.cancel);
            }
        }),
        
        JUMP_DRIVE("跳跃驱动", SkillType.SPECIAL, maid -> {
            // 跳跃后发射驱动斩
            if (maid.onGround()) {
                Vec3 jumpMotion = new Vec3(0, JUMP_HEIGHT_MULTIPLIER * 0.8, 0);
                maid.setDeltaMovement(maid.getDeltaMovement().add(jumpMotion));
            }
            float pitch = getPitchCorrection(maid);
            Drive.doSlash(maid, -20F + pitch, 10, Vec3.ZERO, true, 1.5, 2.5f);
        }),
        
        AERIAL_DASH("空中突进", SkillType.AERIAL, maid -> {
            // 空中前冲斩击
            Vec3 lookAngle = maid.getLookAngle();
            Vec3 airDashMotion = new Vec3(
                lookAngle.x * DASH_DISTANCE_MULTIPLIER * 0.8, 
                0.1, 
                lookAngle.z * DASH_DISTANCE_MULTIPLIER * 0.8
            );
            maid.setDeltaMovement(maid.getDeltaMovement().add(airDashMotion));
            float pitch = getPitchCorrection(maid);
            AttackManager.doSlash(maid, pitch, Vec3.ZERO, false, true, 1.2, KnockBacks.toss);
        }),
        
        DOUBLE_JUMP_SLASH("二段跳斩", SkillType.SPECIAL, maid -> {
            // 二段跳攻击
            if (!maid.onGround()) {
                Vec3 doubleJumpMotion = new Vec3(0, JUMP_HEIGHT_MULTIPLIER * 0.7, 0);
                maid.setDeltaMovement(maid.getDeltaMovement().add(doubleJumpMotion));
            } else {
                Vec3 jumpMotion = new Vec3(0, JUMP_HEIGHT_MULTIPLIER, 0);
                maid.setDeltaMovement(maid.getDeltaMovement().add(jumpMotion));
            }
            float pitch = getPitchCorrection(maid);
            AttackManager.doSlash(maid, -90 + pitch, Vec3.ZERO, false, true, 1.5, KnockBacks.smash);
        });
        
        private final String displayName;
        private final SkillType type;
        private final java.util.function.Consumer<EntityMaid> executor;
        
        DirectSkill(String displayName, SkillType type, java.util.function.Consumer<EntityMaid> executor) {
            this.displayName = displayName;
            this.type = type;
            this.executor = executor;
        }
        
        public String getDisplayName() { return displayName; }
        public SkillType getType() { return type; }
        
        /**
         * 执行技能 - 完全自动化
         */
        public void execute(EntityMaid maid) {
            executor.accept(maid);
        }
    }
    
    /**
     * 技能类型
     */
    public enum SkillType {
        BASIC,      // 基础技能 - 60%权重
        COMBO,      // 连击技能 - 20%权重
        SPECIAL,    // 特殊技能 - 15%权重
        AERIAL      // 空中技能 - 5%权重
    }
    
    /**
     * 地面基础技能池 - 快速简单的单次斩击
     */
    private static final List<DirectSkill> GROUND_BASIC_SKILLS = List.of(
        DirectSkill.BASIC_SLASH,
        DirectSkill.HORIZONTAL_SLASH,
        DirectSkill.VERTICAL_SLASH,
        DirectSkill.DIAGONAL_SLASH_LEFT,
        DirectSkill.DIAGONAL_SLASH_RIGHT,
        DirectSkill.UPWARD_SLASH,
        DirectSkill.DOWNWARD_SLASH
    );
    
    /**
     * 地面连击技能池 - 多次连续攻击
     */
    private static final List<DirectSkill> GROUND_COMBO_SKILLS = List.of(
        DirectSkill.RAPID_SLASH,
        DirectSkill.MULTI_SLASH,
        DirectSkill.CROSS_SLASH,
        DirectSkill.SPINNING_SLASH,
        DirectSkill.RUSHING_COMBO,
        DirectSkill.PHANTOM_SLASH,
        // 冲刺类技能
        DirectSkill.DASH_SLASH,
        DirectSkill.RUSH_ASSAULT,
        DirectSkill.CHARGE_SLASH,
        DirectSkill.SPRINT_COMBO,
        DirectSkill.BACKSTEP_SLASH,
        DirectSkill.SLIDE_SLASH,
        // 跳跃类技能
        DirectSkill.JUMP_SLASH,
        DirectSkill.LEAP_STRIKE,
        DirectSkill.BOUNCE_ATTACK
    );
    
    /**
     * 地面特殊技能池 - 高威力或特殊效果技能
     */
    private static final List<DirectSkill> GROUND_SPECIAL_SKILLS = List.of(
        DirectSkill.CIRCLE_SLASH,
        DirectSkill.FULL_CIRCLE_SLASH,
        DirectSkill.DRIVE_HORIZONTAL,
        DirectSkill.DRIVE_VERTICAL,
        DirectSkill.DRIVE_CROSS,
        DirectSkill.WAVE_EDGE,
        DirectSkill.WAVE_EDGE_BURST,
        DirectSkill.SAKURA_END,
        DirectSkill.JUDGEMENT_CUT,
        DirectSkill.JUDGEMENT_CUT_JUST,
        DirectSkill.VOID_SLASH,
        DirectSkill.PIERCING_DRIVE,
        DirectSkill.DOUBLE_DRIVE,
        DirectSkill.AREA_SWEEP,
        DirectSkill.OMNISLASH,
        // 高级冲刺技能
        DirectSkill.BLITZ_STRIKE,
        DirectSkill.PIERCING_DASH,
        DirectSkill.DASH_DRIVE,
        DirectSkill.WHIRLWIND_DASH,
        // 跳跃特殊技能
        DirectSkill.JUMP_DRIVE,
        DirectSkill.DOUBLE_JUMP_SLASH
    );
    
    /**
     * 空中技能池 - 空中专用技能
     */
    private static final List<DirectSkill> AERIAL_SKILLS = List.of(
        DirectSkill.AIR_SLASH,
        DirectSkill.AIR_SPIN,
        DirectSkill.AIR_CROSS,
        DirectSkill.AIR_DRIVE,
        DirectSkill.AIR_SPIRAL,
        DirectSkill.AIR_WAVE,
        DirectSkill.AERIAL_DASH
    );
    
    /**
     * 所有冲刺技能池 - 用于确保每3次必有一次冲刺
     */
    private static final List<DirectSkill> ALL_DASH_SKILLS = List.of(
        // 连击类冲刺
        DirectSkill.DASH_SLASH,
        DirectSkill.RUSH_ASSAULT,
        DirectSkill.CHARGE_SLASH,
        DirectSkill.SPRINT_COMBO,
        DirectSkill.BACKSTEP_SLASH,
        DirectSkill.SLIDE_SLASH,
        // 特殊类冲刺
        DirectSkill.BLITZ_STRIKE,
        DirectSkill.PIERCING_DASH,
        DirectSkill.DASH_DRIVE,
        DirectSkill.WHIRLWIND_DASH
    );

    /**
     * 构造函数，绑定 MaidSlashBladeData 数据类型和 ResourceLocation 法术类型
     * 注：对于SlashBlade，SA（Slash Art）通过ResourceLocation标识
     */
    public SlashBladeProvider() {
        super(MaidSlashBladeData::getOrCreate, ResourceLocation.class);
    }

    /**
     * 从单个拔刀剑中收集SA
     * @param spellBook 拔刀剑物品堆栈
     * @return 该拔刀剑的SA列表（通常只有一个）
     */
    @Override
    protected List<ResourceLocation> collectSpellFromSingleSpellBook(ItemStack spellBook, EntityMaid maid) {
        List<ResourceLocation> slashArts = new ArrayList<>();
        
        if (spellBook == null || spellBook.isEmpty() || !isSpellBook(spellBook)) {
            return slashArts;
        }
        
        // 从拔刀剑的BladeState中获取SA
        spellBook.getCapability(ItemSlashBlade.BLADESTATE).ifPresent(state -> {
            ResourceLocation slashArtKey = state.getSlashArtsKey();
            if (slashArtKey != null && !slashArtKey.equals(SlashArtsRegistry.NONE.getId())) {
                slashArts.add(slashArtKey);
            }
        });
        
        return slashArts;
    }
    
    @Override
    public boolean isSpellBook(ItemStack itemStack) {
        return itemStack != null && !itemStack.isEmpty() && itemStack.getItem() instanceof ItemSlashBlade;
    }

    @Override
    public void setTarget(EntityMaid maid, LivingEntity target) {
        MaidSlashBladeData data = getData(maid);
        data.setTarget(target);
        
        // 设置拔刀剑的目标实体ID
        ItemStack slashBlade = maid.getMainHandItem();
        if (isSpellBook(slashBlade)) {
            slashBlade.getCapability(ItemSlashBlade.BLADESTATE).ifPresent(state -> {
                if (target != null && target.isAlive()) {
                    state.setTargetEntityId(target.getId());
                }
            });
        }
    }

    @Override
    public void initiateCasting(EntityMaid maid) {
        LOGGER.debug("[MaidSpell] Initiate casting for slashblade");
        ItemStack itemStack = maid.getMainHandItem();

        MaidSlashBladeData data = getData(maid);
        LivingEntity target = data.getTarget();

        // 空中时不蓄力SA，直接执行空中技能
        if (!maid.onGround()) {
            LOGGER.debug("[MaidSpell] Maid is in air, performing aerial attack");
            performSlashBladeAttack(maid, itemStack);
            return;
        }

        if(data.isOnCooldown()||!hasSlashArt(itemStack)){
            if(maid.distanceTo(target)< SimplifiedSpellCaster.MELEE_RANGE){
                performSlashBladeAttack(maid, itemStack);
            }else{
                maid.getNavigation().moveTo(target,0.6);
            }
            return;
        }

        LOGGER.debug("[MaidSpell] Initiate casting for slashblade - start charging");
        // 检查拔刀剑状态
        itemStack.getCapability(ItemSlashBlade.BLADESTATE).map(state -> {
            if (state.isBroken() || state.isSealed()) {
                return false;
            }

            // 开始蓄力
            maid.startUsingItem(InteractionHand.MAIN_HAND);
            
            // 根据SA类型计算目标蓄力时间
            int targetUseTime = state.getFullChargeTicks(maid) + SlashArts.getJustReceptionSpan(maid)/2;
            LOGGER.debug("[MaidSpell] Target use time: {} ticks", targetUseTime);

            // 设置数据状态
            data.setCasting(true);
            data.setSAExecutionStartTime(maid.level().getGameTime());
            data.setTargetUseTime(targetUseTime);

            return true;
        });
    }

    @Override
    public void processContinuousCasting(EntityMaid maid) {
        MaidSlashBladeData data = getData(maid);
        ItemStack slashBlade = maid.getMainHandItem();
        if (!isSpellBook(slashBlade)) {
            return;
        }

        if(data.isOnCooldown()||!data.isCasting()){
            return;
        }

        // 检查是否还在蓄力阶段
        if (maid.isUsingItem()) {
            // 蓄力阶段
            int ticksUsing = maid.getTicksUsingItem();
            if (ticksUsing >= data.getTargetUseTime()) {
                // 触发SA释放
                triggerSlashArt(maid, slashBlade);
            } else {
                // 持续蓄力过程中的处理
                slashBlade.getCapability(ItemSlashBlade.BLADESTATE).ifPresent(state -> {
                    // 确保女仆面向目标
                    LivingEntity target = data.getTarget();
                    if (target != null && target.isAlive()) {
                        BehaviorUtils.lookAtEntity(maid, target);
                        Vec3 targetEyePos = target.getEyePosition();
                        maid.lookAt(net.minecraft.commands.arguments.EntityAnchorArgument.Anchor.EYES, targetEyePos);
                    }
                    
                    // 调用inventoryTick以保持拔刀剑状态更新
                    slashBlade.getItem().inventoryTick(slashBlade, maid.level(), maid, 0, true);
                });
            }
        } else {
            processComboExecution(maid, slashBlade);
        }
    }

    @Override
    public void stopCasting(EntityMaid maid) {
        MaidSlashBladeData data = getData(maid);
        
        if (maid.isUsingItem()) {
            maid.releaseUsingItem();
        }
        
        // 重置拔刀剑状态
        ItemStack slashBlade = maid.getMainHandItem();
        if (isSpellBook(slashBlade)) {
            slashBlade.getCapability(ItemSlashBlade.BLADESTATE).ifPresent(state -> {
                state.setComboSeq(ComboStateRegistry.NONE.getId());
                state.setLastActionTime(maid.level().getGameTime());
            });
        }
        
        data.reset();
        if(data.getCooldown()<-40){
            data.setCooldown(100);
        }


    }

    /**
     * 触发SA释放
     */
    private void triggerSlashArt(EntityMaid maid, ItemStack slashBlade) {
        int ticksUsing = maid.getTicksUsingItem();
        int useDuration = slashBlade.getUseDuration();
        int timeLeft = Math.max(0, useDuration - ticksUsing);

        slashBlade.releaseUsing(maid.level(), maid, timeLeft);
        maid.stopUsingItem();

         // 检查是否成功触发了combo
        slashBlade.getCapability(ItemSlashBlade.BLADESTATE).ifPresent(state -> {
            ResourceLocation currentCombo = state.getComboSeq();

            if (!currentCombo.equals(ComboStateRegistry.NONE.getId())) {
                // SA成功触发，开始combo执行阶段
                MaidSlashBladeData data = getData(maid);
                data.setSAExecutionStartTime(maid.level().getGameTime());
            } else {
                stopCasting(maid);
            }
        });
    }

    /**
     * 检查拔刀剑是否有SA
     * @param itemStack 拔刀剑物品
     * @return 是否有SA
     */
    public boolean hasSlashArt(ItemStack itemStack) {
        if (itemStack.isEmpty() || !(itemStack.getItem() instanceof ItemSlashBlade)) {
            return false;
        }

        return itemStack.getCapability(ItemSlashBlade.BLADESTATE).map(state -> state.getSlashArtsKey() != null && !state.getSlashArtsKey().equals(SlashArtsRegistry.NONE.getId())).orElse(false);
    }

    /**
     * 处理combo执行阶段（SA触发后）
     */
    private void processComboExecution(EntityMaid maid, ItemStack slashBlade) {
        MaidSlashBladeData data = getData(maid);
        
        // 检查是否超过最大执行时间（防止无限循环）
        long currentTime = maid.level().getGameTime();
        long executionTime = currentTime - data.getSAExecutionStartTime();
        if (executionTime > 200) { // 10秒超时（200 ticks = 10 seconds）
            LOGGER.debug("SA execution timeout, forcing stop");
            stopCasting(maid);
            return;
        }
        
        slashBlade.getCapability(ItemSlashBlade.BLADESTATE).ifPresent(state -> {
            ResourceLocation currentCombo = state.getComboSeq();

            if (currentCombo.equals(ComboStateRegistry.NONE.getId())) {
                stopCasting(maid);
                return;
            }
            
            // 更新上一次combo状态
            data.setLastComboState(currentCombo);

            // 获取combo状态并执行tickAction
            ComboState comboState = ComboStateRegistry.REGISTRY.get().getValue(currentCombo);
            if (comboState != null) {
                slashBlade.getItem().inventoryTick(slashBlade, maid.level(), maid, 0, true);
            } else {
                stopCasting(maid);
            }
        });
    }

    /**
     * 执行拔刀剑普攻 - 使用直接技能系统
     */
    private void performSlashBladeAttack(EntityMaid maid, ItemStack slashBlade) {
        LOGGER.debug("[MaidSpell] Performing slash blade attack with direct skill");
        
        MaidSlashBladeData data = getData(maid);
        LivingEntity target = data.getTarget();
        
        // 让女仆朝向目标（包括俯仰角）
        if (target != null && target.isAlive()) {
            adjustMaidLookAngle(maid, target);
        }
        
        // 随机触发跳跃（在地面时有RANDOM_JUMP_CHANCE%的概率跳跃）
        if (maid.onGround() && RANDOM.nextInt(100) < RANDOM_JUMP_CHANCE) {
            if(data.getTarget() != null){
                JUMP_HEIGHT_MULTIPLIER = data.getTarget().getBbHeight()/2;
            }else{
                JUMP_HEIGHT_MULTIPLIER = 1.2;
            }
            Vec3 jumpMotion = new Vec3(0, JUMP_HEIGHT_MULTIPLIER * 0.7, 0);
            maid.setDeltaMovement(maid.getDeltaMovement().add(jumpMotion));
            maid.addEffect(new net.minecraft.world.effect.MobEffectInstance(
                net.minecraft.world.effect.MobEffects.SLOW_FALLING,
                100,
                0,
                false,
                false,
                true
            ));
            LOGGER.debug("[MaidSpell] Random jump triggered!");
        }
        
        // 选择随机技能
        DirectSkill skill = selectRandomSkill(maid);
        LOGGER.debug("[MaidSpell] Selected skill: {} (NonDashCount: {})", skill.getDisplayName(), data.getNonDashSkillCount());
        
        // 更新冲刺计数器
        if (ALL_DASH_SKILLS.contains(skill)) {
            data.resetNonDashSkillCount();
        } else {
            data.incrementNonDashSkillCount();
        }
        
        skill.execute(maid);
        slashBlade.getCapability(ItemSlashBlade.BLADESTATE).ifPresent(state -> state.setLastActionTime(maid.level().getGameTime()));
    }
    
    /**
     * 调整女仆的视角朝向目标（包括俯仰角）
     * @param maid 女仆实体
     * @param target 目标实体
     */
    private void adjustMaidLookAngle(EntityMaid maid, LivingEntity target) {
        // 计算女仆和目标之间的向量
        Vec3 maidPos = maid.getEyePosition();
        Vec3 targetPos = target.getEyePosition();
        Vec3 direction = targetPos.subtract(maidPos);
        
        // 计算水平距离和高度差
        double horizontalDistance = Math.sqrt(direction.x * direction.x + direction.z * direction.z);
        double heightDiff = direction.y;
        
        // 计算俯仰角（pitch）- 单位是度
        float pitch = (float) Math.toDegrees(Math.atan2(heightDiff, horizontalDistance));
        
        // 计算偏航角（yaw）
        float yaw = (float) Math.toDegrees(Math.atan2(-direction.x, direction.z));
        
        // 设置女仆的视角
        maid.setYRot(yaw);
        maid.setXRot(pitch);
        maid.setYHeadRot(yaw);
        maid.yRotO = yaw;
        maid.xRotO = pitch;
        
        LOGGER.debug("[MaidSpell] Adjusted maid look angle: yaw={}, pitch={}, heightDiff={}, horizontalDist={}", 
            yaw, pitch, heightDiff, horizontalDistance);
    }
    
    /**
     * 获取女仆当前的俯仰角修正值
     * 用于技能角度修正
     * @param maid 女仆实体
     * @return 俯仰角（度）
     */
    private static float getPitchCorrection(EntityMaid maid) {
        return maid.getXRot(); // 返回女仆当前的俯仰角
    }

    /**
     * 随机选择一个技能
     * 根据女仆状态（地面/空中）和随机权重选择
     */
    private DirectSkill selectRandomSkill(EntityMaid maid) {
        MaidSlashBladeData data = getData(maid);
        
        // 检查是否需要强制冲刺
        if (data.getNonDashSkillCount() >= DASH_COUNT && maid.onGround()) {
            // 强制选择冲刺技能
            LOGGER.debug("[MaidSpell] Forcing dash skill (count: {})", data.getNonDashSkillCount());
            return ALL_DASH_SKILLS.get(RANDOM.nextInt(ALL_DASH_SKILLS.size()));
        }
        
        List<DirectSkill> availableSkills;
        
        if (maid.onGround()) {
            // 地面时根据权重选择技能类型
            int roll = RANDOM.nextInt(100);
            
            if (roll < 60) {
                // 60% - 基础技能
                availableSkills = GROUND_BASIC_SKILLS;
            } else if (roll < 80) {
                // 20% - 连击技能
                availableSkills = GROUND_COMBO_SKILLS;
            } else {
                // 20% - 特殊技能
                availableSkills = GROUND_SPECIAL_SKILLS;
            }
        } else {
            // 空中时使用空中技能
            availableSkills = AERIAL_SKILLS;
        }
        
        // 从选中的池中随机选择一个技能
        return availableSkills.get(RANDOM.nextInt(availableSkills.size()));
    }



}