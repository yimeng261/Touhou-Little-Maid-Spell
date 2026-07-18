package com.github.yimeng261.maidspell.mixin.ftbteams;

import com.github.yimeng261.maidspell.MaidSpellMod;
import com.github.yimeng261.maidspell.compat.ftbteams.FTBTeamsCompat;
import dev.ftb.mods.ftbteams.api.property.TeamPropertyCollection;
import dev.ftb.mods.ftbteams.data.TeamPropertyCollectionImpl;
import dev.ftb.mods.ftbteams.net.OpenMyTeamGUIMessage;
import net.minecraft.server.level.ServerPlayer;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Mutable;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(value = OpenMyTeamGUIMessage.class, remap = false)
public abstract class OpenGUIMixin {
    @Mutable
    @Shadow
    @Final
    private TeamPropertyCollection properties;

    @Inject(method =
            "<init>(Lnet/minecraft/server/level/ServerPlayer;Ldev/ftb/mods/ftbteams/api/property/TeamPropertyCollection;)V",
            at = @At("RETURN"),
            remap = false
    )
    private void maidspell$hidePropertiesFromMembers(ServerPlayer player, TeamPropertyCollection properties, CallbackInfo callback) {
        if (FTBTeamsCompat.canModifyTeamProperties(player)) {
            return;
        }

        TeamPropertyCollectionImpl filteredProperties = new TeamPropertyCollectionImpl();
        properties.forEach((property, value) -> {
            if (!MaidSpellMod.MOD_ID.equals(property.getId().getNamespace())) {
                filteredProperties.set(property, value.getValue());
            }
        });
        this.properties = filteredProperties;
    }
}
