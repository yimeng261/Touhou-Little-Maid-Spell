package com.github.yimeng261.maidspell.compat.irons_spellbooks.client.renderer.entity;

import com.github.tartaricacid.touhoulittlemaid.geckolib3.geo.GeoLayerRenderer;
import com.github.tartaricacid.touhoulittlemaid.geckolib3.geo.IGeoEntityRenderer;
import com.github.tartaricacid.touhoulittlemaid.geckolib3.geo.animated.AnimatedGeoModel;
import com.github.tartaricacid.touhoulittlemaid.geckolib3.geo.animated.AnimatedGeoBone;
import com.github.tartaricacid.touhoulittlemaid.geckolib3.util.RenderUtils;
import com.github.yimeng261.maidspell.compat.irons_spellbooks.entity.spell.PalmBlackHoleEntity;
import com.github.yimeng261.maidspell.compat.irons_spellbooks.entity.spell.PalmBlackHoleTimeline;
import com.github.yimeng261.maidspell.compat.irons_spellbooks.entity.winefox.MagicalWinefoxBossEntity;
import com.mojang.blaze3d.vertex.PoseStack;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.entity.EntityRendererProvider;
import net.minecraft.world.entity.Mob;
import org.joml.Matrix4f;
import java.util.ArrayList;
import java.util.Collections;

/** 在车万女仆动画算出的定位骨骼处绘制独立的法术实体，就像手持物品那样。 */
public class PalmBlackHoleLocatorLayer<T extends Mob, R extends IGeoEntityRenderer<T>> extends GeoLayerRenderer<T, R> {
    private final EntityRendererProvider.Context context;
    private final PalmBlackHoleRenderer blackHoleRenderer;

    public PalmBlackHoleLocatorLayer(R renderer, EntityRendererProvider.Context context) {
        super(renderer);
        this.context = context;
        this.blackHoleRenderer = new PalmBlackHoleRenderer(context);
    }

    @Override public GeoLayerRenderer<T, R> copy(R renderer) {
        return new PalmBlackHoleLocatorLayer<>(renderer, context);
    }

    @Override public void render(PoseStack poses, MultiBufferSource buffers, int light, T entity,
                                 float limbSwing, float limbAmount, float partialTick, float age,
                                 float netHeadYaw, float pitch) {
        if (!(entity instanceof MagicalWinefoxBossEntity witch) || !witch.isCastingPalmBlackHole()
                || !(getLocationModel(entity) instanceof AnimatedGeoModel model)) return;
        AnimatedGeoBone locator = model.bones().get("Blackhole");
        if (locator == null || locator.getScaleX() <= 0.0001F) return;
        var hierarchy = new ArrayList<AnimatedGeoBone>();
        for (AnimatedGeoBone bone = locator; bone != null; ) {
            hierarchy.add(bone);
            var parent = bone.geoBone().parent();
            bone = parent == null ? null : model.bones().get(parent.name());
        }
        Collections.reverse(hierarchy);
        for (PalmBlackHoleEntity ritual : entity.level().getEntitiesOfClass(PalmBlackHoleEntity.class,
                entity.getBoundingBox().inflate(48), candidate -> candidate.belongsTo(entity))) {
            if (ritual.visualAge(partialTick) >= PalmBlackHoleTimeline.TOP_TICK) continue;
            poses.pushPose();
            Matrix4f base = new Matrix4f(poses.last().pose());
            RenderUtils.prepMatrixForLocator(poses, hierarchy);
            Matrix4f local = base.invert().mul(poses.last().pose());
            PalmBlackHoleRenderer.rememberLocator(ritual, local, witch,
                    getGeoEntity(entity).getMaidInfo().getRenderEntityScale(), partialTick);
            blackHoleRenderer.renderAtLocator(ritual, partialTick, poses, buffers, light);
            poses.popPose();
        }
    }
}
