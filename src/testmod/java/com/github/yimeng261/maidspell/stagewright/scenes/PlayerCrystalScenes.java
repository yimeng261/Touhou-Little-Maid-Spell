package com.github.yimeng261.maidspell.stagewright.scenes;

import com.github.tartaricacid.touhoulittlemaid.entity.passive.EntityMaid;
import com.github.yimeng261.maidspell.compat.curios.DreamCrystalCurios;
import com.github.yimeng261.maidspell.stagewright.support.Actors;
import com.github.yimeng261.maidspell.stagewright.support.Checks;
import com.github.yimeng261.maidspell.stagewright.support.Owners;
import com.github.yimeng261.maidspell.stagewright.support.Players;
import net.magicterra.stagewright.scene.Scene;
import net.magicterra.stagewright.scene.SceneContext;
import net.minecraft.advancements.AdvancementHolder;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.monster.Husk;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import top.theillusivec4.curios.api.CuriosApi;
import top.theillusivec4.curios.api.SlotContext;
import top.theillusivec4.curios.api.type.capability.ICuriosItemHandler;
import top.theillusivec4.curios.api.type.inventory.ICurioStacksHandler;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.TreeMap;

import static com.github.yimeng261.maidspell.stagewright.support.Checks.NS;

/**
 * 玩家在 Curios 的 curio 槽佩戴梦云水晶：槽位规则、戴上/取下时的属性与槽位、伤害裁剪、负面效果与冷却清除、
 * 攻击追加真伤与定身、敌对生物不锁定、致死复活、装备修复。
 */
public final class PlayerCrystalScenes {
    private static final String CRYSTAL = NS + "dream_cat_crystal";
    private static final String SLOT = "curio";
    private static final ResourceLocation ADVANCEMENT = ResourceLocation.fromNamespaceAndPath("touhou_little_maid_spell",
            "dream_crystal/all_spells_mastered");

    private PlayerCrystalScenes() {
    }

    public static List<Scene> integratedServer() {
        List<Scene> scenes = new ArrayList<>();
        scenes.add(Players.hostScene("player_crystal.slotRules", 20, PlayerCrystalScenes::slotRules));
        scenes.add(Players.hostScene("player_crystal.wearAndTakeOff", 40, PlayerCrystalScenes::wearAndTakeOff));
        scenes.add(Players.hostScene("player_crystal.damageRules", 20, PlayerCrystalScenes::damageRules));
        scenes.add(Players.hostScene("player_crystal.effectsAndCooldowns", 20, PlayerCrystalScenes::effectsAndCooldowns));
        scenes.add(Players.hostScene("player_crystal.attackTrueDamageAndFreeze", 20, PlayerCrystalScenes::attack));
        scenes.add(Players.hostScene("player_crystal.hostilesDoNotTarget", 80, PlayerCrystalScenes::hostiles));
        scenes.add(Players.hostScene("player_crystal.revivesOnLethalDamage", 20, PlayerCrystalScenes::revive));
        scenes.add(Players.hostScene("player_crystal.repairsEquipment", 60, PlayerCrystalScenes::repairs));
        return scenes;
    }

    private static ICuriosItemHandler curios(ServerPlayer player) {
        return CuriosApi.getCuriosInventory(player).orElseThrow();
    }

    private static ICurioStacksHandler slot(ServerPlayer player) {
        return curios(player).getStacksHandler(SLOT).orElseThrow();
    }

    /** 清空 curio 槽，放入水晶；场景结束时取下所有 curio 并等一 tick 让取下的逻辑生效。 */
    private static ItemStack wear(SceneContext ctx, ServerPlayer player) {
        ICurioStacksHandler handler = slot(player);
        for (int i = 0; i < handler.getSlots(); i++) {
            handler.getStacks().setStackInSlot(i, ItemStack.EMPTY);
        }
        ctx.cleanup(() -> takeOff(player));
        ItemStack crystal = Actors.stack(CRYSTAL);
        handler.getStacks().setStackInSlot(0, crystal);
        return handler.getStacks().getStackInSlot(0);
    }

    private static void takeOff(ServerPlayer player) {
        ICurioStacksHandler handler = slot(player);
        for (int i = 0; i < handler.getSlots(); i++) {
            handler.getStacks().setStackInSlot(i, ItemStack.EMPTY);
        }
    }

    /** 放进 curio 槽的第 index 格（水晶戴上后才有第 2 格）。 */
    private static void wearExtra(ServerPlayer player, int index, String id) {
        slot(player).getStacks().setStackInSlot(index, Actors.stack(id));
    }

    private static Map<String, Integer> slotCounts(ServerPlayer player) {
        Map<String, Integer> out = new TreeMap<>();
        curios(player).getCurios().forEach((id, handler) -> out.put(id, handler.getSlots()));
        return out;
    }

    private static void slotRules(SceneContext ctx, ServerPlayer player) {
        ItemStack crystal = Actors.stack(CRYSTAL);
        var curio = CuriosApi.getCurio(crystal).orElse(null);
        ItemStack second = Actors.stack(CRYSTAL);
        var secondCurio = CuriosApi.getCurio(second).orElse(null);
        if (curio == null) {
            ctx.fail("梦云水晶不是 Curios 物品");
            return;
        }
        ctx.check(curio.canEquip(new SlotContext(SLOT, player, 0, false, true))).as("能放进 curio 槽").isTrue();
        ctx.check(curio.canEquip(new SlotContext(SLOT, player, 0, true, true))).as("能放进装饰槽").isFalse();
        wear(ctx, player);
        Checks.after(ctx, 2, () -> {
            ctx.check(secondCurio.canEquip(new SlotContext(SLOT, player, 1, false, true))).as("已戴一个时再戴第二个").isFalse();
        });
    }

    private static void wearAndTakeOff(SceneContext ctx, ServerPlayer player) {
        Map<String, Integer> slotsBefore = slotCounts(player);
        double maxHealth = player.getAttributeBaseValue(Attributes.MAX_HEALTH);
        double attackSpeed = player.getAttributeValue(Attributes.ATTACK_SPEED);
        player.addEffect(new MobEffectInstance(MobEffects.POISON, 200));
        wear(ctx, player);
        Checks.after(ctx, 21, () -> {
            ctx.check(player.hasEffect(MobEffects.POISON)).as("戴上后中毒仍在").isFalse();
            ctx.check(player.getMaxHealth()).as("生命上限").isCloseTo((float) (maxHealth * 1.5), 1e-3f);
            ctx.check(player.getAttributeValue(Attributes.ATTACK_SPEED)).as("攻速").isCloseTo(attackSpeed * 2, 1e-3);
            Map<String, Integer> expected = new TreeMap<>();
            slotsBefore.forEach((id, n) -> expected.put(id, n + 1));
            ctx.check(slotCounts(player)).as("各种槽位格数").isEqualTo(expected);
            AdvancementHolder advancement = ctx.server().getAdvancements().get(ADVANCEMENT);
            ctx.check(advancement != null && player.getAdvancements().getOrStartProgress(advancement).isDone())
                    .as("获得「万法皆通」进度").isTrue();
            player.setHealth(player.getMaxHealth());
            takeOff(player);
            Checks.after(ctx, 3, () -> {
                ctx.check(player.getMaxHealth()).as("取下后生命上限").isCloseTo((float) maxHealth, 1e-3f);
                ctx.check(player.getHealth() <= player.getMaxHealth()).as("取下后血量不超过上限").isTrue();
                ctx.check(slotCounts(player)).as("取下后各种槽位格数").isEqualTo(slotsBefore);
                player.addEffect(new MobEffectInstance(MobEffects.POISON, 200));
                ctx.check(player.hasEffect(MobEffects.POISON)).as("取下后能再中毒").isTrue();
            });
        });
    }

    /** 各种伤害打在戴水晶的玩家身上实际扣的血。 */
    private static void damageRules(SceneContext ctx, ServerPlayer player) {
        // 生命上限提到 150（戴上后 225），单次 40 的上限打不死人，复活不会干扰计数
        double baseHealth = player.getAttributeBaseValue(Attributes.MAX_HEALTH);
        player.getAttribute(Attributes.MAX_HEALTH).setBaseValue(150);
        ctx.cleanup(() -> player.getAttribute(Attributes.MAX_HEALTH).setBaseValue(baseHealth));
        wear(ctx, player);
        Checks.after(ctx, 21, () -> {
            Map<String, Float> taken = new LinkedHashMap<>();
            Map<String, Float> expected = new LinkedHashMap<>();
            var sources = player.damageSources();
            record Case(String name, DamageSource source, float amount, float expect) {
            }
            List<Case> cases = List.of(
                    new Case("火焰", sources.inFire(), 10, 0), new Case("溺水", sources.drown(), 10, 0),
                    new Case("爆炸", sources.explosion(null, null), 10, 0), new Case("魔法", sources.magic(), 10, 0),
                    new Case("熔岩", sources.lava(), 10, 0),
                    new Case("普通 10 点", sources.generic(), 10, 7), new Case("普通 100 点（上限 40）", sources.generic(), 100, 40));
            for (Case c : cases) {
                player.setHealth(player.getMaxHealth());
                player.invulnerableTime = 0;
                float before = player.getHealth();
                player.hurt(c.source(), c.amount());
                taken.put(c.name(), before - player.getHealth());
                expected.put(c.name(), c.expect());
            }
            ctx.check(player.isOnFire()).as("着火").isFalse();
            ctx.record("taken", taken);
            taken.forEach((name, amount) -> ctx.check(amount).as(name + " 实际扣血").isCloseTo(expected.get(name), 1e-3f));
            wearExtra(player, 1, NS + "double_heart_chain");
            Checks.after(ctx, 2, () -> {
                player.setHealth(player.getMaxHealth());
                player.invulnerableTime = 0;
                float before = player.getHealth();
                player.hurt(sources.generic(), 10);
                ctx.check(before - player.getHealth()).as("同时戴双心之链时普通 10 点实际扣血").isCloseTo(3.5f, 1e-3f);
            });
        });
    }

    private static void effectsAndCooldowns(SceneContext ctx, ServerPlayer player) {
        wear(ctx, player);
        Checks.after(ctx, 3, () -> {
            player.addEffect(new MobEffectInstance(MobEffects.WITHER, 200));
            ctx.check(player.hasEffect(MobEffects.WITHER)).as("戴着时能被施加凋零").isFalse();
            player.getInventory().setItem(8, new ItemStack(Items.ENDER_PEARL));
            player.getCooldowns().addCooldown(Items.ENDER_PEARL, 200);
            player.setRemainingFireTicks(100);
            Checks.after(ctx, 2, () -> {
                ctx.check(player.getCooldowns().isOnCooldown(Items.ENDER_PEARL)).as("末影珍珠冷却").isFalse();
                ctx.check(player.isOnFire()).as("着火").isFalse();
            });
        });
    }

    /** 玩家打一只僵尸：追加等额真伤并定身，5 格内另一只僵尸受 10% 溅射，旁边自己的女仆不受伤。 */
    private static void attack(SceneContext ctx, ServerPlayer player) {
        wear(ctx, player);
        Husk target = Actors.spawn(ctx, "minecraft:husk", 2, 0, 0, false);
        Husk nearby = Actors.spawn(ctx, "minecraft:husk", 4, 0, 0, true);
        EntityMaid maid = Owners.maid(ctx, player, 3, 0, 2);
        for (Mob mob : List.of(target, nearby)) {
            mob.getAttribute(Attributes.MAX_HEALTH).setBaseValue(200);
            mob.setHealth(200);
            mob.getAttribute(Attributes.ARMOR).setBaseValue(0);
        }
        float maidBefore = maid.getHealth();
        Checks.after(ctx, 3, () -> {
            target.hurt(player.damageSources().playerAttack(player), 10);
            ctx.check(target.isNoAi()).as("目标被定身").isTrue();
            Checks.after(ctx, 2, () -> {
                ctx.check(200 - target.getHealth()).as("目标掉血（10 点 + 等额真伤）").isCloseTo(20f, 1e-3f);
                ctx.check(200 - nearby.getHealth()).as("5 格内的其他敌人受 10% 溅射").isCloseTo(1f, 1e-3f);
                ctx.check(maid.getHealth()).as("自己的女仆生命").isCloseTo(maidBefore, 1e-3f);
            });
        });
    }

    /** 有 AI 的僵尸在旁边 3 秒都不以玩家为目标；强行设成目标的也会在一秒内放弃。 */
    private static void hostiles(SceneContext ctx, ServerPlayer player) {
        wear(ctx, player);
        Husk roaming = Actors.spawn(ctx, "minecraft:husk", 4, 0, 0, false);
        Husk locked = Actors.spawn(ctx, "minecraft:husk", -4, 0, 0, false);
        boolean[] targeted = {false};
        Checks.after(ctx, 3, () -> {
            locked.setTarget(player);
            Checks.watch(ctx, 60, () -> targeted[0] |= roaming.getTarget() == player, () -> {
                ctx.check(targeted[0]).as("僵尸曾以玩家为目标").isFalse();
                ctx.check(locked.getTarget() == player).as("强行锁定的僵尸仍以玩家为目标").isFalse();
            });
        });
    }

    private static void revive(SceneContext ctx, ServerPlayer player) {
        wear(ctx, player);
        Checks.after(ctx, 3, () -> {
            player.setHealth(1);
            player.invulnerableTime = 0;
            player.hurt(player.damageSources().generic(), 1000);
            ctx.check(player.isDeadOrDying()).as("致死一击后死亡").isFalse();
            ctx.check(player.getHealth()).as("复活后生命").isCloseTo(player.getMaxHealth(), 1e-3f);
            player.invulnerableTime = 0;
            float before = player.getHealth();
            player.hurt(player.damageSources().generic(), 10);
            ctx.check(player.getHealth()).as("复活无敌期间受 10 点伤害后的生命").isCloseTo(before, 1e-3f);
        });
    }

    /** 背包里有耐久损耗的剑，每秒修复 1 点。 */
    private static void repairs(SceneContext ctx, ServerPlayer player) {
        wear(ctx, player);
        ItemStack sword = new ItemStack(Items.IRON_SWORD);
        sword.setDamageValue(50);
        player.getInventory().setItem(7, sword);
        Checks.after(ctx, 45, () -> ctx.check(player.getInventory().getItem(7).getDamageValue())
                .as("2 秒多后剑的损耗（原 50）").isBetween(47, 49));
    }
}
