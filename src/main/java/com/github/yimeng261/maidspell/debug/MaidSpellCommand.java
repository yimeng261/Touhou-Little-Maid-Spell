package com.github.yimeng261.maidspell.debug;

import com.github.tartaricacid.touhoulittlemaid.entity.passive.EntityMaid;
import com.github.yimeng261.maidspell.event.FestivalGreetingManager;
import com.mojang.brigadier.CommandDispatcher;
import com.mojang.brigadier.arguments.IntegerArgumentType;
import com.mojang.brigadier.arguments.StringArgumentType;
import com.mojang.brigadier.context.CommandContext;
import com.mojang.brigadier.exceptions.CommandSyntaxException;
import com.mojang.brigadier.suggestion.SuggestionProvider;
import com.mojang.logging.LogUtils;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.commands.SharedSuggestionProvider;
import net.minecraft.commands.arguments.EntityArgument;
import net.minecraft.commands.arguments.ResourceLocationArgument;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.Entity;
import net.minecraftforge.event.RegisterCommandsEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.ModList;
import net.minecraftforge.fml.common.Mod;
import org.slf4j.Logger;

import java.util.Collection;

/**
 * Maid Spell 指令
 * 使实体选择器选中的 EntityMaid 施放指定的 Iron's Spellbooks 法术
 * <p>
 * 用法: /maidspell <targets> <spell_id> [level]
 * 示例: /maidspell @e[type=touhoulittlemaid:maid,limit=1] irons_spellbooks:blood_slash
 * /maidspell @e[type=touhoulittlemaid:maid,limit=1] irons_spellbooks:fireball 5
 */
@Mod.EventBusSubscriber
public class MaidSpellCommand {
    private static final Logger LOGGER = LogUtils.getLogger();
    
    /**
     * 节日 ID 自动补全提供器
     */
    private static final SuggestionProvider<CommandSourceStack> FESTIVAL_SUGGESTIONS = (context, builder) -> {
        return SharedSuggestionProvider.suggest(FestivalGreetingManager.getAllFestivalIds(), builder);
    };

    @SubscribeEvent
    public static void registerCommands(RegisterCommandsEvent event) {
        CommandDispatcher<CommandSourceStack> dispatcher = event.getDispatcher();

        // 仅在安装 Iron's Spellbooks 时注册施法命令，避免可选依赖缺失时加载相关类
        if (ModList.get().isLoaded("irons_spellbooks")) {
            dispatcher.register(Commands.literal("maidspell")
                    .requires(source -> source.hasPermission(2))
                    .then(Commands.argument("targets", EntityArgument.entities())
                            .then(Commands.argument("spell", ResourceLocationArgument.id())
                                    .suggests(IronsSpellbooksCommandBridge.SPELL_SUGGESTIONS)
                                    .executes(context -> IronsSpellbooksCommandBridge.executeSpell(context, 1))
                                    .then(Commands.argument("level", IntegerArgumentType.integer(1, 10))
                                            .executes(context -> IronsSpellbooksCommandBridge.executeSpell(
                                                    context,
                                                    IntegerArgumentType.getInteger(context, "level")
                                            ))
                                    )
                            )
                    )
            );
        }
        
        // 节日祝福测试命令
        dispatcher.register(Commands.literal("maidspell_festival")
                .requires(source -> source.hasPermission(2)) // 需要 OP 权限
                .then(Commands.argument("festival_id", StringArgumentType.string())
                        .suggests(FESTIVAL_SUGGESTIONS)
                        .executes(context -> executeFestivalGreeting(context))
                )
        );
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
    private static final class IronsSpellbooksCommandBridge {
        private static final SuggestionProvider<CommandSourceStack> SPELL_SUGGESTIONS = (context, builder) -> {
            Collection<String> spellIds = io.redspace.ironsspellbooks.api.registry.SpellRegistry.REGISTRY.get()
                    .getValues()
                    .stream()
                    .filter(spell -> spell != io.redspace.ironsspellbooks.api.registry.SpellRegistry.none())
                    .map(io.redspace.ironsspellbooks.api.spells.AbstractSpell::getSpellId)
                    .toList();
            return SharedSuggestionProvider.suggest(spellIds, builder);
        };

        private static int executeSpell(CommandContext<CommandSourceStack> context, int level) throws CommandSyntaxException {
            Collection<? extends Entity> targets = EntityArgument.getEntities(context, "targets");
            ResourceLocation spellId = ResourceLocationArgument.getId(context, "spell");

            io.redspace.ironsspellbooks.api.spells.AbstractSpell spell =
                    io.redspace.ironsspellbooks.api.registry.SpellRegistry.getSpell(spellId);
            if (spell == null || spell == io.redspace.ironsspellbooks.api.registry.SpellRegistry.none()) {
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

        private static void castSpellOnMaid(EntityMaid maid, io.redspace.ironsspellbooks.api.spells.AbstractSpell spell, int level) {
            com.github.yimeng261.maidspell.spell.data.MaidIronsSpellData data =
                    com.github.yimeng261.maidspell.spell.data.MaidIronsSpellData.getOrCreate(maid);

            net.minecraft.world.entity.LivingEntity target = maid.getTarget();
            if (target != null) {
                data.setTarget(target);
            }

            io.redspace.ironsspellbooks.api.spells.SpellData spellData =
                    new io.redspace.ironsspellbooks.api.spells.SpellData(spell, level);
            io.redspace.ironsspellbooks.api.magic.MagicData magicData = data.getMagicData();

            if (!spell.checkPreCastConditions(maid.level(), level, maid, magicData)) {
                LOGGER.debug("法术 {} 前置条件检查失败", spell.getSpellId());
            }

            int effectiveCastTime = spell.getEffectiveCastTime(level, maid);
            io.redspace.ironsspellbooks.api.spells.CastSource castSource =
                    io.redspace.ironsspellbooks.api.spells.CastSource.COMMAND;

            magicData.initiateCast(spell, level, effectiveCastTime, castSource, "command");
            spell.onServerPreCast(maid.level(), level, maid, magicData);

            data.setCurrentCastingSpell(spellData);
            data.setCachedCastSource(castSource);
            data.setCasting(true);

            if (spell.getCastType() == io.redspace.ironsspellbooks.api.spells.CastType.INSTANT || effectiveCastTime <= 0) {
                spell.onCast(maid.level(), level, maid, castSource, magicData);
                spell.onServerCastComplete(maid.level(), level, maid, magicData, false);
                data.resetCastingState();
            }
        }
    }
}
