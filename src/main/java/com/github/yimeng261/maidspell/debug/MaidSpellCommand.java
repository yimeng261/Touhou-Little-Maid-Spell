package com.github.yimeng261.maidspell.debug;

import com.github.tartaricacid.touhoulittlemaid.entity.passive.EntityMaid;
import com.github.yimeng261.maidspell.dimension.RetreatDimensionData;
import com.github.yimeng261.maidspell.dimension.RetreatManager;
import com.github.yimeng261.maidspell.event.FestivalGreetingManager;
import com.github.yimeng261.maidspell.spell.data.MaidIronsSpellData;
import com.mojang.brigadier.CommandDispatcher;
import com.mojang.brigadier.arguments.IntegerArgumentType;
import com.mojang.brigadier.arguments.StringArgumentType;
import com.mojang.brigadier.builder.LiteralArgumentBuilder;
import com.mojang.brigadier.context.CommandContext;
import com.mojang.brigadier.exceptions.CommandSyntaxException;
import com.mojang.brigadier.suggestion.SuggestionProvider;
import com.mojang.logging.LogUtils;
import io.redspace.ironsspellbooks.api.magic.MagicData;
import io.redspace.ironsspellbooks.api.registry.SpellRegistry;
import io.redspace.ironsspellbooks.api.spells.*;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.commands.SharedSuggestionProvider;
import net.minecraft.commands.arguments.EntityArgument;
import net.minecraft.commands.arguments.ResourceLocationArgument;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.ModList;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.event.RegisterCommandsEvent;
import org.slf4j.Logger;

import java.util.Collection;
import java.util.stream.Collectors;

/**
 * Maid Spell 统一命令
 * <p>
 * 用法:
 * /maidspell iron_cast <targets> <spell_id> [level]  - 女仆施法
 * /maidspell retreat reset <player>                   - 重置隐世之境额度
 * /maidspell festival <festival_id>                   - 节日祝福测试
 */
@EventBusSubscriber
public class MaidSpellCommand {
    private static final Logger LOGGER = LogUtils.getLogger();

    /**
     * 节日 ID 自动补全提供器
     */
    private static final SuggestionProvider<CommandSourceStack> FESTIVAL_SUGGESTIONS = (context, builder)
            -> SharedSuggestionProvider.suggest(FestivalGreetingManager.getAllFestivalIds(), builder);

    @SubscribeEvent
    public static void registerCommands(RegisterCommandsEvent event) {
        CommandDispatcher<CommandSourceStack> dispatcher = event.getDispatcher();

        LiteralArgumentBuilder<CommandSourceStack> root = Commands.literal("maidspell")
                .requires(source -> source.hasPermission(2));

        // 女仆施法命令
        // /maidspell iron_cast <targets> <spell_id> [level]
        if (ModList.get().isLoaded("irons_spellbooks")) {
            root.then(Commands.literal("iron_cast")
                    .then(Commands.argument("targets", EntityArgument.entities())
                            .then(Commands.argument("spell", ResourceLocationArgument.id())
                                    .suggests(IronSpellHelper.SPELL_SUGGESTIONS)
                                    // 无等级参数，默认等级为 1
                                    .executes(context -> IronSpellHelper.executeSpell(context, 1))
                                    // 带等级参数
                                    .then(Commands.argument("level", IntegerArgumentType.integer(1, 10))
                                            .executes(context -> IronSpellHelper.executeSpell(context, IntegerArgumentType.getInteger(context, "level")))
                                    )
                            )
                    )
            );
        }

        // /maidspell retreat reset <player>
        root.then(Commands.literal("retreat")
                .then(Commands.literal("reset")
                        .then(Commands.argument("player", EntityArgument.player())
                                .executes(MaidSpellCommand::executeRetreatReset)
                        )
                )
        );

        // 节日祝福测试命令
        // /maidspell festival <festival_id>
        root.then(Commands.literal("festival")
                .then(Commands.argument("festival_id", StringArgumentType.string())
                        .suggests(FESTIVAL_SUGGESTIONS)
                        .executes(MaidSpellCommand::executeFestivalGreeting)
                )
        );

        dispatcher.register(root);
    }

    /**
     * 执行归隐之地额度重置命令
     */
    private static int executeRetreatReset(CommandContext<CommandSourceStack> context) throws CommandSyntaxException {
        ServerPlayer player = EntityArgument.getPlayer(context, "player");
        RetreatDimensionData data = RetreatDimensionData.get(context.getSource().getServer());

        if (!data.hasDimension(player.getUUID())) {
            context.getSource().sendFailure(Component.translatable("command.maidspell.retreat.reset.no_record"));
            return 0;
        }

        data.removeDimension(player.getUUID());
        RetreatManager.updateCache(player.getUUID(), null);
        RetreatManager.removeCachedPlayerRetreat(player.getUUID());

        context.getSource().sendSuccess(() -> Component.translatable(
                "command.maidspell.retreat.reset.success", player.getName().getString()), true);
        return 1;
    }

    /**
     * 执行节日祝福测试命令
     */
    private static int executeFestivalGreeting(CommandContext<CommandSourceStack> context) throws CommandSyntaxException {
        String festivalId = StringArgumentType.getString(context, "festival_id");

        if (!(context.getSource().getEntity() instanceof ServerPlayer player)) {
            context.getSource().sendFailure(Component.literal("此命令只能由玩家执行"));
            return 0;
        }

        FestivalGreetingManager.sendTestGreeting(player, festivalId);
        return 1;
    }

    /**
     * 将 ISS 相关实现隔离到桥接类中，避免顶层命令类在可选依赖缺失时解析这些类型。
     */
    static final class IronSpellHelper {
        /**
         * 法术 ID 自动补全提供器
         */
        private static final SuggestionProvider<CommandSourceStack> SPELL_SUGGESTIONS = (context, builder) -> {
            Collection<String> spellIds = context.getSource().registryAccess()
                    .registry(SpellRegistry.SPELL_REGISTRY_KEY)
                    .orElseThrow(IllegalStateException::new)
                    .stream()
                    .filter(spell -> spell != SpellRegistry.none())
                    .map(AbstractSpell::getSpellId)
                    .collect(Collectors.toList());
            return SharedSuggestionProvider.suggest(spellIds, builder);
        };

        /**
         * 执行施法指令
         */
        private static int executeSpell(CommandContext<CommandSourceStack> context, int level) throws CommandSyntaxException {
            Collection<? extends Entity> targets = EntityArgument.getEntities(context, "targets");
            ResourceLocation spellId = ResourceLocationArgument.getId(context, "spell");

            AbstractSpell spell = SpellRegistry.getSpell(spellId);
            if (spell == null || spell == SpellRegistry.none()) {
                context.getSource().sendFailure(Component.literal("未找到法术: " + spellId));
                return 0;
            }

            int successCount = 0;
            int failCount = 0;

            for (Entity entity : targets) {
                if (!(entity instanceof EntityMaid maid)) {
                    failCount++;
                    continue;
                }

                try {
                    castSpellOnMaid(maid, spell, level);
                    successCount++;
                    LOGGER.debug("女仆 {} 施放了法术 {} (等级 {})", maid.getUUID(), spellId, level);
                } catch (Exception e) {
                    LOGGER.error("女仆 {} 施放法术 {} 失败: {}", maid.getUUID(), spellId, e.getMessage());
                    failCount++;
                }
            }

            if (successCount > 0) {
                final int success = successCount;
                context.getSource().sendSuccess(() -> Component.literal(
                        String.format("成功让 %d 个女仆施放 %s (等级 %d)", success, spellId, level)
                ), true);
            }

            if (failCount > 0) {
                context.getSource().sendFailure(Component.literal(
                        String.format("%d 个目标不是女仆或施法失败", failCount)
                ));
            }

            return successCount;
        }

        /**
         * 让女仆施放指定法术
         */
        private static void castSpellOnMaid(EntityMaid maid, AbstractSpell spell, int level) {
            MaidIronsSpellData data = MaidIronsSpellData.getOrCreate(maid);

            LivingEntity target = maid.getTarget();
            if (target != null) {
                data.setTarget(target);
            }

            SpellData spellData = new SpellData(spell, level);
            SpellSlot spellSlot = new SpellSlot(spellData, 0);
            MagicData magicData = data.getMagicData();

            if (!spell.checkPreCastConditions(maid.level(), level, maid, magicData)) {
                LOGGER.debug("法术 {} 前置条件检查失败", spell.getSpellId());
            }

            int effectiveCastTime = spell.getEffectiveCastTime(level, maid);
            CastSource castSource = CastSource.COMMAND;

            magicData.initiateCast(spell, level, effectiveCastTime, castSource, "command");
            spell.onServerPreCast(maid.level(), level, maid, magicData);

            data.setCurrentCastingSpell(spellSlot);
            data.setCachedCastSource(castSource);
            data.setCasting(true);

            if (spell.getCastType() == CastType.INSTANT || effectiveCastTime <= 0) {
                spell.onCast(maid.level(), level, maid, castSource, magicData);
                spell.onServerCastComplete(maid.level(), level, maid, magicData, false);
                data.resetCastingState();
            }
        }
    }
}
