package com.github.yimeng261.maidspell.spell.providers;

import com.binaris.wizardry.api.content.spell.Spell;
import com.binaris.wizardry.api.content.spell.internal.EntityCastContext;
import com.binaris.wizardry.api.content.spell.internal.SpellModifiers;
import com.binaris.wizardry.api.content.util.SpellUtil;
import com.binaris.wizardry.api.content.util.WandHelper;
import com.binaris.wizardry.content.item.SpellBookItem;
import com.binaris.wizardry.content.item.WandItem;
import com.binaris.wizardry.setup.registries.Spells;
import com.github.tartaricacid.touhoulittlemaid.entity.passive.EntityMaid;
import com.github.yimeng261.maidspell.api.ISpellBookProvider;
import com.github.yimeng261.maidspell.item.bauble.springBloomReturn.SpringBloomReturnBauble;
import com.github.yimeng261.maidspell.spell.data.MaidWizardrySpellData;
import com.mojang.logging.LogUtils;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.ai.behavior.BehaviorUtils;
import net.minecraft.world.item.ItemStack;
import org.slf4j.Logger;

import java.util.ArrayList;
import java.util.List;

/**
 * ElectroblobsWizardryRedux 模组法术提供者
 * 支持魔杖(Wand)和法术书(SpellBook)系统
 * 处理即时和持续性法术，支持蓄力机制
 */
public class WizardryProvider extends ISpellBookProvider<MaidWizardrySpellData, Spell> {

    private static final Logger LOGGER = LogUtils.getLogger();

    /**
     * 构造函数，绑定 MaidWizardrySpellData 数据类型
     */
    public WizardryProvider() {
        super(MaidWizardrySpellData::getOrCreate, Spell.class);
    }

    @Override
    protected List<Spell> collectSpellFromSingleSpellBook(ItemStack spellBook, EntityMaid maid) {
        List<Spell> spells = new ArrayList<>();
        MaidWizardrySpellData data = getData(maid);

        // 处理魔杖 - 可能包含多个法术
        if (spellBook.getItem() instanceof WandItem) {
            List<Spell> wandSpells = WandHelper.getSpells(spellBook);
            for (Spell spell : wandSpells) {
                if (spell != null && spell != Spells.NONE && !data.isSpellOnCooldown(spell.getLocation().toString())) {
                    spells.add(spell);
                }
            }
        }
        // 处理法术书 - 单个法术
        else if (spellBook.getItem() instanceof SpellBookItem) {
            Spell spell = SpellUtil.getSpell(spellBook);
            if (spell != Spells.NONE && !data.isSpellOnCooldown(spell.getLocation().toString())) {
                spells.add(spell);
            }
        }

        return spells;
    }

    /**
     * 检查物品是否为法术容器（魔杖或法术书）
     */
    @Override
    public boolean isSpellBook(ItemStack itemStack) {
        return itemStack != null && !itemStack.isEmpty() &&
               (itemStack.getItem() instanceof WandItem || itemStack.getItem() instanceof SpellBookItem);
    }

    /**
     * 开始施法
     */
    @Override
    public void initiateCasting(EntityMaid maid) {
        MaidWizardrySpellData data = getData(maid);

        // 检查目标
        LivingEntity target = data.getTarget();
        if (target == null || !target.isAlive()) {
            return;
        }

        // 选择法术
        Spell spell = selectSpell(maid);
        if (spell == null) {
            return;
        }

        // 检查法术是否允许实体施放
        if (!spell.canCastByEntity()) {
            LOGGER.debug("法术 {} 不允许被实体施放", spell.getLocation());
            return;
        }

        // 更新朝向
        BehaviorUtils.lookAtEntity(maid, target);

        // 开始施法
        startSpellCasting(maid, spell);
    }

    /**
     * 处理持续施法
     */
    @Override
    public void processContinuousCasting(EntityMaid maid) {
        MaidWizardrySpellData data = getData(maid);
        if (data == null || !data.isCasting() || data.getCurrentSpell() == null) {
            return;
        }

        data.incrementCastingTime();

        // 更新朝向
        updateMaidOrientation(maid, data);

        // 处理蓄力阶段
        if (!data.isCharged()) {
            if (data.getCastingTime() >= data.getChargeupTime()) {
                data.setCharged(true);
                // 对于即时法术，蓄力完成后立即施放
                if (data.getCurrentSpell().isInstantCast()) {
                    castSpell(maid, data);
                    return;
                }
            }
            return;
        }

        // 持续性法术的施放
        if (!data.getCurrentSpell().isInstantCast()) {
            castSpell(maid, data);
        }

        // 检查施法时间是否达到上限
        if (data.getCastingTime() >= data.getMaxCastingTime()) {
            completeCasting(maid);
        }
    }

    /**
     * 停止施法
     */
    @Override
    public void stopCasting(EntityMaid maid) {
        MaidWizardrySpellData data = getData(maid);
        if (data == null || !data.isCasting() || data.getCurrentSpell() == null) {
            return;
        }

        // 调用法术结束逻辑
        if (maid.level() instanceof ServerLevel) {
            EntityCastContext ctx = createContext(maid, data);
            data.getCurrentSpell().endCast(ctx);
        }

        // 设置冷却
        setCooldown(maid, data.getCurrentSpell());
        resetCastingState(maid, data);
    }

    /**
     * 选择一个可用的法术
     */
    private Spell selectSpell(EntityMaid maid) {
        List<Spell> availableSpells = collectSpellFromAvailableSpellBooks(maid);

        if (availableSpells.isEmpty()) {
            return null;
        }

        // 随机选择一个可用的法术
        int randomIndex = (int) (Math.random() * availableSpells.size());
        return availableSpells.get(randomIndex);
    }

    /**
     * 开始施放法术
     */
    private void startSpellCasting(EntityMaid maid, Spell spell) {
        MaidWizardrySpellData data = getData(maid);

        // 获取法术项（魔杖或法术书）
        ItemStack spellItem = findSpellItem(maid, spell);
        if (spellItem.isEmpty()) {
            return;
        }

        // 计算蓄力时间
        int chargeupTime = spell.getCharge();

        // 计算最大施法时间（对于持续性法术）
        int maxCastingTime = chargeupTime;
        if (!spell.isInstantCast()) {
            // 持续性法术最多持续10秒
            maxCastingTime += 200;
        }

        // 初始化施法状态
        data.initiateCastingState(spell, spellItem, chargeupTime, maxCastingTime);

        LOGGER.debug("女仆 {} 开始施放法术 {}, 蓄力时间: {}, 最大施法时间: {}",
                maid.getName().getString(), spell.getLocation(), chargeupTime, maxCastingTime);
    }

    /**
     * 执行法术施放
     */
    private void castSpell(EntityMaid maid, MaidWizardrySpellData data) {
        if (!(maid.level() instanceof ServerLevel)) {
            return;
        }

        EntityCastContext ctx = createContext(maid, data);

        try {
            // 调用法术的施放方法
            boolean success = data.getCurrentSpell().cast(ctx);
            if (!success) {
                LOGGER.debug("法术 {} 施放失败", data.getCurrentSpell().getLocation());
                completeCasting(maid);
            } else {
                SpringBloomReturnBauble.onSpellCast(
                    maid,
                    "ebwizardry",
                    data.getCurrentSpell().getLocation().toString(),
                    data.getTarget()
                );
            }
        } catch (Exception e) {
            LOGGER.error("女仆 {} 施放法术 {} 时出错: {}",
                    maid.getName().getString(), data.getCurrentSpell().getLocation(), e.getMessage());
            completeCasting(maid);
        }
    }

    /**
     * 完成施法
     */
    private void completeCasting(EntityMaid maid) {
        MaidWizardrySpellData data = getData(maid);
        if (data == null || data.getCurrentSpell() == null) {
            return;
        }

        // 调用法术结束逻辑
        if (maid.level() instanceof ServerLevel) {
            EntityCastContext ctx = createContext(maid, data);
            data.getCurrentSpell().endCast(ctx);
        }

        // 设置冷却
        setCooldown(maid, data.getCurrentSpell());
        resetCastingState(maid, data);
    }

    /**
     * 更新女仆朝向
     */
    private void updateMaidOrientation(EntityMaid maid, MaidWizardrySpellData data) {
        LivingEntity target = data.getTarget();
        if (target != null && target.isAlive()) {
            BehaviorUtils.lookAtEntity(maid, target);
        }
    }

    /**
     * 创建实体施法上下文
     */
    private EntityCastContext createContext(EntityMaid maid, MaidWizardrySpellData data) {
        SpellModifiers modifiers = new SpellModifiers();
        // 设置默认修饰器
        modifiers.set(SpellModifiers.POTENCY, 1.0f, false);
        modifiers.set(SpellModifiers.COST, 0.0f, false); // 女仆施法不消耗魔力
        modifiers.set(SpellModifiers.CHARGEUP, 1.0f, false);

        int castingTicks = Math.max(0, data.getCastingTime() - data.getChargeupTime());

        return new EntityCastContext(
                maid.level(),
                maid,
                InteractionHand.MAIN_HAND,
                castingTicks,
                data.getTarget(),
                modifiers
        );
    }

    /**
     * 查找包含指定法术的法术项
     */
    private ItemStack findSpellItem(EntityMaid maid, Spell spell) {
        MaidWizardrySpellData data = getData(maid);

        for (ItemStack spellBook : data.getSpellBooks()) {
            // 检查魔杖
            if (spellBook.getItem() instanceof WandItem) {
                List<Spell> wandSpells = WandHelper.getSpells(spellBook);
                if (wandSpells.contains(spell)) {
                    return spellBook;
                }
            }
            // 检查法术书
            else if (spellBook.getItem() instanceof SpellBookItem) {
                Spell bookSpell = SpellUtil.getSpell(spellBook);
                if (bookSpell == spell) {
                    return spellBook;
                }
            }
        }

        return ItemStack.EMPTY;
    }

    /**
     * 设置法术冷却
     */
    private void setCooldown(EntityMaid maid, Spell spell) {
        MaidWizardrySpellData data = getData(maid);
        if (data != null && spell != null) {
            String spellId = spell.getLocation().toString();
            //int cooldown = spell.getCooldown();
            int cooldown = 20;
            data.setSpellCooldown(spellId, cooldown, maid);
            LOGGER.debug("为女仆 {} 的法术 {} 设置冷却: {} ticks",
                    maid.getName().getString(), spellId, cooldown);
        }
    }

    /**
     * 重置施法状态
     */
    private void resetCastingState(EntityMaid maid, MaidWizardrySpellData data) {
        data.resetCastingState();
    }
}
