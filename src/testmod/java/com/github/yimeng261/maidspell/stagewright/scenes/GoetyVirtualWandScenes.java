package com.github.yimeng261.maidspell.stagewright.scenes;

import com.Polarice3.Goety.api.items.magic.IFocus;
import com.Polarice3.Goety.api.items.magic.IWand;
import com.Polarice3.Goety.api.magic.SpellType;
import com.Polarice3.Goety.common.entities.projectiles.MagicBolt;
import com.Polarice3.Goety.common.items.handler.FocusBagItemHandler;
import com.Polarice3.Goety.common.items.handler.SoulUsingItemHandler;
import com.github.tartaricacid.touhoulittlemaid.entity.passive.EntityMaid;
import com.github.yimeng261.maidspell.compat.goety.GoetyCompatItems;
import com.github.yimeng261.maidspell.compat.goety.GoetyTestAccess;
import com.github.yimeng261.maidspell.compat.goety.VirtualGoetyWandItem;
import com.github.yimeng261.maidspell.compat.goety.VirtualWandManager;
import com.github.yimeng261.maidspell.spell.data.MaidGoetySpellData;
import com.github.yimeng261.maidspell.spell.manager.SpellBookManager;
import com.github.yimeng261.maidspell.spell.providers.GoetyProvider;
import com.github.yimeng261.maidspell.stagewright.support.Actors;
import com.github.yimeng261.maidspell.stagewright.support.Checks;
import net.magicterra.stagewright.scene.Scene;
import net.magicterra.stagewright.scene.SceneContext;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.item.ItemStack;

import java.util.List;

/**
 * Goety 聚晶包施法：女仆只带聚晶包时通过隐藏的虚拟法杖施法，手上的真实法杖优先于虚拟法杖，
 * 背包里的法杖不参与选择；虚拟法杖的聚晶读取代理到聚晶包槽位，施法结束后上下文释放。
 */
public final class GoetyVirtualWandScenes {
    private static final String FOCUS_BAG = "goety:focus_bag";
    private static final String MAGIC_BOLT_FOCUS = "goety:magic_bolt_focus";
    private static final String MAGIC_BOLT_SPELL = "MagicBoltSpell";
    private static final String VEXING_FOCUS = "goety:vexing_focus";
    private static final String BLIZZARD_FOCUS = "goety:blizzard_focus";
    private static final String DARK_WAND = "goety:dark_wand";
    private static final String FROST_STAFF = "goety:frost_staff";

    private GoetyVirtualWandScenes() {
    }

    public static List<Scene> dedicatedServer() {
        return List.of(
                Checks.superflat("spell.goety.virtualWand.castsWithFocusBagOnly", 20, GoetyVirtualWandScenes::castsWithFocusBagOnly),
                Checks.superflat("spell.goety.virtualWand.proxiesBagSlot", 20, GoetyVirtualWandScenes::proxiesBagSlot),
                Checks.superflat("spell.goety.virtualWand.handWandKeepsBagFocus", 20, GoetyVirtualWandScenes::handWandKeepsBagFocus),
                Checks.superflat("spell.goety.virtualWand.prefersMainHandWand", 20,
                        ctx -> castWithHandWand(ctx, InteractionHand.MAIN_HAND)),
                Checks.superflat("spell.goety.virtualWand.prefersOffHandWand", 20,
                        ctx -> castWithHandWand(ctx, InteractionHand.OFF_HAND)),
                Checks.superflat("spell.goety.virtualWand.ignoresBackpackWand", 20, GoetyVirtualWandScenes::ignoresBackpackWand),
                Checks.superflat("spell.goety.virtualWand.handStaffGrantsSchoolBonus", 20, GoetyVirtualWandScenes::handStaffGrantsSchoolBonus),
                Checks.superflat("spell.goety.virtualWand.staleContextStopStillSetsCooldown", 20, GoetyVirtualWandScenes::staleContextStop),
                Checks.superflat("spell.goety.virtualWand.dataResetReleasesContext", 20, GoetyVirtualWandScenes::dataResetReleases),
                Checks.superflat("spell.goety.virtualWand.invalidContextIsNotUsable", 20, GoetyVirtualWandScenes::invalidContextNotUsable));
    }

    /** 本场景里的女仆、目标与聚晶包。 */
    private record Rig(GoetyProvider provider, EntityMaid maid, Mob target, ItemStack bag, MaidGoetySpellData data) {
    }

    /** 女仆带着装有一枚聚晶的聚晶包，目标是不动的僵尸；法术书已登记，还没开始施法。 */
    private static Rig rig(SceneContext ctx, String focusId) {
        VirtualWandManager.clearAll();
        if (!(SpellBookManager.getProvider("goety") instanceof GoetyProvider provider)) {
            ctx.skip("Goety 的法术提供者未注册");
            return null;
        }
        EntityMaid maid = Actors.sittingMaid(ctx, 0, 0, 0);
        Mob target = Actors.spawn(ctx, "minecraft:zombie", 3, 0, 0, true);
        target.setInvulnerable(true);

        ItemStack bag = Actors.stack(FOCUS_BAG);
        ItemStack focus = Actors.stack(focusId);
        ctx.check(!bag.isEmpty() && !focus.isEmpty()).as("Goety 聚晶包与聚晶物品存在").isTrue();
        FocusBagItemHandler.get(bag).setStackInSlot(0, focus);
        maid.getMaidInv().setStackInSlot(0, bag);

        MaidGoetySpellData data = MaidGoetySpellData.getOrCreate(maid);
        data.addSpellBook(bag, maid);
        data.setTarget(target);
        return new Rig(provider, maid, target, bag, data);
    }

    private static ItemStack bagFocus(ItemStack bag) {
        return FocusBagItemHandler.get(bag).getStackInSlot(0);
    }

    private static boolean magicBoltFired(SceneContext ctx, EntityMaid maid) {
        List<MagicBolt> bolts = ctx.level().getEntitiesOfClass(MagicBolt.class, maid.getBoundingBox().inflate(4.0D));
        bolts.forEach(bolt -> ctx.cleanup(() -> Actors.cleanupEntity(bolt)));
        return !bolts.isEmpty();
    }

    /** 只有聚晶包：瞬发的魔弹术通过虚拟法杖放出，施法结束后上下文释放，聚晶仍留在聚晶包里。 */
    private static void castsWithFocusBagOnly(SceneContext ctx) {
        Rig rig = rig(ctx, MAGIC_BOLT_FOCUS);
        if (rig == null) {
            return;
        }
        ItemStack focus = bagFocus(rig.bag()).copy();

        rig.provider().initiateCasting(rig.maid());

        ctx.check(rig.data().isCasting()).as("瞬发法术已施放完毕").isFalse();
        ctx.check(rig.data().getVirtualWandId()).as("瞬发后虚拟法杖上下文编号").isNull();
        ctx.check(GoetyTestAccess.activeVirtualWandContexts()).as("存活的虚拟法杖上下文").isEqualTo(0);
        ctx.check(rig.data().getSpellCooldown(MAGIC_BOLT_SPELL)).as("魔弹术进入冷却").isGreaterThan(0);
        ctx.check(magicBoltFired(ctx, rig.maid())).as("虚拟法杖放出了魔弹").isTrue();
        ctx.check(ItemStack.isSameItemSameComponents(bagFocus(rig.bag()), focus)).as("聚晶仍在聚晶包里").isTrue();
        ctx.check(rig.maid().getMainHandItem().isEmpty() && rig.maid().getOffhandItem().isEmpty())
                .as("女仆手上没有真实法杖").isTrue();
    }

    /** 虚拟法杖上的 Goety 聚晶读取走聚晶包槽位，写入也落回聚晶包，释放后读不到聚晶。 */
    private static void proxiesBagSlot(SceneContext ctx) {
        Rig rig = rig(ctx, MAGIC_BOLT_FOCUS);
        if (rig == null) {
            return;
        }
        ItemStack focus = bagFocus(rig.bag()).copy();
        VirtualWandManager.Handle handle = VirtualWandManager.create(rig.maid(), rig.bag(), 0, bagFocus(rig.bag()));
        ctx.check(handle).as("创建虚拟法杖").isNotNull();
        if (handle == null) {
            return;
        }
        ItemStack wand = handle.stack();
        ctx.check(wand.getItem() instanceof VirtualGoetyWandItem && wand.getItem() instanceof IWand)
                .as("虚拟法杖物品").isTrue();
        ctx.check(wand.getItem() == GoetyCompatItems.VIRTUAL_GOETY_WAND.get()).as("虚拟法杖是登记的隐藏物品").isTrue();
        ctx.check(ItemStack.isSameItemSameComponents(IWand.getFocus(wand), focus)).as("经能力读到的聚晶是聚晶包槽位里的聚晶").isTrue();

        SoulUsingItemHandler.get(wand).setStackInSlot(0, Actors.stack(VEXING_FOCUS));
        ctx.check(bagFocus(rig.bag()).getItem() == Actors.stack(VEXING_FOCUS).getItem()).as("写入虚拟法杖落回聚晶包槽位").isTrue();

        VirtualWandManager.release(handle.id());
        ctx.check(IWand.getFocus(wand).isEmpty()).as("释放后虚拟法杖不再有聚晶").isTrue();
        ctx.check(GoetyTestAccess.activeVirtualWandContexts()).as("释放后存活的上下文").isEqualTo(0);
    }

    /**
     * 施法中虚拟法杖的上下文失效（被释放）后停止施法：不对空聚晶的法杖继续回调，
     * 仍按已发射段数补上冷却，施法数据复位，没有残留上下文。
     */
    private static void staleContextStop(SceneContext ctx) {
        Rig rig = rig(ctx, MAGIC_BOLT_FOCUS);
        if (rig == null) {
            return;
        }
        ItemStack focus = bagFocus(rig.bag()).copy();
        VirtualWandManager.Handle handle = VirtualWandManager.create(rig.maid(), rig.bag(), 0, focus);
        ctx.check(handle).as("创建虚拟法杖").isNotNull();
        if (handle == null || !(focus.getItem() instanceof IFocus iFocus)) {
            return;
        }
        rig.data().setCurrentFocus(focus);
        rig.data().setCurrentStaff(handle.stack());
        rig.data().setVirtualWandId(handle.id());
        rig.data().initiateCastingState(iFocus.getSpell(), 40);
        ctx.check(rig.data().isCasting()).as("模拟施法中").isTrue();

        VirtualWandManager.release(handle.id());
        rig.provider().stopCasting(rig.maid());

        ctx.check(rig.data().isCasting()).as("停止后仍在施法").isFalse();
        ctx.check(rig.data().getVirtualWandId()).as("停止后的上下文编号").isNull();
        ctx.check(rig.data().getSpellCooldown(MAGIC_BOLT_SPELL)).as("法杖失效时仍补上冷却").isGreaterThan(0);
        ctx.check(GoetyTestAccess.activeVirtualWandContexts()).as("存活的上下文").isEqualTo(0);
    }

    /** 绕过 Provider 直接复位施法数据时，上下文也一并释放，不泄漏到 removeData 或 clearAll。 */
    private static void dataResetReleases(SceneContext ctx) {
        Rig rig = rig(ctx, MAGIC_BOLT_FOCUS);
        if (rig == null) {
            return;
        }
        VirtualWandManager.Handle handle = VirtualWandManager.create(rig.maid(), rig.bag(), 0, bagFocus(rig.bag()));
        ctx.check(handle).as("创建虚拟法杖").isNotNull();
        if (handle == null) {
            return;
        }
        rig.data().setVirtualWandId(handle.id());
        ctx.check(GoetyTestAccess.activeVirtualWandContexts()).as("复位前存活的上下文").isEqualTo(1);
        rig.data().resetCastingState();
        ctx.check(rig.data().getVirtualWandId()).as("复位后的上下文编号").isNull();
        ctx.check(GoetyTestAccess.activeVirtualWandContexts()).as("复位后存活的上下文").isEqualTo(0);
    }

    /** 虚拟法杖的上下文有效时是可用法杖；被释放、女仆被移除后都不再有效。 */
    private static void invalidContextNotUsable(SceneContext ctx) {
        Rig rig = rig(ctx, MAGIC_BOLT_FOCUS);
        if (rig == null) {
            return;
        }
        VirtualWandManager.Handle released = VirtualWandManager.create(rig.maid(), rig.bag(), 0, bagFocus(rig.bag()));
        VirtualWandManager.Handle removed = VirtualWandManager.create(rig.maid(), rig.bag(), 0, bagFocus(rig.bag()));
        if (released == null || removed == null) {
            ctx.fail("创建虚拟法杖失败");
            return;
        }
        ctx.check(VirtualWandManager.isActive(released.stack())).as("刚创建的虚拟法杖").isTrue();
        VirtualWandManager.release(released.id());
        ctx.check(VirtualWandManager.isActive(released.stack())).as("释放后的虚拟法杖").isFalse();
        rig.maid().discard();
        ctx.check(VirtualWandManager.isActive(removed.stack())).as("女仆被移除后的虚拟法杖").isFalse();
        ctx.check(VirtualWandManager.isActive(ItemStack.EMPTY)).as("空物品栈").isFalse();
    }

    /** 手上的真实法杖镶着与聚晶包相同的聚晶：用真实法杖，不建虚拟法杖，聚晶包里的聚晶不被消耗。 */
    private static void handWandKeepsBagFocus(SceneContext ctx) {
        Rig rig = rig(ctx, MAGIC_BOLT_FOCUS);
        if (rig == null) {
            return;
        }
        ItemStack focus = bagFocus(rig.bag()).copy();
        ItemStack wand = Actors.stack(DARK_WAND);
        SoulUsingItemHandler.get(wand).setStackInSlot(0, focus.copy());
        rig.maid().setItemInHand(InteractionHand.MAIN_HAND, wand);

        rig.provider().initiateCasting(rig.maid());

        ctx.check(rig.data().getVirtualWandId()).as("匹配的真实法杖不建虚拟法杖").isNull();
        ctx.check(rig.data().getSpellCooldown(MAGIC_BOLT_SPELL)).as("真实法杖路径也实际施法").isGreaterThan(0);
        ctx.check(ItemStack.isSameItemSameComponents(bagFocus(rig.bag()), focus)).as("用真实法杖时聚晶包里的聚晶不被消耗").isTrue();
    }

    /** 手上的真实法杖优先于虚拟法杖，即使法杖镶的是另一枚聚晶。 */
    private static void castWithHandWand(SceneContext ctx, InteractionHand hand) {
        Rig rig = rig(ctx, MAGIC_BOLT_FOCUS);
        if (rig == null) {
            return;
        }
        ItemStack focus = bagFocus(rig.bag()).copy();
        ItemStack wand = Actors.stack(DARK_WAND);
        SoulUsingItemHandler.get(wand).setStackInSlot(0, Actors.stack(VEXING_FOCUS));
        rig.maid().setItemInHand(hand, wand);

        rig.provider().initiateCasting(rig.maid());

        ctx.check(rig.data().getVirtualWandId()).as("手持真实法杖时不建虚拟法杖").isNull();
        ctx.check(rig.data().getSpellCooldown(MAGIC_BOLT_SPELL)).as("手持法杖路径实际施法").isGreaterThan(0);
        ctx.check(magicBoltFired(ctx, rig.maid())).as("手持法杖放出了魔弹").isTrue();
        ctx.check(ItemStack.isSameItemSameComponents(bagFocus(rig.bag()), focus)).as("手持法杖时聚晶包里的聚晶不动").isTrue();
    }

    /** 背包里的法杖不参与选择：只有聚晶包与背包法杖时仍用虚拟法杖，停止施法后释放。 */
    private static void ignoresBackpackWand(SceneContext ctx) {
        Rig rig = rig(ctx, BLIZZARD_FOCUS);
        if (rig == null) {
            return;
        }
        ItemStack backpackWand = Actors.stack(DARK_WAND);
        SoulUsingItemHandler.get(backpackWand).setStackInSlot(0, bagFocus(rig.bag()).copy());
        rig.maid().getMaidInv().setStackInSlot(1, backpackWand);

        rig.provider().initiateCasting(rig.maid());

        ctx.check(rig.data().isCasting()).as("暴风雪开始持续施法").isTrue();
        ctx.check(rig.data().getCurrentStaff().getItem() instanceof VirtualGoetyWandItem).as("背包里的法杖没被选中").isTrue();
        ctx.check(rig.data().getVirtualWandId()).as("只有背包法杖时用虚拟法杖").isNotNull();
        ctx.check(rig.maid().getMainHandItem().isEmpty() && rig.maid().getOffhandItem().isEmpty())
                .as("女仆手上没有真实法杖").isTrue();

        rig.provider().stopCasting(rig.maid());
        ctx.check(rig.data().getVirtualWandId()).as("停止施法后虚拟法杖上下文编号").isNull();
        ctx.check(GoetyTestAccess.activeVirtualWandContexts()).as("停止施法后存活的上下文").isEqualTo(0);
    }

    /** 手持冰霜法杖施放冰霜聚晶时有学派加成，虚拟法杖不给任何学派加成。 */
    private static void handStaffGrantsSchoolBonus(SceneContext ctx) {
        Rig rig = rig(ctx, BLIZZARD_FOCUS);
        if (rig == null) {
            return;
        }
        rig.maid().setItemInHand(InteractionHand.MAIN_HAND, Actors.stack(FROST_STAFF));

        rig.provider().initiateCasting(rig.maid());

        ctx.check(rig.data().isCasting()).as("暴风雪开始持续施法").isTrue();
        ctx.check(rig.data().getCurrentSpell().typeStaff(rig.data().getCurrentStaff(), SpellType.FROST))
                .as("手持冰霜法杖得到对应学派加成").isTrue();
        ctx.check(((IWand) GoetyCompatItems.VIRTUAL_GOETY_WAND.get()).getSpellTypes().contains(SpellType.FROST))
                .as("虚拟法杖给冰霜学派加成").isFalse();

        rig.provider().stopCasting(rig.maid());
    }
}
