package com.github.yimeng261.maidspell.client.gui;

import com.github.yimeng261.maidspell.compat.irons_spellbooks.client.WinefoxSpellSelectionClient;
import com.github.yimeng261.maidspell.network.NetworkHandler;
import com.github.yimeng261.maidspell.network.message.WinefoxChallengeConfigMessage;
import com.github.yimeng261.maidspell.winefox.WinefoxChallengeConfig;
import com.github.yimeng261.maidspell.winefox.WinefoxChallengeConfigMenu;
import com.github.yimeng261.maidspell.winefox.WinefoxChallengePresets;
import com.github.yimeng261.maidspell.winefox.WinefoxSpellChoice;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.components.CycleButton;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.client.gui.screens.inventory.AbstractContainerScreen;
import net.minecraft.client.renderer.Rect2i;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.inventory.Slot;
import net.minecraft.world.item.ItemStack;
import net.minecraftforge.fml.ModList;
import org.lwjgl.glfw.GLFW;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

/** Compact challenge editor with a bottom inventory and room for JEI beside it. */
public class WinefoxChallengeConfigScreen extends AbstractContainerScreen<WinefoxChallengeConfigMenu> {
    private static final String KEY = "screen.touhou_little_maid_spell.winefox_challenge.";
    private static final int PANEL_WIDTH = 304;
    private static final int MAIN_WIDTH = 177;
    private static final int SPELLS_PER_ROW = 9;
    private static final int[] ROOMY_SPELL_ROW_Y = {41, 59, 93, 111};
    private static final int[] COMPACT_SPELL_ROW_Y = {37, 65};
    private static final String[] FIELD_KEYS = {
            "max_health", "damage_multiplier", "spell_power_multiplier", "hit_damage_cap",
            "hit_interval", "maid_damage_multiplier", "damage_to_maid", "phase_two_multiplier"};

    private final EditBox[] fields = new EditBox[8];
    private final WinefoxSpellChoice[] first = new WinefoxSpellChoice[WinefoxSpellChoice.MAX_SLOTS_PER_PHASE];
    private final WinefoxSpellChoice[] second = new WinefoxSpellChoice[WinefoxSpellChoice.MAX_SLOTS_PER_PHASE];
    private final Button[] presetButtons = new Button[WinefoxChallengePresets.COUNT * 3];
    private EditBox chance;
    private EditBox levelField;
    private CycleButton<Boolean> playerToggle;
    private CycleButton<Boolean> maidToggle;
    private Button modeButton;
    private Button presetsButton;
    private Button basicPageButton;
    private Button phasePageButton;
    private Button basicResetButton;
    private Button advancedResetButton;
    private Button decreaseLevelButton;
    private Button increaseLevelButton;
    private WinefoxChallengeConfig draft;
    private boolean advanced;
    private boolean presetsOpen;
    private boolean defaultsPlaced;
    private boolean initialized;
    private boolean imitatePlayer;
    private boolean imitateMaid;
    private int basicPage;
    private int phasePage;
    private int selectedSlot = -1;
    private int inventoryY;
    private boolean twoRowsPerPhase;
    private ItemStack draggedScroll = ItemStack.EMPTY;

    public WinefoxChallengeConfigScreen(WinefoxChallengeConfigMenu menu, Inventory inventory, Component title) {
        super(menu, inventory, title);
        imageWidth = PANEL_WIDTH;
        imageHeight = 232;
        draft = menu.getConfig();
        defaultsPlaced = draft.spellPoolsConfigured();
        copy(draft.phaseOneSpells(), first);
        copy(draft.phaseTwoSpells(), second);
    }

    private static void copy(List<WinefoxSpellChoice> source, WinefoxSpellChoice[] target) {
        for (int i = 0; i < Math.min(source.size(), target.length); i++) target[i] = source.get(i);
    }

    @Override
    protected void init() {
        if (initialized) draft = currentConfig();
        imageHeight = Math.min(232, Math.max(172, height - 8));
        super.init();
        inventoryY = imageHeight - 86;
        twoRowsPerPhase = inventoryY >= 140;
        phasePage %= spellPageCount();
        menu.positionInventory(inventoryY);
        inventoryLabelX = 9;
        inventoryLabelY = inventoryY - 10;
        double[] numbers = values(draft);
        for (int i = 0; i < fields.length; i++) {
            int index = i < firstPageFieldCount() ? i : i - firstPageFieldCount();
            int fieldIndex = i;
            EditBox field = new CommitEditBox(leftPos + 9 + index % 2 * 84,
                    topPos + 39 + index / 2 * 31, 76, 18,
                    Component.translatable(KEY + FIELD_KEYS[i]), () -> normalizeBasicField(fieldIndex));
            field.setMaxLength(18);
            field.setValue(i == 4 ? Integer.toString(draft.hitIntervalTicks()) : Double.toString(numbers[i]));
            fields[i] = addRenderableWidget(field);
        }
        imitatePlayer = draft.imitatePlayerSpells();
        imitateMaid = draft.imitateMaidSpells();
        playerToggle = addRenderableWidget(CycleButton.onOffBuilder(imitatePlayer).create(
                leftPos + 182, topPos + 88, 116, 18, Component.translatable(KEY + "imitate_player_short"),
                (button, selected) -> imitatePlayer = selected));
        maidToggle = addRenderableWidget(CycleButton.onOffBuilder(imitateMaid).create(
                leftPos + 182, topPos + 108, 116, 18, Component.translatable(KEY + "imitate_maid_short"),
                (button, selected) -> imitateMaid = selected));
        chance = addRenderableWidget(new CommitEditBox(leftPos + 252, topPos + 130, 45, 18,
                Component.translatable(KEY + "imitate_chance"), this::normalizeChanceField));
        chance.setMaxLength(3);
        chance.setValue(Integer.toString(draft.imitateChancePercent()));
        levelField = addRenderableWidget(new EditBox(font, leftPos + 207, topPos + 42, 64, 18,
                Component.translatable(KEY + "spell_level")));
        levelField.setMaxLength(2);
        levelField.setResponder(text -> {
            WinefoxSpellChoice selected = selectedSpell();
            int level = parseInt(levelField, -1);
            if (selected != null && level >= 1 && level <= 10) setSelectedSpell(new WinefoxSpellChoice(selected.id(), level));
        });
        decreaseLevelButton = addRenderableWidget(Button.builder(Component.literal("-"), button -> stepLevel(-1))
                .bounds(leftPos + 183, topPos + 42, 20, 18).build());
        increaseLevelButton = addRenderableWidget(Button.builder(Component.literal("+"), button -> stepLevel(1))
                .bounds(leftPos + 275, topPos + 42, 20, 18).build());
        phasePageButton = addRenderableWidget(Button.builder(Component.empty(), button -> {
            phasePage = (phasePage + 1) % spellPageCount();
            selectSlot(-1);
            refreshPageText();
        }).bounds(leftPos + 182, topPos + 65, 116, 18).build());
        basicPageButton = addRenderableWidget(Button.builder(Component.empty(), button -> {
            basicPage = 1 - basicPage;
            refreshPageText();
            updateVisibility();
        }).bounds(leftPos + 182, topPos + 34, 116, 18).build());
        modeButton = addRenderableWidget(Button.builder(Component.empty(), button -> {
            advanced = !advanced;
            if (advanced) placeDefaults();
            selectSlot(-1);
            updateVisibility();
        }).bounds(leftPos + 91, topPos + 5, 78, 18).build());
        presetsButton = addRenderableWidget(Button.builder(Component.empty(), button -> {
            presetsOpen = !presetsOpen;
            updateVisibility();
        }).bounds(leftPos + 182, topPos + 5, 116, 18).build());
        basicResetButton = addRenderableWidget(Button.builder(Component.translatable(KEY + "reset"),
                button -> resetBasic()).bounds(leftPos + 182, topPos + imageHeight - 23, 55, 18).build());
        advancedResetButton = addRenderableWidget(Button.builder(Component.translatable(KEY + "reset_advanced_short"),
                button -> resetAdvanced()).bounds(leftPos + 182, topPos + imageHeight - 23, 55, 18).build());
        addRenderableWidget(Button.builder(Component.translatable("gui.done"), button -> saveAndClose())
                .bounds(leftPos + 242, topPos + imageHeight - 23, 56, 18).build());
        for (int i = 0; i < WinefoxChallengePresets.COUNT; i++) {
            int preset = i;
            int y = topPos + 43 + i * 36;
            presetButtons[3 * i] = addRenderableWidget(Button.builder(Component.translatable(KEY + "save_preset"),
                    button -> savePreset(preset)).bounds(leftPos + 183, y, 36, 18).build());
            presetButtons[3 * i + 1] = addRenderableWidget(Button.builder(Component.translatable(KEY + "load_preset"),
                    button -> loadPreset(preset)).bounds(leftPos + 222, y, 36, 18).build());
            presetButtons[3 * i + 2] = addRenderableWidget(Button.builder(Component.translatable(KEY + "delete_preset"),
                    button -> deletePreset(preset)).bounds(leftPos + 261, y, 36, 18).build());
        }
        initialized = true;
        updateVisibility();
    }

    private static double[] values(WinefoxChallengeConfig config) {
        return new double[]{config.maxHealth(), config.damageMultiplier(), config.spellPowerMultiplier(),
                config.hitDamageCapRatio(), config.hitIntervalTicks(), config.maidDamageMultiplier(),
                config.damageToMaidMultiplier(), config.phaseTwoDamageMultiplier()};
    }

    private int firstPageFieldCount() {
        return twoRowsPerPhase ? 5 : 4;
    }

    private int spellPageCount() {
        return twoRowsPerPhase ? 2 : 3;
    }

    private int visibleSpellsPerPhase() {
        return twoRowsPerPhase ? (phasePage == 0 ? 18 : 9) : 9;
    }

    private void placeDefaults() {
        if (defaultsPlaced || !ModList.get().isLoaded("irons_spellbooks")) return;
        if (draft.phaseOneSpells().isEmpty()) copy(WinefoxSpellSelectionClient.defaultChoices(false), first);
        if (draft.phaseTwoSpells().isEmpty()) copy(WinefoxSpellSelectionClient.defaultChoices(true), second);
        defaultsPlaced = true;
    }

    private void updateVisibility() {
        for (int i = 0; i < fields.length; i++) {
            boolean show = !advanced && (basicPage == 0 ? i < firstPageFieldCount()
                    : i >= firstPageFieldCount());
            fields[i].visible = show;
            fields[i].active = show;
        }
        boolean editing = !presetsOpen;
        basicPageButton.visible = editing && !advanced;
        phasePageButton.visible = editing && advanced;
        playerToggle.visible = editing && advanced;
        maidToggle.visible = editing && advanced;
        chance.visible = editing && advanced;
        chance.active = chance.visible;
        levelField.visible = editing && advanced;
        levelField.active = levelField.visible && selectedSpell() != null;
        decreaseLevelButton.visible = levelField.visible;
        increaseLevelButton.visible = levelField.visible;
        decreaseLevelButton.active = levelField.active;
        increaseLevelButton.active = levelField.active;
        basicResetButton.visible = editing && !advanced;
        advancedResetButton.visible = editing && advanced;
        for (Button button : presetButtons) button.visible = presetsOpen;
        refreshPageText();
    }

    private void refreshPageText() {
        modeButton.setMessage(Component.translatable(KEY + (advanced ? "basic" : "advanced")));
        presetsButton.setMessage(Component.translatable(KEY + (presetsOpen ? "back" : "presets")));
        basicPageButton.setMessage(Component.translatable(KEY + "basic_page", basicPage + 1, 2));
        phasePageButton.setMessage(Component.translatable(KEY + "spell_page", phasePage + 1, spellPageCount()));
    }

    private void resetBasic() {
        WinefoxChallengeConfig defaults = menu.getDefaults();
        double[] numbers = values(defaults);
        for (int i = 0; i < fields.length; i++) {
            fields[i].setValue(i == 4 ? Integer.toString(defaults.hitIntervalTicks()) : Double.toString(numbers[i]));
        }
    }

    private void resetAdvanced() {
        chance.setValue(Integer.toString(menu.getDefaults().imitateChancePercent()));
        playerToggle.setValue(false);
        maidToggle.setValue(false);
        imitatePlayer = false;
        imitateMaid = false;
        Arrays.fill(first, null);
        Arrays.fill(second, null);
        if (ModList.get().isLoaded("irons_spellbooks")) {
            copy(WinefoxSpellSelectionClient.defaultChoices(false), first);
            copy(WinefoxSpellSelectionClient.defaultChoices(true), second);
        }
        defaultsPlaced = true;
        selectSlot(-1);
    }

    private WinefoxChallengeConfig currentConfig() {
        WinefoxChallengeConfig fallback = draft;
        return new WinefoxChallengeConfig(
                parse(fields[0], fallback.maxHealth()), parse(fields[1], fallback.damageMultiplier()),
                parse(fields[2], fallback.spellPowerMultiplier()), parse(fields[3], fallback.hitDamageCapRatio()),
                parseInt(fields[4], fallback.hitIntervalTicks()), parse(fields[5], fallback.maidDamageMultiplier()),
                parse(fields[6], fallback.damageToMaidMultiplier()), parse(fields[7], fallback.phaseTwoDamageMultiplier()),
                imitatePlayer, imitateMaid, parseInt(chance, fallback.imitateChancePercent()),
                chosen(first), chosen(second), defaultsPlaced).sanitized(menu.getDefaults());
    }

    private static List<WinefoxSpellChoice> chosen(WinefoxSpellChoice[] slots) {
        List<WinefoxSpellChoice> result = new ArrayList<>();
        for (WinefoxSpellChoice choice : slots) if (choice != null) result.add(choice);
        return result;
    }

    private static double parse(EditBox field, double fallback) {
        try { return Double.parseDouble(field.getValue()); }
        catch (NumberFormatException ignored) { return fallback; }
    }

    private static int parseInt(EditBox field, int fallback) {
        try { return Integer.parseInt(field.getValue()); }
        catch (NumberFormatException ignored) { return fallback; }
    }

    private void normalizeBasicField(int index) {
        EditBox field = fields[index];
        double normalized = values(currentConfig())[index];
        if (index == 4) {
            int value = parseInt(field, Integer.MIN_VALUE);
            if (value != (int) normalized) field.setValue(Integer.toString((int) normalized));
        } else {
            double value = parse(field, Double.NaN);
            if (!Double.isFinite(value) || Double.compare(value, normalized) != 0) {
                field.setValue(Double.toString(normalized));
            }
        }
    }

    private void normalizeChanceField() {
        int normalized = currentConfig().imitateChancePercent();
        if (parseInt(chance, Integer.MIN_VALUE) != normalized) {
            chance.setValue(Integer.toString(normalized));
        }
    }

    private void normalizeInputs() {
        for (int i = 0; i < fields.length; i++) normalizeBasicField(i);
        normalizeChanceField();
    }

    private final class CommitEditBox extends EditBox {
        private final Runnable onCommit;

        private CommitEditBox(int x, int y, int width, int height, Component label, Runnable onCommit) {
            super(font, x, y, width, height, label);
            this.onCommit = onCommit;
        }

        @Override
        public void setFocused(boolean focused) {
            boolean wasFocused = isFocused();
            super.setFocused(focused);
            if (wasFocused && !focused && onCommit != null) onCommit.run();
        }

        @Override
        public boolean keyPressed(int keyCode, int scanCode, int modifiers) {
            if (keyCode == GLFW.GLFW_KEY_ENTER || keyCode == GLFW.GLFW_KEY_KP_ENTER) {
                onCommit.run();
                return true;
            }
            return super.keyPressed(keyCode, scanCode, modifiers);
        }
    }

    private void sendConfig() {
        NetworkHandler.CHANNEL.sendToServer(new WinefoxChallengeConfigMessage(
                menu.getItemSlot(), currentConfig(), menu.getPresets()));
    }

    private void saveAndClose() {
        normalizeInputs();
        sendConfig();
        minecraft.player.closeContainer();
    }

    private void savePreset(int index) {
        normalizeInputs();
        menu.getPresets().set(index, currentConfig());
        sendConfig();
    }

    private void loadPreset(int index) {
        WinefoxChallengeConfig preset = menu.getPresets().get(index);
        if (preset == null) return;
        preset = preset.sanitized(menu.getDefaults());
        draft = preset;
        double[] numbers = values(preset);
        for (int i = 0; i < fields.length; i++) {
            fields[i].setValue(i == 4 ? Integer.toString(preset.hitIntervalTicks()) : Double.toString(numbers[i]));
        }
        chance.setValue(Integer.toString(preset.imitateChancePercent()));
        playerToggle.setValue(preset.imitatePlayerSpells());
        maidToggle.setValue(preset.imitateMaidSpells());
        imitatePlayer = preset.imitatePlayerSpells();
        imitateMaid = preset.imitateMaidSpells();
        Arrays.fill(first, null);
        Arrays.fill(second, null);
        copy(preset.phaseOneSpells(), first);
        copy(preset.phaseTwoSpells(), second);
        defaultsPlaced = preset.spellPoolsConfigured();
        if (advanced) placeDefaults();
        selectSlot(-1);
    }

    private void deletePreset(int index) {
        menu.getPresets().set(index, null);
        sendConfig();
    }

    private WinefoxSpellChoice selectedSpell() {
        if (selectedSlot < 0) return null;
        return (selectedSlot < first.length ? first : second)[selectedSlot % first.length];
    }

    private void setSelectedSpell(WinefoxSpellChoice choice) {
        if (selectedSlot < 0) return;
        (selectedSlot < first.length ? first : second)[selectedSlot % first.length] = choice;
        if (levelField != null && choice != null && !levelField.getValue().equals(Integer.toString(choice.level()))) {
            levelField.setValue(Integer.toString(choice.level()));
        }
        updateVisibility();
    }

    private void selectSlot(int index) {
        selectedSlot = index;
        WinefoxSpellChoice selected = selectedSpell();
        if (levelField != null) levelField.setValue(selected == null ? "" : Integer.toString(selected.level()));
        updateVisibility();
    }

    private void stepLevel(int delta) {
        WinefoxSpellChoice selected = selectedSpell();
        if (selected != null) setSelectedSpell(new WinefoxSpellChoice(selected.id(),
                Math.max(1, Math.min(10, selected.level() + delta))));
    }

    private int selectedIndex(int visibleSlot) {
        int count = visibleSpellsPerPhase();
        int base = phasePage * (twoRowsPerPhase ? 18 : 9);
        return visibleSlot < count ? base + visibleSlot
                : first.length + base + visibleSlot - count;
    }

    private List<Rect2i> visibleSpellAreas() {
        int rows = visibleSpellsPerPhase() / SPELLS_PER_ROW;
        int[] rowY = twoRowsPerPhase ? ROOMY_SPELL_ROW_Y : COMPACT_SPELL_ROW_Y;
        List<Rect2i> areas = new ArrayList<>(rows * 2 * SPELLS_PER_ROW);
        for (int phase = 0; phase < 2; phase++) {
            for (int row = 0; row < rows; row++) {
                for (int column = 0; column < SPELLS_PER_ROW; column++) {
                    areas.add(new Rect2i(leftPos + 9 + column * 18,
                            topPos + rowY[phase * (twoRowsPerPhase ? 2 : 1) + row], 18, 18));
                }
            }
        }
        return areas;
    }

    public boolean isAdvanced() { return advanced && !presetsOpen; }

    public List<Rect2i> spellAreas() { return visibleSpellAreas(); }

    public Rect2i jeiExclusionArea() {
        return new Rect2i(leftPos - 3, topPos - 3, imageWidth + 6, imageHeight + 6);
    }

    public void acceptScroll(int visibleSlot, ItemStack stack) {
        if (!advanced || !ModList.get().isLoaded("irons_spellbooks")) return;
        WinefoxSpellChoice choice = WinefoxSpellSelectionClient.fromScroll(stack);
        if (choice == null) return;
        selectSlot(selectedIndex(visibleSlot));
        setSelectedSpell(choice);
    }

    private int inventoryIndexAt(double mouseX, double mouseY) {
        int x = (int) mouseX - leftPos - 9;
        int y = (int) mouseY - topPos;
        if (x < 0 || x >= 9 * 18 || x % 18 >= 16) return -1;
        int column = x / 18;
        if (y >= inventoryY && y < inventoryY + 3 * 18) {
            int rowY = y - inventoryY;
            return rowY % 18 < 16 ? 9 + rowY / 18 * 9 + column : -1;
        }
        int hotbarY = y - inventoryY - 58;
        return hotbarY >= 0 && hotbarY < 16 ? column : -1;
    }

    private int spellAreaAt(double mouseX, double mouseY) {
        List<Rect2i> areas = visibleSpellAreas();
        for (int i = 0; i < areas.size(); i++) {
            if (areas.get(i).contains((int) mouseX, (int) mouseY)) return i;
        }
        return -1;
    }

    @Override
    public boolean mouseClicked(double mouseX, double mouseY, int button) {
        if (advanced && !presetsOpen) {
            int area = spellAreaAt(mouseX, mouseY);
            if (area >= 0) {
                selectSlot(selectedIndex(area));
                if (button == 1) setSelectedSpell(null);
                else if (!menu.getCarried().isEmpty()) acceptScroll(area, menu.getCarried());
                return true;
            }
            if (button == 0 && ModList.get().isLoaded("irons_spellbooks")) {
                int inventoryIndex = inventoryIndexAt(mouseX, mouseY);
                if (inventoryIndex >= 0) {
                    ItemStack clicked = minecraft.player.getInventory().getItem(inventoryIndex);
                    if (WinefoxSpellSelectionClient.fromScroll(clicked) != null) {
                        draggedScroll = clicked.copyWithCount(1);
                        return true;
                    }
                }
            }
        }
        return super.mouseClicked(mouseX, mouseY, button);
    }

    @Override
    public boolean mouseReleased(double mouseX, double mouseY, int button) {
        if (!draggedScroll.isEmpty()) {
            int area = spellAreaAt(mouseX, mouseY);
            if (area >= 0) acceptScroll(area, draggedScroll);
            else if (selectedSlot >= 0) {
                WinefoxSpellChoice choice = WinefoxSpellSelectionClient.fromScroll(draggedScroll);
                if (choice != null) setSelectedSpell(choice);
            }
            draggedScroll = ItemStack.EMPTY;
            return true;
        }
        return super.mouseReleased(mouseX, mouseY, button);
    }

    @Override
    protected void renderBg(GuiGraphics graphics, float partialTick, int mouseX, int mouseY) {
        graphics.fill(leftPos, topPos, leftPos + imageWidth, topPos + imageHeight, 0xFF242424);
        graphics.fill(leftPos + 1, topPos + 1, leftPos + imageWidth - 1, topPos + imageHeight - 1, 0xFFC6C6C6);
        graphics.fill(leftPos + MAIN_WIDTH, topPos + 1, leftPos + MAIN_WIDTH + 1,
                topPos + imageHeight - 1, 0xFF555555);
        graphics.fill(leftPos + MAIN_WIDTH + 2, topPos + 1, leftPos + imageWidth - 1,
                topPos + imageHeight - 1, 0xFFB6B6B6);
        for (Slot slot : menu.slots) drawSlot(graphics, leftPos + slot.x, topPos + slot.y);
        if (advanced) {
            List<Rect2i> areas = visibleSpellAreas();
            for (int i = 0; i < areas.size(); i++) {
                Rect2i area = areas.get(i);
                drawSlot(graphics, area.getX(), area.getY());
                WinefoxSpellChoice choice = (selectedIndex(i) < first.length ? first : second)
                        [selectedIndex(i) % first.length];
                if (choice != null && ModList.get().isLoaded("irons_spellbooks")) {
                    ResourceLocation icon = WinefoxSpellSelectionClient.icon(choice);
                    if (icon != null) graphics.blit(icon, area.getX(), area.getY(), 0, 0, 16, 16, 16, 16);
                    graphics.drawString(font, Integer.toString(choice.level()), area.getX() + 15
                            - font.width(Integer.toString(choice.level())), area.getY() + 9, 0xFFFFFF, true);
                }
                if (selectedIndex(i) == selectedSlot) {
                    graphics.renderOutline(area.getX() - 1, area.getY() - 1, 18, 18, 0xFFE6AF38);
                }
            }
        }
    }

    private static void drawSlot(GuiGraphics graphics, int x, int y) {
        graphics.fill(x - 1, y - 1, x + 17, y + 17, 0xFF373737);
        graphics.fill(x, y, x + 16, y + 16, 0xFF767676);
    }

    @Override
    protected void renderLabels(GuiGraphics graphics, int mouseX, int mouseY) {
        graphics.drawString(font, Component.translatable(KEY + "title_short"), 8, 10, 0x303030, false);
        if (inventoryY >= 104) graphics.drawString(font, playerInventoryTitle, 9, inventoryLabelY, 0x404040, false);
        if (advanced) {
            graphics.drawString(font, Component.translatable(KEY + "phase_one"), 9,
                    twoRowsPerPhase ? 29 : 25, 0x303030, false);
            graphics.drawString(font, Component.translatable(KEY + "phase_two"), 9,
                    twoRowsPerPhase ? 81 : 53, 0x303030, false);
        } else {
            for (int i = 0; i < fields.length; i++) {
                if (basicPage == 0 ? i >= firstPageFieldCount() : i < firstPageFieldCount()) continue;
                int index = i < firstPageFieldCount() ? i : i - firstPageFieldCount();
                graphics.drawString(font, Component.translatable(KEY + FIELD_KEYS[i] + "_short"),
                        9 + index % 2 * 84, 27 + index / 2 * 31, 0x303030, false);
            }
        }
        if (presetsOpen) {
            for (int i = 0; i < WinefoxChallengePresets.COUNT; i++) {
                int color = menu.getPresets().get(i) == null ? 0x666666 : 0x303030;
                graphics.drawString(font, Component.translatable(KEY + "preset", i + 1),
                        183, 32 + i * 36, color, false);
            }
        } else if (advanced) {
            graphics.drawString(font, Component.translatable(KEY + "spell_level"), 183, 31, 0x303030, false);
            graphics.drawString(font, Component.translatable(KEY + "imitate_chance_short"), 183, 134, 0x303030, false);
        }
        if (imageHeight >= 210) {
            int warningY = imageHeight - 59;
            graphics.drawString(font, Component.translatable(KEY + "reward_warning_1"),
                    183, warningY, 0x9C3030, false);
            graphics.drawString(font, Component.translatable(KEY + "reward_warning_2"),
                    183, warningY + 10, 0x9C3030, false);
            graphics.drawString(font, Component.translatable(KEY + "reward_warning_3"),
                    183, warningY + 20, 0x9C3030, false);
        } else {
            graphics.drawString(font, Component.literal("!"), 169, 27, 0x9C3030, false);
        }
    }

    @Override
    public void render(GuiGraphics graphics, int mouseX, int mouseY, float partialTick) {
        renderBackground(graphics);
        super.render(graphics, mouseX, mouseY, partialTick);
        renderTooltip(graphics, mouseX, mouseY);
        if (imageHeight < 210 && mouseX >= leftPos + 166 && mouseX < leftPos + 178
                && mouseY >= topPos + 25 && mouseY < topPos + 38) {
            graphics.renderTooltip(font, Component.translatable(KEY + "reward_warning_full"),
                    mouseX, mouseY);
        }
        if (!draggedScroll.isEmpty()) graphics.renderItem(draggedScroll, mouseX - 8, mouseY - 8);
        if (!advanced) {
            for (int i = 0; i < fields.length; i++) {
                if (basicPage == 0 ? i >= firstPageFieldCount() : i < firstPageFieldCount()) continue;
                int index = i < firstPageFieldCount() ? i : i - firstPageFieldCount();
                int x = leftPos + 9 + index % 2 * 84;
                int y = topPos + 27 + index / 2 * 31;
                if (mouseX >= x && mouseX < x + 76 && mouseY >= y && mouseY < y + 9) {
                    graphics.renderTooltip(font, Component.translatable(KEY + FIELD_KEYS[i]), mouseX, mouseY);
                    break;
                }
            }
            return;
        }
        List<Rect2i> areas = visibleSpellAreas();
        for (int i = 0; i < areas.size(); i++) {
            if (!areas.get(i).contains(mouseX, mouseY)) continue;
            int index = selectedIndex(i);
            WinefoxSpellChoice choice = (index < first.length ? first : second)[index % first.length];
            if (choice != null) {
                ResourceLocation id = new ResourceLocation(choice.id());
                graphics.renderTooltip(font, Component.translatable("spell." + id.getNamespace() + "." + id.getPath()),
                        mouseX, mouseY);
            }
            break;
        }
    }
}
