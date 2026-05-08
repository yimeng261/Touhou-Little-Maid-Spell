package com.github.yimeng261.maidspell.spell.providers;

import com.github.tartaricacid.touhoulittlemaid.entity.passive.EntityMaid;
import com.github.yimeng261.maidspell.api.ISpellBookProvider;
import com.github.yimeng261.maidspell.item.bauble.springBloomReturn.SpringBloomReturnBauble;
import com.github.yimeng261.maidspell.spell.data.MaidArsNouveauSpellData;
import com.hollingsworth.arsnouveau.api.item.ICasterTool;
import com.hollingsworth.arsnouveau.api.registry.SpellCasterRegistry;
import com.hollingsworth.arsnouveau.api.spell.AbstractCaster;
import com.hollingsworth.arsnouveau.api.spell.AbstractSpellPart;
import com.hollingsworth.arsnouveau.api.spell.Spell;
import com.hollingsworth.arsnouveau.api.spell.SpellContext;
import com.hollingsworth.arsnouveau.api.spell.SpellResolver;
import com.hollingsworth.arsnouveau.api.spell.wrapped_caster.LivingCaster;
import com.hollingsworth.arsnouveau.api.util.SpellUtil;
import com.hollingsworth.arsnouveau.common.capability.ManaCap;
import com.hollingsworth.arsnouveau.common.spell.augment.AugmentSensitive;
import com.hollingsworth.arsnouveau.setup.registry.CapabilityRegistry;
import com.mojang.logging.LogUtils;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.ai.behavior.BehaviorUtils;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.EntityHitResult;
import net.minecraft.world.phys.HitResult;
import org.slf4j.Logger;

import java.util.ArrayList;
import java.util.List;

/**
 * 新生魔艺模组的法术书提供者
 * 通过 MaidArsNouveauSpellData 管理各女仆的数据
 */
public class ArsNouveauProvider extends ISpellBookProvider<MaidArsNouveauSpellData, Spell> {
    private static final Logger LOGGER = LogUtils.getLogger();

    /**
     * 构造函数，绑定 MaidArsNouveauSpellData 数据类型和 Spell 法术类型
     */
    public ArsNouveauProvider() {
        super(MaidArsNouveauSpellData::getOrCreate, Spell.class);
    }

    // === 核心方法（接受EntityMaid参数） ===

    /**
     * 从单个法术书中收集所有法术
     * @param spellBook 法术书物品堆栈
     * @return 该法术书中的所有法术列表
     */
    @Override
    protected List<Spell> collectSpellFromSingleSpellBook(ItemStack spellBook, EntityMaid maid) {
        List<Spell> spells = new ArrayList<>();

        if (spellBook == null || spellBook.isEmpty() || !isSpellBook(spellBook)) {
            return spells;
        }

        AbstractCaster<?> caster = SpellCasterRegistry.from(spellBook);
        if (caster == null) {
            return spells;
        }

        for (int i = 0; i < caster.getMaxSlots(); i++) {
            Spell spell = caster.getSpell(i);
            if (spell != null && spell.isValid() && !spell.isEmpty()) {
                spells.add(spell);
            }
        }

        return spells;
    }

    /**
     * 检查物品是否为法术书
     */
    @Override
    public boolean isSpellBook(ItemStack itemStack) {
        if (itemStack == null || itemStack.isEmpty()) {
            return false;
        }
        return itemStack.getItem() instanceof ICasterTool;
    }


    /**
     * 开始施法
     */
    @Override
    public void initiateCasting(EntityMaid maid) {
        MaidArsNouveauSpellData data = getData(maid);
        LOGGER.debug("[MaidSpell/Ars] initiateCasting maid={} books={} target={}", maid.getUUID(), data.getSpellBooks().size(), describeEntity(data.getTarget()));

        SpellSelection selection = selectRandomSpell(maid);
        if (selection == null) {
            LOGGER.warn("[MaidSpell/Ars] No available Ars spell for maid={} books={}", maid.getUUID(), data.getSpellBooks().size());
            return;
        }

        Spell spell = selection.spell();
        ensureManaCapability(maid, spell);

        LivingEntity target = data.getTarget();
        if (target != null) {
            BehaviorUtils.lookAtEntity(maid, target);
        }

        data.setCasting(true);
        data.setCastingTicks(0);
        data.setCurrentSpell(spell, selection.spellBook(), selection.slot());
        data.setSpellCooldown(selection.cooldownKey(), spell.getCost() / 2, maid);

        LOGGER.debug("[MaidSpell/Ars] Selected spell maid={} spell={} book={} slot={} cost={} cooldownKey={} target={}",
                maid.getUUID(), describeSpell(spell), describeItem(selection.spellBook()), selection.slot(), spell.getCost(), selection.cooldownKey(), describeEntity(target));

        maid.swing(InteractionHand.MAIN_HAND);
    }

    /**
     * 处理持续施法
     */
    @Override
    public void processContinuousCasting(EntityMaid maid) {
        MaidArsNouveauSpellData data = getData(maid);
        if (data == null || !data.isCasting() || data.getCurrentSpell() == null) {
            if (data != null && LOGGER.isTraceEnabled()) {
                LOGGER.trace("[MaidSpell/Ars] Skip continuous cast maid={} casting={} spell={}", maid.getUUID(), data.isCasting(), describeSpell(data.getCurrentSpell()));
            }
            return;
        }

        data.incrementCastingTicks();

        // 持续面向目标
        LivingEntity target = data.getTarget();
        if (target != null) {
            BehaviorUtils.lookAtEntity(maid, target);
        }

        // 等待一段时间让女仆转向目标，然后完成施法
        if (data.isCastingComplete()) {
            LOGGER.debug("[MaidSpell/Ars] Casting complete maid={} spell={} ticks={} book={} slot={}",
                    maid.getUUID(), describeSpell(data.getCurrentSpell()), data.getCastingTicks(), describeItem(data.getCurrentSpellBook()), data.getCurrentSpellSlot());
            completeCasting(maid);
        }
    }

    /**
     * 停止施法
     */
    @Override
    public void stopCasting(EntityMaid maid) {
        MaidArsNouveauSpellData data = getData(maid);
        if (data == null || !data.isCasting()) {
            return;
        }

        data.resetCastingState();
    }


    // === 私有辅助方法 ===

    /**
     * 确保女仆有魔力能力
     */
    private void ensureManaCapability(EntityMaid maid, Spell spell) {
        if (maid == null) {
            return;
        }
        ManaCap manaCap = CapabilityRegistry.getMana(maid);
        if (manaCap == null) {
            LOGGER.debug("[MaidSpell/Ars] Maid {} has no Ars mana capability; mana checks are bypassed for maid casting spell={}", maid.getUUID(), describeSpell(spell));
            return;
        }
        double requiredMana = Math.max(1000.0, spell != null ? spell.getCost() : 0.0);
        double before = manaCap.getCurrentMana();
        if (before < requiredMana) {
            manaCap.setMana(requiredMana);
            LOGGER.debug("[MaidSpell/Ars] Refilled maid mana maid={} before={} after={} spell={}", maid.getUUID(), before, requiredMana, describeSpell(spell));
        } else if (LOGGER.isDebugEnabled()) {
            LOGGER.debug("[MaidSpell/Ars] Maid mana is enough maid={} mana={} required={} spell={}", maid.getUUID(), before, requiredMana, describeSpell(spell));
        }
    }


    /**
     * 获取可用的法术列表（未在冷却中的法术）
     * @param maid 女仆实体
     * @return 可用的法术列表
     */
    private List<SpellSelection> getAvailableSpells(EntityMaid maid) {
        MaidArsNouveauSpellData data = getData(maid);
        List<SpellSelection> availableSpells = new ArrayList<>();

        if (data.getSpellBooks().isEmpty()) {
            LOGGER.warn("[MaidSpell/Ars] Maid {} has no tracked spell books", maid.getUUID());
        }

        for (ItemStack spellBook : data.getSpellBooks()) {
            if (spellBook == null || spellBook.isEmpty()) {
                LOGGER.warn("[MaidSpell/Ars] Maid {} has empty/null spell book entry", maid.getUUID());
                continue;
            }
            if (!isSpellBook(spellBook)) {
                LOGGER.warn("[MaidSpell/Ars] Tracked item is not an Ars caster tool maid={} item={}", maid.getUUID(), describeItem(spellBook));
                continue;
            }
            AbstractCaster<?> caster = SpellCasterRegistry.from(spellBook);
            if (caster == null) {
                LOGGER.warn("[MaidSpell/Ars] SpellCasterRegistry returned null maid={} item={}", maid.getUUID(), describeItem(spellBook));
                continue;
            }
            LOGGER.debug("[MaidSpell/Ars] Scanning Ars book maid={} item={} slots={}", maid.getUUID(), describeItem(spellBook), caster.getMaxSlots());
            for (int slot = 0; slot < caster.getMaxSlots(); slot++) {
                Spell spell = caster.getSpell(slot);
                if (spell == null) {
                    LOGGER.debug("[MaidSpell/Ars] Slot has null spell maid={} item={} slot={}", maid.getUUID(), describeItem(spellBook), slot);
                    continue;
                }
                if (!spell.isValid() || spell.isEmpty()) {
                    LOGGER.debug("[MaidSpell/Ars] Slot spell invalid/empty maid={} item={} slot={} spell={} valid={} empty={}",
                            maid.getUUID(), describeItem(spellBook), slot, describeSpell(spell), spell.isValid(), spell.isEmpty());
                    continue;
                }
                String cooldownKey = buildCooldownKey(spellBook, slot, spell);
                if (data.isSpellOnCooldown(cooldownKey)) {
                    LOGGER.debug("[MaidSpell/Ars] Slot spell on cooldown maid={} item={} slot={} spell={} cooldownKey={} remaining={}",
                            maid.getUUID(), describeItem(spellBook), slot, describeSpell(spell), cooldownKey, data.getSpellCooldown(cooldownKey));
                    continue;
                }
                LOGGER.debug("[MaidSpell/Ars] Slot spell available maid={} item={} slot={} spell={} cooldownKey={}",
                        maid.getUUID(), describeItem(spellBook), slot, describeSpell(spell), cooldownKey);
                availableSpells.add(new SpellSelection(spell, spellBook, slot, cooldownKey));
            }
        }

        LOGGER.debug("[MaidSpell/Ars] Available Ars spells maid={} count={}", maid.getUUID(), availableSpells.size());
        return availableSpells;
    }

    /**
     * 随机选择一个可用的法术
     * @param maid 女仆实体
     * @return 随机选择的法术，如果没有可用法术则返回null
     */
    private SpellSelection selectRandomSpell(EntityMaid maid) {
        List<SpellSelection> availableSpells = getAvailableSpells(maid);
        if (availableSpells.isEmpty()) {
            return null;
        }

        int randomIndex = (int) (Math.random() * availableSpells.size());
        SpellSelection selection = availableSpells.get(randomIndex);
        LOGGER.debug("[MaidSpell/Ars] Random selected index={} count={} spell={} book={} slot={}",
                randomIndex, availableSpells.size(), describeSpell(selection.spell()), describeItem(selection.spellBook()), selection.slot());
        return selection;
    }

    private String buildCooldownKey(ItemStack spellBook, int slot, Spell spell) {
        StringBuilder key = new StringBuilder();
        key.append(spellBook.getItem().builtInRegistryHolder().key().location()).append('#').append(slot).append(':');
        var parts = spell.unsafeList();
        for (int i = 0; i < parts.size(); i++) {
            if (i > 0) {
                key.append(',');
            }
            key.append(parts.get(i).getRegistryName());
        }
        return key.toString();
    }

    /**
     * 完成施法
     */
    private void completeCasting(EntityMaid maid) {
        MaidArsNouveauSpellData data = getData(maid);
        try {
            ensureManaCapability(maid, data.getCurrentSpell());

            ItemStack spellBook = data.getCurrentSpellBook();
            if (spellBook.isEmpty()) {
                LOGGER.warn("[MaidSpell/Ars] Current spell book is empty before cast maid={} spell={} slot={}",
                        maid.getUUID(), describeSpell(data.getCurrentSpell()), data.getCurrentSpellSlot());
            }
            LOGGER.debug("[MaidSpell/Ars] Resolving spell maid={} spell={} book={} slot={} target={} manaCapPresent={}",
                    maid.getUUID(), describeSpell(data.getCurrentSpell()), describeItem(spellBook), data.getCurrentSpellSlot(),
                    describeEntity(data.getTarget()), CapabilityRegistry.getMana(maid) != null);
            boolean summonSpell = containsSummonEffect(data.getCurrentSpell());
            LivingEntity owner = maid.getOwner();
            LivingEntity contextCaster = summonSpell && owner != null ? owner : maid;
            LivingCaster wrappedCaster = new LivingCaster(contextCaster);
            SpellContext context = new SpellContext(maid.level(), data.getCurrentSpell(), maid, wrappedCaster, spellBook);
            LOGGER.debug("[MaidSpell/Ars] Context caster maid={} spell={} summonSpell={} arsCaster={}",
                    maid.getUUID(), describeSpell(data.getCurrentSpell()), summonSpell, describeEntity(contextCaster));
            SpellResolver resolver = new SpellResolver(context) {
                @Override
                protected boolean enoughMana(LivingEntity entity) {
                    LOGGER.debug("[MaidSpell/Ars] Bypassing Ars mana check for maid={} spell={}", maid.getUUID(), describeSpell(data.getCurrentSpell()));
                    return true;
                }

                @Override
                public void expendMana() {
                    LOGGER.debug("[MaidSpell/Ars] Skipping Ars mana spend for maid={} spell={}", maid.getUUID(), describeSpell(data.getCurrentSpell()));
                }
            };

            boolean castSuccess;
            LivingEntity target = data.getTarget();

            if (target != null) {
                BehaviorUtils.lookAtEntity(maid, target);
                if (summonSpell) {
                    resolver.onResolveEffect(maid.level(), new EntityHitResult(target, target.getEyePosition()));
                    castSuccess = true;
                    LOGGER.debug("[MaidSpell/Ars] Direct summon resolve on target maid={} target={}", maid.getUUID(), describeEntity(target));
                } else {
                    castSuccess = resolver.onCastOnEntity(spellBook, target, InteractionHand.MAIN_HAND);
                    LOGGER.debug("[MaidSpell/Ars] Resolver onCastOnEntity result maid={} target={} success={}", maid.getUUID(), describeEntity(target), castSuccess);
                }
            } else {
                // 没有指定目标，使用射线追踪
                boolean isSensitive = data.getCurrentSpell().getBuffsAtIndex(0, maid, AugmentSensitive.INSTANCE) > 0;
                HitResult result = SpellUtil.rayTrace(maid, 0.5 + 5.0, 0, isSensitive);

                if (result instanceof EntityHitResult entityHitResult && entityHitResult.getEntity() instanceof LivingEntity) {
                    castSuccess = resolver.onCastOnEntity(spellBook, entityHitResult.getEntity(), InteractionHand.MAIN_HAND);
                    LOGGER.debug("[MaidSpell/Ars] RayTrace entity result maid={} hit={} success={}", maid.getUUID(), describeEntity(entityHitResult.getEntity()), castSuccess);
                } else if (result instanceof BlockHitResult blockHitResult) {
                    castSuccess = resolver.onCastOnBlock(blockHitResult);
                    LOGGER.debug("[MaidSpell/Ars] RayTrace block result maid={} pos={} type={} success={}", maid.getUUID(), blockHitResult.getBlockPos(), blockHitResult.getType(), castSuccess);
                } else {
                    castSuccess = resolver.onCast(spellBook, maid.level());
                    LOGGER.debug("[MaidSpell/Ars] Direct onCast result maid={} rayType={} success={}", maid.getUUID(), result.getType(), castSuccess);
                }
            }

            if (castSuccess) {
                LOGGER.debug("[MaidSpell/Ars] Spell cast succeeded maid={} spell={} book={} slot={}",
                        maid.getUUID(), describeSpell(data.getCurrentSpell()), describeItem(spellBook), data.getCurrentSpellSlot());
                SpringBloomReturnBauble.onSpellCast(
                        maid,
                        "ars_nouveau",
                        data.getCurrentSpell() != null ? data.getCurrentSpell().name() : null,
                        target
                );
            } else {
                LOGGER.warn("[MaidSpell/Ars] SpellResolver returned false maid={} spell={} book={} slot={} target={}",
                        maid.getUUID(), describeSpell(data.getCurrentSpell()), describeItem(spellBook), data.getCurrentSpellSlot(), describeEntity(target));
            }
        } catch (Exception e) {
            LOGGER.warn("Failed to cast Ars Nouveau spell for maid {}", maid.getUUID(), e);
        } finally {
            data.resetCastingState();
        }
    }


    private boolean containsSummonEffect(Spell spell) {
        if (spell == null || spell.isEmpty()) {
            return false;
        }
        for (AbstractSpellPart part : spell.unsafeList()) {
            String id = String.valueOf(part.getRegistryName());
            if (id.equals("ars_nouveau:glyph_summon_wolves")
                    || id.equals("ars_nouveau:glyph_summon_decoy")
                    || id.equals("ars_nouveau:glyph_summon_steed")
                    || id.equals("ars_nouveau:glyph_summon_vex")
                    || id.equals("ars_nouveau:glyph_summon_undead")) {
                return true;
            }
        }
        return false;
    }

    private String describeItem(ItemStack stack) {
        if (stack == null) {
            return "<null>";
        }
        if (stack.isEmpty()) {
            return "<empty>";
        }
        return stack.getItem().builtInRegistryHolder().key().location().toString();
    }

    private String describeSpell(Spell spell) {
        if (spell == null) {
            return "<null>";
        }
        StringBuilder builder = new StringBuilder();
        builder.append("name='").append(spell.name()).append("' cost=").append(spell.getCost()).append(" recipe=");
        var parts = spell.unsafeList();
        for (int i = 0; i < parts.size(); i++) {
            if (i > 0) {
                builder.append(",");
            }
            builder.append(parts.get(i).getRegistryName());
        }
        return builder.toString();
    }

    private String describeEntity(Object entity) {
        if (entity == null) {
            return "<null>";
        }
        if (entity instanceof LivingEntity living) {
            return living.getType().builtInRegistryHolder().key().location() + "@" + living.getUUID();
        }
        return entity.toString();
    }

    private record SpellSelection(Spell spell, ItemStack spellBook, int slot, String cooldownKey) {
    }

}
