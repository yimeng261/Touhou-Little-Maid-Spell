package com.github.yimeng261.maidspell.client;

import com.github.yimeng261.maidspell.client.gui.SpellWhiteListScreen;
import com.github.yimeng261.maidspell.client.model.SharedHaloModel;
import com.github.yimeng261.maidspell.client.model.UnholyHaloModel;
import com.github.yimeng261.maidspell.client.renderer.entity.WindSeekingBellRenderer;
import com.github.yimeng261.maidspell.compat.irons_spellbooks.IronsSpellbooksCompat;
import com.github.yimeng261.maidspell.compat.irons_spellbooks.item.StarShadowLongswordItem;
import com.github.yimeng261.maidspell.compat.irons_spellbooks.item.StarShadowStaffItem;
import com.github.yimeng261.maidspell.client.renderer.entity.StarShadowSpearRenderer;
import com.github.yimeng261.maidspell.entity.MaidSpellEntities;
import com.github.yimeng261.maidspell.item.MaidSpellItems;
import com.github.yimeng261.maidspell.item.bauble.spellWhiteList.contianer.MaidSpellContainers;
import net.minecraft.client.gui.screens.MenuScreens;
import net.minecraft.client.renderer.item.CompassItemPropertyFunction;
import net.minecraft.client.renderer.item.ItemProperties;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.CompassItem;
import net.minecraft.server.packs.PackType;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.client.event.EntityRenderersEvent;
import net.minecraftforge.client.event.ModelEvent;
import net.minecraftforge.client.event.RegisterKeyMappingsEvent;
import net.minecraftforge.client.event.RegisterGuiOverlaysEvent;
import com.github.yimeng261.maidspell.client.overlay.EnderPocketHudOverlay;
import net.minecraftforge.client.gui.overlay.VanillaGuiOverlay;
import net.minecraftforge.event.AddPackFindersEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.fml.event.lifecycle.FMLClientSetupEvent;
import com.github.yimeng261.maidspell.client.resource.LegacyPackRepositorySource;

@Mod.EventBusSubscriber(modid = "touhou_little_maid_spell", bus = Mod.EventBusSubscriber.Bus.MOD, value = Dist.CLIENT)
public class MaidSpellClientMod {

    @SubscribeEvent
    public static void onClientSetup(FMLClientSetupEvent event) {
        event.enqueueWork(() -> {
            MenuScreens.register(MaidSpellContainers.SPELL_WHITE_LIST_CONTAINER.get(), SpellWhiteListScreen::new);
            registerSpearThrowingProperty();
            registerCompassAngleProperty();
        });
    }

    /**
     * 观星罗盘的指针朝向。
     *
     * <p>{@code angle} 不是通用谓词，原版只给 {@code Items.COMPASS} 和
     * {@code Items.RECOVERY_COMPASS} 各注册了一份，所以继承 {@code CompassItem}
     * 并不会自动带上它，必须按物品再注册一次。
     *
     * <p>目标取法比原版简单：观星罗盘只指结构，不存在"没绑定时指向出生点"这一档，
     * 没绑过就返回 null，{@code CompassItemPropertyFunction} 会退化成随机转圈，
     * 正好表达"还没找到目标"。
     */
    private static void registerCompassAngleProperty() {
        ItemProperties.register(MaidSpellItems.STARWATCH_COMPASS.get(), new ResourceLocation("angle"),
                new CompassItemPropertyFunction((level, stack, entity) ->
                        CompassItem.isLodestoneCompass(stack)
                                ? CompassItem.getLodestonePosition(stack.getOrCreateTag())
                                : null));
    }

    /**
     * 星影投枪蓄力时换用另一份物品模型，和原版三叉戟一样。
     *
     * <p>原版第三人称的投掷姿势和握持姿势差了整整一个方向（{@code trident_throwing.json}），
     * 少了这份 override，蓄力时枪头就是反的。判据名沿用原版的 {@code throwing}，
     * 属性是按物品注册的，不会和三叉戟冲突。
     */
    private static void registerSpearThrowingProperty() {
        ItemProperties.register(MaidSpellItems.STAR_SHADOW_SPEAR.get(), new ResourceLocation("throwing"),
                (stack, level, entity, seed) ->
                        entity != null && entity.isUsingItem() && entity.getUseItem() == stack ? 1.0F : 0.0F);
    }

    @SubscribeEvent
    public static void onRegisterKeyMappings(RegisterKeyMappingsEvent event) {
        event.register(KeyBinds.OPEN_ENDER_POCKET_GUI);
    }

    @SubscribeEvent
    public static void onRegisterGuiOverlays(RegisterGuiOverlaysEvent event) {
        event.registerAbove(VanillaGuiOverlay.HOTBAR.id(), "ender_pocket_status", new EnderPocketHudOverlay());
    }

    /**
     * 星影长剑/星影法杖物品栏用的平面图标模型。
     *
     * <p>两把武器的物品模型是 {@code builtin/entity}，没任何东西引用这两份平面模型，
     * 不在这里登记就不会被烘焙，取出来就是紫黑方块。
     */
    @SubscribeEvent
    public static void onRegisterAdditionalModels(ModelEvent.RegisterAdditional event) {
        event.register(StarShadowLongswordItem.GUI_MODEL);
        event.register(StarShadowStaffItem.GUI_MODEL);
    }

    @SubscribeEvent
    public static void onRegisterEntityRenderers(EntityRenderersEvent.RegisterRenderers event) {
        event.registerEntityRenderer(MaidSpellEntities.WIND_SEEKING_BELL.get(), WindSeekingBellRenderer::new);
        event.registerEntityRenderer(MaidSpellEntities.STAR_SHADOW_SPEAR.get(), StarShadowSpearRenderer::new);
        IronsSpellbooksCompat.initClient(event);
    }

    @SubscribeEvent
    public static void onRegisterLayerDefinitions(EntityRenderersEvent.RegisterLayerDefinitions event) {
        event.registerLayerDefinition(SharedHaloModel.LAYER_LOCATION, SharedHaloModel::createBodyLayer);
        // 注册不洁光环模型
        event.registerLayerDefinition(UnholyHaloModel.LAYER_LOCATION, UnholyHaloModel::createBodyLayer);
    }
} 
