package com.github.yimeng261.maidspell.compat.goety;

import com.Polarice3.Goety.api.items.magic.IWand;
import com.Polarice3.Goety.api.magic.SpellType;
import com.Polarice3.Goety.common.entities.projectiles.MagicBolt;
import com.Polarice3.Goety.common.items.handler.FocusBagItemHandler;
import com.Polarice3.Goety.common.items.handler.SoulUsingItemHandler;
import com.github.tartaricacid.touhoulittlemaid.entity.passive.EntityMaid;
import com.github.yimeng261.maidspell.MaidSpellMod;
import com.github.yimeng261.maidspell.spell.data.MaidGoetySpellData;
import com.github.yimeng261.maidspell.spell.manager.SpellBookManager;
import com.github.yimeng261.maidspell.spell.providers.GoetyProvider;
import net.minecraft.core.BlockPos;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraftforge.gametest.GameTestHolder;
import net.minecraftforge.gametest.PrefixGameTestTemplate;
import net.minecraftforge.registries.ForgeRegistries;

/**
 * Regression test for the FocusBag-only path. The maid must cast through a
 * hidden IWand without the bag ever being treated as a real staff.
 */
@GameTestHolder(MaidSpellMod.MOD_ID)
@PrefixGameTestTemplate(false)
public final class GoetyVirtualWandGameTest {
    private static final ResourceLocation FOCUS_BAG =
            new ResourceLocation("goety", "focus_bag");
    private static final ResourceLocation MAGIC_BOLT_FOCUS =
            new ResourceLocation("goety", "magic_bolt_focus");
    private static final ResourceLocation DARK_WAND =
            new ResourceLocation("goety", "dark_wand");
    private static final ResourceLocation VEXING_FOCUS =
            new ResourceLocation("goety", "vexing_focus");
    private static final ResourceLocation BLIZZARD_FOCUS =
            new ResourceLocation("goety", "blizzard_focus");
    private static final ResourceLocation FROST_STAFF =
            new ResourceLocation("goety", "frost_staff");

    private GoetyVirtualWandGameTest() {
    }

    @GameTest(template = "gametest/empty", templateNamespace = MaidSpellMod.MOD_ID,
            timeoutTicks = 200)
    public static void castsWithFocusBagOnly(GameTestHelper helper) {
        VirtualWandManager.clearAll();
        ServerLevel level = helper.getLevel();
        EntityMaid maid = spawnMaid(helper, level, 0);
        Mob target = spawnTarget(helper, level);

        Item bagItem = ForgeRegistries.ITEMS.getValue(FOCUS_BAG);
        Item focusItem = ForgeRegistries.ITEMS.getValue(MAGIC_BOLT_FOCUS);
        if (bagItem == null || focusItem == null) {
            helper.fail("Goety focus_bag/magic_bolt_focus is missing");
            return;
        }

        ItemStack bag = new ItemStack(bagItem);
        ItemStack focus = new ItemStack(focusItem);
        FocusBagItemHandler bagHandler = FocusBagItemHandler.get(bag);
        bagHandler.setStackInSlot(0, focus);
        maid.getMaidInv().setStackInSlot(0, bag);

        GoetyProvider provider = getProvider(helper);
        if (provider == null) {
            return;
        }
        MaidGoetySpellData data = MaidGoetySpellData.getOrCreate(maid);
        data.getSpellBooks().add(bag);
        data.setTarget(target);

        provider.initiateCasting(maid);

        helper.assertFalse(data.isCasting(), "instant cast should have completed");
        helper.assertTrue(data.getVirtualWandId() == null,
                "virtual wand context should be released after an instant cast");
        helper.assertTrue(VirtualWandManager.activeContextCount() == 0,
                "no virtual wand context should remain");
        helper.assertTrue(data.getSpellCooldown("MagicBoltSpell") > 0,
                "the spell must actually run and start its cooldown");
        helper.assertTrue(!level.getEntitiesOfClass(MagicBolt.class,
                        maid.getBoundingBox().inflate(4.0D)).isEmpty(),
                "the virtual wand must have produced a MagicBolt projectile");
        helper.assertTrue(ItemStack.isSameItemSameTags(
                        bagHandler.getStackInSlot(0), focus),
                "focus must stay inside the FocusBag");
        helper.assertTrue(maid.getMainHandItem().isEmpty()
                        && maid.getOffhandItem().isEmpty(),
                "the test maid must not hold a real staff");
        helper.succeed();
    }

    @GameTest(template = "gametest/empty", templateNamespace = MaidSpellMod.MOD_ID,
            timeoutTicks = 200)
    public static void prefersMatchingRealWand(GameTestHelper helper) {
        VirtualWandManager.clearAll();
        ServerLevel level = helper.getLevel();
        EntityMaid maid = spawnMaid(helper, level, 0);
        Mob target = spawnTarget(helper, level);

        Item bagItem = ForgeRegistries.ITEMS.getValue(FOCUS_BAG);
        Item focusItem = ForgeRegistries.ITEMS.getValue(MAGIC_BOLT_FOCUS);
        Item wandItem = ForgeRegistries.ITEMS.getValue(DARK_WAND);
        if (bagItem == null || focusItem == null || wandItem == null) {
            helper.fail("Goety focus_bag/magic_bolt_focus/dark_wand is missing");
            return;
        }

        ItemStack bag = new ItemStack(bagItem);
        ItemStack focus = new ItemStack(focusItem);
        FocusBagItemHandler.get(bag).setStackInSlot(0, focus);
        maid.getMaidInv().setStackInSlot(0, bag);

        ItemStack wand = new ItemStack(wandItem);
        SoulUsingItemHandler.get(wand).setStackInSlot(0, focus.copy());
        maid.setItemInHand(net.minecraft.world.InteractionHand.MAIN_HAND, wand);

        GoetyProvider provider = getProvider(helper);
        if (provider == null) {
            return;
        }
        MaidGoetySpellData data = MaidGoetySpellData.getOrCreate(maid);
        data.getSpellBooks().add(bag);
        data.setTarget(target);

        provider.initiateCasting(maid);

        helper.assertTrue(data.getVirtualWandId() == null,
                "a matching real wand must not create a virtual wand");
        helper.assertTrue(data.getSpellCooldown("MagicBoltSpell") > 0,
                "the real-wand path must also actually cast");
        helper.assertTrue(ItemStack.isSameItemSameTags(
                        bagHandlerStack(bag), focus),
                "the bag focus must not be consumed when a real wand is used");
        helper.succeed();
    }

    @GameTest(template = "gametest/empty", templateNamespace = MaidSpellMod.MOD_ID,
            timeoutTicks = 200)
    public static void prefersMainHandWand(GameTestHelper helper) {
        castWithHandWand(helper, InteractionHand.MAIN_HAND);
    }

    @GameTest(template = "gametest/empty", templateNamespace = MaidSpellMod.MOD_ID,
            timeoutTicks = 200)
    public static void prefersOffHandWand(GameTestHelper helper) {
        castWithHandWand(helper, InteractionHand.OFF_HAND);
    }

    @GameTest(template = "gametest/empty", templateNamespace = MaidSpellMod.MOD_ID,
            timeoutTicks = 200)
    public static void ignoresBackpackWand(GameTestHelper helper) {
        VirtualWandManager.clearAll();
        ServerLevel level = helper.getLevel();
        EntityMaid maid = spawnMaid(helper, level, 0);
        Mob target = spawnTarget(helper, level);

        Item bagItem = ForgeRegistries.ITEMS.getValue(FOCUS_BAG);
        Item focusItem = ForgeRegistries.ITEMS.getValue(BLIZZARD_FOCUS);
        Item wandItem = ForgeRegistries.ITEMS.getValue(DARK_WAND);
        if (bagItem == null || focusItem == null || wandItem == null) {
            helper.fail("Goety focus bag/wand/blizzard focus items are missing");
            return;
        }

        ItemStack bag = new ItemStack(bagItem);
        ItemStack focus = new ItemStack(focusItem);
        FocusBagItemHandler.get(bag).setStackInSlot(0, focus);
        maid.getMaidInv().setStackInSlot(0, bag);

        ItemStack backpackWand = new ItemStack(wandItem);
        SoulUsingItemHandler.get(backpackWand).setStackInSlot(0, focus.copy());
        maid.getMaidInv().setStackInSlot(1, backpackWand);

        GoetyProvider provider = getProvider(helper);
        if (provider == null) {
            return;
        }
        MaidGoetySpellData data = MaidGoetySpellData.getOrCreate(maid);
        data.getSpellBooks().add(bag);
        data.setTarget(target);

        provider.initiateCasting(maid);

        helper.assertTrue(data.isCasting(), "Blizzard should start a continuous cast");
        helper.assertTrue(data.getCurrentStaff().getItem() instanceof VirtualGoetyWandItem,
                "a wand stored in the backpack must not be selected");
        helper.assertTrue(data.getVirtualWandId() != null,
                "the backpack-only case must use a virtual wand");
        helper.assertTrue(maid.getMainHandItem().isEmpty() && maid.getOffhandItem().isEmpty(),
                "the test maid must not hold a real staff");

        provider.stopCasting(maid);
        helper.assertTrue(data.getVirtualWandId() == null,
                "stopping the cast should release the virtual wand");
        helper.succeed();
    }

    @GameTest(template = "gametest/empty", templateNamespace = MaidSpellMod.MOD_ID,
            timeoutTicks = 200)
    public static void handStaffGrantsSchoolBonus(GameTestHelper helper) {
        VirtualWandManager.clearAll();
        ServerLevel level = helper.getLevel();
        EntityMaid maid = spawnMaid(helper, level, 0);
        Mob target = spawnTarget(helper, level);

        Item bagItem = ForgeRegistries.ITEMS.getValue(FOCUS_BAG);
        Item focusItem = ForgeRegistries.ITEMS.getValue(BLIZZARD_FOCUS);
        Item staffItem = ForgeRegistries.ITEMS.getValue(FROST_STAFF);
        if (bagItem == null || focusItem == null || staffItem == null) {
            helper.fail("Goety focus bag/blizzard focus/frost staff items are missing");
            return;
        }

        ItemStack bag = new ItemStack(bagItem);
        FocusBagItemHandler.get(bag).setStackInSlot(0, new ItemStack(focusItem));
        maid.getMaidInv().setStackInSlot(0, bag);
        maid.setItemInHand(InteractionHand.MAIN_HAND, new ItemStack(staffItem));

        GoetyProvider provider = getProvider(helper);
        if (provider == null) {
            return;
        }
        MaidGoetySpellData data = MaidGoetySpellData.getOrCreate(maid);
        data.getSpellBooks().add(bag);
        data.setTarget(target);

        provider.initiateCasting(maid);

        helper.assertTrue(data.isCasting(), "Blizzard should start a continuous cast");
        helper.assertTrue(data.getCurrentSpell().typeStaff(
                        data.getCurrentStaff(), SpellType.FROST),
                "a held frost staff must grant its matching school bonus");
        helper.assertFalse(((IWand) GoetyCompatItems.VIRTUAL_GOETY_WAND.get())
                        .getSpellTypes().contains(SpellType.FROST),
                "the virtual wand must not grant a school bonus");

        provider.stopCasting(maid);
        helper.succeed();
    }

    private static void castWithHandWand(GameTestHelper helper, InteractionHand hand) {
        VirtualWandManager.clearAll();
        ServerLevel level = helper.getLevel();
        EntityMaid maid = spawnMaid(helper, level, 0);
        Mob target = spawnTarget(helper, level);

        Item bagItem = ForgeRegistries.ITEMS.getValue(FOCUS_BAG);
        Item focusItem = ForgeRegistries.ITEMS.getValue(MAGIC_BOLT_FOCUS);
        Item wandItem = ForgeRegistries.ITEMS.getValue(DARK_WAND);
        Item otherFocusItem = ForgeRegistries.ITEMS.getValue(VEXING_FOCUS);
        if (bagItem == null || focusItem == null || wandItem == null || otherFocusItem == null) {
            helper.fail("Goety focus bag/wand/focus items are missing");
            return;
        }

        ItemStack bag = new ItemStack(bagItem);
        ItemStack focus = new ItemStack(focusItem);
        FocusBagItemHandler.get(bag).setStackInSlot(0, focus);
        maid.getMaidInv().setStackInSlot(0, bag);

        ItemStack wand = new ItemStack(wandItem);
        SoulUsingItemHandler.get(wand).setStackInSlot(0, new ItemStack(otherFocusItem));
        maid.setItemInHand(hand, wand);

        GoetyProvider provider = getProvider(helper);
        if (provider == null) {
            return;
        }
        MaidGoetySpellData data = MaidGoetySpellData.getOrCreate(maid);
        data.getSpellBooks().add(bag);
        data.setTarget(target);

        provider.initiateCasting(maid);

        helper.assertTrue(data.getVirtualWandId() == null,
                "a hand-held real wand must take priority over the virtual wand");
        helper.assertTrue(data.getSpellCooldown("MagicBoltSpell") > 0,
                "the hand-wand path must actually cast");
        helper.assertTrue(!level.getEntitiesOfClass(MagicBolt.class,
                        maid.getBoundingBox().inflate(4.0D)).isEmpty(),
                "the hand wand must have produced a MagicBolt projectile");
        helper.assertTrue(ItemStack.isSameItemSameTags(
                        bagHandlerStack(bag), focus),
                "the bag focus must stay untouched when a hand wand is used");
        helper.succeed();
    }

    private static GoetyProvider getProvider(GameTestHelper helper) {
        if (!(SpellBookManager.getProvider("goety") instanceof GoetyProvider provider)) {
            helper.fail("GoetyProvider is not registered");
            return null;
        }
        return provider;
    }

    private static EntityMaid spawnMaid(GameTestHelper helper, ServerLevel level, int x) {
        EntityMaid maid = new EntityMaid(level);
        maid.moveTo(helper.absolutePos(new BlockPos(x, 1, 1)), 0.0F, 0.0F);
        level.addFreshEntity(maid);
        return maid;
    }

    private static Mob spawnTarget(GameTestHelper helper, ServerLevel level) {
        Mob target = EntityType.ZOMBIE.create(level);
        if (target == null) {
            throw new IllegalStateException("Could not create the test target");
        }
        target.moveTo(helper.absolutePos(new BlockPos(3, 1, 1)), 0.0F, 0.0F);
        target.setNoAi(true);
        target.setInvulnerable(true);
        level.addFreshEntity(target);
        return target;
    }

    private static ItemStack bagHandlerStack(ItemStack bag) {
        return FocusBagItemHandler.get(bag).getStackInSlot(0);
    }
}
