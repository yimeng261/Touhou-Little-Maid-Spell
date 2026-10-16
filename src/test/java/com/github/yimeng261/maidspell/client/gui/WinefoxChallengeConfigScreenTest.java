package com.github.yimeng261.maidspell.client.gui;

import com.github.yimeng261.maidspell.winefox.WinefoxChallengeConfig;
import com.github.yimeng261.maidspell.winefox.WinefoxChallengeConfigMenu;
import com.github.yimeng261.maidspell.winefox.WinefoxChallengePresets;
import com.mojang.blaze3d.font.GlyphInfo;
import com.mojang.blaze3d.font.SheetGlyphInfo;
import io.netty.buffer.Unpooled;
import net.minecraft.SharedConstants;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.client.gui.font.FontSet;
import net.minecraft.client.gui.font.glyphs.BakedGlyph;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.Bootstrap;
import net.minecraft.world.entity.player.Inventory;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.BeforeAll;

import java.lang.reflect.Field;
import java.util.List;
import java.util.function.Function;

import static org.junit.jupiter.api.Assertions.*;

/** Exercises the real screen's widgets and input routing without a render context. */
class WinefoxChallengeConfigScreenTest {
    @BeforeAll
    static void bootstrap() throws Exception {
        SharedConstants.tryDetectVersion();
        // Full Forge bootstrap installs network listeners that require ModLauncher
        // transformations. These widget tests only need the vanilla registries.
        Field bootstrapped = Bootstrap.class.getDeclaredField("isBootstrapped");
        bootstrapped.setAccessible(true);
        bootstrapped.setBoolean(null, true);
        if (!BuiltInRegistries.PAINTING_VARIANT.containsKey(new ResourceLocation("minecraft", "kebab"))) {
            BuiltInRegistries.bootStrap();
        }
    }

    @Test
    void basicInputsKeepFocusAndAcceptTextWhenSelectedRepeatedly() throws Exception {
        TestScreen screen = screen(240);
        EditBox[] fields = (EditBox[]) value(screen, "fields");
        for (int index : new int[]{0, 1, 0, 1, 1}) {
            EditBox field = fields[index];
            assertTrue(screen.children().contains(field), "Input must receive screen events directly");
            screen.setFocused(field);
            assertSame(field, screen.getFocused());
            assertTrue(field.isFocused());
            field.setValue("");
            assertTrue(screen.charTyped('1', 0));
            assertTrue(screen.charTyped('8', 0));
            assertEquals("18", field.getValue());
        }
    }

    @Test
    void scrollbarReceivesDragAndReleaseOutsideThePanel() throws Exception {
        TestScreen screen = screen(240);
        EditBox[] fields = (EditBox[]) value(screen, "fields");
        int before = fields[0].getY();
        assertTrue(screen.mouseClicked(screen.left() + 171, screen.top() + 45, 0));
        assertTrue(screen.mouseDragged(screen.left() + 171, screen.top() + 70, 0, 0, 25));
        assertTrue(fields[0].getY() < before);
        assertTrue(screen.mouseReleased(screen.left() + 300, screen.top() + 150, 0));
        int released = fields[0].getY();
        // No scrollbar drag remains pending after an outside release.
        Object panel = value(screen, "basicPanel");
        assertFalse(((net.minecraftforge.client.gui.widget.ScrollPanel) panel)
                .mouseDragged(screen.left() + 171, screen.top() + 90, 0, 0, 20));
        assertEquals(released, fields[0].getY());
    }

    @Test
    void scrollingUpdatesHitboxesAndOffscreenInputsDoNotTakeClicks() throws Exception {
        TestScreen screen = screen(180);
        EditBox[] fields = (EditBox[]) value(screen, "fields");
        int before = fields[0].getY();
        assertTrue(screen.mouseScrolled(screen.left() + 40, screen.top() + 45, -20));
        assertTrue(fields[0].getY() < before);
        assertFalse(fields[0].mouseClicked(fields[0].getX() + 5, fields[0].getY() + 5, 0));
        screen.setFocused(fields[8]);
        assertTrue(fields[8].isFocused());
        fields[8].setValue("");
        assertTrue(screen.charTyped('1', 0));
        assertEquals("1", fields[8].getValue());
        assertTrue(fields[8].getY() + fields[8].getHeight() < screen.top() + screen.inventoryY());
    }

    @Test
    void switchingModesReleasesHiddenInputFocusAndRestoresEditing() throws Exception {
        TestScreen screen = screen(240);
        EditBox[] fields = (EditBox[]) value(screen, "fields");
        screen.setFocused(fields[1]);
        Field defaultsPlaced = WinefoxChallengeConfigScreen.class.getDeclaredField("defaultsPlaced");
        defaultsPlaced.setAccessible(true);
        defaultsPlaced.setBoolean(screen, true);
        Button mode = (Button) value(screen, "modeButton");
        mode.onPress();
        assertNull(screen.getFocused());
        assertFalse(fields[1].visible);
        assertFalse(fields[1].active);
        mode.onPress();
        assertTrue(fields[1].visible);
        screen.setFocused(fields[1]);
        fields[1].setValue("");
        assertTrue(screen.charTyped('9', 0));
        assertEquals("9", fields[1].getValue());
    }

    private static TestScreen screen(int height) {
        Inventory inventory = new Inventory(null);
        WinefoxChallengeConfig config = new WinefoxChallengeConfig(600, 12, 1, 1, 0.08, 6,
                0.5, 5, 0.8, false, false, 25, List.of(), List.of(), false);
        FriendlyByteBuf buffer = new FriendlyByteBuf(Unpooled.buffer());
        try {
            buffer.writeInt(0);
            config.writeToBuffer(buffer);
            config.writeToBuffer(buffer);
            WinefoxChallengePresets.writeBuffer(buffer, WinefoxChallengePresets.empty());
            TestScreen screen = new TestScreen(WinefoxChallengeConfigMenu.createClientSide(1, inventory, buffer), inventory);
            screen.initialize(height);
            return screen;
        } finally {
            buffer.release();
        }
    }

    private static Object value(TestScreen screen, String name) throws Exception {
        Field field = WinefoxChallengeConfigScreen.class.getDeclaredField(name);
        field.setAccessible(true);
        return field.get(screen);
    }

    private static class TestScreen extends WinefoxChallengeConfigScreen {
        TestScreen(WinefoxChallengeConfigMenu menu, Inventory inventory) {
            super(menu, inventory, Component.literal("Challenge"));
        }

        void initialize(int guiHeight) {
            width = 400;
            height = guiHeight;
            FontSet fontSet = new FontSet(null, new ResourceLocation("test", "font")) {
                @Override
                public GlyphInfo getGlyphInfo(int codePoint, boolean filterFishyGlyphs) {
                    return new GlyphInfo() {
                        @Override
                        public float getAdvance() {
                            return 6;
                        }

                        @Override
                        public BakedGlyph bake(Function<SheetGlyphInfo, BakedGlyph> stitch) {
                            throw new UnsupportedOperationException("Tests do not render glyphs");
                        }
                    };
                }
            };
            font = new Font(location -> fontSet, false);
            init();
        }

        int left() { return leftPos; }
        int top() { return topPos; }
        int inventoryY() { return imageHeight - 86; }
    }
}
