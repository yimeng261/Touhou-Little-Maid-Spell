package com.github.yimeng261.maidspell.compat.irons_spellbooks.client.renderer;

import com.github.tartaricacid.touhoulittlemaid.client.renderer.entity.EntityMaidRenderer;
import com.mojang.blaze3d.vertex.PoseStack;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.entity.EntityRendererProvider;
import net.minecraft.world.entity.Mob;

/**
 * 万法酒狐直接复用车万女仆的渲染器：模型、贴图、动画控制器、持物层全部走 TLM 那一套，
 * 具体模型由 {@code MagicalWinefoxBossEntity.getModelId()} 指到内置模型包里那份。
 */
public class MagicalWinefoxBossRenderer extends EntityMaidRenderer {
    public MagicalWinefoxBossRenderer(EntityRendererProvider.Context context) {
        super(context);
        this.shadowRadius = 0.8F;
    }

    @Override
    public void render(Mob entity, float entityYaw, float partialTick, PoseStack poseStack,
                       MultiBufferSource bufferSource, int packedLight) {
        super.render(entity, entityYaw, partialTick, poseStack, bufferSource, packedLight);
    }
}
