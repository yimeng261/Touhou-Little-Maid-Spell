package com.github.yimeng261.maidspell.event;

import com.github.tartaricacid.touhoulittlemaid.entity.passive.EntityMaid;
import com.github.yimeng261.maidspell.MaidSpellMod;
import com.github.yimeng261.maidspell.item.MaidSpellItems;
import com.github.yimeng261.maidspell.spell.manager.BaubleStateManager;
import net.minecraft.tags.DamageTypeTags;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.damagesource.DamageTypes;
import net.minecraftforge.event.entity.living.LivingAttackEvent;
import net.minecraftforge.eventbus.api.EventPriority;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;

@Mod.EventBusSubscriber(modid = MaidSpellMod.MOD_ID)
public final class MoltenFoxLeafMaidEvents {
    private MoltenFoxLeafMaidEvents() {
    }

    @SubscribeEvent(priority = EventPriority.HIGHEST)
    public static void onMaidLivingAttack(LivingAttackEvent event) {
        if (!(event.getEntity() instanceof EntityMaid maid)) {
            return;
        }
        if (!BaubleStateManager.hasBauble(maid, MaidSpellItems.MOLTEN_FOX_LEAF)) {
            return;
        }

        DamageSource source = event.getSource();
        if (source.is(DamageTypeTags.BYPASSES_INVULNERABILITY)) {
            return;
        }

        if (source.is(DamageTypeTags.IS_FIRE)
            || source.is(DamageTypes.LAVA)
            || source.is(DamageTypes.HOT_FLOOR)
            || source.is(DamageTypes.IN_FIRE)
            || source.is(DamageTypes.ON_FIRE)) {
            event.setCanceled(true);
        }
    }
}
