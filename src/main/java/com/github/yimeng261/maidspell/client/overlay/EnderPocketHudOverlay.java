package com.github.yimeng261.maidspell.client.overlay;

import com.github.tartaricacid.touhoulittlemaid.entity.passive.EntityMaid;
import com.github.yimeng261.maidspell.client.EnderPocketClientConfig;
import com.github.yimeng261.maidspell.item.bauble.enderPocket.EnderPocketService;
import com.mojang.math.Axis;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.screens.inventory.InventoryScreen;
import net.minecraft.client.renderer.Rect2i;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.Entity;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.api.distmarker.OnlyIn;
import net.minecraftforge.client.gui.overlay.ForgeGui;
import net.minecraftforge.client.gui.overlay.IGuiOverlay;
import org.joml.Quaternionf;

import java.util.HashMap;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/** Left-side status column for maids linked through an Ender Pocket. */
@OnlyIn(Dist.CLIENT)
public final class EnderPocketHudOverlay implements IGuiOverlay {
    public static final int ROW_WIDTH = 176;
    private static final int ROW_HEIGHT = 44;
    private static final Map<UUID, EntityMaid> PREVIEW_MAIDS = new HashMap<>();
    private static List<EnderPocketService.EnderPocketMaidInfo> maidInfos = List.of();

    public static void update(List<EnderPocketService.EnderPocketMaidInfo> infos) {
        maidInfos = List.copyOf(infos);
        PREVIEW_MAIDS.keySet().removeIf(id -> infos.stream().noneMatch(info -> info.maidUUID().equals(id)));
    }

    @Override
    public void render(ForgeGui gui, GuiGraphics graphics, float partialTick, int screenWidth, int screenHeight) {
        Minecraft mc = Minecraft.getInstance();
        if (mc.player == null || mc.level == null || mc.options.hideGui || mc.screen != null
                || maidInfos.isEmpty() || !EnderPocketClientConfig.HUD_ENABLED.get()) {
            return;
        }

        int rowPitch = ROW_HEIGHT + 3;
        int usableBottom = screenHeight - Math.max(gui.leftHeight, gui.rightHeight) - 6;
        int preferredX = Mth.clamp(EnderPocketClientConfig.HUD_X.get(),
                0, Math.max(0, screenWidth - ROW_WIDTH));
        int preferredY = Mth.clamp(EnderPocketClientConfig.HUD_Y.get(),
                0, Math.max(0, usableBottom - ROW_HEIGHT));
        int maxRows = Math.min(maidInfos.size(), Math.max(0, (usableBottom + 3) / rowPitch));
        if (maxRows == 0) {
            return;
        }

        List<Rect2i> exclusions = HudExclusionAreas.get();
        int[] position = null;
        int visibleRows = maxRows;
        while (visibleRows > 0 && position == null) {
            int hudHeight = visibleRows * rowPitch - 3;
            position = findNearestFreePosition(preferredX, preferredY, ROW_WIDTH, hudHeight,
                    screenWidth, usableBottom, exclusions);
            if (position == null) {
                visibleRows--;
            }
        }
        if (position == null) {
            return;
        }

        for (int i = 0; i < visibleRows; i++) {
            renderRow(graphics, mc, maidInfos.get(i), position[0], position[1] + i * rowPitch);
        }
    }

    private static int[] findNearestFreePosition(int preferredX, int preferredY, int width, int height,
                                                 int screenWidth, int screenBottom, List<Rect2i> exclusions) {
        int maxX = Math.max(0, screenWidth - width);
        int maxY = Math.max(0, screenBottom - height);
        List<Integer> xCandidates = new ArrayList<>();
        List<Integer> yCandidates = new ArrayList<>();
        xCandidates.add(Mth.clamp(preferredX, 0, maxX));
        xCandidates.add(0);
        xCandidates.add(maxX);
        yCandidates.add(Mth.clamp(preferredY, 0, maxY));
        yCandidates.add(0);
        yCandidates.add(maxY);

        for (Rect2i area : exclusions) {
            xCandidates.add(Mth.clamp(area.getX() - width - 4, 0, maxX));
            xCandidates.add(Mth.clamp(area.getX() + area.getWidth() + 4, 0, maxX));
            yCandidates.add(Mth.clamp(area.getY() - height - 4, 0, maxY));
            yCandidates.add(Mth.clamp(area.getY() + area.getHeight() + 4, 0, maxY));
        }

        int[] best = null;
        long bestDistance = Long.MAX_VALUE;
        for (int x : xCandidates) {
            for (int y : yCandidates) {
                if (!isFree(x, y, width, height, exclusions)) {
                    continue;
                }
                long dx = x - (long) preferredX;
                long dy = y - (long) preferredY;
                long distance = dx * dx + dy * dy;
                if (distance < bestDistance) {
                    bestDistance = distance;
                    best = new int[]{x, y};
                }
            }
        }
        return best;
    }

    private static boolean isFree(int x, int y, int width, int height, List<Rect2i> exclusions) {
        for (Rect2i area : exclusions) {
            if (area.getWidth() <= 0 || area.getHeight() <= 0) {
                continue;
            }
            if (x < area.getX() + area.getWidth() && x + width > area.getX()
                    && y < area.getY() + area.getHeight() && y + height > area.getY()) {
                return false;
            }
        }
        return true;
    }

    public static int getEditorPreviewHeight(int availableBottom, int y) {
        int rowPitch = ROW_HEIGHT + 3;
        int requestedRows = Math.max(1, maidInfos.size());
        int visibleRows = Math.min(requestedRows, Math.max(1, (availableBottom - y) / rowPitch));
        return visibleRows * rowPitch - 3;
    }

    public static void renderEditorPreview(GuiGraphics graphics, int x, int y, int availableBottom) {
        Minecraft mc = Minecraft.getInstance();
        if (mc.player == null || mc.level == null) {
            return;
        }
        if (maidInfos.isEmpty()) {
            graphics.fill(x, y, x + ROW_WIDTH, y + ROW_HEIGHT, 0xB8101018);
            graphics.renderOutline(x, y, ROW_WIDTH, ROW_HEIGHT, 0xFF565664);
            graphics.drawCenteredString(mc.font,
                    net.minecraft.network.chat.Component.translatable("gui.maidspell.ender_pocket.hud_editor.title"),
                    x + ROW_WIDTH / 2, y + 18, 0xFFFFFFFF);
            return;
        }

        int rowPitch = ROW_HEIGHT + 3;
        int visibleRows = Math.min(maidInfos.size(), Math.max(1, (availableBottom - y) / rowPitch));
        for (int i = 0; i < visibleRows; i++) {
            renderRow(graphics, mc, maidInfos.get(i), x, y + i * rowPitch);
        }
    }

    private static void renderRow(GuiGraphics graphics, Minecraft mc,
                                  EnderPocketService.EnderPocketMaidInfo info, int x, int y) {
        graphics.fill(x, y, x + ROW_WIDTH, y + ROW_HEIGHT, 0xB8101018);
        graphics.renderOutline(x, y, ROW_WIDTH, ROW_HEIGHT,
                info.hasAnchorCore() ? 0xFF8A6BC7 : 0xFF565664);

        renderAvatar(graphics, mc, info, x + 2, y + 2);

        String name = trimToWidth(mc, info.maidName(), 92);
        graphics.drawString(mc.font, name, x + 42, y + 4, 0xFFFFFFFF, true);

        int barX = x + 42;
        int barY = y + 17;
        int barWidth = 79;
        float ratio = info.maxHealth() <= 0.0F ? 0.0F : Mth.clamp(info.health() / info.maxHealth(), 0.0F, 1.0F);
        graphics.fill(barX, barY, barX + barWidth, barY + 8, 0xFF28191D);
        graphics.fill(barX + 1, barY + 1, barX + 1 + Math.round((barWidth - 2) * ratio), barY + 7,
                ratio > 0.25F ? 0xFFD74752 : 0xFF9E2430);
        String health = Math.max(0, Mth.ceil(info.health())) + "/" + Math.max(0, Mth.ceil(info.maxHealth()));
        graphics.drawCenteredString(mc.font, health, barX + barWidth / 2, barY, 0xFFFFFFFF);

        graphics.drawString(mc.font, "\u25C6 " + info.armor(), x + 125, y + 17, 0xFFB8D5ED, true);
        String coordinates = Mth.floor(info.x()) + ", " + Mth.floor(info.y()) + ", " + Mth.floor(info.z());
        graphics.drawString(mc.font, trimToWidth(mc, coordinates, 96),
                x + 42, y + 31, 0xFFB8B8C5, false);

        boolean sameDimension = mc.level.dimension().location().toString().equals(info.dimension());
        renderDirectionArrow(graphics, mc, info, x + 163, y + 14,
                sameDimension ? 0xFFFFFFFF : 0xFFB78CFF);
        if (sameDimension) {
            int distance = Mth.floor(Math.sqrt(mc.player.distanceToSqr(info.x(), info.y(), info.z())));
            graphics.drawCenteredString(mc.font, distance + "m", x + 158, y + 31, 0xFFD6D6DE);
        } else {
            graphics.drawCenteredString(mc.font, "DIM", x + 158, y + 31, 0xFFB78CFF);
        }
    }

    private static void renderAvatar(GuiGraphics graphics, Minecraft mc,
                                     EnderPocketService.EnderPocketMaidInfo info, int x, int y) {
        Entity entity = mc.level.getEntity(info.maidEntityId());
        EntityMaid maid = entity instanceof EntityMaid candidate
                && candidate.getUUID().equals(info.maidUUID()) ? candidate : null;
        if (maid == null) {
            maid = PREVIEW_MAIDS.get(info.maidUUID());
            if (maid == null || maid.level() != mc.level) {
                maid = new EntityMaid(mc.level);
                PREVIEW_MAIDS.put(info.maidUUID(), maid);
            }
            maid.setModelId(info.modelId());
            maid.setCustomNameVisible(false);
        }

        graphics.enableScissor(x, y, x + 34, y + 40);
        Quaternionf pose = new Quaternionf().rotateZ((float) Math.PI).rotateY((float) Math.PI);
        InventoryScreen.renderEntityInInventory(graphics, x + 17, y + 39, 16, pose, null, maid);
        graphics.disableScissor();
    }

    private static void renderDirectionArrow(GuiGraphics graphics, Minecraft mc,
                                             EnderPocketService.EnderPocketMaidInfo info,
                                             int x, int y, int color) {
        double dx = info.x() - mc.player.getX();
        double dz = info.z() - mc.player.getZ();
        float bearing = (float) Math.toDegrees(Math.atan2(-dx, dz));
        float rotation = Mth.wrapDegrees(bearing - mc.player.getYRot());
        graphics.pose().pushPose();
        graphics.pose().translate(x, y, 0);
        graphics.pose().mulPose(Axis.ZP.rotationDegrees(rotation));
        graphics.drawCenteredString(mc.font, "\u2191", 0, -4, color);
        graphics.pose().popPose();
    }

    private static String trimToWidth(Minecraft mc, String text, int width) {
        if (mc.font.width(text) <= width) {
            return text;
        }
        return mc.font.plainSubstrByWidth(text, Math.max(0, width - mc.font.width("..."))) + "...";
    }
}
