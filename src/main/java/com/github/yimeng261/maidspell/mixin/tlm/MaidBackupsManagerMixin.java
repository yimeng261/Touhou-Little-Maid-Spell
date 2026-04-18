package com.github.yimeng261.maidspell.mixin.tlm;

import com.github.tartaricacid.touhoulittlemaid.world.backups.MaidBackupsManager;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.NbtIo;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.storage.LevelResource;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.UUID;

import static com.github.tartaricacid.touhoulittlemaid.TouhouLittleMaid.LOGGER;

@Mixin(value = MaidBackupsManager.class, remap = false)
public class MaidBackupsManagerMixin {
    @Unique
    private static final String MAIDSPELL$BACKUPS_FOLDER_NAME = "maid_backups";

    @Inject(method = "getMaidBackupFiles", at = @At("HEAD"), cancellable = true, remap = false)
    private static void maidspell$useOverworldBackupFolderForListing(ServerPlayer player, UUID maidUuid,
                                                                     CallbackInfoReturnable<List<String>> cir) {
        List<String> backupFiles = new ArrayList<>();
        Path folderPath = maidspell$getBackupFolder(player, maidUuid);
        if (folderPath == null || !Files.isDirectory(folderPath)) {
            cir.setReturnValue(backupFiles);
            return;
        }

        File[] files = folderPath.toFile().listFiles((dir, name) -> name.endsWith(".dat"));
        if (files == null) {
            cir.setReturnValue(backupFiles);
            return;
        }

        for (File file : files) {
            backupFiles.add(file.getName());
        }
        backupFiles.sort(Comparator.reverseOrder());
        cir.setReturnValue(backupFiles);
    }

    @Inject(method = "getMaidBackFile", at = @At("HEAD"), cancellable = true, remap = false)
    private static void maidspell$useOverworldBackupFolderForRestore(ServerPlayer player, UUID maidUuid, String fileName,
                                                                     CallbackInfoReturnable<CompoundTag> cir) {
        Path folderPath = maidspell$getBackupFolder(player, maidUuid);
        if (folderPath == null) {
            cir.setReturnValue(new CompoundTag());
            return;
        }

        File file = folderPath.resolve(fileName).toFile();
        if (!file.exists() || !file.isFile()) {
            cir.setReturnValue(new CompoundTag());
            return;
        }

        try {
            cir.setReturnValue(NbtIo.readCompressed(file));
        } catch (IOException e) {
            LOGGER.error("Failed to read maid backup file: {}", file, e);
            cir.setReturnValue(new CompoundTag());
        }
    }

    @Unique
    private static Path maidspell$getBackupFolder(ServerPlayer player, UUID maidUuid) {
        MinecraftServer server = player.getServer();
        if (server == null) {
            return null;
        }

        ServerLevel overworld = server.getLevel(Level.OVERWORLD);
        if (overworld == null) {
            return null;
        }

        return server.getWorldPath(LevelResource.ROOT)
                .resolve("data")
                .resolve(MAIDSPELL$BACKUPS_FOLDER_NAME)
                .resolve(player.getUUID().toString())
                .resolve(maidUuid.toString());
    }
}
