package com.github.yimeng261.maidspell.spell.data;

import com.hollingsworth.arsnouveau.api.spell.ISpellCaster;
import com.hollingsworth.arsnouveau.api.spell.Spell;
import com.github.tartaricacid.touhoulittlemaid.entity.passive.EntityMaid;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.item.ItemStack;

import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.Map;

/**
 * 女仆新生魔艺数据存储类
 * 集中管理每个女仆的新生魔艺相关状态和数据
 */
public class MaidArsNouveauSpellData {
    
    // 全局数据存储，按女仆UUID映射
    private static final Map<UUID, MaidArsNouveauSpellData> MAID_DATA_MAP = new ConcurrentHashMap<>();
    
    // === 基本状态 ===
    private LivingEntity target;
    private ItemStack spellBook = ItemStack.EMPTY;
    
    // === 施法状态 ===
    private boolean isCasting = false;
    private int castingTicks = 0;
    private Spell currentSpell = null;
    private ISpellCaster currentCaster = null;
    
    // === 施法参数 ===
    private static final int CASTING_DURATION = 10; // 新生魔艺法术施法时间（tick）
    
    // === 构造函数 ===
    private MaidArsNouveauSpellData() {
        // 私有构造函数，通过静态方法获取实例
    }
    
    // === 静态工厂方法 ===
    
    /**
     * 获取指定女仆的法术数据，如果不存在则创建新的
     */
    public static MaidArsNouveauSpellData getOrCreate(UUID maidUuid) {
        return MAID_DATA_MAP.computeIfAbsent(maidUuid, k -> new MaidArsNouveauSpellData());
    }

    public static MaidArsNouveauSpellData getOrCreate(EntityMaid maidEntity) {
        UUID maidUuid = maidEntity.getUUID();
        return MAID_DATA_MAP.computeIfAbsent(maidUuid, k -> new MaidArsNouveauSpellData());
    }
    
    /**
     * 获取指定女仆的法术数据，如果不存在返回null
     */
    public static MaidArsNouveauSpellData get(UUID maidUuid) {
        return MAID_DATA_MAP.get(maidUuid);
    }
    
    /**
     * 移除指定女仆的法术数据（当女仆被删除时调用）
     */
    public static void remove(UUID maidUuid) {
        MAID_DATA_MAP.remove(maidUuid);
    }
    
    /**
     * 清理所有数据（用于服务器关闭等场景）
     */
    public static void clearAll() {
        MAID_DATA_MAP.clear();
    }
    
    /**
     * 获取当前存储的女仆数据数量
     */
    public static int getDataCount() {
        return MAID_DATA_MAP.size();
    }
    
    // === 基本状态管理 ===
    
    public LivingEntity getTarget() {
        return target;
    }
    
    public void setTarget(LivingEntity target) {
        this.target = target;
    }
    
    public ItemStack getSpellBook() {
        return spellBook;
    }
    
    public void setSpellBook(ItemStack spellBook) {
        this.spellBook = spellBook != null ? spellBook : ItemStack.EMPTY;
    }
    
    // === 施法状态管理 ===
    
    public boolean isCasting() {
        return isCasting;
    }
    
    public void setCasting(boolean casting) {
        this.isCasting = casting;
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
    
    public Spell getCurrentSpell() {
        return currentSpell;
    }
    
    public void setCurrentSpell(Spell spell) {
        this.currentSpell = spell;
    }
    
    public ISpellCaster getCurrentCaster() {
        return currentCaster;
    }
    
    public void setCurrentCaster(ISpellCaster caster) {
        this.currentCaster = caster;
    }
    
    // === 施法参数 ===
    
    public int getCastingDuration() {
        return CASTING_DURATION;
    }
    
    /**
     * 检查施法是否完成
     */
    public boolean isCastingComplete() {
        return castingTicks >= CASTING_DURATION;
    }
    
    /**
     * 重置施法状态
     */
    public void resetCastingState() {
        isCasting = false;
        castingTicks = 0;
        currentSpell = null;
        // 保持currentCaster，因为它与spellBook绑定
    }
    
    /**
     * 清理所有数据（女仆被移除时调用）
     */
    public void cleanup() {
        resetCastingState();
        target = null;
        spellBook = ItemStack.EMPTY;
        currentCaster = null;
    }
} 