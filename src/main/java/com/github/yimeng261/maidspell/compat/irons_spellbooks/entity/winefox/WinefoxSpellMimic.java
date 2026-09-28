package com.github.yimeng261.maidspell.compat.irons_spellbooks.entity.winefox;

import com.github.tartaricacid.touhoulittlemaid.entity.passive.EntityMaid;
import com.github.yimeng261.maidspell.spell.data.MaidIronsSpellData;
import com.github.yimeng261.maidspell.winefox.WinefoxChallengeConfig;
import io.redspace.ironsspellbooks.api.spells.AbstractSpell;
import io.redspace.ironsspellbooks.api.spells.ISpellContainer;
import io.redspace.ironsspellbooks.api.spells.SpellData;
import io.redspace.ironsspellbooks.api.spells.SpellSlot;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraftforge.fml.ModList;
import top.theillusivec4.curios.api.CuriosApi;

import java.util.ArrayList;
import java.util.List;

/** Collects the challenger's Iron's spell containers and performs one instant copy. */
final class WinefoxSpellMimic {
    private static final double SEARCH_RADIUS = 48.0D;

    private WinefoxSpellMimic() {
    }

    static void afterSpell(MagicalWinefoxBossEntity boss) {
        WinefoxChallengeConfig config = boss.maidspell$challengeConfig();
        if (config == null || config.imitateChancePercent() <= 0
                || boss.getRandom().nextInt(100) >= config.imitateChancePercent()) {
            return;
        }
        Player player = boss.maidspell$challenger();
        if (player == null || !player.isAlive()) {
            return;
        }
        List<SpellData> spells = new ArrayList<>();
        if (config.imitatePlayerSpells()) {
            collectPlayerSpells(player, spells);
        }
        if (config.imitateMaidSpells()) {
            boss.level().getEntitiesOfClass(EntityMaid.class,
                    boss.getBoundingBox().inflate(SEARCH_RADIUS), maid -> maid.isAlive()
                            && player.getUUID().equals(maid.getOwnerUUID()))
                    .forEach(maid -> collectMaidSpells(maid, spells));
        }
        spells.removeIf(data -> data == null || data.getSpell() == null
                || data.getSpell() == io.redspace.ironsspellbooks.api.registry.SpellRegistry.none());
        if (spells.isEmpty()) {
            return;
        }
        SpellData selected = spells.get(boss.getRandom().nextInt(spells.size()));
        AbstractSpell spell = selected.getSpell();
        if (spell != null) {
            WinefoxBossSpells.castInstant(boss, boss.getTarget(), spell, selected.getLevel());
        }
    }

    private static void collectPlayerSpells(Player player, List<SpellData> result) {
        player.getInventory().items.forEach(stack -> collectStack(stack, result));
        player.getInventory().armor.forEach(stack -> collectStack(stack, result));
        player.getInventory().offhand.forEach(stack -> collectStack(stack, result));
        if (ModList.get().isLoaded("curios")) {
            CuriosApi.getCuriosInventory(player).ifPresent(handler ->
                    handler.findCurios(ISpellContainer::isSpellContainer)
                            .forEach(found -> collectStack(found.stack(), result)));
        }
    }

    private static void collectMaidSpells(EntityMaid maid, List<SpellData> result) {
        MaidIronsSpellData data = MaidIronsSpellData.get(maid.getUUID());
        if (data != null) {
            data.getSpellBooks().forEach(stack -> collectStack(stack, result));
        }
        maid.getHandSlots().forEach(stack -> collectStack(stack, result));
        maid.getArmorSlots().forEach(stack -> collectStack(stack, result));
        if (ModList.get().isLoaded("curios")) {
            CuriosApi.getCuriosInventory(maid).ifPresent(handler ->
                    handler.findCurios(ISpellContainer::isSpellContainer)
                            .forEach(found -> collectStack(found.stack(), result)));
        }
    }

    private static void collectStack(ItemStack stack, List<SpellData> result) {
        if (stack.isEmpty() || !ISpellContainer.isSpellContainer(stack)) {
            return;
        }
        ISpellContainer container = ISpellContainer.get(stack);
        if (container == null || container.isEmpty()) {
            return;
        }
        for (SpellSlot slot : container.getActiveSpells()) {
            if (slot != null && slot.spellData() != null && slot.spellData().getSpell() != null) {
                result.add(slot.spellData());
            }
        }
    }
}
