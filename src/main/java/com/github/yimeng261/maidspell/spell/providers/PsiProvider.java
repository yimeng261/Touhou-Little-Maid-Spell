package com.github.yimeng261.maidspell.spell.providers;

import com.github.tartaricacid.touhoulittlemaid.entity.passive.EntityMaid;
import com.github.yimeng261.maidspell.api.ISpellBookProvider;
import com.github.yimeng261.maidspell.spell.data.MaidPsiSpellData;

import net.minecraft.nbt.CompoundTag;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraftforge.common.util.FakePlayer;
import net.minecraftforge.common.util.FakePlayerFactory;

import vazkii.psi.api.PsiAPI;
import vazkii.psi.api.cad.ICAD;
import vazkii.psi.api.cad.ISocketable;
import vazkii.psi.api.internal.IPlayerData;
import vazkii.psi.api.spell.ISpellAcceptor;
import vazkii.psi.api.spell.Spell;
import vazkii.psi.api.spell.SpellContext;
import vazkii.psi.api.spell.SpellPiece;
import vazkii.psi.api.spell.EnumSpellStat;
import vazkii.psi.common.core.handler.PlayerDataHandler;
import vazkii.psi.common.item.ItemCAD;

import org.slf4j.Logger;
import com.mojang.logging.LogUtils;

import javax.annotation.Nullable;
import java.util.ArrayList;
import java.util.List;

/**
 * Psi模组的法术提供者
 * 使女仆能够使用Psi的CAD和法术弹进行施法
 */
public class PsiProvider implements ISpellBookProvider {
    private static final Logger LOGGER = LogUtils.getLogger();

    public PsiProvider() {
    }

    /**
     * 获取指定女仆的Psi法术数据
     */
    private MaidPsiSpellData getData(EntityMaid maid) {
        if (maid == null) {
            return null;
        }
        return MaidPsiSpellData.getOrCreate(maid.getUUID());
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
        if (itemStack.getItem() instanceof ICAD) {
            return true;
        }
        
        return false;
    }

    /**
     * 设置目标
     */
    @Override
    public void setTarget(EntityMaid maid, LivingEntity target) {
        MaidPsiSpellData data = getData(maid);
        if (data != null) {
            data.setTarget(target);
        }
    }

    /**
     * 获取目标
     */
    @Override
    public LivingEntity getTarget(EntityMaid maid) {
        MaidPsiSpellData data = getData(maid);
        return data != null ? data.getTarget() : null;
    }

    /**
     * 设置CAD
     */
    @Override
    public void setSpellBook(EntityMaid maid, ItemStack cad) {
        MaidPsiSpellData data = getData(maid);
        if (data != null) {
            data.setCAD(cad);
        }
    }

    /**
     * 检查是否正在施法
     */
    @Override
    public boolean isCasting(EntityMaid maid) {
        MaidPsiSpellData data = getData(maid);
        return data != null && data.isCasting();
    }

    /**
     * 开始施法
     */
    @Override
    public boolean initiateCasting(EntityMaid maid) {
        MaidPsiSpellData data = getData(maid);
        if (data == null) {
            return false;
        }

        ItemStack cad = data.getCAD();
        if (cad.isEmpty() || !(cad.getItem() instanceof ICAD)) {
            return false;
        }

        // 获取CAD的可插拔组件
        ISocketable sockets = cad.getCapability(PsiAPI.SOCKETABLE_CAPABILITY).orElse(null);
        if (sockets == null) {
            return false;
        }

        // 从弹夹中随机选择一个有效的法术弹
        ItemStack bullet = getRandomBulletFromMagazine(sockets, maid);
        if (bullet.isEmpty() || !ISpellAcceptor.hasSpell(bullet)) {
            return false;
        }

        // 尝试施放法术
        return attemptCastSpell(maid, cad, bullet);
    }

    /**
     * 从CAD的弹夹中随机选择一个有效的法术弹
     */
    private ItemStack getRandomBulletFromMagazine(ISocketable sockets, EntityMaid maid) {
        List<ItemStack> validBullets = new ArrayList<>();
        
        // 遍历所有弹夹槽位，收集有效的法术弹
        // 从0开始遍历到最后一个槽位
        for (int i = 0; i <= sockets.getLastSlot(); i++) {
            if (sockets.isSocketSlotAvailable(i)) {
                ItemStack bullet = sockets.getBulletInSocket(i);
                if (!bullet.isEmpty() && ISpellAcceptor.hasSpell(bullet)) {
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
        ItemStack selectedBullet = validBullets.get(randomIndex);
        
        return selectedBullet;
    }

    /**
     * 尝试施放法术
     */
    private boolean attemptCastSpell(EntityMaid maid, ItemStack cad, ItemStack bullet) {
        try {
            // 获取法术
            ISpellAcceptor spellContainer = ISpellAcceptor.acceptor(bullet);
            Spell spell = spellContainer.getSpell();
            if (spell == null) {
                return false;
            }

            // 获取法术的唯一标识符
            String spellId = getSpellId(spell);
            
            // 检查法术是否在冷却中
            MaidPsiSpellData data = getData(maid);
            if (data.isSpellOnCooldown(spellId)) {  
                return false;
            }

            // 创建法术上下文
            SpellContext context = createSpellContext(maid, spell);
            if (context == null || !context.isValid()) {
                return false;
            }

            // 为虚假玩家设置虚拟 PlayerData，确保不消耗 PSI
            if (context.caster != null) {
                setupVirtualPlayerData(context.caster);
            }

            // 检查法术是否可以施放
            if (!context.cspell.metadata.evaluateAgainst(cad)) {
                return false;
            }

            // 计算法术消耗和冷却时间
            int cost = ItemCAD.getRealCost(cad, bullet, context.cspell.metadata.getStat(EnumSpellStat.COST));
            int cooldownTicks = calculateCooldownFromCost(cost);

            // 执行法术（不消耗PSI）
            context.cspell.safeExecute(context);
            
            // 设置施法状态
            data.setCasting(true);
            data.setCurrentSpell(spell);
            data.setCastingTicks(20); // 1秒的施法时间
            
            // 设置法术冷却时间
            data.setSpellCooldown(spellId, cooldownTicks);

            return true;

        } catch (Exception e) {
            return false;
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
        if (spell == null || spell.name == null) {
            return "unknown_spell";
        }
        return spell.name;
    }

    /**
     * 创建法术上下文
     */
    private SpellContext createSpellContext(EntityMaid maid, Spell spell) {
        try {
            SpellContext context = new SpellContext();
            
            // 设置施法者（需要一个玩家对象，使用女仆的主人或创建虚拟玩家）
            Player caster = getValidCaster(maid);
            if (caster == null) {
                return null;
            }
            
            context.caster = caster;
            
            // 设置施法焦点（使用女仆作为施法焦点）
            context.setFocalPoint(maid);
            
            // 设置法术
            context.setSpell(spell);
            
            // 设置手部（默认主手）
            context.castFrom = InteractionHand.MAIN_HAND;

            return context;

        } catch (Exception e) {
            return null;
        }
    }

    /**
     * 获取有效的施法者
     * 创建一个虚假玩家，确保不会消耗真实玩家的 PSI
     */
    private Player getValidCaster(EntityMaid maid) {
        // 创建虚假玩家，不会消耗真实玩家的资源
        if (maid.level() instanceof ServerLevel serverLevel) {
            try {
                // 创建一个专门用于女仆施法的虚假玩家
                FakePlayer fakePlayer = FakePlayerFactory.get(serverLevel, 
                    new com.mojang.authlib.GameProfile(
                        java.util.UUID.randomUUID(), 
                        "MaidCaster_" + maid.getUUID().toString().substring(0, 8)
                    )
                );
                
                // 设置虚假玩家的位置为女仆的位置
                fakePlayer.setPos(maid.getX(), maid.getY(), maid.getZ());
                fakePlayer.setYRot(maid.getYRot());
                fakePlayer.setXRot(maid.getXRot());
                
                // 确保虚假玩家有无限的 PSI（通过我们的虚拟 PlayerData）
                return fakePlayer;
                
            } catch (Exception e) {
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

        // 减少施法时间
        int remainingTicks = data.getCastingTicks() - 1;
        data.setCastingTicks(remainingTicks);

        // 如果施法完成，重置状态
        if (remainingTicks <= 0) {
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
            data.setCasting(false);
            data.setCurrentSpell(null);
            data.setCastingTicks(0);
        }
    }

    /**
     * 执行法术
     */
    @Override
    public boolean castSpell(EntityMaid maid) {
        return initiateCasting(maid);
    }

    /**
     * 更新冷却时间
     */
    @Override
    public void updateCooldown(EntityMaid maid) {
        MaidPsiSpellData data = getData(maid);
        if (data != null) {
            data.updateCooldowns();
        }
    }

    /**
     * 虚拟玩家数据类，用于女仆施法
     * 提供无限PSI但不实际消耗的功能
     */
    private static class VirtualPlayerData implements IPlayerData {
        
        @Override
        public int getTotalPsi() {
            return Integer.MAX_VALUE; // 无限PSI容量
        }

        @Override
        public int getAvailablePsi() {
            return Integer.MAX_VALUE; // 无限可用PSI
        }

        @Override
        public int getLastAvailablePsi() {
            return Integer.MAX_VALUE;
        }

        @Override
        public int getRegenCooldown() {
            return 0;
        }

        @Override
        public int getRegenPerTick() {
            return 0;
        }

        @Override
        public boolean isOverflowed() {
            return false;
        }

        @Override
        public void deductPsi(int psi, int cd, boolean sync, boolean shatter) {
            // 不消耗PSI，什么都不做
        }

        @Override
        public boolean isPieceGroupUnlocked(ResourceLocation group, @Nullable ResourceLocation piece) {
            return true; // 所有法术组件都解锁
        }

        @Override
        public void unlockPieceGroup(ResourceLocation group) {
            // 不需要解锁
        }

        @Override
        public void markPieceExecuted(SpellPiece piece) {
            // 不记录执行
        }

        @Override
        public CompoundTag getCustomData() {
            return new CompoundTag();
        }

        @Override
        public void save() {
            // 不需要保存
        }
    }
} 