package com.github.yimeng261.maidspell.spell.data;

import com.github.tartaricacid.touhoulittlemaid.entity.passive.EntityMaid;
import com.github.yimeng261.maidspell.api.IMaidSpellData;
import com.mna.api.spells.base.ISpellDefinition;
import net.minecraft.world.item.ItemStack;

import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/**
 * 女仆 Mana and Artifice 法术数据存储类。
 */
public class MaidManaAndArtificeSpellData extends IMaidSpellData {
    private static final Map<UUID, MaidManaAndArtificeSpellData> MAID_DATA_MAP = new ConcurrentHashMap<>();
    private static final int CASTING_DURATION = 10;

    private int castingTicks = 0;
    private int channelDurationTicks = 0;
    private boolean currentSpellApplied = false;
    private ISpellDefinition currentSpell = null;
    private ItemStack currentSpellBook = ItemStack.EMPTY;
    private ItemStack currentSpellStack = ItemStack.EMPTY;

    private MaidManaAndArtificeSpellData() {
    }

    public static MaidManaAndArtificeSpellData getOrCreate(UUID maidUuid) {
        return MAID_DATA_MAP.computeIfAbsent(maidUuid, k -> new MaidManaAndArtificeSpellData());
    }

    public static MaidManaAndArtificeSpellData getOrCreate(EntityMaid maidEntity) {
        return getOrCreate(maidEntity.getUUID());
    }

    public static MaidManaAndArtificeSpellData get(UUID maidUuid) {
        return MAID_DATA_MAP.get(maidUuid);
    }

    public static void remove(UUID maidUuid) {
        MAID_DATA_MAP.remove(maidUuid);
    }

    public static void clearAll() {
        MAID_DATA_MAP.clear();
    }

    public int getCastingTicks() {
        return castingTicks;
    }

    public void setCastingTicks(int castingTicks) {
        this.castingTicks = castingTicks;
    }

    public void incrementCastingTicks() {
        this.castingTicks++;
    }

    public ISpellDefinition getCurrentSpell() {
        return currentSpell;
    }

    public void setCurrentSpell(ISpellDefinition spell, ItemStack spellBook, ItemStack spellStack, String spellId) {
        this.currentSpell = spell;
        this.currentSpellBook = spellBook == null ? ItemStack.EMPTY : spellBook;
        this.currentSpellStack = spellStack == null ? ItemStack.EMPTY : spellStack;
        setCurrentSpellId(spell == null ? null : spellId);
    }

    public ItemStack getCurrentSpellBook() {
        return currentSpellBook;
    }

    public ItemStack getCurrentSpellStack() {
        return currentSpellStack;
    }

    public boolean isCastingComplete() {
        return castingTicks >= CASTING_DURATION;
    }

    public int getCastingDuration() {
        return CASTING_DURATION;
    }

    public int getChannelDurationTicks() {
        return channelDurationTicks;
    }

    public void setChannelDurationTicks(int channelDurationTicks) {
        this.channelDurationTicks = Math.max(0, channelDurationTicks);
    }

    public boolean isCurrentSpellApplied() {
        return currentSpellApplied;
    }

    public void setCurrentSpellApplied(boolean currentSpellApplied) {
        this.currentSpellApplied = currentSpellApplied;
    }

    public boolean isChannelComplete() {
        return currentSpellApplied && castingTicks >= CASTING_DURATION + channelDurationTicks;
    }

    public boolean isManagingChannel() {
        return isCasting() && currentSpell != null && currentSpell.isChanneled()
                && currentSpellApplied && !isChannelComplete();
    }

    @Override
    public void resetCastingState() {
        super.resetCastingState();
        castingTicks = 0;
        channelDurationTicks = 0;
        currentSpellApplied = false;
        currentSpell = null;
        currentSpellBook = ItemStack.EMPTY;
        currentSpellStack = ItemStack.EMPTY;
        setCurrentSpellId(null);
    }
}
