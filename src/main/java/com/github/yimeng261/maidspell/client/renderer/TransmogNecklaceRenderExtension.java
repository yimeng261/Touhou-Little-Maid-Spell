package com.github.yimeng261.maidspell.client.renderer;

import com.github.tartaricacid.touhoulittlemaid.api.ILittleMaid;
import com.github.tartaricacid.touhoulittlemaid.api.LittleMaidExtension;
import com.github.tartaricacid.touhoulittlemaid.client.renderer.entity.EntityMaidRenderer;
import com.github.tartaricacid.touhoulittlemaid.client.renderer.entity.GeckoEntityMaidRenderer;
import com.github.yimeng261.maidspell.client.renderer.layer.GeckoLayerTransmogHalo;
import net.minecraft.client.renderer.entity.EntityRendererProvider;
import net.minecraft.world.entity.Mob;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.api.distmarker.OnlyIn;

/**
 * 幻化项链渲染扩展
 */
@LittleMaidExtension
public class TransmogNecklaceRenderExtension implements ILittleMaid {
    @Override
    @OnlyIn(Dist.CLIENT)
    @SuppressWarnings({"rawtypes", "unchecked"})
    public void addAdditionGeckoMaidLayer(GeckoEntityMaidRenderer<? extends Mob> renderer, EntityRendererProvider.Context context) {
        renderer.addLayer(new GeckoLayerTransmogHalo(renderer));
    }

    @Override
    @OnlyIn(Dist.CLIENT)
    public void addAdditionMaidLayer(EntityMaidRenderer renderer, EntityRendererProvider.Context context) {
    }
}
