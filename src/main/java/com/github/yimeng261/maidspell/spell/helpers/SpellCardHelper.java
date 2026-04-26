package com.github.yimeng261.maidspell.spell.helpers;

import dev.xkmc.youkaishomecoming.content.entity.danmaku.DanmakuHelper;
import dev.xkmc.youkaishomecoming.content.spell.game.TouhouSpellCards;
import dev.xkmc.youkaishomecoming.content.spell.mover.CompositeMover;
import dev.xkmc.youkaishomecoming.content.spell.mover.PolarMover;
import dev.xkmc.youkaishomecoming.content.spell.mover.RectMover;
import dev.xkmc.youkaishomecoming.content.spell.mover.ZeroMover;
import dev.xkmc.youkaishomecoming.content.spell.spellcard.CardHolder;
import dev.xkmc.youkaishomecoming.content.spell.spellcard.SpellCard;
import dev.xkmc.youkaishomecoming.content.spell.spellcard.SpellCardWrapper;
import dev.xkmc.youkaishomecoming.init.registrate.YHDanmaku;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.DyeColor;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.phys.Vec3;
import net.minecraftforge.registries.ForgeRegistries;
import org.slf4j.Logger;
import com.mojang.logging.LogUtils;

import java.lang.reflect.Field;
import java.util.*;
import java.util.function.Supplier;

/**
 * 符卡辅助类
 * 用于反射访问TouhouSpellCards的私有MAP
 * 以及管理符卡物品到符卡ID的映射
 */
public class SpellCardHelper {
    
    private static final Logger LOGGER = LogUtils.getLogger();
    
    // 符卡物品注册名到符卡ID的映射
    private static final Map<String, String> ITEM_TO_SPELL_CARD = new HashMap<>();
    private static final Map<String, Supplier<SpellCard>> ITEM_TO_DIRECT_SPELL_CARD = new HashMap<>();
    
    private static String getItemRegistryName(Item item) {
        ResourceLocation key = ForgeRegistries.ITEMS.getKey(item);
        return key == null ? null : key.toString();
    }
    
    static {
        // 初始化符卡物品到符卡ID的映射
        ITEM_TO_SPELL_CARD.put("youkaishomecoming:spell_reimu", "touhou_little_maid:hakurei_reimu");
        ITEM_TO_SPELL_CARD.put("youkaishomecoming:spell_sanae", "touhou_little_maid:kochiya_sanae");
        ITEM_TO_SPELL_CARD.put("youkaishomecoming:spell_marisa", "touhou_little_maid:kirisame_marisa");
        ITEM_TO_SPELL_CARD.put("youkaishomecoming:spell_mystia", "touhou_little_maid:mystia_lorelei");
        ITEM_TO_SPELL_CARD.put("youkaishomecoming:spell_remilia", "touhou_little_maid:remilia_scarlet");
        ITEM_TO_SPELL_CARD.put("youkaishomecoming:spell_koishi", "touhou_little_maid:komeiji_koishi");
        ITEM_TO_DIRECT_SPELL_CARD.put("youkaishomecoming:spell_yukari_butterfly", YukariButterflyItemSpellCard::new);
        ITEM_TO_DIRECT_SPELL_CARD.put("youkaishomecoming:spell_yukari_laser", YukariLaserItemSpellCard::new);
        ITEM_TO_SPELL_CARD.put("youkaishomecoming:spell_clownpiece", "touhou_little_maid:clownpiece");
        // custom_spell_* 走 YH 自定义法术系统，不随机伪装成预设符卡
    }
    private static Map<String, Supplier<SpellCard>> spellCardMap = null;
    
    /**
     * 获取符卡MAP（通过反射）
     */
    @SuppressWarnings("unchecked")
    private static Map<String, Supplier<SpellCard>> getSpellCardMap() {
        if (spellCardMap != null) {
            return spellCardMap;
        }
        
        try {
            Field mapField = TouhouSpellCards.class.getDeclaredField("MAP");
            mapField.setAccessible(true);
            spellCardMap = (Map<String, Supplier<SpellCard>>) mapField.get(null);
            return spellCardMap;
        } catch (Exception e) {
            LOGGER.error("Failed to access TouhouSpellCards MAP: {}", e.getMessage());
            return null;
        }
    }
    
    /**
     * 创建符卡包装器
     * @param spellCardId 符卡ID
     * @return 符卡包装器，失败返回null
     */
    public static SpellCardWrapper createSpellCard(String spellCardId) {
        Map<String, Supplier<SpellCard>> map = getSpellCardMap();
        if (map == null) {
            LOGGER.error("SpellCard MAP is null, cannot create spell card");
            return null;
        }
        
        Supplier<SpellCard> supplier = map.get(spellCardId);
        if (supplier == null) {
            LOGGER.warn("No spell card found for id: {}", spellCardId);
            return null;
        }
        
        try {
            SpellCardWrapper wrapper = new SpellCardWrapper();
            wrapper.modelId = spellCardId;
            wrapper.card = supplier.get();
            return wrapper;
        } catch (Exception e) {
            LOGGER.error("Failed to create spell card {}: {}", spellCardId, e.getMessage());
            return null;
        }
    }
    
    /**
     * 检查符卡是否存在
     */
    public static boolean hasSpellCard(String spellCardId) {
        Map<String, Supplier<SpellCard>> map = getSpellCardMap();
        return map != null && map.containsKey(spellCardId);
    }
    
    /**
     * 从物品堆栈获取对应的符卡ID
     * @param itemStack 符卡物品堆栈
     * @return 符卡ID，如果无对应符卡返回null
     */
    private static SpellCardWrapper createDirectSpellCard(String itemId, Supplier<SpellCard> supplier) {
        try {
            SpellCardWrapper wrapper = new SpellCardWrapper();
            wrapper.modelId = itemId;
            wrapper.card = supplier.get();
            return wrapper;
        } catch (Exception e) {
            LOGGER.error("Failed to create direct YH spell card {}: {}", itemId, e.getMessage());
            return null;
        }
    }

    public static String getSpellCardIdFromItem(ItemStack itemStack) {
        if (itemStack == null || itemStack.isEmpty()) {
            return null;
        }
        
        Item item = itemStack.getItem();
        String itemId = getItemRegistryName(item);
        if (itemId == null) {
            return null;
        }
        
        return ITEM_TO_SPELL_CARD.get(itemId);
    }
    
    /**
     * 从物品堆栈创建符卡包装器
     * @param itemStack 符卡物品堆栈
     * @return 符卡包装器，失败返回null
     */
    public static SpellCardWrapper createSpellCardFromItem(ItemStack itemStack) {
        if (itemStack == null || itemStack.isEmpty()) {
            return null;
        }

        String itemId = getItemRegistryName(itemStack.getItem());
        if (itemId == null) {
            return null;
        }

        Supplier<SpellCard> directSupplier = ITEM_TO_DIRECT_SPELL_CARD.get(itemId);
        if (directSupplier != null) {
            return createDirectSpellCard(itemId, directSupplier);
        }

        String spellCardId = getSpellCardIdFromItem(itemStack);
        if (spellCardId == null) {
            return null;
        }
        
        return createSpellCard(spellCardId);
    }
    
    /**
     * 检查物品是否为符卡物品（可以转换为符卡）
     */
    public static boolean isSpellCardItem(ItemStack itemStack) {
        if (itemStack == null || itemStack.isEmpty()) {
            return false;
        }
        
        String itemId = getItemRegistryName(itemStack.getItem());
        if (itemId == null) {
            return false;
        }
        
        // 只识别有明确上游符卡映射的预设符卡；custom_spell_* 走 YH 自定义法术系统，不随机伪装成预设符卡。
        return ITEM_TO_SPELL_CARD.containsKey(itemId) || ITEM_TO_DIRECT_SPELL_CARD.containsKey(itemId);
    }

    private static class YukariLaserItemSpellCard extends SpellCard {
        private int tick;
        private Vec3 pos;
        private Vec3 forward;

        @Override
        public void tick(CardHolder holder) {
            if (tick == 0) {
                forward = holder.forward().multiply(1, 0.5, 1).normalize();
                pos = holder.center();
            }
            var ori = DanmakuHelper.getOrientation(forward);
            var r = holder.random();
            addLaserBeams(holder, pos, ori.rotateDegrees(-45), 1 + tick * 0.5, r.nextDouble(), DyeColor.RED);
            addLaserBeams(holder, pos, ori.rotateDegrees(45), 1 + tick * 0.5, r.nextDouble(), DyeColor.BLUE);
            if (tick == 20) {
                shootGroup(holder, DyeColor.RED);
            }
            if (tick == 40) {
                shootGroup(holder, DyeColor.BLUE);
            }
            tick++;
        }

        private void shootGroup(CardHolder holder, DyeColor color) {
            double speed = 1;
            double dv = 0.5;
            double dev = 30;
            int n0 = 5;
            int n1 = 50;
            int life = 60;
            int dl = 20;

            var forward = holder.forward();
            var rand = holder.random();
            var ori = DanmakuHelper.getOrientation(forward);

            for (int i = 0; i < n0; i++) {
                double d0 = (rand.nextDouble() * 2 - 1) * dev * i / n0;
                double d1 = (rand.nextDouble() * 2 - 1) * dev * i / n0;
                double sp = speed - dv / n0 * i;
                var vec = ori.rotateDegrees(d0, d1).scale(sp);
                int lf = life + rand.nextInt(dl);
                holder.shoot(holder.prepareDanmaku(lf, vec, YHDanmaku.Bullet.BUBBLE, color));
            }
            for (int i = 0; i < n1; i++) {
                double d0 = (rand.nextDouble() * 2 - 1) * dev * i / n1;
                double d1 = (rand.nextDouble() * 2 - 1) * dev * i / n1;
                double sp = speed - dv / n1 * i;
                var vec = ori.rotateDegrees(d0, d1).scale(sp);
                int lf = life + rand.nextInt(dl);
                holder.shoot(holder.prepareDanmaku(lf, vec, YHDanmaku.Bullet.MENTOS, color));
            }
        }

        private void addLaserBeams(CardHolder holder, Vec3 pos, Vec3 dir, double step, double r, DyeColor color) {
            Vec3 p = pos.add(dir.scale(step));
            var ori = DanmakuHelper.getOrientation(dir).rotate(Math.PI / 2, r * Math.PI * 2);
            holder.shoot(holder.prepareLaser(100, p, ori, 80, YHDanmaku.Laser.LASER, color));
        }

        @Override
        public void reset() {
            tick = 0;
            pos = null;
            forward = null;
        }
    }

    private static class YukariButterflyItemSpellCard extends SpellCard {
        private boolean launched;

        @Override
        public void tick(CardHolder holder) {
            if (launched) {
                return;
            }
            launched = true;
            launchButterfly(holder, YHDanmaku.Bullet.BUTTERFLY, DyeColor.CYAN, 1);
            launchButterfly(holder, YHDanmaku.Bullet.BUTTERFLY, DyeColor.MAGENTA, -1);
        }

        private void launchButterfly(CardHolder holder, YHDanmaku.Bullet type, DyeColor color, int dire) {
            var r = holder.random();
            var pos = holder.center();
            DanmakuHelper.Orientation o0 = DanmakuHelper.getOrientation(holder.forward());

            int n = 100;
            int mrange = 12, vrange = 8;
            int t0 = 40;
            int t1 = 10;
            double tvr = 0.8;
            int t2 = 10;
            int t3 = 30;
            int t4 = 40;
            double avar = Math.PI / 4;

            float wvr = (float) (tvr / mrange) * dire;
            int total = t0 + t1 + t2 + t3 + t4;
            for (int i = 0; i < n; i++) {
                double a0 = 2 * Math.PI / n * i;
                double ver = (r.nextDouble() * 2 - 1) * avar;
                Vec3 a1 = o0.rotate(a0, ver);
                Vec3 vn = o0.rotate(a0, ver + Math.PI / 2);
                float range = mrange + vrange * (float) (r.nextDouble() * 2 - 1);
                float va = range * 2 / (t0 * t0);
                float vr = va * t0;
                var mover = new CompositeMover();
                var a2 = PolarMover.ofPlane(pos, a1, vn)
                        .radial(range, 0, 0).angular(0, wvr, 0).dir(0)
                        .scale(100).normalize();
                var polar0 = PolarMover.ofPlane(pos, a1, vn)
                        .radial(range, 0, 0).angular(0, 0, wvr / t2);
                var polar1 = polar0.copy().atTime(t2).clearAccel();
                var rect = polar1.copy().atTime(t3).toRect();
                var v1 = a1.scale(vr);
                mover.add(t0, new RectMover(pos, v1, a1.scale(-va)));
                mover.add(t1, new ZeroMover(a1, a2, t1));
                mover.add(t2, polar0);
                mover.add(t3, polar1);
                mover.add(t4, rect);
                var danmaku = holder.prepareDanmaku(total + r.nextInt(40), v1, type, color);
                danmaku.mover = mover;
                holder.shoot(danmaku);
            }
        }

        @Override
        public void reset() {
            launched = false;
        }
    }

}