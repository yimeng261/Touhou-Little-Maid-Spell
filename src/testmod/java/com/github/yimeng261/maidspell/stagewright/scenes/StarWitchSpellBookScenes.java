package com.github.yimeng261.maidspell.stagewright.scenes;

import com.github.tartaricacid.touhoulittlemaid.entity.passive.EntityMaid;
import com.github.tartaricacid.touhoulittlemaid.init.InitDataComponent;
import com.github.yimeng261.maidspell.compat.touhou_little_maid.MaidOriginData;
import com.github.yimeng261.maidspell.compat.touhou_little_maid.StellarWitchStarterMaid;
import com.github.yimeng261.maidspell.stagewright.support.Actors;
import com.github.yimeng261.maidspell.stagewright.support.Checks;
import com.github.yimeng261.maidspell.stagewright.support.Maids;
import io.redspace.ironsspellbooks.api.spells.ISpellContainer;
import net.magicterra.stagewright.scene.Scene;
import net.magicterra.stagewright.scene.SceneContext;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.Tag;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.component.CustomData;

import java.util.List;
import java.util.UUID;

import static com.github.yimeng261.maidspell.stagewright.support.Checks.NS;

/**
 * 星之魔女魂符出厂时的法术书容量归一化（需要铁魔法）。
 *
 * <p>这条逻辑只在 {@link StellarWitchStarterMaid#createSoulCharm} 里对即将写进魂符的序列化数据跑一次，
 * 不参与女仆入世流程。纯 NBT 部分覆盖容器组件、越界过滤、残缺数据与幂等性；
 * 另外核对出厂魂符里的法术书是 20 格，以及星之魔女女仆入世、读档后背包里的法术书保持原容量。
 */
public final class StarWitchSpellBookScenes {
    private static final String CONTAINER = "irons_spellbooks:spell_container";
    private static final String BOOK_ID = "irons_spellbooks:dragonskin_spell_book";
    private static final String ONE_SLOT_BOOK = "irons_spellbooks:gold_spell_book[irons_spellbooks:spell_container="
            + "{maxSpells:1,mustEquip:false,spellWheel:true,data:[{id:\"irons_spellbooks:firebolt\",index:0,level:1,locked:false}]}]";

    private StarWitchSpellBookScenes() {
    }

    public static List<Scene> dedicatedServer() {
        return List.of(
                Checks.scene("star_witch.spellBook.containerRaisedKeepsSpells", 5, StarWitchSpellBookScenes::containerRaised),
                Checks.scene("star_witch.spellBook.nonSpellBooksUntouched", 5, StarWitchSpellBookScenes::nonSpellBooks),
                Checks.scene("star_witch.spellBook.otherNamespacesUntouched", 5, StarWitchSpellBookScenes::otherNamespaces),
                Checks.scene("star_witch.spellBook.brokenDataTolerated", 5, StarWitchSpellBookScenes::brokenData),
                Checks.scene("star_witch.spellBook.upgradeIdempotent", 5, StarWitchSpellBookScenes::idempotent),
                Checks.scene("star_witch.spellBook.soulCharmShipsTwentySlots", 5, StarWitchSpellBookScenes::soulCharm),
                Checks.superflat("star_witch.spellBook.joinAndReloadKeepCapacity", 40, StarWitchSpellBookScenes::joinKeepsCapacity));
    }

    private static void containerRaised(SceneContext ctx) {
        CompoundTag maidData = maidWith(item(BOOK_ID, container(12, 15)));

        MaidOriginData.upgradeStarWitchSpellBookData(maidData);

        CompoundTag upgraded = containerOf(firstItem(maidData));
        ctx.check(upgraded.getInt("maxSpells")).as("容量抬到出厂值").isEqualTo(20);
        ListTag spells = upgraded.getList("data", Tag.TAG_COMPOUND);
        ctx.check(spells.size()).as("法术条目保留").isEqualTo(15);
        ctx.check(upgraded.getBoolean("mustEquip")).as("mustEquip 保留").isTrue();
        ctx.check(upgraded.getBoolean("spellWheel")).as("spellWheel 保留").isTrue();
        ctx.check(spells.getCompound(3).getString("id")).as("第 4 条法术 id").isEqualTo("irons_spellbooks:magic_missile");
        ctx.check(spells.getCompound(3).getInt("level")).as("第 4 条法术等级").isEqualTo(10);
    }

    /** 出厂长剑自带 1 格容器（虚空相位），它不是 spell_book 物品，容量不该被改。 */
    private static void nonSpellBooks(SceneContext ctx) {
        CompoundTag maidData = maidWith(item(NS + "star_shadow_longsword", container(1, 1)),
                item(NS + "star_shadow_staff", null));

        MaidOriginData.upgradeStarWitchSpellBookData(maidData);

        ListTag items = maidItems(maidData);
        ctx.check(containerOf(items.getCompound(0)).getInt("maxSpells")).as("长剑容器容量").isEqualTo(1);
        ctx.check(items.getCompound(1).contains("components")).as("没有组件的物品被写入组件").isFalse();
    }

    private static void otherNamespaces(SceneContext ctx) {
        CompoundTag maidData = maidWith(item("othermod:spell_book", container(6, 2)));

        MaidOriginData.upgradeStarWitchSpellBookData(maidData);

        ctx.check(containerOf(firstItem(maidData)).getInt("maxSpells")).as("其它模组法术书容量").isEqualTo(6);
    }

    /** 没有背包、没有 Items 列表、条目缺 id 或缺组件的数据都静默跳过，也不凭空补出容器组件。 */
    private static void brokenData(SceneContext ctx) {
        MaidOriginData.upgradeStarWitchSpellBookData(new CompoundTag());

        CompoundTag noItems = new CompoundTag();
        noItems.put(EntityMaid.MAID_INVENTORY_TAG, new CompoundTag());
        MaidOriginData.upgradeStarWitchSpellBookData(noItems);
        ctx.check(noItems.getCompound(EntityMaid.MAID_INVENTORY_TAG).contains("Items")).as("空背包被补出 Items").isFalse();

        CompoundTag broken = maidWith(item(null, null), item(BOOK_ID, null),
                item(BOOK_ID, new CompoundTag()));
        MaidOriginData.upgradeStarWitchSpellBookData(broken);
        ListTag items = maidItems(broken);
        ctx.check(items.getCompound(0).contains("components")).as("缺 id 条目被写入组件").isFalse();
        ctx.check(items.getCompound(1).contains("components")).as("缺组件的法术书被写入组件").isFalse();
        ctx.check(items.getCompound(2).getCompound("components").contains(CONTAINER)).as("法术书被凭空补出容器").isFalse();
    }

    private static void idempotent(SceneContext ctx) {
        CompoundTag maidData = maidWith(item(BOOK_ID, container(12, 15)));

        MaidOriginData.upgradeStarWitchSpellBookData(maidData);
        MaidOriginData.upgradeStarWitchSpellBookData(maidData);

        CompoundTag upgraded = containerOf(firstItem(maidData));
        ctx.check(upgraded.getInt("maxSpells")).as("跑两遍后的容量").isEqualTo(20);
        ctx.check(upgraded.getList("data", Tag.TAG_COMPOUND).size()).as("跑两遍后的法术条目数").isEqualTo(15);
    }

    /** 魂符走的是数据包里的出厂模板；模板背包第 0 格是长剑（自带 1 格容器），法术书按 id 找。 */
    private static void soulCharm(SceneContext ctx) {
        ItemStack charm = StellarWitchStarterMaid.createSoulCharm(UUID.randomUUID());
        ctx.check(charm.isEmpty()).as("出厂模板缺失，魂符为空").isFalse();
        CustomData info = charm.get(InitDataComponent.MAID_INFO);
        ctx.check(info).as("魂符的女仆数据").isNotNull();
        if (info == null) {
            return;
        }
        CompoundTag maidData = info.copyTag();
        CompoundTag book = findSpellBook(maidData);
        ctx.check(book).as("出厂魂符里的铁魔法法术书").isNotNull();
        if (book == null) {
            return;
        }
        ctx.check(containerOf(book).getInt("maxSpells")).as("出厂法术书容量").isEqualTo(20);
        ctx.check(containerOf(book).getList("data", Tag.TAG_COMPOUND).size()).as("出厂法术书的法术条目数").isEqualTo(15);

        MaidOriginData.upgradeStarWitchSpellBookData(maidData);
        ctx.check(containerOf(findSpellBook(maidData)).getInt("maxSpells")).as("再跑一遍后的出厂容量").isEqualTo(20);
    }

    /** 星之魔女女仆背包里的 1 格法术书：入世与按区块存档重建之后都还是 1 格。 */
    private static void joinKeepsCapacity(SceneContext ctx) {
        EntityMaid maid = Actors.spawn(ctx, Actors.MAID, 0, 0, 0, true, (EntityMaid m) -> {
            m.getPersistentData().putBoolean(MaidOriginData.STAR_WITCH_VICTORY_TAMED, true);
            m.getMaidInv().setStackInSlot(0, Actors.parse(ctx, ONE_SLOT_BOOK));
        });
        Checks.after(ctx, 2, () -> {
            ctx.check(MaidOriginData.isStarWitchVictoryTamed(maid)).as("星之魔女标记").isTrue();
            ctx.check(capacity(maid)).as("入世后的法术书容量").isEqualTo(1);
            Maids.reload(ctx, maid, 2, loaded ->
                    Checks.after(ctx, 2, () -> ctx.check(capacity((EntityMaid) loaded)).as("读档后的法术书容量").isEqualTo(1)));
        });
    }

    private static int capacity(EntityMaid maid) {
        return ISpellContainer.get(maid.getMaidInv().getStackInSlot(0)).getMaxSpellCount();
    }

    private static CompoundTag findSpellBook(CompoundTag maidData) {
        for (Tag element : maidItems(maidData)) {
            CompoundTag item = (CompoundTag) element;
            String id = item.getString("id");
            if (id.startsWith("irons_spellbooks:") && id.contains("spell_book")) {
                return item;
            }
        }
        return null;
    }

    // ---- 构造 NBT 的小工具 ----

    private static CompoundTag maidWith(CompoundTag... items) {
        CompoundTag inventory = new CompoundTag();
        inventory.putInt("Size", 36);
        ListTag list = new ListTag();
        for (int slot = 0; slot < items.length; slot++) {
            CompoundTag item = items[slot].copy();
            item.putByte("Slot", (byte) slot);
            list.add(item);
        }
        inventory.put("Items", list);
        CompoundTag maidData = new CompoundTag();
        maidData.put(EntityMaid.MAID_INVENTORY_TAG, inventory);
        return maidData;
    }

    private static CompoundTag item(String id, CompoundTag components) {
        CompoundTag item = new CompoundTag();
        if (id != null) {
            item.putString("id", id);
        }
        item.putInt("count", 1);
        if (components != null) {
            item.put("components", components);
        }
        return item;
    }

    private static CompoundTag container(int maxSpells, int spellCount) {
        CompoundTag container = new CompoundTag();
        container.putInt("maxSpells", maxSpells);
        container.putBoolean("mustEquip", true);
        container.putBoolean("spellWheel", true);
        ListTag spells = new ListTag();
        for (int index = 0; index < spellCount; index++) {
            CompoundTag spell = new CompoundTag();
            spell.putInt("index", index);
            spell.putInt("level", index == 3 ? 10 : 5);
            spell.putString("id", index == 3 ? "irons_spellbooks:magic_missile" : NS + "test_spell_" + index);
            spells.add(spell);
        }
        container.put("data", spells);
        CompoundTag components = new CompoundTag();
        components.put(CONTAINER, container);
        return components;
    }

    private static ListTag maidItems(CompoundTag maidData) {
        return maidData.getCompound(EntityMaid.MAID_INVENTORY_TAG).getList("Items", Tag.TAG_COMPOUND);
    }

    private static CompoundTag firstItem(CompoundTag maidData) {
        return maidItems(maidData).getCompound(0);
    }

    private static CompoundTag containerOf(CompoundTag item) {
        return item.getCompound("components").getCompound(CONTAINER);
    }
}
