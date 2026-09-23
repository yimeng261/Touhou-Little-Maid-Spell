package com.github.yimeng261.maidspell.debug;

import com.github.yimeng261.maidspell.MaidSpellMod;
import com.github.yimeng261.maidspell.compat.curios.DreamCrystalCurios;
import com.github.yimeng261.maidspell.compat.irons_spellbooks.entity.winefox.MagicalWinefoxBossEntity;
import com.github.yimeng261.maidspell.compat.irons_spellbooks.entity.winefox.WinefoxRetiredStateAccessor;
import com.github.yimeng261.maidspell.compat.irons_spellbooks.registry.IronsSpellbooksCompatEntities;
import com.github.yimeng261.maidspell.entity.StarShadowSpearEntity;
import com.github.yimeng261.maidspell.item.MaidSpellItems;
import com.github.tartaricacid.touhoulittlemaid.entity.passive.EntityMaid;
import com.github.tartaricacid.touhoulittlemaid.init.InitEntities;
import com.mojang.authlib.GameProfile;
import net.minecraft.core.BlockPos;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.network.Connection;
import net.minecraft.network.protocol.PacketFlow;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.core.Direction;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.EntityHitResult;
import net.minecraft.world.phys.Vec3;
import net.minecraftforge.gametest.GameTestHolder;
import net.minecraftforge.gametest.PrefixGameTestTemplate;
import net.minecraftforge.fml.ModList;
import top.theillusivec4.curios.api.CuriosApi;
import net.minecraftforge.common.util.FakePlayerFactory;
import net.minecraftforge.event.TickEvent;
import com.github.yimeng261.maidspell.compat.curios.DreamCrystalPlayerEvents;
import java.util.UUID;

@GameTestHolder(MaidSpellMod.MOD_ID)
@PrefixGameTestTemplate(false)
public final class WinefoxGameTests {
    private WinefoxGameTests() {}

    @GameTest(template = "winefox_test_arena", timeoutTicks = 250)
    public static void challengeReturnsHomeAndCanRestart(GameTestHelper helper) {
        if (!ModList.get().isLoaded("irons_spellbooks")) { helper.succeed(); return; }
        MagicalWinefoxBossEntity boss = helper.spawn(IronsSpellbooksCompatEntities.MAGICAL_WINEFOX_BOSS.get(), 16, 1, 16);
        ServerPlayer player = player(helper, "duelist");
        player.moveTo(boss.position().add(6, 0, 0));
        ItemStack dagger = new ItemStack(MaidSpellItems.STARGLINT_DAGGER.get());
        player.setItemInHand(InteractionHand.MAIN_HAND, dagger);
        helper.assertTrue(boss.getMainHandItem().isEmpty(), "Seated boss must be empty-handed");
        helper.assertTrue(boss.getOffers().size() == 3, "Initial supply trades must be available");
        boss.mobInteract(player, InteractionHand.MAIN_HAND);
        helper.runAtTickTime(58, () -> helper.assertTrue(boss.isSeated(), "Challenge must wait three seconds"));
        helper.runAtTickTime(63, () -> {
            helper.assertTrue(!boss.isSeated(), "Challenge must start after three seconds");
            helper.assertTrue(boss.isBattleMusicActive(), "Challenge start must synchronize battle music");
            helper.assertTrue(boss.getAttributeValue(Attributes.ARMOR) == 20.0D,
                "Cosmetic equipment must not increase the designed armor");
            helper.assertTrue(boss.getAttributeValue(Attributes.ATTACK_DAMAGE) == 10.0D,
                "Cosmetic equipment must not increase the designed melee damage");
            helper.assertTrue(dagger.getCount() == 1, "Dagger must not be consumed");
            boss.cancelCast();
            boss.setNoAi(true);
            boss.hurt(player.damageSources().playerAttack(player), 100000.0F);
            helper.assertTrue(boss.isDefeated() && boss.getHealth() == 1.0F, "Boss must survive defeat at one health");
            helper.assertTrue(!boss.isBattleMusicActive(), "Boss defeat must stop battle music");
            helper.assertTrue(!boss.isRestricted() && boss.getOffers().size() > 3, "Fair win must unlock equipment");
        });
        helper.runAtTickTime(167, () -> {
            helper.assertTrue(boss.isSeated() && !boss.isDefeated(), "Boss must return to idle");
            helper.assertTrue(boss.getMainHandItem().isEmpty(), "Returning boss must be empty-handed");
            helper.assertTrue(boss.getHealth() == boss.getMaxHealth(), "Retry must start at full health");
            boss.mobInteract(player, InteractionHand.MAIN_HAND);
        });
        helper.runAtTickTime(230, () -> {
            helper.assertTrue(!boss.isSeated() && dagger.getCount() == 1, "Same dagger must start another challenge");
            helper.getLevel().getServer().getPlayerList().remove(player);
            boss.discard();
            helper.succeed();
        });
    }

    @GameTest(template = "winefox_test_arena", timeoutTicks = 150)
    public static void trueDamageFinisherCannotUnlockRewards(GameTestHelper helper) {
        if (!ModList.get().isLoaded("irons_spellbooks")) { helper.succeed(); return; }
        MagicalWinefoxBossEntity boss = helper.spawn(IronsSpellbooksCompatEntities.MAGICAL_WINEFOX_BOSS.get(), 16, 1, 16);
        ServerPlayer player = player(helper, "restricted");
        player.moveTo(boss.position().add(6, 0, 0));
        player.setItemInHand(InteractionHand.MAIN_HAND, new ItemStack(MaidSpellItems.STARGLINT_DAGGER.get()));
        boss.mobInteract(player, InteractionHand.MAIN_HAND);
        helper.runAtTickTime(63, () -> {
            boss.cancelCast();
            boss.setNoAi(true);
            boss.maidspell$redirectTrueDamage(100000.0F, player);
            helper.assertTrue(boss.isDefeated() && boss.isRestricted(), "True damage finisher must be counted before rewards");
            helper.assertTrue(boss.getOffers().size() == 3, "Restricted win must retain only supply trades");
            helper.getLevel().getServer().getPlayerList().remove(player);
            boss.discard();
            helper.succeed();
        });
    }

    @GameTest(template = "winefox_test_arena", timeoutTicks = 90)
    public static void spearDealsDirectAndSplashDamage(GameTestHelper helper) {
        LivingEntity shooter = helper.spawnWithNoFreeWill(EntityType.COW, 3, 1, 3);
        LivingEntity direct = helper.spawnWithNoFreeWill(EntityType.COW, 12, 1, 12);
        LivingEntity nearby = helper.spawnWithNoFreeWill(EntityType.COW, 14, 1, 12);
        direct.getAttribute(Attributes.MAX_HEALTH).setBaseValue(100);
        nearby.getAttribute(Attributes.MAX_HEALTH).setBaseValue(100);
        direct.setHealth(100);
        nearby.setHealth(100);
        class TestSpear extends StarShadowSpearEntity {
            TestSpear() { super(helper.getLevel(), shooter, new ItemStack(MaidSpellItems.STAR_SHADOW_SPEAR.get())); }
            void impactEntity() { onHit(new EntityHitResult(direct)); }
            void impactBlock() {
                onHit(new BlockHitResult(position(), Direction.NORTH, blockPosition(), false));
            }
        }
        TestSpear spear = new TestSpear();
        spear.setBossProjectile();
        spear.setDeltaMovement(new Vec3(1.0D, 0.0D, 0.0D));
        helper.getLevel().addFreshEntity(spear);
        spear.impactEntity();
        helper.assertTrue(direct.getHealth() == 80, "Spear direct damage must be 20");
        helper.assertTrue(nearby.getHealth() == 90, "Spear splash must be 10 without double-hitting primary target");
        helper.assertTrue(!spear.isRemoved(), "Entity hit must not remove or plant the spear");
        helper.assertTrue(spear.getDeltaMovement().equals(new Vec3(1.0D, 0.0D, 0.0D)),
            "Entity hit must preserve spear velocity");
        spear.impactBlock();
        helper.assertTrue(!spear.isRemoved(), "Spear must remain planted after impact");
        helper.assertTrue(spear.getDeltaMovement().equals(new Vec3(1.0D, 0.0D, 0.0D)),
            "Block hit must preserve spear velocity data");
        helper.runAtTickTime(59, () -> helper.assertTrue(!spear.isRemoved(), "Planted spear must last three seconds"));
        helper.runAtTickTime(62, () -> {
            helper.assertTrue(spear.isRemoved(), "Planted spear must disappear after three seconds");
            helper.succeed();
        });
    }

    @GameTest(template = "winefox_test_arena", timeoutTicks = 90)
    public static void crystalRefreshPreservesOccupiedExtraSlot(GameTestHelper helper) {
        if (!ModList.get().isLoaded("curios")) { helper.succeed(); return; }
        ServerPlayer player = player(helper, "crystal");
        player.moveTo(helper.absoluteVec(new net.minecraft.world.phys.Vec3(16, 1, 16)));
        var handler = CuriosApi.getCuriosInventory(player).resolve().orElseThrow();
        var slots = handler.getStacksHandler("curio").orElseThrow();
        int originalSlots = slots.getSlots();
        float originalHealth = player.getMaxHealth();
        handler.setEquippedCurio("curio", 0, new ItemStack(MaidSpellItems.DREAM_CAT_CRYSTAL.get()));
        // Forge fake players deliberately skip tick(); dispatch the wearable tick explicitly.
        for (int tick = 1; tick < 70; tick++) {
            int elapsedTick = tick;
            helper.runAtTickTime(tick, () -> {
                player.tickCount = elapsedTick;
                DreamCrystalPlayerEvents.tick(new TickEvent.PlayerTickEvent(TickEvent.Phase.END, player));
            });
        }
        helper.runAtTickTime(24, () -> {
            helper.assertTrue(slots.getSlots() == originalSlots + 1, "Crystal must add one slot: actual="
                + slots.getSlots() + ", base=" + originalSlots + ", crystal=" + DreamCrystalCurios.findCrystal(player)
                + ", health=" + player.getMaxHealth());
            helper.assertTrue(player.getMaxHealth() == originalHealth * 1.5F, "Crystal must boost player health");
            if (ModList.get().isLoaded("irons_spellbooks")) {
                helper.assertTrue(player.getAttributeValue(
                    io.redspace.ironsspellbooks.api.registry.AttributeRegistry.ENDER_SPELL_POWER.get()) == 2.0D,
                    "Crystal must double the player's Iron's Spells attributes");
                var cooldown = new io.redspace.ironsspellbooks.api.events.SpellCooldownAddedEvent.Pre(200,
                    io.redspace.ironsspellbooks.api.registry.SpellRegistry.MAGIC_MISSILE_SPELL.get(), player,
                    io.redspace.ironsspellbooks.api.spells.CastSource.SPELLBOOK);
                net.minecraftforge.common.MinecraftForge.EVENT_BUS.post(cooldown);
                helper.assertTrue(cooldown.getEffectiveCooldown() == 0, "Crystal must remove player spell cooldowns");
            }
            handler.setEquippedCurio("curio", originalSlots, new ItemStack(MaidSpellItems.CHAOS_BOOK.get()));
            boolean damaged = player.hurt(player.damageSources().fall(), 1000.0F);
            helper.assertTrue(player.isAlive() && player.getHealth() == player.getMaxHealth(),
                "First lethal hit must revive the player at full health: damaged=" + damaged
                    + ", health=" + player.getHealth() + ", max=" + player.getMaxHealth());
            helper.assertTrue(DreamCrystalCurios.findCrystal(player).getOrCreateTag()
                .getInt("dream_crystal_invulnerable_time") > 0, "Revival must grant invulnerability");
            player.hurt(player.damageSources().fall(), 1000.0F);
            helper.assertTrue(player.getHealth() == player.getMaxHealth(), "Revival protection must block follow-up damage");
            boolean poisoned = player.addEffect(new net.minecraft.world.effect.MobEffectInstance(
                net.minecraft.world.effect.MobEffects.POISON, 200));
            helper.assertTrue(!poisoned, "Crystal must reject harmful effects");
        });
        helper.runAtTickTime(66, () -> {
            helper.assertTrue(slots.getStacks().getStackInSlot(originalSlots).is(MaidSpellItems.CHAOS_BOOK.get()),
                "Periodic refresh must not eject an occupied extra slot");
            handler.setEquippedCurio("curio", 0, ItemStack.EMPTY);
        });
        helper.runAtTickTime(70, () -> {
            helper.assertTrue(DreamCrystalCurios.findCrystal(player).isEmpty(), "Crystal must be unequipped");
            helper.assertTrue(slots.getSlots() == originalSlots, "Unequip must restore slot count");
            helper.assertTrue(player.getMaxHealth() == originalHealth, "Unequip must restore player attributes");
            helper.getLevel().getServer().getPlayerList().remove(player);
            helper.succeed();
        });
    }

    private static ServerPlayer player(GameTestHelper helper, String name) {
        var server = helper.getLevel().getServer();
        ServerPlayer player = new ServerPlayer(server, helper.getLevel(), new GameProfile(UUID.randomUUID(), name));
        // Reuse Forge's no-op packet listener while retaining real player damage and death behavior.
        player.connection = FakePlayerFactory.get(helper.getLevel(), new GameProfile(UUID.randomUUID(), name + "_net")).connection;
        helper.getLevel().addNewPlayer(player);
        player.setGameMode(net.minecraft.world.level.GameType.SURVIVAL);
        for (int tick = 0; tick < 61; tick++) player.tick();
        return player;
    }

    @GameTest(template = "winefox_test_arena", timeoutTicks = 280)
    public static void phaseTwoKeepsDesignedDamageAfterHealing(GameTestHelper helper) {
        if (!ModList.get().isLoaded("irons_spellbooks")) { helper.succeed(); return; }
        MagicalWinefoxBossEntity boss = helper.spawn(IronsSpellbooksCompatEntities.MAGICAL_WINEFOX_BOSS.get(), 16, 1, 16);
        ServerPlayer player = player(helper, "phase_two");
        player.getAttribute(Attributes.MAX_HEALTH).setBaseValue(10000);
        player.setHealth(player.getMaxHealth());
        player.moveTo(boss.position().add(6, 0, 0));
        player.setItemInHand(InteractionHand.MAIN_HAND, new ItemStack(MaidSpellItems.STARGLINT_DAGGER.get()));
        boss.mobInteract(player, InteractionHand.MAIN_HAND);
        helper.runAtTickTime(63, () -> boss.setHealth(boss.getMaxHealth() * 0.49F));
        helper.runAtTickTime(195, () -> {
            helper.assertTrue(boss.isPhaseTwo() && !boss.isTransitioning(), "Half health must complete phase transition");
            helper.assertTrue(boss.getAttributeValue(Attributes.ATTACK_DAMAGE) == 10.0D,
                "Sword form must deal the designed base 10 damage");
            boss.heal(boss.getMaxHealth());
        });
        helper.runAtTickTime(220, () -> {
            helper.assertTrue(boss.isPhaseTwo() && !boss.isTransitioning(), "Healing must not replay the transition");
            helper.getLevel().getServer().getPlayerList().remove(player);
            boss.discard();
            helper.succeed();
        });
    }

    @GameTest(template = "winefox_test_arena", timeoutTicks = 100)
    public static void lethalDuelDamageEndsBattleAndLeavesOneHealth(GameTestHelper helper) {
        if (!ModList.get().isLoaded("irons_spellbooks")) { helper.succeed(); return; }
        MagicalWinefoxBossEntity boss = helper.spawn(IronsSpellbooksCompatEntities.MAGICAL_WINEFOX_BOSS.get(), 16, 1, 16);
        ServerPlayer player = player(helper, "nonlethal");
        player.moveTo(boss.position().add(6, 0, 0));
        player.setItemInHand(InteractionHand.MAIN_HAND, new ItemStack(MaidSpellItems.STARGLINT_DAGGER.get()));
        boss.mobInteract(player, InteractionHand.MAIN_HAND);
        helper.runAtTickTime(63, () -> {
            player.setSecondsOnFire(10);
            player.hurt(boss.damageSources().mobAttack(boss), 10000.0F);
            helper.assertTrue(player.isAlive() && player.getHealth() == 1.0F, "Duel must leave the challenger at one health");
            helper.assertTrue(!boss.isBattleActive(), "Lethal duel damage must immediately end combat");
            helper.assertTrue(!boss.isBattleMusicActive(), "Player defeat must stop battle music before returning home");
            helper.assertTrue(!player.isOnFire(), "Duel fire must not kill the player after combat");
            helper.getLevel().getServer().getPlayerList().remove(player);
            boss.discard();
            helper.succeed();
        });
    }

    @GameTest(template = "winefox_test_arena", timeoutTicks = 100)
    public static void ordinaryMobCanInterruptPlayerDuelTarget(GameTestHelper helper) {
        if (!ModList.get().isLoaded("irons_spellbooks")) { helper.succeed(); return; }
        MagicalWinefoxBossEntity boss = helper.spawn(IronsSpellbooksCompatEntities.MAGICAL_WINEFOX_BOSS.get(), 16, 1, 16);
        ServerPlayer player = player(helper, "mob_interrupt");
        player.moveTo(boss.position().add(6, 0, 0));
        player.setItemInHand(InteractionHand.MAIN_HAND, new ItemStack(MaidSpellItems.STARGLINT_DAGGER.get()));
        boss.mobInteract(player, InteractionHand.MAIN_HAND);
        LivingEntity attacker = helper.spawnWithNoFreeWill(EntityType.ZOMBIE, 22, 1, 16);
        helper.runAtTickTime(63, () -> {
            boss.cancelCast();
            boss.setNoAi(true);
            boss.hurt(attacker.damageSources().mobAttack(attacker), 20.0F);
            helper.assertTrue(boss.getTarget() == attacker,
                "A non-player attacker must become the target during a player duel");
            helper.assertTrue(!boss.isDefeated(), "Ordinary damage must not immediately end an active duel");
            attacker.discard();
            player.getServer().getPlayerList().remove(player);
            boss.discard();
            helper.succeed();
        });
    }

    @GameTest(template = "winefox_test_arena", timeoutTicks = 100)
    public static void ordinaryMobDefeatUsesBossReturnSequence(GameTestHelper helper) {
        if (!ModList.get().isLoaded("irons_spellbooks")) { helper.succeed(); return; }
        MagicalWinefoxBossEntity boss = helper.spawn(IronsSpellbooksCompatEntities.MAGICAL_WINEFOX_BOSS.get(), 16, 1, 16);
        LivingEntity attacker = helper.spawnWithNoFreeWill(EntityType.ZOMBIE, 22, 1, 16);
        float attackerHealth = attacker.getHealth();
        boss.hurt(attacker.damageSources().mobAttack(attacker), 100000.0F);
        helper.assertTrue(boss.isDefeated() && boss.getHealth() == 1.0F,
            "A non-player finisher must leave the boss at one health for the defeat sequence");
        helper.assertTrue(attacker.getHealth() == attackerHealth,
            "The non-player attacker must not receive the duel health floor");
        helper.runAtTickTime(110, () -> {
            helper.assertTrue(boss.isSeated() && !boss.isDefeated(),
                "A boss defeated by another mob must return to her original seated state");
            helper.assertTrue(boss.getHealth() == boss.getMaxHealth(),
                "Returning after an ordinary mob defeat must restore full boss health");
            attacker.discard();
            boss.discard();
            helper.succeed();
        });
    }

    /**
     * 坐姿待机时玩家的女仆不该自己开战：连目标都锁不上，绕过索敌的伤害也不叫醒她；
     * 只有玩家递星芒短剑才会起身，起身之后女仆才作为挑战参与者下场（致命伤只到 1 点血）。
     */
    @GameTest(template = "winefox_test_arena", timeoutTicks = 260)
    public static void seatedBossIgnoresOwnedMaidAggression(GameTestHelper helper) {
        if (!ModList.get().isLoaded("irons_spellbooks")) { helper.succeed(); return; }
        MagicalWinefoxBossEntity boss = helper.spawn(IronsSpellbooksCompatEntities.MAGICAL_WINEFOX_BOSS.get(), 16, 1, 16);
        ServerPlayer owner = player(helper, "maid_owner");
        owner.moveTo(boss.position().add(6, 0, 0));
        EntityMaid maid = helper.spawn(InitEntities.MAID.get(), 14, 1, 16);
        maid.setOwnerUUID(owner.getUUID());
        maid.setTame(true);
        // 女仆锁定她：LivingChangeTargetEvent 被取消，女仆的 ATTACK_TARGET 记忆落不下去。
        maid.setTarget(boss);
        helper.assertTrue(maid.getTarget() == null, "A seated winefox must not become a maid's target");
        helper.assertTrue(boss.isSeated(), "A maid must not wake the seated winefox");
        // 绕过索敌的伤害（范围法术 / 召唤物 / 弹体）同样不生效。
        float healthBefore = boss.getHealth();
        boss.hurt(boss.damageSources().mobAttack(maid), 100.0F);
        helper.assertTrue(boss.isSeated() && !boss.isBattleActive(),
            "Damage from an owned maid must not open a fight while she is seated");
        helper.assertTrue(boss.getHealth() == healthBefore,
            "That damage must not land: " + boss.getHealth() + " != " + healthBefore);
        // 玩家递短剑才是唯一入口。
        owner.setItemInHand(InteractionHand.MAIN_HAND, new ItemStack(MaidSpellItems.STARGLINT_DAGGER.get()));
        boss.mobInteract(owner, InteractionHand.MAIN_HAND);
        helper.runAtTickTime(63, () -> {
            helper.assertTrue(!boss.isSeated() && boss.isBattleActive(),
                "The dagger must still open the challenge");
            // 正式挑战里女仆是参与者：致命伤只到 1 点血并被劝退，不会被打死。
            maid.hurt(boss.damageSources().mobAttack(boss), 1000.0F);
            helper.assertTrue(maid.isAlive() && maid.getHealth() == 1.0F,
                "A participant maid must keep the one-health floor: " + maid.getHealth());
            helper.assertTrue(MagicalWinefoxBossEntity.isRetiredMaid(maid),
                "The defeated maid must retire in place instead of dying");
            // 劝退状态必须同时进同步位：客户端读不到 ForgeData，只看得到这一份；
            // 少了它客户端会放行魂符的本地预测，把渲染删掉而实体留在服务端。
            helper.assertTrue(((WinefoxRetiredStateAccessor) maid).maidspell$isWinefoxRetired(),
                "The retired state must be mirrored into synched data for the client");
            // 打服她：100t 战败演出之后归位，女仆被释放，同步位也要跟着清掉。
            boss.setNoAi(true);
            boss.hurt(owner.damageSources().playerAttack(owner), 100000.0F);
        });
        helper.runAtTickTime(200, () -> {
            helper.assertTrue(boss.isSeated() && !boss.isDefeated(),
                "The defeated boss must return to the swing");
            helper.assertTrue(!((WinefoxRetiredStateAccessor) maid).maidspell$isWinefoxRetired(),
                "Releasing the maid must clear the synched retired mirror");
            helper.getLevel().getServer().getPlayerList().remove(owner);
            maid.discard();
            boss.discard();
            helper.succeed();
        });
    }

    @GameTest(template = "winefox_test_arena", timeoutTicks = 140)
    public static void returnHomeIgnoresStaleFlightInput(GameTestHelper helper) {
        if (!ModList.get().isLoaded("irons_spellbooks")) { helper.succeed(); return; }
        MagicalWinefoxBossEntity boss = helper.spawn(IronsSpellbooksCompatEntities.MAGICAL_WINEFOX_BOSS.get(), 16, 1, 16);
        LivingEntity attacker = helper.spawnWithNoFreeWill(EntityType.ZOMBIE, 22, 1, 16);
        // FlyingMoveControl 处理完一条朝上的 MOVE_TO 之后，实体身上留下的正是这个组合：
        // yya = ±speedModifier * FLYING_SPEED，speed 是同一份值。
        boss.setYya(1.0F);
        boss.setSpeed(1.0F);
        boss.hurt(attacker.damageSources().mobAttack(attacker), 100000.0F);
        helper.assertTrue(boss.isDefeated(), "The finisher must start the defeat sequence");
        double startY = boss.getY();
        // 战败演出是 100t。归位期间 isImmobile() 为真，serverAiStep 整条不跑，
        // MoveControl 也就不会 tick —— 没被清掉的那份输入会被 travel() 逐 tick 累加，
        // 第 90t 早就该把她顶到几十格高了。
        helper.runAtTickTime(90, () -> helper.assertTrue(boss.getY() <= startY + 0.5D,
            "Stale flight input must not lift the boss while returning home: startY="
                + startY + ", y=" + boss.getY()));
        helper.runAtTickTime(115, () -> {
            helper.assertTrue(boss.isSeated() && !boss.isDefeated(), "Boss must still return to the swing");
            attacker.discard();
            boss.discard();
            helper.succeed();
        });
    }

    /** 虚空相变对所有伤害都追加虚空伤害，不再限于近战与末影法术。 */
    @GameTest(template = "winefox_test_arena", timeoutTicks = 40)
    public static void voidPhaseAddsVoidDamageToAnyHit(GameTestHelper helper) {
        if (!ModList.get().isLoaded("irons_spellbooks")) { helper.succeed(); return; }
        // 僵尸是攻击方，牛只是血包：这里验证的是「一次非末影系的直接伤害」也会带出追加伤害。
        LivingEntity attacker = helper.spawnWithNoFreeWill(EntityType.ZOMBIE, 3, 1, 3);
        LivingEntity target = helper.spawnWithNoFreeWill(EntityType.COW, 5, 1, 3);
        target.getAttribute(Attributes.MAX_HEALTH).setBaseValue(100);
        target.setHealth(100);

        // 先量一次没有虚空相变时的基准伤害，免得后面的断言被基础伤害本身蒙混过去。
        float before = target.getHealth();
        target.hurt(attacker.damageSources().mobAttack(attacker), 10.0F);
        helper.assertTrue(target.getHealth() == before - 10.0F,
            "The baseline hit must only deal its own 10 damage: health=" + target.getHealth());

        attacker.addEffect(new net.minecraft.world.effect.MobEffectInstance(
            com.github.yimeng261.maidspell.compat.irons_spellbooks.registry.IronsSpellbooksCompatEffects
                .VOID_PHASE.get(), 200, 0));
        before = target.getHealth();
        target.hurt(attacker.damageSources().mobAttack(attacker), 10.0F);

        // 追加的虚空伤害必须先于主伤害落地，所以这里要的是「掉得比 10 更多」。
        helper.assertTrue(target.getHealth() < before - 10.0F,
            "Void Phase must add void damage to every hit: health=" + target.getHealth()
                + ", before=" + before);

        attacker.discard();
        target.discard();
        helper.succeed();
    }
}
