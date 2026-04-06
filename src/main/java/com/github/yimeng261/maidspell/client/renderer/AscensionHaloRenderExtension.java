package com.github.yimeng261.maidspell.client.renderer;

import com.github.tartaricacid.touhoulittlemaid.api.ILittleMaid;
import com.github.tartaricacid.touhoulittlemaid.api.LittleMaidExtension;
import com.github.tartaricacid.touhoulittlemaid.client.renderer.entity.EntityMaidRenderer;
import com.github.tartaricacid.touhoulittlemaid.client.renderer.entity.GeckoEntityMaidRenderer;
import com.github.yimeng261.maidspell.client.renderer.layer.GeckoLayerAscensionHalo;
import net.minecraft.client.renderer.entity.EntityRendererProvider;
import net.minecraft.world.entity.Mob;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.api.distmarker.OnlyIn;

/**
 * 晋升之环渲染扩展
 * 用于注册女仆的晋升之环渲染层
 */
@LittleMaidExtension
public class AscensionHaloRenderExtension implements ILittleMaid {

    /**
     * 添加 Gecko 风格的晋升之环渲染层
     */
    @Override
    @OnlyIn(Dist.CLIENT)
    @SuppressWarnings({"unchecked", "rawtypes"})
    public void addAdditionGeckoMaidLayer(GeckoEntityMaidRenderer<? extends Mob> renderer, EntityRendererProvider.Context context) {
        renderer.addLayer(new GeckoLayerAscensionHalo(renderer));
    }

    /**
     * 添加默认模型风格的晋升之环渲染层
     * 注意：目前只实现了 Gecko 风格，如果需要支持默认模型，需要创建对应的 Layer
     */
    @Override
    @OnlyIn(Dist.CLIENT)
    public void addAdditionMaidLayer(EntityMaidRenderer renderer, EntityRendererProvider.Context context) {
        // TODO: 如果需要支持默认模型风格，在这里添加对应的渲染层
        // renderer.addLayer(new LayerAscensionHalo(renderer));
    }
}
