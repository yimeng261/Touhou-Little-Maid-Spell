package com.github.yimeng261.maidspell.spell.providers;

import com.github.tartaricacid.touhoulittlemaid.entity.passive.EntityMaid;
import com.github.yimeng261.maidspell.api.ISpellBookProvider;
import com.github.yimeng261.maidspell.spell.data.MaidArsNouveauSpellData;

import com.hollingsworth.arsnouveau.common.items.SpellBook;
import com.hollingsworth.arsnouveau.api.util.CasterUtil;
import com.hollingsworth.arsnouveau.api.spell.ISpellCaster;
import com.hollingsworth.arsnouveau.api.spell.Spell;
import com.hollingsworth.arsnouveau.api.spell.SpellResolver;
import com.hollingsworth.arsnouveau.api.spell.SpellContext;
import com.hollingsworth.arsnouveau.api.spell.wrapped_caster.LivingCaster;
import com.hollingsworth.arsnouveau.api.util.SpellUtil;
import com.hollingsworth.arsnouveau.api.mana.IManaCap;
import com.hollingsworth.arsnouveau.setup.registry.CapabilityRegistry;
import com.hollingsworth.arsnouveau.common.spell.augment.AugmentSensitive;
import com.hollingsworth.arsnouveau.common.spell.method.MethodProjectile;
import com.hollingsworth.arsnouveau.common.entity.EntityProjectileSpell;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.ai.behavior.BehaviorUtils;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.phys.HitResult;
import net.minecraft.world.phys.EntityHitResult;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.Vec3;
import net.minecraft.sounds.SoundSource;

import org.slf4j.Logger;
import com.mojang.logging.LogUtils;

import java.util.List;
import java.util.ArrayList;

/**
 * 新生魔艺模组的法术书提供者
 * 通过 MaidArsNouveauSpellData 管理各女仆的数据
 */
public class ArsNouveauProvider extends ISpellBookProvider<MaidArsNouveauSpellData, Spell> {
    @SuppressWarnings("unused")
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
        
        // 获取法术书的施法者接口
        ISpellCaster caster = CasterUtil.getCaster(spellBook);
        if (caster == null) {
            return spells;
        }
        
        // 遍历法术书的所有槽位，收集有效法术
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
        return itemStack.getItem() instanceof SpellBook;
    }

    
    /**
     * 开始施法
     */
    @Override
    public void initiateCasting(EntityMaid maid) {
        MaidArsNouveauSpellData data = getData(maid);

        // 确保女仆有魔力能力
        ensureManaCapability(maid);

        // 选择一个法术进行施放
        Spell spell = selectRandomSpell(maid);
        if (spell == null) {
            return;
        }
        
        // 确保女仆面向目标
        LivingEntity target = data.getTarget();
        if (target != null) {
            BehaviorUtils.lookAtEntity(maid, target);
        }
        
        // 开始施法
        data.setCasting(true);
        data.setCastingTicks(0);
        data.setCurrentSpell(spell);
        data.setSpellCooldown(spell.name,spell.getCost()/2,maid);
        
        // 播放手臂挥舞动画
        maid.swing(InteractionHand.MAIN_HAND);

    }
    
    /**
     * 处理持续施法
     */
    @Override
    public void processContinuousCasting(EntityMaid maid) {
        MaidArsNouveauSpellData data = getData(maid);
        if (data == null || !data.isCasting() || data.getCurrentSpell() == null) {
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
    private void ensureManaCapability(EntityMaid maid) {
        if (maid != null) {
            var manaOpt = CapabilityRegistry.getMana(maid);
            if (manaOpt.isPresent()) {
                IManaCap manaCap = manaOpt.orElse(null);
                // 设置女仆有足够的魔力
                if (manaCap.getCurrentMana() < 1000) {
                    manaCap.setMana(1000.0); // 给女仆1000点魔力
                }
            }
        }
    }

    
    /**
     * 获取可用的法术列表（未在冷却中的法术）
     * @param maid 女仆实体
     * @return 可用的法术列表
     */
    private List<Spell> getAvailableSpells(EntityMaid maid) {
        MaidArsNouveauSpellData data = getData(maid);
        List<Spell> allSpells = collectSpellFromAvailableSpellBooks(maid);
        List<Spell> availableSpells = new ArrayList<>();
        
        // 过滤掉冷却中的法术
        for (Spell spell : allSpells) {
            if (!data.isSpellOnCooldown(spell.name)) {
                availableSpells.add(spell);
            }
        }
        
        return availableSpells;
    }
    
    /**
     * 随机选择一个可用的法术
     * @param maid 女仆实体
     * @return 随机选择的法术，如果没有可用法术则返回null
     */
    private Spell selectRandomSpell(EntityMaid maid) {
        List<Spell> availableSpells = getAvailableSpells(maid);
        if (availableSpells.isEmpty()) {
            return null;
        }
        
        int randomIndex = (int) (Math.random() * availableSpells.size());
        return availableSpells.get(randomIndex);
    }
    
    /**
     * 完成施法
     */
    private void completeCasting(EntityMaid maid) {
        MaidArsNouveauSpellData data = getData(maid);
        try {
            
            // 确保女仆有足够的魔力
            ensureManaCapability(maid);
            
            // 创建自定义的法术上下文和解析器
            LivingCaster wrappedCaster = new LivingCaster(maid);
            SpellContext context = new SpellContext(maid.level(), data.getCurrentSpell(), maid, wrappedCaster, data.getSpellBook());
            
            // 创建一个跳过魔力检查的解析器
            SpellResolver resolver = new SpellResolver(context) {
                @Override
                protected boolean enoughMana(LivingEntity entity) {
                    // 女仆总是有足够的魔力
                    return true;
                }
                
                @Override
                public void expendMana() {
                    // 女仆不消耗魔力，或者消耗很少
                    // 为了保持系统一致性，我们仍然可以设置一个最小消耗
                }
            };
            
            boolean castSuccess;
            LivingEntity target = data.getTarget();
            
            if (target != null) {
                // 如果有指定目标，手动创建和发射弹射物
                Vec3 targetPos = target.getEyePosition();
                Vec3 maidPos = maid.getEyePosition();
                Vec3 direction = targetPos.subtract(maidPos).normalize();
                
                // 计算正确的朝向角度
                float yaw = (float) (Math.atan2(direction.x, direction.z) * 180.0 / Math.PI);
                float pitch = (float) (Math.asin(-direction.y) * 180.0 / Math.PI);
                
                // 立即设置女仆的朝向
                maid.setYRot(yaw);
                maid.setXRot(pitch);
                maid.yRotO = yaw;
                maid.xRotO = pitch;
                
                // 如果是弹射物法术，直接创建弹射物实体
                if (data.getCurrentSpell().getCastMethod() instanceof MethodProjectile) {
                    try {
                        // 手动创建弹射物
                        EntityProjectileSpell projectile = new EntityProjectileSpell(maid.level(), resolver);
                        projectile.setOwner(maid);
                        
                        // 设置弹射物位置为女仆眼部位置
                        projectile.setPos(maidPos.x, maidPos.y - 0.1, maidPos.z);
                        
                        // 计算速度
                        float velocity = Math.max(0.1f, 0.75f + resolver.getCastStats().getAccMultiplier() / 2);
                        
                        // 直接使用计算好的方向向量发射弹射物
                        projectile.shoot(direction.x, direction.y, direction.z, velocity, 0.0f);
                        
                        // 添加到世界
                        maid.level().addFreshEntity(projectile);
                        
                        castSuccess = true;
                    } catch (Exception e) {
                        // 回退到标准方法
                        castSuccess = resolver.onCastOnEntity(data.getSpellBook(), target, InteractionHand.MAIN_HAND);
                    }
                } else {
                    // 非弹射物法术使用标准方法
                    resolver.hitResult = new EntityHitResult(target, targetPos);
                    castSuccess = resolver.onCastOnEntity(data.getSpellBook(), target, InteractionHand.MAIN_HAND);
                }
                
            } else {
                // 没有指定目标，使用射线追踪
                boolean isSensitive = data.getCurrentSpell().getBuffsAtIndex(0, maid, AugmentSensitive.INSTANCE) > 0;
                HitResult result = SpellUtil.rayTrace(maid, 0.5 + 5.0, 0, isSensitive);
                
                if (result instanceof EntityHitResult entityHitResult && entityHitResult.getEntity() instanceof LivingEntity) {
                    // 射线追踪到实体
                    castSuccess = resolver.onCastOnEntity(data.getSpellBook(), entityHitResult.getEntity(), InteractionHand.MAIN_HAND);
                } else if (result instanceof BlockHitResult blockHitResult) {
                    // 射线追踪到方块
                    castSuccess = resolver.onCastOnBlock(blockHitResult);
                } else {
                    // 没有特定目标，直接施法
                    castSuccess = resolver.onCast(data.getSpellBook(), maid.level());
                }
            }
            
            if (castSuccess) {
                // 播放音效
                if (data.getCurrentSpell().sound != null && data.getCurrentSpell().sound.sound != null) {
                    maid.level().playSound(null, 
                        maid.getX(), maid.getY(), maid.getZ(),
                        data.getCurrentSpell().sound.sound.getSoundEvent(),
                        SoundSource.NEUTRAL,
                        data.getCurrentSpell().sound.volume,
                        data.getCurrentSpell().sound.pitch);
                }
            }   
        } catch (Exception ignored) {
        } finally {
            // 重置施法状态
            data.resetCastingState();
        }
    }
} 