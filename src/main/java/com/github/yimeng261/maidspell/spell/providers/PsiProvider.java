package com.github.yimeng261.maidspell.spell.providers;

import com.github.tartaricacid.touhoulittlemaid.entity.passive.EntityMaid;
import com.github.yimeng261.maidspell.api.ISpellBookProvider;
import com.github.yimeng261.maidspell.item.bauble.springBloomReturn.SpringBloomReturnBauble;
import com.github.yimeng261.maidspell.spell.data.MaidPsiSpellData;
import com.mojang.authlib.GameProfile;
import com.mojang.logging.LogUtils;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraftforge.common.util.FakePlayer;
import net.minecraftforge.common.util.FakePlayerFactory;
import org.slf4j.Logger;
import vazkii.psi.api.PsiAPI;
import vazkii.psi.api.cad.ICAD;
import vazkii.psi.api.cad.ISocketable;
import vazkii.psi.api.spell.EnumSpellStat;
import vazkii.psi.api.spell.ISpellAcceptor;
import vazkii.psi.api.spell.Spell;
import vazkii.psi.api.spell.SpellContext;
import vazkii.psi.common.core.handler.PlayerDataHandler;
import vazkii.psi.common.item.ItemCAD;

import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Psi mod spell provider. Lets maids cast spells from Psi CAD items.
 */
public class PsiProvider extends ISpellBookProvider<MaidPsiSpellData, Spell> {
    private static final Logger LOGGER = LogUtils.getLogger();
    private static final String PSI_LOG_PREFIX = "[MaidSpell/Psi]";
    public static final String FAKE_PLAYER_UUID_NAMESPACE = "touhou_little_maid_spell:psi_caster:";
    private static final int FAKE_PLAYER_CAD_SLOT = 0;
    private static final int LOOPCAST_TICKS = 60;
    private static final Map<UUID, CopiedCadContext> ACTIVE_LOOPCAST_CONTEXTS = new ConcurrentHashMap<>();


    public static UUID getMaidUuidFromCaster(Player caster) {
        String name = caster.getGameProfile().getName();
        if (name == null || !name.startsWith("MaidCaster_")) {
            return null;
        }
        String uuid = caster.getPersistentData().getString("MaidSpellPsiCasterMaidUuid");
        if (uuid.isEmpty()) {
            return null;
        }
        try {
            return UUID.fromString(uuid);
        } catch (IllegalArgumentException e) {
            LOGGER.warn("{} abort stage=fake_caster_lookup caster={} reason=bad_maid_uuid value={}", PSI_LOG_PREFIX,
                    caster.getUUID(), uuid);
            return null;
        }
    }

    /**
     * 构造函数，绑定 MaidPsiSpellData 数据类型和 Spell 法术类型
     */
    public PsiProvider() {
        super(MaidPsiSpellData::getOrCreate, MaidPsiSpellData::get,
                MaidPsiSpellData::remove, MaidPsiSpellData::clearAll, Spell.class);
    }

    @Override
    protected void releaseProviderRuntimeReferences(EntityMaid maid, MaidPsiSpellData data) {
        cleanupLoopcast(maid.getUUID(), "maid_release");
    }

    @Override
    public void clearAllData() {
        try {
            clearAllLoopcastContexts("provider_clear");
        } finally {
            super.clearAllData();
        }
    }

    /**
     * 从单个CAD中收集所有法术
     * @param spellBook CAD物品堆栈
     * @return 该CAD中的所有法术列表
     */
    @Override
    protected List<Spell> collectSpellFromSingleSpellBook(ItemStack spellBook, EntityMaid maid) {
        List<Spell> spells = new ArrayList<>();

        if (spellBook == null || spellBook.isEmpty() || !isSpellBook(spellBook)) {
            return spells;
        }

        // 获取CAD的可插拔组件
        ISocketable sockets = spellBook.getCapability(PsiAPI.SOCKETABLE_CAPABILITY).orElse(null);
        if (sockets == null) {
            return spells;
        }

        // 遍历所有弹夹槽位，收集有效的法术
        for (int i = 0; i <= sockets.getLastSlot(); i++) {
            if (sockets.isSocketSlotAvailable(i)) {
                ItemStack bullet = sockets.getBulletInSocket(i);
                if (!bullet.isEmpty() && ISpellAcceptor.hasSpell(bullet)) {
                    ISpellAcceptor spellContainer = ISpellAcceptor.acceptor(bullet);
                    Spell spell = spellContainer.getSpell();
                    if (spell != null) {
                        spells.add(spell);
                    }
                }
            }
        }

        return spells;
    }

    /**
     * 检查物品是否为Psi的CAD
     */
    @Override
    public boolean isSpellBook(ItemStack itemStack) {
        if (itemStack == null || itemStack.isEmpty()) {
            return false;
        }

        // 检查是否为CAD
        return itemStack.getItem() instanceof ICAD;
    }

    /**
     * 开始施法
     */
    @Override
    public void initiateCasting(EntityMaid maid) {
        MaidPsiSpellData data = getData(maid);
        if (data == null) {
            return;
        }

        ItemStack cad = data.getSpellBook();
        if (!isSpellBook(cad)) {
            return;
        }

        // 获取CAD的可插拔组件
        ISocketable sockets = cad.getCapability(PsiAPI.SOCKETABLE_CAPABILITY).orElse(null);
        if (sockets == null) {
            LOGGER.warn("{} abort stage=socket maid={} reason=no_socket_cap cad={}", PSI_LOG_PREFIX, maid.getUUID(), describeStack(cad));
            return;
        }

        // 从弹夹中随机选择一个有效的法术弹
        ItemStack bullet = getRandomBulletFromMagazine(sockets, maid);
        if (bullet.isEmpty() || !ISpellAcceptor.hasSpell(bullet)) {
            return;
        }

        // 尝试施放法术
        attemptCastSpell(maid, cad, bullet);
    }

    /**
     * 从CAD的弹夹中随机选择一个有效的法术弹
     */
    private ItemStack getRandomBulletFromMagazine(ISocketable sockets, EntityMaid maid) {
        if (sockets == null) {
            return ItemStack.EMPTY;
        }

        List<ItemStack> validBullets = new ArrayList<>();

        // 遍历所有弹夹槽位，收集有效的法术弹
        // 从0开始遍历到最后一个槽位
        for (int i = 0; i <= sockets.getLastSlot(); i++) {
            if (sockets.isSocketSlotAvailable(i)) {
                ItemStack bullet = sockets.getBulletInSocket(i);
                boolean hasSpell = !bullet.isEmpty() && ISpellAcceptor.hasSpell(bullet);
                if (hasSpell) {
                    validBullets.add(bullet);
                }
            }
        }

        // 如果没有有效的法术弹，返回空物品栈
        if (validBullets.isEmpty()) {
            return ItemStack.EMPTY;
        }

        // 随机选择一个法术弹
        int randomIndex = maid.getRandom().nextInt(validBullets.size());
        return validBullets.get(randomIndex);
    }

    /**
     * 尝试施放法术
     */
    private boolean attemptCastSpell(EntityMaid maid, ItemStack cad, ItemStack bullet) {
        MaidPsiSpellData data = getData(maid);
        if (data == null) {
            return false;
        }

        ISpellAcceptor spellContainer;
        Spell spell;
        try {
            spellContainer = ISpellAcceptor.acceptor(bullet);
            spell = spellContainer.getSpell();
        } catch (Exception e) {
            LOGGER.warn("{} abort stage=acceptor maid={} reason=read_failed bullet={}", PSI_LOG_PREFIX,
                    maid.getUUID(), describeStack(bullet), e);
            return false;
        }

        if (spell == null) {
            return false;
        }

        String spellId = getSpellId(spell);
        if (data.isSpellOnCooldown(spellId)) {
            return false;
        }

        FakePlayer caster = getValidCaster(maid);
        if (caster == null) {
            LOGGER.warn("{} abort stage=fake_caster maid={} reason=null_caster", PSI_LOG_PREFIX, maid.getUUID());
            return false;
        }

        CopiedCadContext cadContext = copyCadToCaster(caster, cad);
        boolean keepCadForLoopcast = false;
        try {
            SpellContext context = createSpellContext(maid, caster, spell);
            if (context == null) {
                return false;
            }
            if (!context.isValid()) {
                LOGGER.warn("{} abort stage=context maid={} spell={} reason=invalid_context cspell=null", PSI_LOG_PREFIX,
                        maid.getUUID(), spellId);
                return false;
            }
            setupVirtualPlayerData(context.caster);

            boolean metadataPassed = context.cspell.metadata.evaluateAgainst(cad);
            if (!metadataPassed) {
                return false;
            }

            int cost = ItemCAD.getRealCost(cad, bullet, context.cspell.metadata.getStat(EnumSpellStat.COST));
            int cooldownTicks = calculateCooldownFromCost(cost);

            // Use the bullet acceptor so projectile/circle/mine bullets keep their native Psi behavior.
            ArrayList<Entity> spellEntities = spellContainer.castSpell(context);

            boolean success = isSpellCastSuccessful(spellContainer, spellEntities, context);
            if (!success) {
                return false;
            }

            boolean loopcast = isLoopcastBullet(spellContainer, bullet);
            data.setCasting(true);
            data.setCurrentSpell(spell);
            data.setCastingTicks(loopcast ? LOOPCAST_TICKS : 20);
            if (loopcast) {
                keepCadForLoopcast = true;
                replaceLoopcastContext(maid, cadContext);
            }
            data.setSpellCooldown(spellId, cooldownTicks, maid);
            SpringBloomReturnBauble.onSpellCast(maid, "psi", spellId, data.getTarget());

            return true;
        } catch (Exception e) {
            LOGGER.warn("{} abort stage=exception maid={} spell={} cad={} bullet={}", PSI_LOG_PREFIX,
                    maid.getUUID(), spellId, describeStack(cad), describeStack(bullet), e);
            return false;
        } finally {
            if (!keepCadForLoopcast) {
                cadContext.close();
            }
        }
    }

    /**
     * 根据法术消耗计算冷却时间
     * 消耗越高，冷却时间越长
     */
    private int calculateCooldownFromCost(int cost) {
        return (cost / 300) * 20;
    }

    /**
     * 获取法术的唯一标识符
     */
    private String getSpellId(Spell spell) {
        if (spell == null) {
            return "unknown_spell";
        }
        if (spell.uuid != null) {
            return spell.uuid.toString();
        }
        if (spell.name != null && !spell.name.isEmpty()) {
            return spell.name;
        }
        return "unknown_spell";
    }

    /**
     * 创建法术上下文
     */
    private SpellContext createSpellContext(EntityMaid maid, Player caster, Spell spell) {
        try {
            SpellContext context = new SpellContext();
            context.caster = caster;
            context.setFocalPoint(maid);
            context.setSpell(spell);
            context.castFrom = InteractionHand.MAIN_HAND;
            return context;
        } catch (Exception e) {
            LOGGER.warn("{} abort stage=context_create maid={} reason=exception", PSI_LOG_PREFIX, maid.getUUID(), e);
            return null;
        }
    }

    /**
     * 获取有效的施法者
     * 创建一个虚假玩家，确保不会消耗真实玩家的 PSI
     */
    private FakePlayer getValidCaster(EntityMaid maid) {
        // 创建虚假玩家，不会消耗真实玩家的资源
        if (maid.level() instanceof ServerLevel serverLevel) {
            try {
                GameProfile profile = createMaidCasterProfile(maid);
                FakePlayer fakePlayer = FakePlayerFactory.get(serverLevel, profile);

                // 设置虚假玩家的位置为女仆的位置
                fakePlayer.setPos(maid.getX(), maid.getY(), maid.getZ());
                fakePlayer.setYRot(maid.getYRot());
                fakePlayer.setXRot(maid.getXRot());
                fakePlayer.setDeltaMovement(maid.getDeltaMovement());
                fakePlayer.getPersistentData().putString("MaidSpellPsiCasterMaidUuid", maid.getUUID().toString());
                return fakePlayer;
            } catch (Exception e) {
                LOGGER.warn("{} abort stage=fake_caster_create maid={} reason=exception", PSI_LOG_PREFIX, maid.getUUID(), e);
            }
        }

        return null;
    }

    /**
     * 为虚假玩家设置虚拟 PlayerData，确保不消耗 PSI
     */
    private void setupVirtualPlayerData(Player fakePlayer) {
        try {
            // 获取或创建虚拟 PlayerData
            PlayerDataHandler.PlayerData playerData = PlayerDataHandler.get(fakePlayer);

            // 设置无限 PSI
            playerData.availablePsi = Integer.MAX_VALUE;
            playerData.overflowed = false;
            playerData.regenCooldown = 0;
        } catch (Exception e) {
            LOGGER.warn("{} abort stage=player_data caster={} reason=exception", PSI_LOG_PREFIX, fakePlayer.getUUID(), e);
        }
    }

    private GameProfile createMaidCasterProfile(EntityMaid maid) {
        UUID uuid = UUID.nameUUIDFromBytes((FAKE_PLAYER_UUID_NAMESPACE + maid.getUUID()).getBytes(StandardCharsets.UTF_8));
        return new GameProfile(uuid, "MaidCaster_" + maid.getUUID().toString().substring(0, 8));
    }

    private CopiedCadContext copyCadToCaster(FakePlayer caster, ItemStack cad) {
        Inventory inventory = caster.getInventory();
        ItemStack previousStack = inventory.getItem(FAKE_PLAYER_CAD_SLOT).copy();
        inventory.setItem(FAKE_PLAYER_CAD_SLOT, cad.copy());
        inventory.selected = FAKE_PLAYER_CAD_SLOT;
        return new CopiedCadContext(caster, inventory, previousStack);
    }

    private String describeStack(ItemStack stack) {
        if (stack == null) {
            return "null";
        }
        if (stack.isEmpty()) {
            return "empty";
        }
        return stack.getItem().builtInRegistryHolder().key().location() + "x" + stack.getCount();
    }

    private String describeSpell(Spell spell) {
        if (spell == null) {
            return "null";
        }
        return "name=" + spell.name + ",uuid=" + spell.uuid;
    }

    private boolean isSpellCastSuccessful(ISpellAcceptor spellContainer, ArrayList<Entity> spellEntities, SpellContext context) {
        if (spellEntities != null && !spellEntities.isEmpty()) {
            return true;
        }
        if (isLoopcastAcceptor(spellContainer)) {
            return PlayerDataHandler.get(context.caster).loopcasting;
        }
        if (spellContainer.isCADOnlyContainer()) {
            return false;
        }
        return context.actions != null && context.actions.isEmpty() && !context.stopped;
    }

    private boolean isLoopcastBullet(ISpellAcceptor spellContainer, ItemStack bullet) {
        return isLoopcastAcceptor(spellContainer) || describeStack(bullet).contains("spell_bullet_loop");
    }

    private boolean isLoopcastAcceptor(ISpellAcceptor spellContainer) {
        return spellContainer.isCADOnlyContainer()
                && spellContainer.getClass().getName().contains("ItemSpellBullet$SpellAcceptor");
    }

    private void replaceLoopcastContext(EntityMaid maid, CopiedCadContext newContext) {
        CopiedCadContext oldContext;
        synchronized (ACTIVE_LOOPCAST_CONTEXTS) {
            oldContext = ACTIVE_LOOPCAST_CONTEXTS.put(maid.getUUID(), newContext);
        }
        if (oldContext != null && oldContext != newContext) {
            closeLoopcastContext(maid.getUUID(), oldContext, "context_replaced");
        }
    }

    private void cleanupLoopcast(UUID maidId, String reason) {
        CopiedCadContext context;
        synchronized (ACTIVE_LOOPCAST_CONTEXTS) {
            context = ACTIVE_LOOPCAST_CONTEXTS.remove(maidId);
        }
        if (context == null) {
            return;
        }
        closeLoopcastContext(maidId, context, reason);
    }

    private void clearAllLoopcastContexts(String reason) {
        List<Map.Entry<UUID, CopiedCadContext>> contexts;
        synchronized (ACTIVE_LOOPCAST_CONTEXTS) {
            contexts = new ArrayList<>(ACTIVE_LOOPCAST_CONTEXTS.entrySet());
            ACTIVE_LOOPCAST_CONTEXTS.clear();
        }
        contexts.forEach(entry -> closeLoopcastContext(entry.getKey(), entry.getValue(), reason));
    }

    private void closeLoopcastContext(UUID maidId, CopiedCadContext context, String reason) {
        try {
            PlayerDataHandler.PlayerData playerData = PlayerDataHandler.get(context.caster());
            playerData.stopLoopcast();
        } catch (Exception e) {
            LOGGER.warn("{} abort stage=loopcast_stop maid={} reason={}", PSI_LOG_PREFIX, maidId, reason, e);
        } finally {
            try {
                context.close();
            } catch (Exception e) {
                LOGGER.warn("{} abort stage=loopcast_close maid={} reason={}", PSI_LOG_PREFIX, maidId, reason, e);
            }
        }
    }

    /**
     * 处理持续施法
     */
    @Override
    public void processContinuousCasting(EntityMaid maid) {
        MaidPsiSpellData data = getData(maid);
        if (data == null || !data.isCasting()) {
            return;
        }

        CopiedCadContext loopcastContext = ACTIVE_LOOPCAST_CONTEXTS.get(maid.getUUID());
        if (loopcastContext != null) {
            try {
                FakePlayer caster = loopcastContext.caster();
                caster.setPos(maid.getX(), maid.getY(), maid.getZ());
                caster.setYRot(maid.getYRot());
                caster.setXRot(maid.getXRot());
                PlayerDataHandler.PlayerData playerData = PlayerDataHandler.get(caster);
                playerData.availablePsi = Integer.MAX_VALUE;
                playerData.overflowed = false;
                playerData.tick();
                if (!playerData.loopcasting) {
                    cleanupLoopcast(maid.getUUID(), "psi_stopped");
                    data.setCasting(false);
                    data.setCurrentSpell(null);
                    data.setCastingTicks(0);
                    return;
                }
            } catch (Exception e) {
                LOGGER.warn("{} abort stage=loopcast_tick maid={} reason=exception", PSI_LOG_PREFIX, maid.getUUID(), e);
                cleanupLoopcast(maid.getUUID(), "tick_exception");
                data.setCasting(false);
                data.setCurrentSpell(null);
                data.setCastingTicks(0);
                return;
            }
        }

        // 减少施法时间
        int remainingTicks = data.getCastingTicks() - 1;
        data.setCastingTicks(remainingTicks);

        // 如果施法完成，重置状态
        if (remainingTicks <= 0) {
            cleanupLoopcast(maid.getUUID(), "duration_elapsed");
            data.setCasting(false);
            data.setCurrentSpell(null);
            data.setCastingTicks(0);
        }
    }

    /**
     * 停止施法
     */
    @Override
    public void stopCasting(EntityMaid maid) {
        MaidPsiSpellData data = getData(maid);
        if (data != null) {
            cleanupLoopcast(maid.getUUID(), "stop_casting");
            data.setCasting(false);
            data.setCurrentSpell(null);
            data.setCastingTicks(0);
        }
    }

    private record CopiedCadContext(FakePlayer caster, Inventory inventory, ItemStack previousStack) implements AutoCloseable {
        @Override
        public void close() {
            inventory.setItem(FAKE_PLAYER_CAD_SLOT, previousStack);
        }
    }
}
