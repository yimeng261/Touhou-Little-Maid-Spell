package com.github.yimeng261.maidspell.compat.irons_spellbooks.client;

import com.github.yimeng261.maidspell.MaidSpellMod;
import com.github.yimeng261.maidspell.compat.irons_spellbooks.client.animation.WinefoxMaidAnimationStates;
import com.github.yimeng261.maidspell.compat.irons_spellbooks.client.model.GenericSpellHumanoidModel;
import com.github.yimeng261.maidspell.compat.irons_spellbooks.client.renderer.entity.WinefoxSwordProjectileRenderer;
import com.github.yimeng261.maidspell.compat.irons_spellbooks.client.renderer.entity.HolyConstructRenderer;
import com.github.yimeng261.maidspell.compat.irons_spellbooks.client.renderer.entity.GenericSpellHumanoidRenderer;
import com.github.yimeng261.maidspell.compat.irons_spellbooks.client.renderer.MagicalWinefoxBossRenderer;
import com.github.yimeng261.maidspell.compat.irons_spellbooks.registry.IronsSpellbooksCompatEntities;
import io.redspace.ironsspellbooks.entity.spells.comet.CometRenderer;
import net.minecraft.client.renderer.entity.NoopRenderer;
import net.minecraft.resources.ResourceLocation;
import net.minecraftforge.client.event.EntityRenderersEvent;

public final class IronsSpellbooksCompatClient {
    private IronsSpellbooksCompatClient() {
    }

    /**
     * 万法酒狐要的三条 main 通道动画。TLM 那张动画状态表是全局静态的，{@code reloadPacks()}
     * 不清它 —— 只能注册一次，绝不能挂进任何资源重载回调。
     */
    public static void onClientSetup() {
        WinefoxMaidAnimationStates.register();
    }

    public static void onRegisterEntityRenderers(EntityRenderersEvent.RegisterRenderers event) {
        event.registerEntityRenderer(IronsSpellbooksCompatEntities.CORRUPTED_KNIGHT.get(), context ->
                new GenericSpellHumanoidRenderer(context, new GenericSpellHumanoidModel(
                        new ResourceLocation(MaidSpellMod.MOD_ID, "geo/corrupted_knight.geo.json"),
                        new ResourceLocation(MaidSpellMod.MOD_ID, "textures/entity/corrupted_knight.png"))));
        event.registerEntityRenderer(IronsSpellbooksCompatEntities.SHADOW_ASSASSIN.get(), context ->
                new GenericSpellHumanoidRenderer(context, new GenericSpellHumanoidModel(
                        new ResourceLocation(MaidSpellMod.MOD_ID, "geo/shadow_assassin.geo.json"),
                        new ResourceLocation(MaidSpellMod.MOD_ID, "textures/entity/shadow_assassin.png"))));
        event.registerEntityRenderer(IronsSpellbooksCompatEntities.ELF_TEMPLAR.get(), context ->
                new GenericSpellHumanoidRenderer(context, new GenericSpellHumanoidModel(
                        new ResourceLocation(MaidSpellMod.MOD_ID, "geo/elf_templar.geo.json"),
                        new ResourceLocation(MaidSpellMod.MOD_ID, "textures/entity/elf_templar.png"))));
        event.registerEntityRenderer(IronsSpellbooksCompatEntities.GUARDIAN_WITCH.get(), context ->
                new GenericSpellHumanoidRenderer(context, new GenericSpellHumanoidModel(
                        new ResourceLocation(MaidSpellMod.MOD_ID, "geo/guardian_witch.geo.json"),
                        new ResourceLocation(MaidSpellMod.MOD_ID, "textures/entity/guardian_witch.png"))));
        event.registerEntityRenderer(IronsSpellbooksCompatEntities.HOLY_CONSTRUCT.get(), HolyConstructRenderer::new);
        event.registerEntityRenderer(IronsSpellbooksCompatEntities.MODIFIED_STARFALL_CLOUD.get(), NoopRenderer::new);
        event.registerEntityRenderer(IronsSpellbooksCompatEntities.MODIFIED_STARFALL_COMET.get(), context ->
                new CometRenderer(context, 0.75F));
        event.registerEntityRenderer(IronsSpellbooksCompatEntities.WINEFOX_SWORD_PROJECTILE.get(),
                WinefoxSwordProjectileRenderer::new);
        event.registerEntityRenderer(IronsSpellbooksCompatEntities.MAGICAL_WINEFOX_BOSS.get(),
                MagicalWinefoxBossRenderer::new);
    }
}
