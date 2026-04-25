package com.github.yimeng261.maidspell.spell.providers.slashblade;

import com.github.tartaricacid.touhoulittlemaid.entity.passive.EntityMaid;
import com.github.yimeng261.maidspell.spell.data.MaidSlashBladeData;
import mods.flammpfeil.slashblade.slasharts.CircleSlash;
import mods.flammpfeil.slashblade.slasharts.Drive;
import mods.flammpfeil.slashblade.slasharts.JudgementCut;
import mods.flammpfeil.slashblade.slasharts.SakuraEnd;
import mods.flammpfeil.slashblade.slasharts.WaveEdge;
import mods.flammpfeil.slashblade.util.AttackManager;
import mods.flammpfeil.slashblade.util.KnockBacks;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;

import java.util.List;
import java.util.Random;

public final class SlashBladeDirectSkills {
    private static final Random RANDOM = new Random();
    private static final double DASH_DISTANCE_FALLBACK = 1.6D;
    private static double jumpHeightMultiplier = 1.1D;

    private SlashBladeDirectSkills() {
    }

    private static void doDashPhaseSlash(EntityMaid maid, float rollOffset, double damage, KnockBacks knockBack, boolean critical) {
        AttackManager.doSlash(maid, getPitchCorrection(maid) + rollOffset, Vec3.ZERO, false, critical, damage, knockBack);
    }

    private static double getDashDistance(EntityMaid maid, double scale) {
        LivingEntity target = MaidSlashBladeData.getOrCreate(maid.getUUID()).getTarget();
        if (target == null || !target.isAlive() || target.isRemoved()) {
            return DASH_DISTANCE_FALLBACK * scale;
        }

        Vec3 look = maid.getLookAngle();
        if (look.lengthSqr() < 1.0E-6D) {
            return DASH_DISTANCE_FALLBACK * scale;
        }

        Vec3 direction = look.normalize();
        AABB box = target.getBoundingBox();
        double thickness = Math.abs(direction.x) * box.getXsize()
                + Math.abs(direction.y) * box.getYsize()
                + Math.abs(direction.z) * box.getZsize();
        double dashDistance = Math.min(thickness * 1.1D, thickness + 1.5D);
        return Math.max(0.2D, dashDistance * scale);
    }

    private static void doSpinDashSlash(EntityMaid maid, boolean critical) {
        float pitch = getPitchCorrection(maid);
        for (int i = 0; i < 4; i++) {
            AttackManager.doSlash(maid, pitch + i * 90.0F, Vec3.ZERO, true, critical && i == 3, 0.32, KnockBacks.cancel);
        }
    }

    private static LivingEntity getCurrentTarget(EntityMaid maid) {
        return MaidSlashBladeData.getOrCreate(maid.getUUID()).getTarget();
    }

    private static void reaimAtCurrentTarget(EntityMaid maid) {
        LivingEntity target = getCurrentTarget(maid);
        if (target != null && target.isAlive() && !target.isRemoved()) {
            adjustMaidLookAngle(maid, target);
        }
    }
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
        

        CIRCLE_SLASH("回旋斩", SkillType.SPECIAL, maid -> {
            // 4个方向的斩击波，形成圆形
            for (int i = 0; i < 4; i++) {
                float yRot = i * 90;
                CircleSlash.doCircleSlashAttack(maid, yRot);
            }
        }),
        



        DRIVE_CROSS("驱动斩·十字", SkillType.SPECIAL, maid -> {
            // 发射横纵两道驱动斩，考虑俯仰角
            float pitch = getPitchCorrection(maid);
            Drive.doSlash(maid, pitch, 8, Vec3.ZERO, false, 1.2, 2.2f);
            Drive.doSlash(maid, -90F + pitch, 8, Vec3.ZERO, false, 1.2, 2.2f);
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
        

        VOID_SLASH("虚空斩", SkillType.SPECIAL, AttackManager::doVoidSlashAttack),
        
        PIERCING_DRIVE("贯穿驱动", SkillType.SPECIAL, maid -> {
            // 发射贯穿性的驱动斩，考虑俯仰角
            float pitch = getPitchCorrection(maid);
            Drive.doSlash(maid, pitch, 15, Vec3.ZERO, true, 2.0, KnockBacks.smash, 2.5f);
        }),
        

        AIR_SLASH("空中斩", SkillType.AERIAL, maid -> {
            float pitch = getPitchCorrection(maid);
            AttackManager.doSlash(maid, -20 + pitch, Vec3.ZERO, false, false, 0.28, KnockBacks.cancel);
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
        

        AREA_SWEEP("范围横扫", SkillType.SPECIAL, maid -> {
            // 大范围横扫攻击，考虑俯仰角
            float pitch = getPitchCorrection(maid);
            for (int i = 0; i < 3; i++) {
                float roll = -30 + i * 30;
                Vec3 offset = new Vec3(0, 0, (i - 1) * 0.4);
                AttackManager.doSlash(maid, roll + pitch, offset, false, i == 2, 0.8, KnockBacks.cancel);
            }
        }),
        

        DASH_SLASH("冲刺斩", SkillType.COMBO, maid -> {
            // 位移前后各攻击一次；位移距离压低，避免冲过目标。
            doDashPhaseSlash(maid, 0.0F, 0.65, KnockBacks.cancel, false);
            Vec3 lookAngle = maid.getLookAngle();
            double dashDistance = getDashDistance(maid, 1.0D);
            Vec3 dashMotion = new Vec3(lookAngle.x * dashDistance, 0.06, lookAngle.z * dashDistance);
            maid.setDeltaMovement(maid.getDeltaMovement().add(dashMotion));
            reaimAtCurrentTarget(maid);
            doDashPhaseSlash(maid, 0.0F, 0.75, KnockBacks.toss, true);
        }),


        PIERCING_DASH("贯穿冲刺", SkillType.SPECIAL, maid -> {
            // 先近身斩，再短距离贯穿，最后补驱动斩。
            float pitch = getPitchCorrection(maid);
            doDashPhaseSlash(maid, 0.0F, 0.55, KnockBacks.cancel, false);
            Vec3 lookAngle = maid.getLookAngle();
            double dashDistance = getDashDistance(maid, 1.0D);
            Vec3 pierceMotion = new Vec3(lookAngle.x * dashDistance, 0.06, lookAngle.z * dashDistance);
            maid.setDeltaMovement(maid.getDeltaMovement().add(pierceMotion));
            reaimAtCurrentTarget(maid);
            Drive.doSlash(maid, pitch, 8, Vec3.ZERO, true, 0.7, 2.2f);
            doDashPhaseSlash(maid, 0.0F, 0.65, KnockBacks.cancel, true);
        }),
        
        SPIN_DASH("回旋冲刺", SkillType.SPECIAL, maid -> {
            // 冲刺前后各打一轮回旋斩，位移只负责贴近/穿身。
            doSpinDashSlash(maid, false);
            Vec3 lookAngle = maid.getLookAngle();
            double dashDistance = getDashDistance(maid, 1.0D);
            Vec3 dashMotion = new Vec3(lookAngle.x * dashDistance, 0.06, lookAngle.z * dashDistance);
            maid.setDeltaMovement(maid.getDeltaMovement().add(dashMotion));
            reaimAtCurrentTarget(maid);
            doSpinDashSlash(maid, true);
        }),
        

        JUMP_SLASH("跳跃斩", SkillType.COMBO, maid -> {
            // 跳跃后下劈
            if (maid.onGround()) {
                Vec3 jumpMotion = new Vec3(0, jumpHeightMultiplier, 0);
                maid.setDeltaMovement(maid.getDeltaMovement().add(jumpMotion));
            }
            float pitch = getPitchCorrection(maid);
            AttackManager.doSlash(maid, 90 + pitch, Vec3.ZERO, false, true, 1.3, KnockBacks.smash);
        }),
        
        LEAP_STRIKE("跃击", SkillType.COMBO, maid -> {
            // 跃击也视为位移 DS：起跳前后各有攻击反馈。
            doDashPhaseSlash(maid, -30.0F, 0.55, KnockBacks.cancel, false);
            Vec3 lookAngle = maid.getLookAngle();
            if (maid.onGround()) {
                double dashDistance = getDashDistance(maid, 0.45D);
                Vec3 leapMotion = new Vec3(
                    lookAngle.x * dashDistance,
                    jumpHeightMultiplier * 0.75,
                    lookAngle.z * dashDistance
                );
                maid.setDeltaMovement(maid.getDeltaMovement().add(leapMotion));
            }
            reaimAtCurrentTarget(maid);
            doDashPhaseSlash(maid, -45.0F, 0.75, KnockBacks.toss, true);
        }),
        

        JUMP_DRIVE("跳跃驱动", SkillType.SPECIAL, maid -> {
            // 跳跃后发射驱动斩
            if (maid.onGround()) {
                Vec3 jumpMotion = new Vec3(0, jumpHeightMultiplier * 0.8, 0);
                maid.setDeltaMovement(maid.getDeltaMovement().add(jumpMotion));
            }
            float pitch = getPitchCorrection(maid);
            Drive.doSlash(maid, -20F + pitch, 10, Vec3.ZERO, true, 1.5, 2.5f);
        }),
        
        AERIAL_DASH("空中突进", SkillType.AERIAL, maid -> {
            // 空中短突进，前后各斩一次以保证贴身和离身都有判定。
            doDashPhaseSlash(maid, 0.0F, 0.5, KnockBacks.cancel, false);
            Vec3 lookAngle = maid.getLookAngle();
            double dashDistance = getDashDistance(maid, 0.55D);
            Vec3 airDashMotion = new Vec3(
                lookAngle.x * dashDistance,
                0.06,
                lookAngle.z * dashDistance
            );
            maid.setDeltaMovement(maid.getDeltaMovement().add(airDashMotion));
            reaimAtCurrentTarget(maid);
            doDashPhaseSlash(maid, 0.0F, 0.7, KnockBacks.toss, true);
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
    public static final List<DirectSkill> GROUND_BASIC_SKILLS = List.of(
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
    public static final List<DirectSkill> GROUND_COMBO_SKILLS = List.of(
        // 保留动作差异明显的连斩，剔除连续斩/残影斩等多段斩重复项。
        DirectSkill.RAPID_SLASH,
        DirectSkill.CROSS_SLASH,
        DirectSkill.SPINNING_SLASH,
        DirectSkill.RUSHING_COMBO,
        // 冲刺类只保留代表动作：前突。
        DirectSkill.DASH_SLASH,
        // 跳跃类保留起跳下劈和前跃突进。
        DirectSkill.JUMP_SLASH,
        DirectSkill.LEAP_STRIKE
    );
    
    /**
     * 地面特殊技能池 - 高威力或特殊效果技能
     */
    public static final List<DirectSkill> GROUND_SPECIAL_SKILLS = List.of(
        // 保留不同视觉/功能类别：回旋、驱动、波刃、次元斩、虚空、范围和机动。
        DirectSkill.CIRCLE_SLASH,
        DirectSkill.DRIVE_CROSS,
        DirectSkill.WAVE_EDGE_BURST,
        DirectSkill.SAKURA_END,
        DirectSkill.JUDGEMENT_CUT,
        DirectSkill.VOID_SLASH,
        DirectSkill.PIERCING_DRIVE,
        DirectSkill.AREA_SWEEP,
        // 高级冲刺保留穿刺和回旋冲刺。
        DirectSkill.PIERCING_DASH,
        DirectSkill.SPIN_DASH,
        // 跳跃特殊保留远程驱动，二段跳斩与普通跳斩重复度较高。
        DirectSkill.JUMP_DRIVE
    );
    
    /**
     * 空中技能池 - 空中专用技能
     */
    public static final List<DirectSkill> AERIAL_SKILLS = List.of(
        DirectSkill.AIR_SLASH,
        DirectSkill.AIR_CROSS,
        DirectSkill.AIR_DRIVE,
        DirectSkill.AIR_SPIRAL,
        DirectSkill.AERIAL_DASH
    );
    
    /**
     * 所有冲刺技能池 - 用于确保每3次必有一次冲刺
     */
    public static final List<DirectSkill> ALL_DASH_SKILLS = List.of(
        // 只放入当前技能池仍会选到的机动动作。
        DirectSkill.DASH_SLASH,
        DirectSkill.PIERCING_DASH,
        DirectSkill.SPIN_DASH
    );

    public static void updateJumpHeightFromTarget(MaidSlashBladeData data) {
        if (data.getTarget() != null) {
            jumpHeightMultiplier = data.getTarget().getBbHeight() / 2.0D;
        } else {
            jumpHeightMultiplier = 1.2D;
        }
    }

    public static void adjustMaidLookAngle(EntityMaid maid, LivingEntity target) {
        Vec3 maidPos = maid.getEyePosition();
        Vec3 targetPos = target.getEyePosition();
        Vec3 direction = targetPos.subtract(maidPos);
        double horizontalDistance = Math.sqrt(direction.x * direction.x + direction.z * direction.z);
        float xRot = (float) Math.toDegrees(Math.atan2(direction.y, horizontalDistance));
        float yRot = (float) Math.toDegrees(Math.atan2(-direction.x, direction.z));
        maid.setYRot(yRot);
        maid.setYHeadRot(yRot);
        maid.setYBodyRot(yRot);
        maid.setXRot(xRot);
        maid.yRotO = yRot;
        maid.xRotO = xRot;
    }

    private static float getPitchCorrection(EntityMaid maid) {
        return maid.getXRot();
    }
}
