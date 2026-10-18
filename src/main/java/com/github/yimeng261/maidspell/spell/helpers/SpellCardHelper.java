package com.github.yimeng261.maidspell.spell.helpers;

import com.github.yimeng261.maidspell.spell.holders.MaidCardHolder;
import com.mojang.logging.LogUtils;
import dev.xkmc.danmakuapi.content.entity.DanmakuHelper;
import dev.xkmc.danmakuapi.content.spell.item.ItemSpell;
import dev.xkmc.danmakuapi.content.spell.mover.CompositeMover;
import dev.xkmc.danmakuapi.content.spell.mover.PolarMover;
import dev.xkmc.danmakuapi.content.spell.mover.RectMover;
import dev.xkmc.danmakuapi.content.spell.mover.ZeroMover;
import dev.xkmc.danmakuapi.content.spell.spellcard.CardHolder;
import dev.xkmc.danmakuapi.content.spell.spellcard.SpellCard;
import dev.xkmc.danmakuapi.content.spell.spellcard.SpellCardWrapper;
import dev.xkmc.danmakuapi.init.registrate.DanmakuItems;
import dev.xkmc.youkaishomecoming.compat.touhoulittlemaid.TouhouSpellCards;
import dev.xkmc.youkaishomecoming.mixin.SpellItemAccessor;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.item.DyeColor;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.phys.Vec3;
import org.slf4j.Logger;

import java.lang.reflect.Field;
import java.util.HashMap;
import java.util.Map;
import java.util.function.Supplier;

/**
 * 符卡辅助类
 * 用于反射访问妖怪的归家 TouhouSpellCards 的私有 MAP，以及管理符卡物品到符卡的映射
 */
public class SpellCardHelper {

    private static final Logger LOGGER = LogUtils.getLogger();

    // 符卡物品注册名到符卡ID的映射
    private static final Map<String, String> ITEM_TO_SPELL_CARD = new HashMap<>();
    private static final Map<String, Supplier<SpellCard>> ITEM_TO_DIRECT_SPELL_CARD = new HashMap<>();

    static {
        ITEM_TO_SPELL_CARD.put("youkaishomecoming:spell_reimu", "touhou_little_maid:hakurei_reimu");
        ITEM_TO_SPELL_CARD.put("youkaishomecoming:spell_sanae", "touhou_little_maid:kochiya_sanae");
        ITEM_TO_SPELL_CARD.put("youkaishomecoming:spell_marisa", "touhou_little_maid:kirisame_marisa");
        ITEM_TO_SPELL_CARD.put("youkaishomecoming:spell_mystia", "touhou_little_maid:mystia_lorelei");
        ITEM_TO_SPELL_CARD.put("youkaishomecoming:spell_remilia", "touhou_little_maid:remilia_scarlet");
        ITEM_TO_SPELL_CARD.put("youkaishomecoming:spell_koishi", "touhou_little_maid:komeiji_koishi");
        ITEM_TO_DIRECT_SPELL_CARD.put("youkaishomecoming:spell_yukari_butterfly", YukariButterflyItemSpellCard::new);
        ITEM_TO_DIRECT_SPELL_CARD.put("youkaishomecoming:spell_yukari_laser", YukariLaserItemSpellCard::new);
        // 1.21 移植版的 TouhouSpellCards 没有小丑皮丝，找不到时改用物品自带的法术
        ITEM_TO_SPELL_CARD.put("youkaishomecoming:spell_clownpiece", "touhou_little_maid:clownpiece");
        // custom_spell_* 走 YH 自定义法术系统，不随机伪装成预设符卡
    }

    private static Map<String, Supplier<SpellCard>> spellCardMap = null;

    private static String getItemRegistryName(ItemStack stack) {
        return BuiltInRegistries.ITEM.getKey(stack.getItem()).toString();
    }

    /**
     * 获取符卡MAP（通过反射）；读不到时为空表
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
        } catch (Exception e) {
            LOGGER.warn("Failed to access TouhouSpellCards MAP: {}", e.toString());
            spellCardMap = Map.of();
        }
        return spellCardMap;
    }

    private static SpellCardWrapper wrap(String modelId, Supplier<SpellCard> supplier) {
        try {
            SpellCardWrapper wrapper = new SpellCardWrapper();
            wrapper.modelId = modelId;
            wrapper.card = supplier.get();
            return wrapper;
        } catch (Exception e) {
            LOGGER.error("Failed to create YH spell card {}: {}", modelId, e.getMessage());
            return null;
        }
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
        String itemId = getItemRegistryName(itemStack);

        Supplier<SpellCard> directSupplier = ITEM_TO_DIRECT_SPELL_CARD.get(itemId);
        if (directSupplier != null) {
            return wrap(itemId, directSupplier);
        }

        String spellCardId = ITEM_TO_SPELL_CARD.get(itemId);
        if (spellCardId == null) {
            return null;
        }
        Supplier<SpellCard> supplier = getSpellCardMap().get(spellCardId);
        if (supplier != null) {
            return wrap(spellCardId, supplier);
        }
        // 物品自带的法术：妖怪的归家用 mixin 给 SpellItem 加的访问器
        if (itemStack.getItem() instanceof SpellItemAccessor accessor) {
            Supplier<ItemSpell> itemSpell = accessor.getSpell();
            return wrap(itemId, () -> new ItemSpellCard(itemSpell));
        }
        LOGGER.warn("No spell card found for item {}", itemId);
        return null;
    }

    /**
     * 检查物品是否为符卡物品（可以转换为符卡）
     */
    public static boolean isSpellCardItem(ItemStack itemStack) {
        if (itemStack == null || itemStack.isEmpty()) {
            return false;
        }
        String itemId = getItemRegistryName(itemStack);
        // 只识别有明确上游符卡映射的预设符卡；custom_spell_* 走 YH 自定义法术系统，不随机伪装成预设符卡。
        return ITEM_TO_SPELL_CARD.containsKey(itemId) || ITEM_TO_DIRECT_SPELL_CARD.containsKey(itemId);
    }

    /**
     * 把物品自带的法术当符卡放：第一次 tick 时对女仆的目标启动，法术自行结束后不再发射
     */
    private static class ItemSpellCard extends SpellCard {
        private final Supplier<? extends ItemSpell> factory;
        private ItemSpell spell;
        private boolean finished;

        private ItemSpellCard(Supplier<? extends ItemSpell> factory) {
            this.factory = factory;
        }

        @Override
        public void tick(CardHolder holder) {
            if (finished) {
                return;
            }
            LivingEntity self = holder.self();
            if (spell == null) {
                LivingEntity target = holder instanceof MaidCardHolder maidHolder ? maidHolder.targetEntity() : null;
                if (target == null) {
                    finished = true;
                    return;
                }
                spell = factory.get();
                spell.start(self, target);
            }
            finished = spell.tick(self);
        }

        @Override
        public void reset() {
            spell = null;
            finished = false;
        }
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
                holder.shoot(holder.prepareDanmaku(lf, vec, DanmakuItems.Bullet.BUBBLE, color));
            }
            for (int i = 0; i < n1; i++) {
                double d0 = (rand.nextDouble() * 2 - 1) * dev * i / n1;
                double d1 = (rand.nextDouble() * 2 - 1) * dev * i / n1;
                double sp = speed - dv / n1 * i;
                var vec = ori.rotateDegrees(d0, d1).scale(sp);
                int lf = life + rand.nextInt(dl);
                holder.shoot(holder.prepareDanmaku(lf, vec, DanmakuItems.Bullet.MENTOS, color));
            }
        }

        private void addLaserBeams(CardHolder holder, Vec3 pos, Vec3 dir, double step, double r, DyeColor color) {
            Vec3 p = pos.add(dir.scale(step));
            var ori = DanmakuHelper.getOrientation(dir).rotate(Math.PI / 2, r * Math.PI * 2);
            holder.shoot(holder.prepareLaser(100, p, ori, 80, DanmakuItems.Laser.LASER, color));
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
            launchButterfly(holder, DanmakuItems.Bullet.BUTTERFLY, DyeColor.CYAN, 1);
            launchButterfly(holder, DanmakuItems.Bullet.BUTTERFLY, DyeColor.MAGENTA, -1);
        }

        private void launchButterfly(CardHolder holder, DanmakuItems.Bullet type, DyeColor color, int dire) {
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
