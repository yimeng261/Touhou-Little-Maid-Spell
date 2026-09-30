package com.github.yimeng261.maidspell.stagewright.scenes;

import com.github.tartaricacid.touhoulittlemaid.entity.passive.EntityMaid;
import com.github.tartaricacid.touhoulittlemaid.entity.task.TaskManager;
import com.github.yimeng261.maidspell.api.IMaidSpellData;
import com.github.yimeng261.maidspell.api.ISpellBookProvider;
import com.github.yimeng261.maidspell.spell.manager.SpellBookManager;
import com.github.yimeng261.maidspell.stagewright.support.Actors;
import com.github.yimeng261.maidspell.stagewright.support.Checks;
import com.github.yimeng261.maidspell.stagewright.support.Maids;
import com.github.yimeng261.maidspell.stagewright.support.Owners;
import com.github.yimeng261.maidspell.stagewright.support.Players;
import com.github.yimeng261.maidspell.stagewright.support.Reflect;
import com.github.yimeng261.maidspell.task.SpellCombatFarTask;
import com.github.yimeng261.maidspell.task.SpellCombatMeleeTask;
import net.magicterra.stagewright.scene.Scene;
import net.magicterra.stagewright.scene.SceneContext;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.Vec3;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

import static com.github.yimeng261.maidspell.stagewright.support.Checks.NS;

/**
 * 女仆法术数据的生命周期（专用服，主人为模拟玩家）：每个联动模组各验证一遍
 * 收进魂符再放出、区块卸载后 5 秒内回来与超过 5 秒、换维度、死亡、被复活饰品救下时法术书与冷却的去留；
 * 铁魔法真实施法中收进魂符、打开背包时停止施法；法术战斗任务的女仆被设成 NoAI 后自动恢复。
 */
public final class SpellLifecycleScenes {
    /** 各联动模组的法术提供者 ID。 */
    public static final List<String> MODS = List.of("irons_spellbooks", "ars_nouveau", "psi", "goety", "usefulmagic", "slashblade");
    /** 离开世界后法术数据保留的 tick 数。 */
    private static final int RETENTION = 100;
    private static final String PROBE_COOLDOWN = NS + "lifecycle_probe";
    private static final String CONE_OF_COLD_BOOK = "irons_spellbooks:gold_spell_book[irons_spellbooks:spell_container="
            + "{maxSpells:1,mustEquip:false,spellWheel:true,data:[{id:\"irons_spellbooks:cone_of_cold\",index:0,level:5,locked:false}]}]";

    private SpellLifecycleScenes() {
    }

    public static List<Scene> dedicatedServer() {
        List<Scene> scenes = new ArrayList<>();
        for (String mod : MODS) {
            scenes.add(Checks.superflat("spell.lifecycle." + mod + ".slabRoundTrip", 100, ctx -> slabRoundTrip(ctx, mod)));
            scenes.add(Checks.superflat("spell.lifecycle." + mod + ".unloadBriefly", RETENTION + 40, ctx -> unload(ctx, mod, RETENTION / 2)));
            scenes.add(Checks.superflat("spell.lifecycle." + mod + ".unloadLong", RETENTION * 2 + 40, ctx -> unload(ctx, mod, RETENTION + 20)));
            scenes.add(Checks.superflat("spell.lifecycle." + mod + ".dimensionChange", 100, ctx -> dimensionChange(ctx, mod)));
            scenes.add(Checks.superflat("spell.lifecycle." + mod + ".death", 40, ctx -> death(ctx, mod, false)));
            scenes.add(Checks.superflat("spell.lifecycle." + mod + ".revived", 100, ctx -> death(ctx, mod, true)));
        }
        scenes.add(Checks.superflat("spell.lifecycle.irons_spellbooks.slabWhileCasting", 1600, SpellLifecycleScenes::slabWhileCasting));
        scenes.add(Checks.superflat("spell.lifecycle.irons_spellbooks.backpackStopsCasting", 1600, SpellLifecycleScenes::backpackStopsCasting));
        scenes.add(Checks.superflat("spell.task.noAiRestoredForSpellTasks", 80, SpellLifecycleScenes::noAiRestored));
        return scenes;
    }

    // ---- 工具 ----

    private static ISpellBookProvider<?, ?> provider(SceneContext ctx, String mod) {
        ISpellBookProvider<?, ?> provider = SpellBookManager.getProvider(mod);
        if (provider == null) {
            ctx.skip(mod + " 的法术提供者未注册");
        }
        return provider;
    }

    static IMaidSpellData data(ISpellBookProvider<?, ?> provider, UUID maid) {
        return Reflect.call(provider, ISpellBookProvider.class, "getExistingData", new Class<?>[]{UUID.class}, maid);
    }

    /** 主人为模拟玩家、主手拿着该模组法术书、有一条探测冷却的女仆。 */
    private static Setup setup(SceneContext ctx, String mod) {
        ISpellBookProvider<?, ?> provider = provider(ctx, mod);
        ServerPlayer owner = Owners.visitor(ctx, "TlmsSpellOwner", 0, 0, -3);
        EntityMaid maid = Actors.sittingMaid(ctx, 0, 0, 0);
        ctx.check(Players.tame(owner, maid)).as("驯服女仆").isTrue();
        com.github.yimeng261.maidspell.stagewright.support.SpellFixtures.equip(ctx, mod, provider, owner, maid);
        return new Setup(provider, owner, maid, maid.getUUID());
    }

    private record Setup(ISpellBookProvider<?, ?> provider, ServerPlayer owner, EntityMaid maid, UUID id) {
        /** 等法术书登记进数据后，记一条 400 tick 的探测冷却，再运行 next。 */
        void ready(SceneContext ctx, Runnable next) {
            ctx.await(() -> {
                IMaidSpellData data = data(provider, id);
                return data != null && !data.getSpellBooks().isEmpty();
            }).within(20).then(() -> {
                data(provider, id).setSpellCooldown(PROBE_COOLDOWN, 400, maid);
                next.run();
            });
        }

        /** 法术书仍登记着、探测冷却仍在、不在施法。 */
        void expectKept(SceneContext ctx, String label) {
            expectKept(ctx, label, true);
        }

        void expectKept(SceneContext ctx, String label, boolean keepCooldown) {
            IMaidSpellData data = data(provider, id);
            ctx.check(data).as(label + " 的法术数据").isNotNull();
            if (data == null) {
                return;
            }
            ctx.check(data.getSpellBooks().size()).as(label + " 登记的法术书数").isEqualTo(1);
            if (keepCooldown) {
                ctx.check(data.getSpellCooldown(PROBE_COOLDOWN)).as(label + " 的探测冷却").isGreaterThan(0);
            } else {
                ctx.check(data.getSpellCooldown(PROBE_COOLDOWN)).as(label + " 水晶清零探测冷却").isEqualTo(0);
            }
            ctx.check(data.isCasting()).as(label + " 仍在施法").isFalse();
        }
    }

    // ---- 各模组 ----

    /** 收进魂符再立即放出：法术书、冷却都在，不在施法；放出后能实际施法。 */
    private static void slabRoundTrip(SceneContext ctx, String mod) {
        Setup s = setup(ctx, mod);
        s.ready(ctx, () -> {
            ItemStack slab = Players.storeInSlab(s.owner(), s.maid());
            ctx.check(s.maid().isRemoved()).as("女仆已收进魂符").isTrue();
            Players.releaseSlab(ctx, s.owner(), slab, ctx.rel(0, -1, 2), () -> Checks.after(ctx, 2, () -> {
                Entity released = Maids.find(ctx.level(), s.id());
                ctx.check(released instanceof EntityMaid).as("魂符放出了同一只女仆").isTrue();
                if (!(released instanceof EntityMaid maid)) {
                    return;
                }
                ctx.cleanup(() -> Actors.cleanupEntity(maid));
                s.expectKept(ctx, "放出后");
                expectCast(ctx, s.provider(), maid);
            }));
        });
    }

    /** 随区块卸载离开世界：away tick 内回来数据仍在；超过保留时间后数据被删除。 */
    private static void unload(SceneContext ctx, String mod, int away) {
        Setup s = setup(ctx, mod);
        s.ready(ctx, () -> Maids.reload(ctx, s.maid(), away, loaded -> {
            if (away < RETENTION) {
                s.expectKept(ctx, "卸载 " + away + " tick 后回来");
            } else {
                IMaidSpellData data = data(s.provider(), s.id());
                ctx.check(data == null || data.getSpellCooldown(PROBE_COOLDOWN) == 0)
                        .as("卸载超过保留时间后回来，旧冷却已随数据删除").isTrue();
                ctx.check(data != null && data.getSpellBooks().size() == 1).as("回来后按背包重新登记法术书").isTrue();
            }
        }));
    }

    /** 跟随主人换维度（传送到下界）：法术书与冷却随女仆过去，能开始施法。 */
    private static void dimensionChange(SceneContext ctx, String mod) {
        Setup s = setup(ctx, mod);
        s.ready(ctx, () -> {
            ServerLevel nether = ctx.server().getLevel(Level.NETHER);
            boolean forced = nether.getForcedChunks().contains(net.minecraft.world.level.ChunkPos.asLong(0, 0));
            if (!forced) {
                nether.setChunkForced(0, 0, true);
                ctx.cleanup(() -> nether.setChunkForced(0, 0, false));
            }
            nether.getChunk(0, 0);
            Entity moved = Maids.copyToDimension(ctx, s.maid(), nether, new Vec3(0.5, 64, 0.5));
            ctx.check(moved instanceof EntityMaid).as("换维度后的女仆").isTrue();
            if (!(moved instanceof EntityMaid maid)) {
                return;
            }
            ctx.cleanup(() -> Actors.cleanupEntity(maid));
            Checks.after(ctx, 2, () -> {
                s.expectKept(ctx, "换维度后");
                expectCast(ctx, s.provider(), maid);
            });
        });
    }

    /** 真正死亡时法术数据立即清掉；梦云水晶救下时法术书保留、冷却清零。 */
    private static void death(SceneContext ctx, String mod, boolean revived) {
        Setup s = setup(ctx, mod);
        if (revived) {
            Actors.equipBauble(s.maid(), NS + "dream_cat_crystal");
        }
        s.ready(ctx, () -> {
            Maids.kill(s.maid());
            ctx.check(s.maid().isAlive()).as("致死后女仆存活").isEqualTo(revived);
            if (revived) {
                ctx.check(s.maid().getHealth()).as("水晶复活恢复满血").isCloseTo(s.maid().getMaxHealth(), 1e-3f);
                s.expectKept(ctx, "被救下后", false);
                expectCast(ctx, s.provider(), s.maid());
            } else {
                ctx.check(data(s.provider(), s.id())).as("死亡后的法术数据").isNull();
            }
        });
    }

    /** 目标与施法者处于同一维度，启动施法、发射弹体或造成伤害才算恢复成功。 */
    private static void expectCast(SceneContext ctx, ISpellBookProvider<?, ?> provider, EntityMaid maid) {
        var target = net.minecraft.world.entity.EntityType.HUSK.create(maid.level());
        if (target == null) throw new IllegalStateException("无法创建施法目标");
        target.setNoAi(true);
        target.setPersistenceRequired();
        target.setNoGravity(true);
        target.noPhysics = true;
        target.moveTo(maid.getX(), maid.getY(), maid.getZ() + 2, 0, 0);
        maid.level().addFreshEntity(target);
        ctx.cleanup(() -> Actors.cleanupEntity(target));
        float health = target.getHealth();
        java.util.Set<Integer> existingProjectiles = maid.level().getEntities(maid, maid.getBoundingBox().inflate(8),
                entity -> entity instanceof net.minecraft.world.entity.projectile.Projectile).stream()
                .map(Entity::getId).collect(java.util.stream.Collectors.toSet());
        ctx.await(() -> {
            provider.setTarget(maid, target);
            provider.castSpell(maid);
            List<Entity> firedProjectiles = maid.level().getEntities(maid, maid.getBoundingBox().inflate(8),
                    entity -> entity instanceof net.minecraft.world.entity.projectile.Projectile projectile
                            && projectile.getOwner() == maid && !existingProjectiles.contains(entity.getId()));
            firedProjectiles.forEach(projectile -> ctx.cleanup(() -> Actors.cleanupEntity(projectile)));
            boolean fired = !firedProjectiles.isEmpty();
            ctx.record("castTargetHealth", target.getHealth());
            ctx.record("castMaidAlive", maid.isAlive());
            ctx.record("castProjectileCreated", fired);
            return provider.isCasting(maid) || target.getHealth() < health || fired;
        }).within(40).then(() -> ctx.passNote("生命周期恢复后已实际施法、发射弹体或命中目标"));
    }

    // ---- 铁魔法真实施法 ----

    /** 开始施放持续法术（冰锥术）的铁魔法女仆。 */
    private static EntityMaid castingMaid(SceneContext ctx, ServerPlayer owner, Mob target) {
        EntityMaid maid = Actors.sittingMaid(ctx, 0, 0, 0);
        ctx.check(Players.tame(owner, maid)).as("驯服女仆").isTrue();
        ItemStack book = Actors.parse(ctx, CONE_OF_COLD_BOOK);
        maid.setItemSlot(EquipmentSlot.OFFHAND, book);
        SpellBookManager manager = SpellBookManager.getOrCreateManager(maid);
        manager.addSpellItem(maid, book);
        SpellBookManager.getProvider("irons_spellbooks").setTarget(maid, target);
        manager.castSpell(maid);
        return maid;
    }

    /** 施法中收进魂符再放出：不崩溃、不在施法、法术书仍在，可以再次施法。 */
    private static void slabWhileCasting(SceneContext ctx) {
        ISpellBookProvider<?, ?> iss = provider(ctx, "irons_spellbooks");
        ServerPlayer owner = Owners.visitor(ctx, "TlmsSpellOwner", 0, 0, -3);
        Mob target = Actors.spawn(ctx, "minecraft:zombie", 0, 0, 5, true);
        EntityMaid maid = castingMaid(ctx, owner, target);
        UUID id = maid.getUUID();
        ctx.await(() -> iss.isCasting(maid)).within(40).then(() -> {
            ItemStack slab = Players.storeInSlab(owner, maid);
            Players.releaseSlab(ctx, owner, slab, ctx.rel(0, -1, 2), () -> Checks.after(ctx, 2, () -> {
                Entity released = Maids.find(ctx.level(), id);
                ctx.check(released instanceof EntityMaid).as("魂符放出了同一只女仆").isTrue();
                if (!(released instanceof EntityMaid again)) {
                    return;
                }
                ctx.cleanup(() -> Actors.cleanupEntity(again));
                ctx.check(iss.isCasting(again)).as("放出后仍在施法").isFalse();
                IMaidSpellData data = data(iss, id);
                ctx.check(data != null && !data.getSpellBooks().isEmpty()).as("放出后法术书仍登记").isTrue();
                castAfterCooldown(ctx, iss, again, target, "放出后能再次施法");
            }));
        });
    }

    /** 打开女仆背包时停止施法；关闭后能恢复施法。 */
    private static void backpackStopsCasting(SceneContext ctx) {
        ISpellBookProvider<?, ?> iss = provider(ctx, "irons_spellbooks");
        ServerPlayer owner = Owners.visitor(ctx, "TlmsSpellOwner", 0, 0, -3);
        Mob target = Actors.spawn(ctx, "minecraft:zombie", 0, 0, 5, true);
        EntityMaid maid = castingMaid(ctx, owner, target);
        ctx.await(() -> iss.isCasting(maid)).within(40).then(() -> {
            maid.openMaidGui(owner);
            ctx.await(() -> !iss.isCasting(maid)).within(10).then(() -> {
                owner.closeContainer();
                Checks.after(ctx, 2, () -> {
                    castAfterCooldown(ctx, iss, maid, target, "关闭背包后能再次施法");
                });
            });
        });
    }

    /** 中断施法会进入正常冷却，冷却自然结束后重新发起施法。 */
    private static void castAfterCooldown(SceneContext ctx, ISpellBookProvider<?, ?> iss,
                                          EntityMaid maid, Mob target, String note) {
        IMaidSpellData data = data(iss, maid.getUUID());
        String spell = "irons_spellbooks:cone_of_cold";
        ctx.record("interruptedSpellCooldown", data.getSpellCooldown(spell));
        ctx.await(() -> data.getSpellCooldown(spell) == 0).within(1400).then(() -> {
            iss.setTarget(maid, target);
            SpellBookManager.getOrCreateManager(maid).castSpell(maid);
            ctx.await(() -> iss.isCasting(maid)).within(40).then(() -> ctx.passNote(note));
        });
    }

    // ---- 任务 ----

    /** 法术近战、法术远程任务的女仆被设成 NoAI 后约 1 秒内自动恢复；其他任务的女仆保持 NoAI。 */
    private static void noAiRestored(SceneContext ctx) {
        EntityMaid melee = Actors.sittingMaid(ctx, 2, 0, 0);
        EntityMaid far = Actors.sittingMaid(ctx, -2, 0, 0);
        EntityMaid idle = Actors.sittingMaid(ctx, 0, 0, 2);
        melee.setTask(TaskManager.findTask(SpellCombatMeleeTask.UID).orElseThrow());
        far.setTask(TaskManager.findTask(SpellCombatFarTask.UID).orElseThrow());
        for (EntityMaid maid : List.of(melee, far, idle)) {
            maid.setNoAi(true);
        }
        Checks.after(ctx, 45, () -> {
            ctx.check(melee.isNoAi()).as("法术近战女仆 45 tick 后仍 NoAI").isFalse();
            ctx.check(far.isNoAi()).as("法术远程女仆 45 tick 后仍 NoAI").isFalse();
            ctx.check(idle.isNoAi()).as("其他任务的女仆 45 tick 后仍 NoAI").isTrue();
        });
    }
}
