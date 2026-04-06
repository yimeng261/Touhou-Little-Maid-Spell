package com.github.yimeng261.maidspell.event;

import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.Tag;
import net.minecraft.server.MinecraftServer;
import net.minecraft.world.level.saveddata.SavedData;
import org.jetbrains.annotations.NotNull;

import java.util.*;

/**
 * 持久化存储玩家节日祝福记录
 * 记录每个玩家在每个节日是否已经收到过祝福
 */
public class FestivalGreetingData extends SavedData {
    private static final String DATA_NAME = "maidspell_festival_greetings";
    
    // 存储格式：玩家UUID -> (节日ID -> 已发送年份列表)
    private final Map<UUID, Map<String, Set<Integer>>> playerGreetings = new HashMap<>();
    
    public FestivalGreetingData() {
        super();
    }
    
    /**
     * 从NBT加载数据
     */
    public static FestivalGreetingData load(CompoundTag tag) {
        FestivalGreetingData data = new FestivalGreetingData();
        
        ListTag playerList = tag.getList("Players", Tag.TAG_COMPOUND);
        for (int i = 0; i < playerList.size(); i++) {
            CompoundTag playerTag = playerList.getCompound(i);
            UUID playerUUID = playerTag.getUUID("UUID");
            
            Map<String, Set<Integer>> festivals = new HashMap<>();
            CompoundTag festivalsTag = playerTag.getCompound("Festivals");
            
            for (String festivalId : festivalsTag.getAllKeys()) {
                int[] years = festivalsTag.getIntArray(festivalId);
                Set<Integer> yearSet = new HashSet<>();
                for (int year : years) {
                    yearSet.add(year);
                }
                festivals.put(festivalId, yearSet);
            }
            
            data.playerGreetings.put(playerUUID, festivals);
        }
        
        return data;
    }
    
    /**
     * 保存数据到NBT
     */
    @Override
    public @NotNull CompoundTag save(@NotNull CompoundTag tag) {
        ListTag playerList = new ListTag();
        
        for (Map.Entry<UUID, Map<String, Set<Integer>>> entry : playerGreetings.entrySet()) {
            CompoundTag playerTag = new CompoundTag();
            playerTag.putUUID("UUID", entry.getKey());
            
            CompoundTag festivalsTag = new CompoundTag();
            for (Map.Entry<String, Set<Integer>> festivalEntry : entry.getValue().entrySet()) {
                int[] years = festivalEntry.getValue().stream().mapToInt(Integer::intValue).toArray();
                festivalsTag.putIntArray(festivalEntry.getKey(), years);
            }
            
            playerTag.put("Festivals", festivalsTag);
            playerList.add(playerTag);
        }
        
        tag.put("Players", playerList);
        return tag;
    }
    
    /**
     * 检查玩家在某年是否已经收到过某个节日的祝福
     */
    public boolean hasReceivedGreeting(UUID playerUUID, String festivalId, int year) {
        Map<String, Set<Integer>> festivals = playerGreetings.get(playerUUID);
        if (festivals == null) {
            return false;
        }
        
        Set<Integer> years = festivals.get(festivalId);
        return years != null && years.contains(year);
    }
    
    /**
     * 标记玩家在某年已经收到过某个节日的祝福
     */
    public void markGreetingReceived(UUID playerUUID, String festivalId, int year) {
        Map<String, Set<Integer>> festivals = playerGreetings.computeIfAbsent(playerUUID, k -> new HashMap<>());
        Set<Integer> years = festivals.computeIfAbsent(festivalId, k -> new HashSet<>());
        years.add(year);
        setDirty();
    }
    
    /**
     * 获取服务器的节日祝福数据实例
     */
    public static FestivalGreetingData get(MinecraftServer server) {
        return server.overworld().getDataStorage().computeIfAbsent(
            FestivalGreetingData::load,
            FestivalGreetingData::new,
            DATA_NAME
        );
    }
}

