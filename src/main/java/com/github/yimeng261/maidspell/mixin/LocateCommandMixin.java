package com.github.yimeng261.maidspell.mixin;

import com.github.yimeng261.maidspell.Config;
import com.github.yimeng261.maidspell.MaidSpellMod;
import com.mojang.brigadier.exceptions.CommandSyntaxException;
import com.mojang.brigadier.exceptions.DynamicCommandExceptionType;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.arguments.ResourceOrTagKeyArgument;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.commands.LocateCommand;
import net.minecraft.world.level.levelgen.structure.Structure;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/**
 * Mixin到LocateCommand，阻止玩家使用 locate 指令查找结构
 */
@Mixin(LocateCommand.class)
public class LocateCommandMixin {
    @Unique
    private static final ResourceLocation HIDDEN_RETREAT_ID =
            ResourceLocation.fromNamespaceAndPath(MaidSpellMod.MOD_ID, "hidden_retreat");

    @Unique
    private static final DynamicCommandExceptionType ERROR_STRUCTURE_INVALID =
            new DynamicCommandExceptionType(id -> Component.translatable("commands.locate.structure.invalid", id));

    @Inject(method = "locateStructure", at = @At("HEAD"))
    private static void onLocateStructure(
            CommandSourceStack source,
            ResourceOrTagKeyArgument.Result<Structure> structure,
            CallbackInfoReturnable<Integer> cir) throws CommandSyntaxException {
        var key = structure.unwrap().left();
        if (key.isPresent()) {
            ResourceLocation structureId = key.get().location();
            if (HIDDEN_RETREAT_ID.equals(structureId)) {
                ResourceLocation dimId = source.getLevel().dimension().location();
                boolean inRetreat = dimId.getNamespace().equals(MaidSpellMod.MOD_ID)
                        && dimId.getPath().startsWith("the_retreat");
                if (!inRetreat) {
                    throw ERROR_STRUCTURE_INVALID.create(structureId.toString());
                }
                // 共享模式+配额限制下，/locate 无法获取搜索许可，阻止无意义的区块生成
                if (!Config.enablePrivateDimensions && Config.enableSharedQuotaLimit) {
                    throw ERROR_STRUCTURE_INVALID.create(structureId.toString());
                }
            }
        }
    }
}
