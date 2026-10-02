package com.github.yimeng261.maidspell.stagewright.scenes;

import com.github.tartaricacid.touhoulittlemaid.entity.passive.EntityMaid;
import com.github.yimeng261.maidspell.Config;
import com.github.yimeng261.maidspell.api.IMaidSpellData;
import com.github.yimeng261.maidspell.api.ISpellBookProvider;
import com.github.yimeng261.maidspell.item.MaidSpellDataComponents;
import com.github.yimeng261.maidspell.spell.manager.AllianceManager;
import com.github.yimeng261.maidspell.spell.manager.SpellBookManager;
import com.github.yimeng261.maidspell.stagewright.support.Actors;
import com.github.yimeng261.maidspell.stagewright.support.Checks;
import com.github.yimeng261.maidspell.stagewright.support.Maids;
import com.github.yimeng261.maidspell.utils.PortableTimerMath;
import net.magicterra.stagewright.scene.Scene;
import net.magicterra.stagewright.scene.SceneContext;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.portal.DimensionTransition;
import net.minecraft.world.phys.Vec3;
import net.minecraft.world.scores.PlayerTeam;
import net.minecraft.world.scores.Scoreboard;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;

import static com.github.yimeng261.maidspell.stagewright.support.Checks.NS;

/**
 * 旧状态迁移与配置：旧版结盟队伍的清理、法术黑名单与配置项、春归计时改用主世界时间及旧物品计时迁移。
 */
public final class StateMigrationScenes {
    private static final String SPRING_BLOOM = NS + "spring_bloom_return";
    private static final int MIGRATION_GRACE = 20;
    private static final String FIREBOLT_BOOK = "irons_spellbooks:gold_spell_book[irons_spellbooks:spell_container="
            + "{maxSpells:1,mustEquip:false,spellWheel:true,data:[{id:\"irons_spellbooks:firebolt\",index:0,level:1,locked:false}]}]";

    private StateMigrationScenes() {
    }

    public static List<Scene> dedicatedServer() {
        List<Scene> scenes = new ArrayList<>();
        scenes.add(Checks.scene("legacy.allianceTeamsRemoved", 5, StateMigrationScenes::allianceTeams));
        scenes.add(Checks.scene("config.autoAllianceRemoved", 5, StateMigrationScenes::autoAllianceRemoved));
        scenes.add(Checks.scene("config.retreatRecordRetentionDaysDefault", 5, StateMigrationScenes::retentionDefault));
        scenes.add(Checks.superflat("config.spellBlacklistBlocksIronsSpell", 120, StateMigrationScenes::spellBlacklist));
        scenes.add(Checks.superflat("spring_bloom_return.stacksExpire", 40, StateMigrationScenes::stacksExpire));
        scenes.add(Checks.scene("spring_bloom_return.timersUseOverworldClockInNether", 40, StateMigrationScenes::netherClock)
                .withDimension("minecraft:the_nether"));
        scenes.add(Checks.superflat("spring_bloom_return.legacyTimersMigrate", 20, StateMigrationScenes::legacyTimers));
        scenes.add(Checks.superflat("spring_bloom_return.timersFollowItemAcrossMaids", 40, StateMigrationScenes::followsItem));
        scenes.add(Checks.superflat("spring_bloom_return.refundsCooldownAtFavorabilityLevel2", 40, StateMigrationScenes::refundAtLevel2));
        return scenes;
    }

    private static long now(SceneContext ctx) {
        return PortableTimerMath.overworldGameTime(ctx.server());
    }

    // ---- 结盟与配置 ----

    /** 以 maidspell_alliance_ 开头的旧队伍都被删掉，玩家自己建的队伍不动。 */
    private static void allianceTeams(SceneContext ctx) {
        Scoreboard scoreboard = ctx.server().getScoreboard();
        List<String> legacy = List.of("maidspell_alliance_0badf00d", "maidspell_alliance_deadbeef");
        for (String name : legacy) {
            if (scoreboard.getPlayerTeam(name) == null) {
                scoreboard.addPlayerTeam(name);
            }
        }
        PlayerTeam own = scoreboard.getPlayerTeam("tlms_player_team");
        PlayerTeam kept = own != null ? own : scoreboard.addPlayerTeam("tlms_player_team");
        ctx.cleanup(() -> scoreboard.removePlayerTeam(kept));
        AllianceManager.cleanupLegacyTeams(ctx.server());
        ctx.check(legacy.stream().filter(name -> scoreboard.getPlayerTeam(name) != null).toList()).as("清理后仍在的旧队伍").isEmpty();
        ctx.check(scoreboard.getPlayerTeam("tlms_player_team")).as("玩家自建的队伍").isNotNull();
    }

    /** 新生成的通用配置文件里没有 autoAllianceEnabled。 */
    private static void autoAllianceRemoved(SceneContext ctx) {
        Path file = ctx.server().getServerDirectory().resolve("config").resolve("touhou_little_maid_spell-common.toml");
        try {
            String text = Files.readString(file);
            ctx.check(text.contains("autoAllianceEnabled")).as(file + " 里有 autoAllianceEnabled").isFalse();
            ctx.check(text.contains("retreatRecordRetentionDays")).as(file + " 里有 retreatRecordRetentionDays").isTrue();
        } catch (IOException e) {
            ctx.fail("读不到通用配置文件 " + file + ": " + e.getMessage());
        }
    }

    /** retreatRecordRetentionDays 默认 0（不清理）。 */
    private static void retentionDefault(SceneContext ctx) {
        ctx.check(Config.retreatRecordRetentionDays).as("retreatRecordRetentionDays 当前值").isEqualTo(0);
    }

    /** 黑名单里的铁魔法法术女仆不会施放；移出黑名单后照常施放。 */
    private static void spellBlacklist(SceneContext ctx) {
        List<String> original = Config.ironsSpellBlacklist;
        Config.ironsSpellBlacklist = new ArrayList<>(List.of("irons_spellbooks:firebolt"));
        ctx.cleanup(() -> Config.ironsSpellBlacklist = original);
        ISpellBookProvider<?, ?> iss = SpellBookManager.getProvider("irons_spellbooks");
        EntityMaid maid = Actors.sittingMaid(ctx, 0, 0, 0);
        ItemStack book = Actors.parse(ctx, FIREBOLT_BOOK);
        maid.setItemSlot(EquipmentSlot.OFFHAND, book);
        Mob target = Actors.spawn(ctx, "minecraft:zombie", 0, 0, 5, true);
        Checks.after(ctx, 2, () -> {
            cast(maid, iss, book, target);
            Checks.after(ctx, 20, () -> {
                ctx.check(iss.isCasting(maid)).as("火球术在黑名单时女仆施法").isFalse();
                ctx.check(target.getHealth()).as("火球术在黑名单时目标生命").isEqualTo(target.getMaxHealth());
                Config.ironsSpellBlacklist = new ArrayList<>();
                cast(maid, iss, book, target);
                ctx.await(() -> iss.isCasting(maid) || target.getHealth() < target.getMaxHealth()).within(80)
                        .then(() -> ctx.passNote("移出黑名单后开始施法"));
            });
        });
    }

    private static void cast(EntityMaid maid, ISpellBookProvider<?, ?> iss, ItemStack book, Mob target) {
        SpellBookManager manager = SpellBookManager.getOrCreateManager(maid);
        manager.initSpellBooks(maid);
        manager.addSpellItem(maid, book);
        iss.setTarget(maid, target);
        manager.castSpell(maid);
    }

    // ---- 春归 ----

    /** 层数按主世界时间到期消失。 */
    private static void stacksExpire(SceneContext ctx) {
        EntityMaid maid = Actors.sittingMaid(ctx, 0, 0, 0);
        ItemStack stack = Actors.equipBauble(maid, SPRING_BLOOM);
        stack.set(MaidSpellDataComponents.SPRING_BLOOM_RETURN_CLOCK_VERSION.get(), 1);
        stack.set(MaidSpellDataComponents.SPRING_BLOOM_RETURN_EXPIRIES.get(), List.of(now(ctx) + 5));
        Checks.after(ctx, 15, () -> {
            Actors.castSpringBloom(maid);
            List<Long> expiries = Actors.springBloomExpiries(maid);
            ctx.check(expiries.stream().filter(expiry -> expiry <= now(ctx)).toList()).as("到期后仍在的层").isEmpty();
        });
    }

    /** 下界里施法：层数的到期时间是主世界时间 + 持续时间，不会立即过期也不会永不过期。 */
    private static void netherClock(SceneContext ctx) {
        EntityMaid maid = Actors.sittingMaid(ctx, 0, 0, 0);
        Actors.equipBauble(maid, SPRING_BLOOM);
        Checks.after(ctx, 2, () -> {
            long at = now(ctx);
            Actors.castSpringBloom(maid);
            ctx.check(Actors.springBloomExpiries(maid)).as("下界施法后的层数到期时间")
                    .isEqualTo(List.of(at + Config.springBloomReturnStackDurationTicks));
        });
    }

    /**
     * 旧物品（没有时钟版本号、按所在维度时间记录）：层数和冷却大致保留（剩余时间最多多出迁移余量），
     * 不会变成永久冷却，也不会一次性清空。
     */
    private static void legacyTimers(SceneContext ctx) {
        EntityMaid maid = Actors.sittingMaid(ctx, 0, 0, 0);
        long local = maid.level().getGameTime();
        ItemStack stack = Actors.stack(SPRING_BLOOM);
        stack.set(MaidSpellDataComponents.SPRING_BLOOM_RETURN_EXPIRIES.get(), List.of(local + 200, local + 300));
        stack.set(MaidSpellDataComponents.SPRING_BLOOM_RETURN_TRIGGER_COOLDOWN_UNTIL.get(), local + 10_000_000L);
        maid.getMaidBauble().setStackInSlot(0, stack);
        ItemStack worn = maid.getMaidBauble().getStackInSlot(0);
        Checks.after(ctx, 2, () -> {
            long now = now(ctx);
            Actors.castSpringBloom(maid);
            List<Long> expiries = Actors.springBloomExpiries(maid);
            ctx.record("expiries", expiries);
            ctx.check(worn.get(MaidSpellDataComponents.SPRING_BLOOM_RETURN_CLOCK_VERSION.get())).as("迁移后的时钟版本").isEqualTo(1);
            ctx.check(expiries.size()).as("迁移后保留的层数（施法冷却内不新增）").isBetween(2, 3);
            ctx.check(expiries.stream().allMatch(expiry -> expiry > now
                    && expiry <= now + Config.springBloomReturnStackDurationTicks + MIGRATION_GRACE)).as("迁移后各层剩余时间合理").isTrue();
            Long trigger = worn.get(MaidSpellDataComponents.SPRING_BLOOM_RETURN_TRIGGER_COOLDOWN_UNTIL.get());
            ctx.check(trigger == null || trigger - now <= Config.springBloomReturnTriggerCooldownTicks + MIGRATION_GRACE)
                    .as("迁移后的触发冷却不超过一次冷却时长（" + trigger + "）").isTrue();
        });
    }

    /** 春归交给另一只在下界的女仆后重新装备：层数与到期时间不变。 */
    private static void followsItem(SceneContext ctx) {
        EntityMaid first = Actors.sittingMaid(ctx, 0, 0, 0);
        Actors.equipBauble(first, SPRING_BLOOM);
        Checks.after(ctx, 2, () -> {
            Actors.castSpringBloom(first);
            List<Long> before = Actors.springBloomExpiries(first);
            ItemStack moved = Maids.takeOff(first, 0);
            ServerLevel nether = ctx.server().getLevel(Level.NETHER);
            EntityMaid second = Actors.sittingMaid(ctx, 2, 0, 0);
            EntityMaid inNether = (EntityMaid) Maids.copyToDimension(ctx, second, nether, new Vec3(0.5, 64, 0.5));
            ctx.check(inNether).as("传送到下界的女仆").isNotNull();
            if (inNether == null) {
                return;
            }
            ctx.cleanup(() -> Actors.cleanupEntity(inNether));
            Maids.putOn(inNether, 0, moved);
            Checks.after(ctx, 5, () -> ctx.check(Actors.springBloomExpiries(inNether)).as("换女仆、换维度后的层").isEqualTo(before));
        });
    }

    /** 好感度 2 级：触发时按比例退还法术冷却。 */
    private static void refundAtLevel2(SceneContext ctx) {
        EntityMaid maid = Actors.sittingMaid(ctx, 0, 0, 0);
        Actors.equipBauble(maid, SPRING_BLOOM);
        maid.setFavorability(maid.getFavorabilityManager().getPointByLevel(2));
        ISpellBookProvider<?, ?> iss = SpellBookManager.getProvider("irons_spellbooks");
        ItemStack book = Actors.parse(ctx, FIREBOLT_BOOK);
        maid.setItemSlot(EquipmentSlot.OFFHAND, book);
        Checks.after(ctx, 2, () -> {
            SpellBookManager.getOrCreateManager(maid).addSpellItem(maid, book);
            IMaidSpellData data = SpellLifecycleScenes.data(iss, maid.getUUID());
            ctx.check(data).as("铁魔法法术数据").isNotNull();
            if (data == null) {
                return;
            }
            data.setSpellCooldown("irons_spellbooks:firebolt", 1000, maid);
            int before = data.getSpellCooldown("irons_spellbooks:firebolt");
            Actors.castSpringBloom(maid);
            Actors.setMaidHealth(maid, maid.getMaxHealth() / 2);
            maid.invulnerableTime = 0;
            maid.hurt(maid.damageSources().generic(), 8);
            int after = data.getSpellCooldown("irons_spellbooks:firebolt");
            ctx.record("cooldown", before + " -> " + after);
            ctx.check(after).as("触发后火球术冷却（原 " + before + "）").isLessThan(before);
        });
    }
}
