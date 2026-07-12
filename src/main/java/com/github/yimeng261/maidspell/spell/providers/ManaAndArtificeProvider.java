package com.github.yimeng261.maidspell.spell.providers;

import com.github.tartaricacid.touhoulittlemaid.entity.passive.EntityMaid;
import com.github.yimeng261.maidspell.api.ISpellBookProvider;
import com.github.yimeng261.maidspell.item.bauble.springBloomReturn.SpringBloomReturnBauble;
import com.github.yimeng261.maidspell.spell.data.MaidManaAndArtificeSpellData;
import com.mna.api.ManaAndArtificeMod;
import com.mna.api.capabilities.IPlayerMagic;
import com.mna.api.items.inventory.ISpellBookInventory;
import com.mna.api.spells.ICanContainSpell;
import com.mna.api.spells.ISpellHelper;
import com.mna.api.spells.SpellCastingResult;
import com.mna.api.spells.SpellCastingResultCode;
import com.mna.api.spells.attributes.Attribute;
import com.mna.api.spells.base.IModifiedSpellPart;
import com.mna.api.spells.base.ISpellDefinition;
import com.mna.api.spells.parts.Shape;
import com.mna.api.spells.targeting.SpellContext;
import com.mna.api.spells.targeting.SpellSource;
import com.mna.api.spells.targeting.SpellTarget;
import com.mna.items.sorcery.SpellBook;
import com.mna.spells.SpellCaster;
import com.mojang.logging.LogUtils;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.ai.behavior.BehaviorUtils;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.phys.Vec3;
import org.slf4j.Logger;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ThreadLocalRandom;

/**
 * Mana and Artifice 法术提供者。
 * 使用 M&A 的底层 Affect 入口，避免依赖玩家专属 mana/progression capability。
 */
public class ManaAndArtificeProvider extends ISpellBookProvider<MaidManaAndArtificeSpellData, ISpellDefinition> {
    private static final Logger LOGGER = LogUtils.getLogger();

    public ManaAndArtificeProvider() {
        super(MaidManaAndArtificeSpellData::getOrCreate, MaidManaAndArtificeSpellData::get,
                MaidManaAndArtificeSpellData::remove, MaidManaAndArtificeSpellData::clearAll,
                ISpellDefinition.class);
    }

    @Override
    protected List<ISpellDefinition> collectSpellFromSingleSpellBook(ItemStack spellBook, EntityMaid maid) {
        List<ISpellDefinition> spells = new ArrayList<>();
        if (spellBook == null || spellBook.isEmpty() || !isSpellBook(spellBook)) {
            return spells;
        }

        for (SpellSelection selection : collectSelections(spellBook, maid)) {
            if (isUsableSpell(selection.spell())) {
                spells.add(selection.spell());
            }
        }
        return spells;
    }

    @Override
    public boolean isSpellBook(ItemStack itemStack) {
        return itemStack != null && !itemStack.isEmpty() && itemStack.getItem() instanceof ICanContainSpell;
    }

    @Override
    public void initiateCasting(EntityMaid maid) {
        MaidManaAndArtificeSpellData data = getData(maid);
        if (data == null) {
            return;
        }

        LivingEntity target = data.getTarget();
        if (target == null || !target.isAlive()) {
            return;
        }

        SpellSelection selection = selectSpell(maid);
        if (selection == null) {
            LOGGER.debug("[MaidSpell/MNA] No available spell maid={} books={}", maid.getUUID(), data.getSpellBooks().size());
            return;
        }

        BehaviorUtils.lookAtEntity(maid, target);
        data.setCasting(true);
        data.setCastingTicks(0);
        data.setCurrentSpell(selection.spell(), selection.container(), selection.spellStack(), selection.cooldownKey());
        data.setChannelDurationTicks(getChannelDurationTicks(selection.spell()));
        data.setCurrentSpellApplied(false);
        maid.swing(InteractionHand.MAIN_HAND);

        LOGGER.debug("[MaidSpell/MNA] Selected spell maid={} spell={} item={} target={}",
                maid.getUUID(), selection.cooldownKey(), describeItem(selection.container()), describeEntity(target));
    }

    @Override
    public void processContinuousCasting(EntityMaid maid) {
        MaidManaAndArtificeSpellData data = getData(maid);
        if (data == null || !data.isCasting() || data.getCurrentSpell() == null) {
            return;
        }

        LivingEntity target = data.getTarget();
        if (target != null) {
            BehaviorUtils.lookAtEntity(maid, target);
        }

        data.incrementCastingTicks();
        ISpellDefinition spell = data.getCurrentSpell();
        if (spell.isChanneled()) {
            if (!data.isCurrentSpellApplied() && data.isCastingComplete()) {
                boolean success = applyCurrentSpell(maid, data);
                data.setCurrentSpellApplied(true);
                if (!success) {
                    data.resetCastingState();
                    return;
                }
                setCooldown(maid, data, spell);
                SpringBloomReturnBauble.onSpellCast(maid, "mna", data.getCurrentSpellId(), target);
            }
            if (data.isChannelComplete()) {
                LOGGER.debug("[MaidSpell/MNA] Channel complete maid={} spell={} ticks={}",
                        maid.getUUID(), data.getCurrentSpellId(), data.getCastingTicks());
                data.resetCastingState();
            }
        } else if (data.isCastingComplete()) {
            completeCasting(maid);
        }
    }

    @Override
    public void stopCasting(EntityMaid maid) {
        MaidManaAndArtificeSpellData data = getData(maid);
        if (data == null || !data.isCasting()) {
            return;
        }
        data.resetCastingState();
    }

    private SpellSelection selectSpell(EntityMaid maid) {
        MaidManaAndArtificeSpellData data = getData(maid);
        if (data == null) {
            return null;
        }

        List<SpellSelection> available = new ArrayList<>();
        for (ItemStack spellBook : data.getSpellBooks()) {
            for (SpellSelection selection : collectSelections(spellBook, maid)) {
                if (!isUsableSpell(selection.spell())) {
                    continue;
                }
                if (data.isSpellOnCooldown(selection.cooldownKey())) {
                    LOGGER.debug("[MaidSpell/MNA] Spell on cooldown maid={} spell={} remaining={}",
                            maid.getUUID(), selection.cooldownKey(), data.getSpellCooldown(selection.cooldownKey()));
                    continue;
                }
                available.add(selection);
            }
        }

        if (available.isEmpty()) {
            return null;
        }
        return available.get(ThreadLocalRandom.current().nextInt(available.size()));
    }

    private List<SpellSelection> collectSelections(ItemStack container, EntityMaid maid) {
        Map<String, SpellSelection> selections = new LinkedHashMap<>();
        // SpellBook 有多槽位，走专用路径；单法术容器（法杖、手环等）走通用路径
        if (container.getItem() instanceof SpellBook) {
            collectSpellBookActiveSpells(container, maid, selections);
        } else {
            collectSingleSpellContainer(container, selections);
        }
        return new ArrayList<>(selections.values());
    }

    private void collectSingleSpellContainer(ItemStack container, Map<String, SpellSelection> selections) {
        if (!(container.getItem() instanceof ICanContainSpell spellContainer)) {
            return;
        }

        try {
            if (!spellContainer.containsSpell(container)) {
                return;
            }
            ISpellDefinition spell = spellContainer.getSpell(container);
            addSelection(container, container, spell, buildCooldownKey(container, spell), selections);
        } catch (Exception e) {
            LOGGER.debug("[MaidSpell/MNA] Failed to read spell container item={}", describeItem(container), e);
        }
    }

    private void collectSpellBookActiveSpells(ItemStack container, EntityMaid maid, Map<String, SpellSelection> selections) {
        if (!(container.getItem() instanceof SpellBook spellBook)) {
            return;
        }

        try {
            IPlayerMagic ownerMagic = resolveOwnerMagic(maid);
            ISpellBookInventory inventory = spellBook.getInventory(container, ownerMagic);
            if (inventory == null) {
                LOGGER.debug("[MaidSpell/MNA] Spell book inventory unavailable maid={} item={} ownerMagic={}",
                        maid != null ? maid.getUUID() : null, describeItem(container), ownerMagic != null);
                return;
            }
            ItemStack[] activeSpells = inventory.getActiveSpells();
            for (int slot = 0; slot < activeSpells.length; slot++) {
                ItemStack spellStack = activeSpells[slot];
                if (spellStack == null || spellStack.isEmpty()) {
                    continue;
                }
                ISpellDefinition spell = parseSpell(spellStack);
                addSelection(container, spellStack, spell, buildCooldownKey(container, slot, spell), selections);
            }
        } catch (Exception e) {
            LOGGER.debug("[MaidSpell/MNA] Failed to read active spell book slots item={}", describeItem(container), e);
        }
    }

    private IPlayerMagic resolveOwnerMagic(EntityMaid maid) {
        if (maid == null || !(maid.getOwner() instanceof Player owner)) {
            return null;
        }
        return owner.getCapability(ManaAndArtificeMod.getMagicCapability()).orElse(null);
    }

    private ISpellDefinition parseSpell(ItemStack stack) {
        if (stack.getItem() instanceof ICanContainSpell spellContainer && spellContainer.containsSpell(stack)) {
            return spellContainer.getSpell(stack);
        }
        ISpellHelper helper = ManaAndArtificeMod.getSpellHelper();
        return helper == null ? null : helper.parseSpellDefinition(stack);
    }

    private void addSelection(ItemStack container, ItemStack spellStack, ISpellDefinition spell, String cooldownKey,
                              Map<String, SpellSelection> selections) {
        if (!isUsableSpell(spell)) {
            return;
        }
        selections.putIfAbsent(cooldownKey, new SpellSelection(spell, container, spellStack, cooldownKey));
    }

    private boolean isUsableSpell(ISpellDefinition spell) {
        return spell != null && spell != ISpellDefinition.EMPTY && spell.isValid();
    }

    private void completeCasting(EntityMaid maid) {
        MaidManaAndArtificeSpellData data = getData(maid);
        if (data == null || data.getCurrentSpell() == null) {
            return;
        }

        ISpellDefinition spell = data.getCurrentSpell();
        String cooldownKey = data.getCurrentSpellId();

        try {
            if (applyCurrentSpell(maid, data)) {
                setCooldown(maid, data, spell);
                SpringBloomReturnBauble.onSpellCast(maid, "mna", cooldownKey, data.getTarget());
            }
        } catch (Exception e) {
            LOGGER.warn("[MaidSpell/MNA] Failed to cast spell maid={} spell={}", maid.getUUID(), cooldownKey, e);
        } finally {
            data.resetCastingState();
        }
    }

    private boolean applyCurrentSpell(EntityMaid maid, MaidManaAndArtificeSpellData data) {
        ISpellDefinition spell = data.getCurrentSpell();
        ItemStack container = data.getCurrentSpellBook();
        LivingEntity target = data.getTarget();
        String cooldownKey = data.getCurrentSpellId();

        if (target == null || !target.isAlive()) {
            LOGGER.debug("[MaidSpell/MNA] Target missing before cast maid={} spell={}", maid.getUUID(), cooldownKey);
            return false;
        }

        BehaviorUtils.lookAtEntity(maid, target);
        Vec3 forward = computeForwardToTarget(maid, target);
        SpellSource source = new SpellSource(maid, InteractionHand.MAIN_HAND, maid.getEyePosition(), forward);
        SpellTarget spellTarget = new SpellTarget(target);
        SpellContext context = new SpellContext(maid.level(), spell, maid);
        SpellCastingResult result = SpellCaster.Affect(container, spell, maid.level(), source, spellTarget, context);
        SpellCastingResultCode code = result == null ? SpellCastingResultCode.EXCEPTION_THROWN : result.getCode();

        LOGGER.debug("[MaidSpell/MNA] Cast result maid={} spell={} target={} code={} channeled={}",
                maid.getUUID(), cooldownKey, describeEntity(target), code, spell.isChanneled());

        return code.isConsideredSuccess();
    }

    private void setCooldown(EntityMaid maid, MaidManaAndArtificeSpellData data, ISpellDefinition spell) {
        if (data != null && spell != null) {
            data.setSpellCooldown(data.getCurrentSpellId(), Math.max(20, spell.getCooldown(maid)), maid);
        }
    }

    private int getChannelDurationTicks(ISpellDefinition spell) {
        if (spell == null || !spell.isChanneled()) {
            return 0;
        }
        try {
            IModifiedSpellPart<Shape> shape = spell.getShape();
            if (shape == null || shape.getPart() == null) {
                return 100;
            }
            int maxChannelTime = shape.getPart().maxChannelTime(shape);
            if (maxChannelTime > 0) {
                return Math.min(maxChannelTime, 200);
            }
            float durationSeconds = shape.getContainedAttributes().contains(Attribute.DURATION)
                    ? shape.getValue(Attribute.DURATION) : 5.0F;
            return Math.min(Math.max(20, Math.round(durationSeconds * 20.0F)), 200);
        } catch (Exception e) {
            LOGGER.debug("[MaidSpell/MNA] Failed to determine channel duration spell={}", describeSpell(spell), e);
            return 100;
        }
    }

    private String buildCooldownKey(ItemStack container, ISpellDefinition spell) {
        return buildCooldownKey(container, -1, spell);
    }

    private String buildCooldownKey(ItemStack container, int slot, ISpellDefinition spell) {
        StringBuilder key = new StringBuilder("mna:");
        key.append(describeItem(container));
        if (slot >= 0) {
            key.append('#').append(slot);
        }
        key.append(':').append(describeSpell(spell));
        return key.toString();
    }

    private String describeSpell(ISpellDefinition spell) {
        if (spell == null) {
            return "<null>";
        }

        try {
            ResourceLocation shape = spell.getShape() == null || spell.getShape().getPart() == null
                    ? null : spell.getShape().getPart().getRegistryName();
            StringBuilder builder = new StringBuilder(String.valueOf(shape));
            spell.iterateComponents(component -> {
                if (component.getPart() != null) {
                    builder.append('+').append(component.getPart().getRegistryName());
                }
            });
            if (builder.length() > 0 && !"null".contentEquals(builder)) {
                return builder.toString();
            }
        } catch (Exception ignored) {
            // Fall through to NBT description.
        }

        try {
            CompoundTag tag = new CompoundTag();
            spell.writeToNBT(tag);
            return tag.toString();
        } catch (Exception e) {
            return String.valueOf(spell);
        }
    }

    private String describeItem(ItemStack stack) {
        return SpellProviderUtils.describeItem(stack);
    }

    private String describeEntity(LivingEntity entity) {
        return SpellProviderUtils.describeEntity(entity);
    }

    /**
     * 计算从女仆眼部到目标的精确方向向量，并同步更新女仆朝向。
     * BehaviorUtils.lookAtEntity 只调度下一 tick 的朝向更新，
     * 而 Shape.Target() 在当前 tick 就需要正确的 forward 向量。
     */
    private Vec3 computeForwardToTarget(EntityMaid maid, LivingEntity target) {
        return SpellProviderUtils.orientAndGetDirection(maid, target);
    }

    private record SpellSelection(ISpellDefinition spell, ItemStack container, ItemStack spellStack, String cooldownKey) {
    }
}
