package com.github.yimeng261.maidspell.stagewright.support;

import com.Polarice3.Goety.common.items.handler.FocusBagItemHandler;
import com.github.tartaricacid.touhoulittlemaid.entity.passive.EntityMaid;
import com.github.yimeng261.maidspell.api.ISpellBookProvider;
import com.hollingsworth.arsnouveau.api.registry.SpellCasterRegistry;
import com.hollingsworth.arsnouveau.common.spell.effect.EffectHarm;
import com.hollingsworth.arsnouveau.common.spell.method.MethodProjectile;
import net.magicterra.stagewright.scene.SceneContext;
import net.minecraft.core.component.DataComponents;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.component.ItemContainerContents;
import vazkii.psi.api.PsiAPI;
import vazkii.psi.api.spell.ISpellAcceptor;
import vazkii.psi.api.spell.Spell;
import vazkii.psi.api.spell.SpellParam;
import vazkii.psi.api.spell.SpellPiece;
import vazkii.psi.common.item.ItemCAD;
import vazkii.psi.common.spell.selector.PieceSelectorCaster;
import vazkii.psi.common.spell.trick.PieceTrickDebug;

import java.util.List;

/** 可实际施放的联动法术书，使用各模组的物品组件和公开装载接口。 */
public final class SpellFixtures {
    private SpellFixtures() { }

    public static ItemStack equip(SceneContext ctx, String mod, ISpellBookProvider<?, ?> provider,
                                  ServerPlayer owner, EntityMaid maid) {
        ItemStack book = switch (mod) {
            case "irons_spellbooks" -> Actors.parse(ctx, "irons_spellbooks:gold_spell_book[irons_spellbooks:spell_container="
                    + "{maxSpells:1,mustEquip:false,spellWheel:true,data:[{id:\"irons_spellbooks:cone_of_cold\",index:0,level:5,locked:false}]}]");
            case "ars_nouveau" -> {
                ItemStack stack = Actors.stack("ars_nouveau:novice_spell_book");
                SpellCasterRegistry.from(stack).setSpell(new com.hollingsworth.arsnouveau.api.spell.Spell(
                        MethodProjectile.INSTANCE, EffectHarm.INSTANCE), 0).saveToStack(stack);
                yield stack;
            }
            case "goety" -> {
                ItemStack stack = Actors.stack("goety:focus_bag");
                FocusBagItemHandler.get(stack).setStackInSlot(0, Actors.stack("goety:fireball_focus"));
                yield stack;
            }
            case "usefulmagic" -> {
                ItemStack stack = Actors.stack("usefulmagic:spell_bag");
                stack.set(DataComponents.CONTAINER, ItemContainerContents.fromItems(List.of(Actors.stack("usefulmagic:barrage_magic"))));
                maid.setItemSlot(EquipmentSlot.OFFHAND, Actors.stack("usefulmagic:netherite_wand"));
                yield stack;
            }
            case "psi" -> psi(owner);
            case "slashblade" -> BuiltInRegistries.ITEM.stream().map(ItemStack::new)
                    .filter(provider::isSpellBook).findFirst().orElseThrow();
            default -> throw new IllegalArgumentException("未知法术提供者 " + mod);
        };
        ctx.check(!book.isEmpty() && provider.isSpellBook(book)).as("可施法的法术书 " + mod).isTrue();
        ctx.record("spellBook", BuiltInRegistries.ITEM.getKey(book.getItem()).toString());
        maid.setItemSlot(EquipmentSlot.MAINHAND, book);
        return book;
    }

    private static ItemStack psi(ServerPlayer owner) {
        Spell spell = new Spell();
        spell.name = "Lifecycle Debug";
        PieceTrickDebug debug = new PieceTrickDebug(spell);
        place(spell, debug, 4, 4);
        place(spell, new PieceSelectorCaster(spell), 5, 4);
        debug.paramSides.put(debug.params.get(SpellParam.GENERIC_NAME_TARGET), SpellParam.Side.RIGHT);
        ItemStack bullet = Actors.stack("psi:spell_bullet");
        ISpellAcceptor.acceptor(bullet).setSpell(owner, spell);
        ItemStack cad = ItemCAD.getCreativeTabItems().getLast().copy();
        var sockets = cad.getCapability(PsiAPI.SOCKETABLE_CAPABILITY);
        if (sockets == null) throw new IllegalStateException("CAD 没有弹夹能力");
        sockets.setBulletInSocket(0, bullet);
        sockets.setSelectedSlot(0);
        return cad;
    }

    private static void place(Spell spell, SpellPiece piece, int x, int y) {
        piece.x = x;
        piece.y = y;
        piece.isInGrid = true;
        spell.grid.gridData[x][y] = piece;
    }
}
