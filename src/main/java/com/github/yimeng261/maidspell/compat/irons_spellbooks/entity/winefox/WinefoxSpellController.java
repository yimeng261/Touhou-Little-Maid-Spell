package com.github.yimeng261.maidspell.compat.irons_spellbooks.entity.winefox;

import com.github.yimeng261.maidspell.winefox.WinefoxSpellChoice;
import com.github.yimeng261.maidspell.compat.irons_spellbooks.spell.SevenStarGuidanceSpell;
import com.github.yimeng261.maidspell.compat.irons_spellbooks.spell.SpellbreakingEchoSpell;
import io.redspace.ironsspellbooks.api.spells.AbstractSpell;
import java.util.EnumSet;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.entity.LivingEntity;
import org.jetbrains.annotations.Nullable;

/**
 * 单只首领的法术操作层。
 *
 * <p>{@link WinefoxBossSpells} 负责与铁魔法的对接；这个类负责单只首领的施法操作生命周期，
 * 调用方因此可以取消或查询一次施法，而不必直接碰铁魔法的状态机。</p>
 */
public final class WinefoxSpellController {
    private final MagicalWinefoxBossEntity boss;
    private final EnumSet<WinefoxBossSpellAction> firstDialogueCasts = EnumSet.noneOf(WinefoxBossSpellAction.class);
    @Nullable
    private WinefoxBossSpellAction activeAction;
    @Nullable
    private LivingEntity activeTarget;

    WinefoxSpellController(MagicalWinefoxBossEntity boss) {
        this.boss = boss;
    }

    public boolean cast(@Nullable LivingEntity target, WinefoxBossSpellAction action, int spellLevel) {
        boolean started = WinefoxBossSpells.cast(this.boss, target, action, spellLevel);
        if (started) {
            this.activeAction = action;
            this.activeTarget = target;
            this.onCastStarted(action);
        }
        return started;
    }

    public boolean castCustom(@Nullable LivingEntity target, WinefoxSpellChoice choice) {
        boolean started = WinefoxBossSpells.castCustom(this.boss, target, choice);
        if (started) {
            this.activeAction = null;
            this.activeTarget = target;
            this.onCastStarted(dialogueAction(ResourceLocation.parse(choice.id())));
        }
        return started;
    }

    public boolean castInstant(@Nullable LivingEntity target, AbstractSpell spell, int spellLevel) {
        boolean started = WinefoxBossSpells.castInstant(this.boss, target, spell, spellLevel);
        if (started) {
            this.activeAction = null;
            this.activeTarget = target;
            this.onCastStarted(dialogueAction(spell.getSpellResource()));
        }
        return started;
    }

    public void cancel() {
        this.boss.cancelCast();
        this.clear();
    }

    public void tick() {
        if (!this.boss.isCasting()) {
            this.clear();
        }
    }

    @Nullable
    public WinefoxBossSpellAction activeAction() {
        return this.activeAction;
    }

    @Nullable
    public LivingEntity activeTarget() {
        return this.activeTarget;
    }

    void clear() {
        this.activeAction = null;
        this.activeTarget = null;
    }

    private void onCastStarted(@Nullable WinefoxBossSpellAction action) {
        if (!this.boss.isBattleActive() || action == null) return;
        Component line = WinefoxDialogue.firstSpellCast(action);
        // 概率判定没中，也算用掉第一次机会。
        if (line != null && this.firstDialogueCasts.add(action) && this.boss.getRandom().nextInt(100) < 70) {
            this.boss.speakSpellDialogue(line);
        }
    }

    @Nullable
    private static WinefoxBossSpellAction dialogueAction(ResourceLocation spellId) {
        if (SevenStarGuidanceSpell.SPELL_ID.equals(spellId)) return WinefoxBossSpellAction.SEVEN_STAR_GUIDANCE;
        if (SpellbreakingEchoSpell.SPELL_ID.equals(spellId)) return WinefoxBossSpellAction.SPELLBREAKING_ECHO;
        return null;
    }

    void resetEncounter() {
        this.firstDialogueCasts.clear();
    }

    void saveEncounter(CompoundTag tag) {
        CompoundTag casts = new CompoundTag();
        for (WinefoxBossSpellAction action : this.firstDialogueCasts) {
            casts.putBoolean(action.name(), true);
        }
        tag.put("WinefoxSpellDialogue", casts);
    }

    void loadEncounter(CompoundTag tag) {
        this.resetEncounter();
        CompoundTag casts = tag.getCompound("WinefoxSpellDialogue");
        for (WinefoxBossSpellAction action : WinefoxBossSpellAction.values()) {
            if (casts.getBoolean(action.name())) this.firstDialogueCasts.add(action);
        }
    }
}
