package com.github.yimeng261.maidspell.spell.providers;

import com.binaris.wizardry.api.content.data.ConjureData;
import com.binaris.wizardry.api.content.event.SpellCastEvent;
import com.binaris.wizardry.api.content.spell.Spell;
import com.binaris.wizardry.api.content.spell.internal.EntityCastContext;
import com.binaris.wizardry.api.content.spell.internal.LocationCastContext;
import com.binaris.wizardry.api.content.spell.internal.SpellModifiers;
import com.binaris.wizardry.api.content.util.CastItemDataHelper;
import com.binaris.wizardry.api.content.util.CastItemUtils;
import com.binaris.wizardry.api.content.util.RegistryUtils;
import com.binaris.wizardry.content.item.SpellBookItem;
import com.binaris.wizardry.content.item.WandItem;
import com.binaris.wizardry.content.spell.DefaultProperties;
import com.binaris.wizardry.content.spell.abstr.ConjureItemSpell;
import com.binaris.wizardry.content.spell.sorcery.ConjureArmor;
import com.binaris.wizardry.core.event.WizardryEventBus;
import com.binaris.wizardry.core.networking.s2c.NPCSpellCastS2C;
import com.binaris.wizardry.core.platform.Services;
import com.binaris.wizardry.setup.registries.EBItems;
import com.binaris.wizardry.setup.registries.Spells;
import com.github.tartaricacid.touhoulittlemaid.entity.passive.EntityMaid;
import com.github.yimeng261.maidspell.api.ISpellBookProvider;
import com.github.yimeng261.maidspell.item.bauble.springBloomReturn.SpringBloomReturnBauble;
import com.github.yimeng261.maidspell.spell.data.MaidWizardrySpellData;
import com.mojang.logging.LogUtils;
import net.minecraft.core.Direction;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.ai.behavior.BehaviorUtils;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraftforge.items.ItemHandlerHelper;
import org.slf4j.Logger;

import java.lang.reflect.Field;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.concurrent.ThreadLocalRandom;

/**
 * ElectroblobsWizardryRedux 模组法术提供者
 * 支持魔杖(Wand)和法术书(SpellBook)系统
 * 处理即时和持续性法术，支持蓄力机制
 */
public class WizardryProvider extends ISpellBookProvider<MaidWizardrySpellData, Spell> {

    private static final Logger LOGGER = LogUtils.getLogger();
    private static final int CONTINUOUS_CAST_DURATION = 200;
    private static final Optional<Field> CONJURE_ITEM_FIELD = findConjureItemField();
    private static final List<MaidWizardrySpellAdapter> SPELL_ADAPTERS = List.of(
            new ConjureArmorAdapter(),
            new ConjureItemAdapter(),
            new EntityCastAdapter(),
            new LocationCastAdapter()
    );

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
        if (data == null || spellBook == null || spellBook.isEmpty()) {
            return spells;
        }

        for (Spell spell : getSpells(spellBook)) {
            if (isMaidCombatSpell(spell) && !data.isSpellOnCooldown(spell.getLocation().toString())) {
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
        return itemStack != null && !itemStack.isEmpty()
                && (itemStack.getItem() instanceof WandItem || itemStack.getItem() instanceof SpellBookItem);
    }

    /**
     * 开始施法
     */
    @Override
    public void initiateCasting(EntityMaid maid) {
        MaidWizardrySpellData data = getData(maid);
        if (data == null) {
            return;
        }

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

        finishCasting(maid, data, true);
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
        int randomIndex = ThreadLocalRandom.current().nextInt(availableSpells.size());
        return availableSpells.get(randomIndex);
    }

    /**
     * 开始施放法术
     */
    private void startSpellCasting(EntityMaid maid, Spell spell) {
        MaidWizardrySpellData data = getData(maid);
        if (data == null) {
            return;
        }

        // 获取法术项（魔杖或法术书）
        ItemStack spellItem = findSpellItem(maid, spell);
        if (spellItem.isEmpty()) {
            return;
        }

        SpellModifiers modifiers = createModifiers();
        if (WizardryEventBus.getInstance().fire(new SpellCastEvent.Pre(SpellCastEvent.Source.NPC, spell, maid, modifiers))) {
            LOGGER.debug("法术 {} 的预施法事件被取消", spell.getLocation());
            return;
        }

        // 计算蓄力时间
        int chargeupTime = CastItemUtils.calcCharge(spell, modifiers);

        // 计算最大施法时间（对于持续性法术）
        int maxCastingTime = chargeupTime;
        if (!spell.isInstantCast()) {
            maxCastingTime += CONTINUOUS_CAST_DURATION;
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

        Spell spell = data.getCurrentSpell();
        EntityCastContext ctx = createContext(maid, data);
        SpellModifiers modifiers = ctx.modifiers();

        try {
            if (!spell.isInstantCast() && WizardryEventBus.getInstance().fire(
                    new SpellCastEvent.Tick(SpellCastEvent.Source.NPC, spell, maid, modifiers, ctx.castingTicks()))) {
                LOGGER.debug("法术 {} 的持续施法事件被取消", spell.getLocation());
                finishCasting(maid, data, false);
                return;
            }

            // 调用法术的施放方法，部分 EBWR 法术需要女仆专用上下文或物品处理。
            boolean success = castMaidAwareSpell(maid, data, ctx);
            if (!success) {
                LOGGER.debug("法术 {} 施放失败", spell.getLocation());
                finishCasting(maid, data, false);
                return;
            }

            if (spell.isInstantCast() || ctx.castingTicks() == 1) {
                WizardryEventBus.getInstance().fire(new SpellCastEvent.Post(SpellCastEvent.Source.NPC, spell, maid, modifiers));
                sendSpellCastPacket(maid, data, modifiers);
            }

            SpringBloomReturnBauble.onSpellCast(
                    maid,
                    "ebwizardry",
                    spell.getLocation().toString(),
                    data.getTarget()
            );

            if (spell.isInstantCast()) {
                finishCasting(maid, data, false);
            }
        } catch (Exception e) {
            LOGGER.error("女仆 {} 施放法术 {} 时出错: {}",
                    maid.getName().getString(), spell.getLocation(), e.getMessage());
            finishCasting(maid, data, false);
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

        finishCasting(maid, data, false);
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
        int castingTicks = Math.max(0, data.getCastingTime() - data.getChargeupTime());

        return new EntityCastContext(
                maid.level(),
                maid,
                InteractionHand.MAIN_HAND,
                castingTicks,
                data.getTarget(),
                createModifiers()
        );
    }

    private SpellModifiers createModifiers() {
        SpellModifiers modifiers = new SpellModifiers();
        modifiers.set(SpellModifiers.POTENCY, 1.0f);
        modifiers.set(SpellModifiers.COST, 1.0f);
        modifiers.set(SpellModifiers.CHARGEUP, 1.0f);
        modifiers.set(SpellModifiers.COOLDOWN, 1.0f);
        return modifiers;
    }

    /**
     * 查找包含指定法术的法术项
     */
    private ItemStack findSpellItem(EntityMaid maid, Spell spell) {
        MaidWizardrySpellData data = getData(maid);
        if (data == null) {
            return ItemStack.EMPTY;
        }

        for (ItemStack spellBook : data.getSpellBooks()) {
            if (containsSpell(spellBook, spell)) {
                return spellBook;
            }
        }

        return ItemStack.EMPTY;
    }

    private List<Spell> getSpells(ItemStack spellBook) {
        if (spellBook.getItem() instanceof WandItem) {
            return CastItemDataHelper.getSpells(spellBook);
        }
        if (spellBook.getItem() instanceof SpellBookItem) {
            return List.of(RegistryUtils.getSpell(spellBook));
        }
        return List.of();
    }

    private boolean containsSpell(ItemStack spellBook, Spell spell) {
        for (Spell containedSpell : getSpells(spellBook)) {
            if (isAvailableSpell(containedSpell) && containedSpell.equals(spell)) {
                return true;
            }
        }
        return false;
    }

    private boolean isAvailableSpell(Spell spell) {
        return spell != null && spell != Spells.NONE;
    }

    private boolean isMaidCombatSpell(Spell spell) {
        return isAvailableSpell(spell);
    }

    private boolean castMaidAwareSpell(EntityMaid maid, MaidWizardrySpellData data, EntityCastContext ctx) {
        Spell spell = data.getCurrentSpell();
        if (spell == null) {
            return false;
        }
        MaidWizardryCastRequest request = new MaidWizardryCastRequest(this, maid, data, ctx);
        for (MaidWizardrySpellAdapter adapter : SPELL_ADAPTERS) {
            if (adapter.supports(spell) && adapter.cast(request)) {
                return true;
            }
        }
        return false;
    }

    private LocationCastContext createTargetLocationContext(MaidWizardrySpellData data, SpellModifiers modifiers, int castingTicks) {
        LivingEntity target = data.getTarget();
        if (target == null) {
            return null;
        }
        return new LocationCastContext(
                target.level(),
                target.getX(),
                target.getY(),
                target.getZ(),
                Direction.UP,
                castingTicks,
                CONTINUOUS_CAST_DURATION,
                modifiers
        );
    }

    private static class MaidWizardryCastRequest {
        private final WizardryProvider provider;
        private final EntityMaid maid;
        private final MaidWizardrySpellData data;
        private final EntityCastContext entityContext;

        private MaidWizardryCastRequest(WizardryProvider provider, EntityMaid maid, MaidWizardrySpellData data,
                                        EntityCastContext entityContext) {
            this.provider = provider;
            this.maid = maid;
            this.data = data;
            this.entityContext = entityContext;
        }

        private Spell spell() {
            return data.getCurrentSpell();
        }
    }

    private interface MaidWizardrySpellAdapter {
        boolean supports(Spell spell);

        boolean cast(MaidWizardryCastRequest request);
    }

    private static class ConjureArmorAdapter implements MaidWizardrySpellAdapter {
        @Override
        public boolean supports(Spell spell) {
            return spell instanceof ConjureArmor;
        }

        @Override
        public boolean cast(MaidWizardryCastRequest request) {
            int duration = request.provider.getConjureDuration(request.spell(), request.entityContext.modifiers());
            long expireTime = request.maid.level().getGameTime() + duration;
            boolean equippedAny = false;
            equippedAny |= request.provider.equipConjuredItem(request.maid, EquipmentSlot.HEAD,
                    new ItemStack(EBItems.SPECTRAL_HELMET.get()), duration, expireTime);
            equippedAny |= request.provider.equipConjuredItem(request.maid, EquipmentSlot.CHEST,
                    new ItemStack(EBItems.SPECTRAL_CHESTPLATE.get()), duration, expireTime);
            equippedAny |= request.provider.equipConjuredItem(request.maid, EquipmentSlot.LEGS,
                    new ItemStack(EBItems.SPECTRAL_LEGGINGS.get()), duration, expireTime);
            equippedAny |= request.provider.equipConjuredItem(request.maid, EquipmentSlot.FEET,
                    new ItemStack(EBItems.SPECTRAL_BOOTS.get()), duration, expireTime);
            return equippedAny;
        }
    }

    private static class ConjureItemAdapter implements MaidWizardrySpellAdapter {
        @Override
        public boolean supports(Spell spell) {
            return spell instanceof ConjureItemSpell;
        }

        @Override
        public boolean cast(MaidWizardryCastRequest request) {
            ItemStack stack = request.provider.createConjuredStack(request.spell());
            if (stack.isEmpty()) {
                return false;
            }
            int duration = request.provider.getConjureDuration(request.spell(), request.entityContext.modifiers());
            long expireTime = request.maid.level().getGameTime() + duration;
            request.provider.markConjured(stack, duration, expireTime);
            ItemStack remainder = ItemHandlerHelper.insertItemStacked(request.maid.getAvailableInv(false), stack, false);
            if (!remainder.isEmpty()) {
                request.maid.spawnAtLocation(remainder);
            }
            return true;
        }
    }

    private static class EntityCastAdapter implements MaidWizardrySpellAdapter {
        @Override
        public boolean supports(Spell spell) {
            return spell.canCastByEntity();
        }

        @Override
        public boolean cast(MaidWizardryCastRequest request) {
            return request.spell().cast(request.entityContext);
        }
    }

    private static class LocationCastAdapter implements MaidWizardrySpellAdapter {
        @Override
        public boolean supports(Spell spell) {
            return spell.canCastByLocation();
        }

        @Override
        public boolean cast(MaidWizardryCastRequest request) {
            LocationCastContext locationCtx = request.provider.createTargetLocationContext(
                    request.data, request.entityContext.modifiers(), request.entityContext.castingTicks());
            return locationCtx != null && request.spell().cast(locationCtx);
        }
    }

    private int getConjureDuration(Spell spell, SpellModifiers modifiers) {
        return Math.max(1, (int) (spell.property(DefaultProperties.ITEM_LIFETIME) * modifiers.get(SpellModifiers.DURATION)));
    }

    private ItemStack createConjuredStack(Spell spell) {
        if (!(spell instanceof ConjureItemSpell conjureItemSpell)) {
            return ItemStack.EMPTY;
        }
        return getConjureItem(conjureItemSpell)
                .map(ItemStack::new)
                .orElse(ItemStack.EMPTY);
    }

    private static Optional<Field> findConjureItemField() {
        try {
            Field field = ConjureItemSpell.class.getDeclaredField("item");
            field.setAccessible(true);
            return Optional.of(field);
        } catch (ReflectiveOperationException e) {
            LOGGER.warn("Unable to access EBWR ConjureItemSpell item field; maid conjure item adapter will be limited", e);
            return Optional.empty();
        }
    }

    private static Optional<Item> getConjureItem(ConjureItemSpell spell) {
        if (CONJURE_ITEM_FIELD.isEmpty()) {
            return Optional.empty();
        }
        try {
            Object value = CONJURE_ITEM_FIELD.get().get(spell);
            return value instanceof Item item ? Optional.of(item) : Optional.empty();
        } catch (IllegalAccessException e) {
            LOGGER.warn("Failed to read EBWR conjured item from {}", spell.getLocation(), e);
            return Optional.empty();
        }
    }

    private boolean equipConjuredItem(EntityMaid maid, EquipmentSlot slot, ItemStack stack, int duration, long expireTime) {
        if (!maid.getItemBySlot(slot).isEmpty()) {
            return false;
        }
        markConjured(stack, duration, expireTime);
        maid.setItemSlot(slot, stack);
        return true;
    }

    private void markConjured(ItemStack stack, int duration, long expireTime) {
        ConjureData conjureData = Services.OBJECT_DATA.getConjureData(stack);
        conjureData.setDuration(duration);
        conjureData.setExpireTime(expireTime);
        conjureData.setSummoned(true);
    }

    private void sendSpellCastPacket(EntityMaid maid, MaidWizardrySpellData data, SpellModifiers modifiers) {
        Spell spell = data.getCurrentSpell();
        if (!(maid.level() instanceof ServerLevel) || spell == null || !spell.requiresPacket()) {
            return;
        }

        LivingEntity target = data.getTarget();
        int targetId = target == null ? -1 : target.getId();
        NPCSpellCastS2C msg = new NPCSpellCastS2C(maid.getId(), targetId, InteractionHand.MAIN_HAND, spell, modifiers);
        Services.NETWORK_HELPER.sendToTracking(maid, msg);
    }

    private void finishCasting(EntityMaid maid, MaidWizardrySpellData data, boolean callEndCast) {
        Spell spell = data.getCurrentSpell();
        if (spell == null) {
            data.resetCastingState();
            return;
        }

        EntityCastContext ctx = null;
        if (callEndCast && maid.level() instanceof ServerLevel) {
            ctx = createContext(maid, data);
            spell.endCast(ctx);
        }

        if (!spell.isInstantCast() && maid.level() instanceof ServerLevel) {
            SpellModifiers modifiers = ctx == null ? createModifiers() : ctx.modifiers();
            WizardryEventBus.getInstance().fire(new SpellCastEvent.Finish(
                    SpellCastEvent.Source.NPC, spell, maid, modifiers,
                    Math.max(0, data.getCastingTime() - data.getChargeupTime())));
        }

        setCooldown(maid, spell);
        resetCastingState(maid, data);
    }

    /**
     * 设置法术冷却
     */
    private void setCooldown(EntityMaid maid, Spell spell) {
        MaidWizardrySpellData data = getData(maid);
        if (data != null && spell != null) {
            SpellModifiers modifiers = createModifiers();
            String spellId = spell.getLocation().toString();
            int cooldown = Math.max(1, CastItemUtils.calcCastCooldown(spell, modifiers));
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


