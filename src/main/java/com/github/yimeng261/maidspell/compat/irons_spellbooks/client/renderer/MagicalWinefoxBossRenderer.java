package com.github.yimeng261.maidspell.compat.irons_spellbooks.client.renderer;

import com.github.tartaricacid.touhoulittlemaid.client.renderer.entity.EntityMaidRenderer;
import com.mojang.blaze3d.vertex.PoseStack;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.entity.EntityRendererProvider;
import net.minecraft.world.entity.Mob;

/**
 * 万法酒狐直接复用车万女仆的渲染器：模型、贴图、动画控制器、持物层全部走 TLM 那一套，
 * 具体模型由 {@code MagicalWinefoxBossEntity.getModelId()} 指到内置模型包里那份。
 *
 * <p>本类只做一件事 —— 把 boss 整体放大。
 *
 * <p><b>放大不能靠覆写 {@code scale()}。</b>{@code EntityMaidRenderer.render} 判到
 * gecko 模型时转交给 {@code GeckoEntityMaidRenderer} 之后直接 return，
 * 压根走不到 {@code LivingEntityRenderer.render} 里那次 {@code scale()} 回调。
 * gecko 那条路的比例来自 {@code IGeoRenderer.render} 里的
 * {@code getWidthScale} / {@code getHeightScale}，而 {@code GeckoEntityMaidRenderer}
 * 把这两个写死成了 {@code MaidModelInfo.getRenderEntityScale()} —— 那是模型包里的值，
 * 玩家女仆穿这套皮时一样吃到，不能为了 boss 调大（方案文档 D13）。
 *
 * <p>所以放大只能落在外面：包住整个 {@code render}，模型和各层一起缩放。
 * 影子不在这一层画（{@code EntityRenderDispatcher} 按 {@code shadowRadius} 单独画），
 * 所以另设一次。名牌会跟着放大 —— boss 走血条、本来就不显示名牌，不管。
 */
public class MagicalWinefoxBossRenderer extends EntityMaidRenderer {
    /**
     * 模型包给的 {@code render_entity_scale} 是 0.7，按女仆体型（0.6×1.5）定的；
     * boss 的碰撞箱是 0.8×2.4，2.4/1.5 = 1.6。实机再微调。
     */
    private static final float BOSS_SCALE = 1.6F;

    public MagicalWinefoxBossRenderer(EntityRendererProvider.Context context) {
        super(context);
        this.shadowRadius = 0.8F;
    }

    @Override
    public void render(Mob entity, float entityYaw, float partialTick, PoseStack poseStack,
                       MultiBufferSource bufferSource, int packedLight) {
        poseStack.pushPose();
        poseStack.scale(BOSS_SCALE, BOSS_SCALE, BOSS_SCALE);
        super.render(entity, entityYaw, partialTick, poseStack, bufferSource, packedLight);
        poseStack.popPose();
    }
}
