package com.github.yimeng261.maidspell.compat.irons_spellbooks.client.model;

import com.github.yimeng261.maidspell.MaidSpellMod;
import com.github.yimeng261.maidspell.compat.irons_spellbooks.entity.MagicalWinefoxBossEntity;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.util.Mth;
import software.bernie.geckolib.cache.object.GeoBone;
import software.bernie.geckolib.constant.DataTickets;
import software.bernie.geckolib.core.animation.AnimationState;
import software.bernie.geckolib.core.molang.MolangParser;
import software.bernie.geckolib.model.GeoModel;
import software.bernie.geckolib.model.data.EntityModelData;

import java.util.Map;
import java.util.WeakHashMap;

public class MagicalWinefoxBossModel extends GeoModel<MagicalWinefoxBossEntity> {
    private static final ResourceLocation MODEL =
            new ResourceLocation(MaidSpellMod.MOD_ID, "geo/magical_winefox_boss.geo.json");
    private static final ResourceLocation TEXTURE =
            new ResourceLocation(MaidSpellMod.MOD_ID, "textures/entity/magical_winefox_boss.png");
    private static final ResourceLocation ANIMATION =
            new ResourceLocation(MaidSpellMod.MOD_ID, "animations/magical_winefox_boss.animation.json");

    private final Map<MagicalWinefoxBossEntity, PhysicsState> physicsStates = new WeakHashMap<>();
    private final AppliedRotation headRotation = new AppliedRotation();
    private final AppliedRotation centerHairRotation = new AppliedRotation();
    private final AppliedRotation leftHairRotation = new AppliedRotation();
    private final AppliedRotation rightHairRotation = new AppliedRotation();

    @Override
    public ResourceLocation getModelResource(MagicalWinefoxBossEntity animatable) {
        return MODEL;
    }

    @Override
    public ResourceLocation getTextureResource(MagicalWinefoxBossEntity animatable) {
        return TEXTURE;
    }

    @Override
    public ResourceLocation getAnimationResource(MagicalWinefoxBossEntity animatable) {
        return ANIMATION;
    }

    @Override
    public void applyMolangQueries(MagicalWinefoxBossEntity entity, double animTime) {
        this.headRotation.restore(this.getBone("Head").orElse(null));
        this.centerHairRotation.restore(this.getBone("FLongHair").orElse(null));
        this.leftHairRotation.restore(this.getBone("FLongLeftHair").orElse(null));
        this.rightHairRotation.restore(this.getBone("FLongRightHair").orElse(null));
        super.applyMolangQueries(entity, animTime);

        float partialTick = Mth.clamp((float) (animTime - entity.tickCount), 0.0F, 1.0F);
        float headYaw = Mth.clamp(Mth.wrapDegrees(entity.getViewYRot(partialTick)
                - Mth.rotLerp(partialTick, entity.yBodyRotO, entity.yBodyRot)), -55.0F, 55.0F);
        float headPitch = Mth.clamp(entity.getViewXRot(partialTick), -35.0F, 45.0F);
        float horizontalSpeed = (float) entity.getDeltaMovement().horizontalDistance();
        float groundSpeed = horizontalSpeed * 20.0F;
        float verticalSpeed = (float) (entity.getY() - entity.yo) * 20.0F;
        float inputVertical = calculateInputVertical(entity, partialTick, horizontalSpeed);

        PhysicsState physics = this.physicsStates.computeIfAbsent(entity, ignored -> new PhysicsState());
        physics.update(entity, headYaw, headPitch, inputVertical, groundSpeed, verticalSpeed, partialTick);

        MolangParser parser = MolangParser.INSTANCE;
        parser.setMemoizedValue("query.ground_speed", () -> groundSpeed);
        parser.setMemoizedValue("query.vertical_speed", () -> verticalSpeed);
        parser.setMemoizedValue("variable.winefox_input_vertical", () -> inputVertical);
        parser.setMemoizedValue("variable.winefox_head_yaw", () -> headYaw);
        parser.setMemoizedValue("variable.winefox_head_pitch", () -> headPitch);
        parser.setMemoizedValue("variable.winefox_body_yaw", () -> physics.bodyYaw);
        parser.setMemoizedValue("variable.winefox_has_helmet",
                () -> entity.getItemBySlot(net.minecraft.world.entity.EquipmentSlot.HEAD).isEmpty() ? 0.0D : 1.0D);
        parser.setMemoizedValue("variable.winefox_has_mainhand",
                () -> entity.getMainHandItem().isEmpty() ? 0.0D : 1.0D);
        parser.setMemoizedValue("variable.winefox_has_offhand",
                () -> entity.getOffhandItem().isEmpty() ? 0.0D : 1.0D);
    }

    @Override
    public void setCustomAnimations(MagicalWinefoxBossEntity entity, long instanceId,
                                    AnimationState<MagicalWinefoxBossEntity> state) {
        EntityModelData modelData = state.getData(DataTickets.ENTITY_MODEL_DATA);
        if (modelData != null) {
            float headYaw = Mth.clamp(modelData.netHeadYaw(), -55.0F, 55.0F);
            float headPitch = Mth.clamp(modelData.headPitch(), -35.0F, 45.0F);
            // isDefeated() 而不是 isDeadOrDying()：她战败时血量停在 1，后者恒为假，
            // 结果就是人已经躺下了，脑袋还在追着玩家转。
            if (!entity.isDefeated() && !entity.isActionAnimationPlaying()) {
                this.getBone("Head").ifPresent(head ->
                        this.headRotation.apply(head, headPitch * Mth.DEG_TO_RAD, headYaw * Mth.DEG_TO_RAD, 0.0F));
            }
        }

        PhysicsState physics = this.physicsStates.get(entity);
        if (physics != null) {
            this.applyHairPhysics("FLongHair", physics, this.centerHairRotation);
            this.applyHairPhysics("FLongLeftHair", physics, this.leftHairRotation);
            this.applyHairPhysics("FLongRightHair", physics, this.rightHairRotation);
        }
    }

    private void applyHairPhysics(String name, PhysicsState physics, AppliedRotation appliedRotation) {
        this.getBone(name).ifPresent(bone -> {
            appliedRotation.apply(bone, physics.hairPitch * Mth.DEG_TO_RAD,
                    0.0F, physics.hairYaw * Mth.DEG_TO_RAD);
        });
    }

    private static float calculateInputVertical(MagicalWinefoxBossEntity entity, float partialTick,
                                                float horizontalSpeed) {
        if (horizontalSpeed < 1.0E-4F) {
            return 0.0F;
        }
        float viewYaw = entity.getViewYRot(partialTick) * Mth.DEG_TO_RAD;
        return (float) ((-Mth.sin(viewYaw) * entity.getDeltaMovement().x
                + Mth.cos(viewYaw) * entity.getDeltaMovement().z) / horizontalSpeed);
    }

    private static final class PhysicsState {
        private final SecondOrderValue pitchFilter = new SecondOrderValue(2.0F);
        private final SecondOrderValue yawFilter = new SecondOrderValue(2.0F);
        private final SecondOrderValue bodyYawFilter = new SecondOrderValue(0.0F);
        private float hairPitch;
        private float hairYaw;
        private float bodyYaw;
        private float lastTick = Float.NaN;

        private void update(MagicalWinefoxBossEntity entity, float headYaw, float headPitch, float inputVertical,
                            float groundSpeed, float verticalSpeed, float partialTick) {
            float tick = entity.tickCount + partialTick;
            float pitchInput = inputVertical * groundSpeed * 10.0F
                    + headPitch - Math.min(verticalSpeed, 0.0F);

            if (Float.isNaN(this.lastTick)) {
                this.lastTick = tick;
                this.pitchFilter.reset(pitchInput);
                this.yawFilter.reset(headYaw);
                this.bodyYawFilter.reset(-headYaw);
                this.hairPitch = Mth.clamp(pitchInput, -20.0F, 45.0F + headPitch * 0.5F);
                this.bodyYaw = -headYaw;
                return;
            }
            float delta = Mth.clamp(tick - this.lastTick, 0.0F, 1.0F);
            this.lastTick = tick;
            if (delta <= 0.0F) {
                return;
            }
            float timeStep = delta / 20.0F;
            float filteredPitch = this.pitchFilter.update(pitchInput, timeStep);
            float filteredYaw = this.yawFilter.update(headYaw, timeStep);
            this.hairPitch = Mth.clamp(filteredPitch, -20.0F, 45.0F + headPitch * 0.5F);
            this.hairYaw = -(headYaw - filteredYaw) * 0.5F;
            this.bodyYaw = this.bodyYawFilter.update(-headYaw, timeStep);
        }
    }

    private static final class SecondOrderValue {
        private static final float FREQUENCY = 1.0F;
        private static final float COEFFICIENT = 0.8F;

        private final float response;
        private float input;
        private float value;
        private float velocity;

        private SecondOrderValue(float response) {
            this.response = response;
        }

        private void reset(float input) {
            this.input = input;
            this.value = input;
            this.velocity = 0.0F;
        }

        private float update(float input, float timeStep) {
            float k1 = COEFFICIENT / Mth.PI / FREQUENCY;
            float k2 = 1.0F / Mth.square(2.0F * Mth.PI * FREQUENCY);
            float k3 = this.response * COEFFICIENT / (2.0F * Mth.PI * FREQUENCY);
            float inputVelocity = (input - this.input) / timeStep;
            this.input = input;

            float maxTimeStep = (float) Math.sqrt(4.0F * k2 + k1 * k1) - k1;
            int steps = Math.max(1, Mth.ceil(timeStep / maxTimeStep));
            float step = timeStep / steps;
            for (int i = 0; i < steps; i++) {
                this.value += step * this.velocity;
                this.velocity += step * (k3 * inputVelocity + input - this.value - k1 * this.velocity) / k2;
            }
            return this.value;
        }
    }

    private static final class AppliedRotation {
        private GeoBone bone;
        private float x;
        private float y;
        private float z;

        private void apply(GeoBone bone, float x, float y, float z) {
            bone.setRotX(bone.getRotX() + x);
            bone.setRotY(bone.getRotY() + y);
            bone.setRotZ(bone.getRotZ() + z);
            this.bone = bone;
            this.x = x;
            this.y = y;
            this.z = z;
        }

        private void restore(GeoBone currentBone) {
            if (currentBone != null && currentBone == this.bone) {
                currentBone.setRotX(currentBone.getRotX() - this.x);
                currentBone.setRotY(currentBone.getRotY() - this.y);
                currentBone.setRotZ(currentBone.getRotZ() - this.z);
                currentBone.resetStateChanges();
            }
            this.bone = null;
            this.x = 0.0F;
            this.y = 0.0F;
            this.z = 0.0F;
        }
    }
}
