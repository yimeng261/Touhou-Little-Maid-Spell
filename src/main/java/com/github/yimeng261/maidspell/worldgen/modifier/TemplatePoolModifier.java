package com.github.yimeng261.maidspell.worldgen.modifier;

import com.github.yimeng261.maidspell.MaidSpellMod;
import com.mojang.datafixers.util.Pair;
import net.minecraft.core.Registry;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.level.levelgen.structure.pools.StructurePoolElement;
import net.minecraft.world.level.levelgen.structure.pools.StructureTemplatePool;
import net.minecraftforge.event.server.ServerAboutToStartEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.ModList;
import net.minecraftforge.fml.common.Mod;

import java.util.ArrayList;
import java.util.List;

/**
 * 向原版模板池添加结构
 *
 * @author Gardel &lt;gardel741@outlook.com&gt;
 * @since 2026-01-25 18:24
 */
@Mod.EventBusSubscriber
public class TemplatePoolModifier {
    private static final ResourceLocation VILLAGE_DESERT_STREETS_POOL = ResourceLocation.fromNamespaceAndPath("minecraft", "village/desert/streets");
    private static final ResourceLocation VILLAGE_PLAINS_STREETS_POOL = ResourceLocation.fromNamespaceAndPath("minecraft", "village/plains/streets");
    private static final ResourceLocation VILLAGE_SAVANNA_STREETS_POOL = ResourceLocation.fromNamespaceAndPath("minecraft", "village/savanna/streets");
    private static final ResourceLocation VILLAGE_SNOWY_STREETS_POOL = ResourceLocation.fromNamespaceAndPath("minecraft", "village/snowy/streets");
    private static final ResourceLocation VILLAGE_TAIGA_STREETS_POOL = ResourceLocation.fromNamespaceAndPath("minecraft", "village/taiga/streets");

    private static final ResourceLocation ENCHANTRESS_FOOTSTEPS_VILLAGE_DESERT_STREETS_POOL = ResourceLocation.fromNamespaceAndPath(MaidSpellMod.MOD_ID, "enchantress_footsteps/village/desert_streets");
    private static final ResourceLocation ENCHANTRESS_FOOTSTEPS_VILLAGE_PLAINS_STREETS_POOL = ResourceLocation.fromNamespaceAndPath(MaidSpellMod.MOD_ID, "enchantress_footsteps/village/plains_streets");
    private static final ResourceLocation ENCHANTRESS_FOOTSTEPS_VILLAGE_SAVANNA_STREETS_POOL = ResourceLocation.fromNamespaceAndPath(MaidSpellMod.MOD_ID, "enchantress_footsteps/village/savanna_streets");
    private static final ResourceLocation ENCHANTRESS_FOOTSTEPS_VILLAGE_SNOWY_STREETS_POOL = ResourceLocation.fromNamespaceAndPath(MaidSpellMod.MOD_ID, "enchantress_footsteps/village/snowy_streets");
    private static final ResourceLocation ENCHANTRESS_FOOTSTEPS_VILLAGE_TAIGA_STREETS_POOL = ResourceLocation.fromNamespaceAndPath(MaidSpellMod.MOD_ID, "enchantress_footsteps/village/taiga_streets");

    private static void mergeBuildingPool(Registry<StructureTemplatePool> templatePoolRegistry, ResourceLocation target, ResourceLocation from) {
        StructureTemplatePool targetTemplatePool = templatePoolRegistry.get(target);
        StructureTemplatePool fromTemplatePool = templatePoolRegistry.get(from);
        if (targetTemplatePool == null || fromTemplatePool == null) {
            return;
        }
        var templates = fromTemplatePool.templates;
        targetTemplatePool.templates.addAll(templates);
        var rawTemplates = fromTemplatePool.rawTemplates;
        List<Pair<StructurePoolElement, Integer>> mergedRawTemplates = new ArrayList<>(targetTemplatePool.rawTemplates);
        mergedRawTemplates.addAll(rawTemplates);
        targetTemplatePool.rawTemplates = mergedRawTemplates;
    }

    @SubscribeEvent
    public static void modifyTemplatePoolBeforeServerStart(ServerAboutToStartEvent event) {
        Registry<StructureTemplatePool> templatePoolRegistry = event.getServer().registryAccess().registry(Registries.TEMPLATE_POOL).orElseThrow();
        if (ModList.get().isLoaded("irons_spellbooks")) {
            // TODO 需要有一个 server 配置项，控制这些结是否要生成
            mergeBuildingPool(templatePoolRegistry, VILLAGE_DESERT_STREETS_POOL, ENCHANTRESS_FOOTSTEPS_VILLAGE_DESERT_STREETS_POOL);
            mergeBuildingPool(templatePoolRegistry, VILLAGE_PLAINS_STREETS_POOL, ENCHANTRESS_FOOTSTEPS_VILLAGE_PLAINS_STREETS_POOL);
            mergeBuildingPool(templatePoolRegistry, VILLAGE_SAVANNA_STREETS_POOL, ENCHANTRESS_FOOTSTEPS_VILLAGE_SAVANNA_STREETS_POOL);
            mergeBuildingPool(templatePoolRegistry, VILLAGE_SNOWY_STREETS_POOL, ENCHANTRESS_FOOTSTEPS_VILLAGE_SNOWY_STREETS_POOL);
            mergeBuildingPool(templatePoolRegistry, VILLAGE_TAIGA_STREETS_POOL, ENCHANTRESS_FOOTSTEPS_VILLAGE_TAIGA_STREETS_POOL);
        }
    }
}
