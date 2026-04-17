package com.github.yimeng261.maidspell.client.renderer;

import com.github.tartaricacid.touhoulittlemaid.api.ILittleMaid;
import com.github.tartaricacid.touhoulittlemaid.api.LittleMaidExtension;
import com.github.tartaricacid.touhoulittlemaid.client.renderer.entity.EntityMaidRenderer;
import com.github.tartaricacid.touhoulittlemaid.client.renderer.entity.GeckoEntityMaidRenderer;
import com.github.yimeng261.maidspell.client.renderer.layer.GeckoLayerUnholyHalo;
import net.minecraft.client.renderer.entity.EntityRendererProvider;
import net.minecraft.world.entity.Mob;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.api.distmarker.OnlyIn;

/**
 * 不洁圣冠渲染扩展
 * 用于注册女仆的不洁光环渲染层
 */
@LittleMaidExtension
public class UnholyHatRenderExtension implements ILittleMaid {

    @Override
    @OnlyIn(Dist.CLIENT)
    @SuppressWarnings({"unchecked", "rawtypes"})
    public void addAdditionGeckoMaidLayer(GeckoEntityMaidRenderer<? extends Mob> renderer, EntityRendererProvider.Context context) {
        renderer.addLayer(new GeckoLayerUnholyHalo(renderer));
    }

    @Override
    @OnlyIn(Dist.CLIENT)
    public void addAdditionMaidLayer(EntityMaidRenderer renderer, EntityRendererProvider.Context context) {
        // TODO: 如果需要支持默认模型风格，在这里添加对应的渲染层
    }
}
