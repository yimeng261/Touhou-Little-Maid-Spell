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
 * 新生魔艺模组的法术书提供者 - 单例版本
 * 通过 MaidArsNouveauSpellData 管理各女仆的数据
 */
public class ArsNouveauProvider implements ISpellBookProvider {
    private static final Logger LOGGER = LogUtils.getLogger();
    public static final String MOD_ID = "ars_nouveau";

    /**
     * 私有构造函数，防止外部实例化
     */
    public ArsNouveauProvider() {
        // 私有构造函数
    }
    

    /**
     * 获取指定女仆的法术数据
     */
    private MaidArsNouveauSpellData getData(EntityMaid maid) {
        if (maid == null) {
            return null;
        }
        return MaidArsNouveauSpellData.getOrCreate(maid.getUUID());
    }
    
    // === 核心方法（接受EntityMaid参数） ===
    
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
     * 设置目标
     */
    @Override
    public void setTarget(EntityMaid maid, LivingEntity target) {
        MaidArsNouveauSpellData data = getData(maid);
        if (data != null) {
            data.setTarget(target);
        }
    }

    /**
     * 获取目标
     */
    @Override
    public LivingEntity getTarget(EntityMaid maid) {
        MaidArsNouveauSpellData data = getData(maid);
        return data != null ? data.getTarget() : null;
    }

    /**
     * 设置法术书
     */
    @Override
    public void setSpellBook(EntityMaid maid, ItemStack spellBook) {
        MaidArsNouveauSpellData data = getData(maid);
        if (data != null) {
            data.setSpellBook(spellBook);
            // 重置施法器缓存
            if (isSpellBook(spellBook)) {
                ISpellCaster caster = CasterUtil.getCaster(spellBook);
                data.setCurrentCaster(caster);
            } else {
                data.setCurrentCaster(null);
            }
        }
    }
    
    /**
     * 检查是否正在施法
     */
    @Override
    public boolean isCasting(EntityMaid maid) {
        MaidArsNouveauSpellData data = getData(maid);
        return data != null && data.isCasting();
    }
    
    /**
     * 开始施法
     */
    @Override
    public boolean initiateCasting(EntityMaid maid) {
        MaidArsNouveauSpellData data = getData(maid);
        if (data == null || data.isCasting() || !isSpellBook(data.getSpellBook()) || 
            data.getCurrentCaster() == null) {
            return false;
        }

        // 确保女仆有魔力能力
        ensureManaCapability(maid);

        // 选择一个法术进行施放
        Spell spell = selectRandomSpell(data);
        if (spell == null) {
            return false;
        }
        
        // 确保女仆面向目标
        LivingEntity target = data.getTarget();
        if (target != null) {
            maid.getLookControl().setLookAt(target.getX(), target.getEyeY(), target.getZ());
        }
        
        // 开始施法
        data.setCasting(true);
        data.setCastingTicks(0);
        data.setCurrentSpell(spell);
        
        // 播放手臂挥舞动画
        maid.swing(InteractionHand.MAIN_HAND);
        
        return true;
    }
    
    /**
     * 处理持续施法
     */
    @Override
    public void processContinuousCasting(EntityMaid maid) {
        MaidArsNouveauSpellData data = getData(maid);
        if (data == null || !data.isCasting() || data.getCurrentSpell() == null || maid == null) {
            return;
        }
        
        data.incrementCastingTicks();
        
        // 持续面向目标
        LivingEntity target = data.getTarget();
        if (target != null) {
            maid.getLookControl().setLookAt(target.getX(), target.getEyeY(), target.getZ());
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
    
    /**
     * 执行法术
     */
    @Override
    public boolean castSpell(EntityMaid maid) {
        // 防止在已经施法时重复开始施法
        MaidArsNouveauSpellData data = getData(maid);
        if (data != null && data.isCasting()) { 
            return false;
        }
        return initiateCasting(maid);
    }
    
    /**
     * 更新冷却时间
     */
    @Override
    public void updateCooldown(EntityMaid maid) {
        // 新生魔艺法术没有内置冷却系统，这里预留接口供将来的魔力系统使用
        // 定期补充女仆的魔力
        if (maid != null && maid.tickCount % 100 == 0) { // 每5秒检查一次
            ensureManaCapability(maid);
        }
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
     * 检查是否有可用法术
     */
    private boolean hasAvailableSpells(MaidArsNouveauSpellData data) {
        if (data.getCurrentCaster() == null) {
            return false;
        }
        
        // 检查是否有有效的法术
        List<Spell> availableSpells = getAvailableSpells(data);
        return !availableSpells.isEmpty();
    }
    
    /**
     * 获取可用的法术列表
     */
    private List<Spell> getAvailableSpells(MaidArsNouveauSpellData data) {
        List<Spell> availableSpells = new ArrayList<>();
        ISpellCaster caster = data.getCurrentCaster();
        if (caster == null) {
            return availableSpells;
        }
        
        // 遍历法术书的所有槽位
        for (int i = 0; i < caster.getMaxSlots(); i++) {
            Spell spell = caster.getSpell(i);
            if (spell.isValid() && !spell.isEmpty()) {
                availableSpells.add(spell);
            }
        }
        return availableSpells;
    }
    
    /**
     * 随机选择一个可用的法术
     */
    private Spell selectRandomSpell(MaidArsNouveauSpellData data) {
        List<Spell> availableSpells = getAvailableSpells(data);
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
        if (data == null || !data.isCasting() || data.getCurrentSpell() == null || 
            data.getCurrentCaster() == null) {
            return;
        }
        
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
            
            boolean castSuccess = false;
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
                    EntityHitResult entityHit = new EntityHitResult(target, targetPos);
                    resolver.hitResult = entityHit;
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
        } catch (Exception e) { 
        } finally {
            // 重置施法状态
            data.resetCastingState();
        }
    }
} 