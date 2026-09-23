package com.github.yimeng261.maidspell.debug;

import com.github.yimeng261.maidspell.MaidSpellMod;
import com.github.yimeng261.maidspell.compat.irons_spellbooks.entity.spell.StarShadowStrikeEntity;
import com.github.yimeng261.maidspell.compat.irons_spellbooks.entity.winefox.MagicalWinefoxBossEntity;
import com.github.yimeng261.maidspell.compat.irons_spellbooks.registry.IronsSpellbooksCompatEntities;
import com.github.yimeng261.maidspell.compat.irons_spellbooks.registry.IronsSpellbooksCompatSpells;
import com.github.yimeng261.maidspell.compat.irons_spellbooks.spell.StarShadowStrikeSpell;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.tags.DamageTypeTags;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.damagesource.DamageTypes;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.monster.Zombie;
import net.minecraftforge.gametest.GameTestHolder;
import net.minecraftforge.gametest.PrefixGameTestTemplate;
import net.minecraftforge.fml.ModList;

@GameTestHolder(MaidSpellMod.MOD_ID)
@PrefixGameTestTemplate(false)
public final class StarShadowStrikeGameTests {
    private StarShadowStrikeGameTests() {
    }

    @GameTest(template = "winefox_test_arena", timeoutTicks = 40)
    public static void starShadowStrikeUsesAttributedVanillaVoidDamage(GameTestHelper helper) {
        if (!ModList.get().isLoaded("irons_spellbooks")) {
            helper.succeed();
            return;
        }

        MagicalWinefoxBossEntity boss = helper.spawn(
                IronsSpellbooksCompatEntities.MAGICAL_WINEFOX_BOSS.get(), 3, 1, 3);
        Zombie target = helper.spawn(EntityType.ZOMBIE, 5, 1, 3);
        boss.setNoAi(true);
        boss.setYRot(-90.0F);
        boss.setXRot(0.0F);
        float healthBefore = target.getHealth();

        StarShadowStrikeSpell spell =
                (StarShadowStrikeSpell) IronsSpellbooksCompatSpells.STAR_SHADOW_STRIKE.get();
        spell.performSlash(helper.getLevel(), 3, boss, false);

        DamageSource source = target.getLastDamageSource();
        helper.assertTrue(source != null && source.is(DamageTypes.FELL_OUT_OF_WORLD),
                "Starshadow Strike must use minecraft:out_of_world damage");
        helper.assertTrue(source.is(DamageTypeTags.BYPASSES_INVULNERABILITY),
                "Vanilla void damage must retain its invulnerability-bypass tag");
        helper.assertTrue(source.getEntity() == boss,
                "Void damage must retain the Winefox as its attacker");
        helper.assertTrue(target.getHealth() < healthBefore,
                "Starshadow Strike must damage its target");
        helper.assertTrue(!helper.getLevel().getEntitiesOfClass(StarShadowStrikeEntity.class,
                        boss.getBoundingBox().inflate(8.0D)).isEmpty(),
                "The spell must spawn the Starshadow Strike visual");

        target.discard();
        boss.discard();
        helper.succeed();
    }
}
