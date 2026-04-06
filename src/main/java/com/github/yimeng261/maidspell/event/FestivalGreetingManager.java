package com.github.yimeng261.maidspell.event;

import com.github.yimeng261.maidspell.MaidSpellMod;
import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;

import java.time.LocalDate;
import java.time.Month;
import java.util.HashMap;
import java.util.Map;

/**
 * 节日祝福管理器
 * 负责检测节日日期并向玩家发送祝福消息
 */
public class FestivalGreetingManager {
    
    /**
     * 节日定义类
     */
    private static class Festival {
        final String id;
        final Month month;
        final int day;
        final String title;
        final String subtitle;
        final String message;
        final ChatFormatting titleColor;
        final ChatFormatting messageColor;
        
        Festival(String id, Month month, int day, String title, String subtitle, 
                String message, ChatFormatting titleColor, ChatFormatting messageColor) {
            this.id = id;
            this.month = month;
            this.day = day;
            this.title = title;
            this.subtitle = subtitle;
            this.message = message;
            this.titleColor = titleColor;
            this.messageColor = messageColor;
        }
        
        boolean isToday(LocalDate date) {
            return date.getMonth() == month && date.getDayOfMonth() == day;
        }
    }
    
    // 定义所有节日（2026年及以后的年份）
    private static final Map<String, Festival> FESTIVALS = new HashMap<>();
    
    static {
        // 大年初一 - 2月17日（从2026年开始）
        FESTIVALS.put("lunar_new_year_day1", new Festival(
            "lunar_new_year_day1",
            Month.FEBRUARY, 17,
            "大年初一·开门红",
            "万法伊始",
            "新春甫至，紫气东来。愿您与女仆：法杖生辉光更耀，书卷常开墨愈浓。",
            ChatFormatting.GOLD,
            ChatFormatting.YELLOW
        ));
        
        // 大年初二 - 2月18日
        FESTIVALS.put("lunar_new_year_day2", new Festival(
            "lunar_new_year_day2",
            Month.FEBRUARY, 18,
            "大年初二·回门",
            "主仆共鸣",
            "初二日暖，主仆同辉。祝您和女仆：主仆同心心意契，福泽双至共朝晖。",
            ChatFormatting.GOLD,
            ChatFormatting.YELLOW
        ));
        
        // 大年初三 - 2月19日
        FESTIVALS.put("lunar_new_year_day3", new Festival(
            "lunar_new_year_day3",
            Month.FEBRUARY, 19,
            "大年初三·小年朝",
            "锐意进取",
            "初三赤狗，宜宅家，宜\"御火\"。愿您：御火迎难千嶂越，法力浩荡万般宁。",
            ChatFormatting.GOLD,
            ChatFormatting.YELLOW
        ));
        
        // 元宵节 - 3月3日（2026年）
        FESTIVALS.put("lantern_festival", new Festival(
            "lantern_festival",
            Month.MARCH, 3,
            "元宵节·圆满",
            "灯火映照法术书",
            "上元佳节，花灯如昼。愿您：灯火千盏照前路，万法归宗启新程。",
            ChatFormatting.LIGHT_PURPLE,
            ChatFormatting.AQUA
        ));
    }
    
    /**
     * 检查并发送节日祝福
     * 在玩家登录时调用
     */
    public static void checkAndSendGreeting(ServerPlayer player) {
        try {
            LocalDate today = LocalDate.now();
            int currentYear = today.getYear();
            
            // 只在2026年及以后生效
            if (currentYear < 2026) {
                return;
            }
            
            // 获取持久化数据
            FestivalGreetingData data = FestivalGreetingData.get(player.getServer());
            
            // 检查所有节日
            for (Festival festival : FESTIVALS.values()) {
                if (festival.isToday(today)) {
                    // 检查今年是否已经发送过
                    if (!data.hasReceivedGreeting(player.getUUID(), festival.id, currentYear)) {
                        // 发送祝福
                        sendGreeting(player, festival);
                        
                        // 标记为已发送
                        data.markGreetingReceived(player.getUUID(), festival.id, currentYear);
                        
                        MaidSpellMod.LOGGER.info("Sent festival greeting '{}' to player {} for year {}", 
                            festival.title, player.getName().getString(), currentYear);
                    }
                }
            }
        } catch (Exception e) {
            MaidSpellMod.LOGGER.error("Failed to check/send festival greeting", e);
        }
    }
    
    /**
     * 发送节日祝福消息给玩家
     */
    private static void sendGreeting(ServerPlayer player, Festival festival) {
        // 发送空行
        player.sendSystemMessage(Component.literal(""));
        
        // 发送装饰线
        player.sendSystemMessage(Component.literal("═══════════════════════════════════")
            .withStyle(ChatFormatting.GRAY));
        
        // 发送标题
        player.sendSystemMessage(Component.literal("✨ " + festival.title + " ✨")
            .withStyle(festival.titleColor, ChatFormatting.BOLD));
        
        // 发送副标题
        player.sendSystemMessage(Component.literal("【" + festival.subtitle + "】")
            .withStyle(ChatFormatting.GRAY, ChatFormatting.ITALIC));
        
        // 发送空行
        player.sendSystemMessage(Component.literal(""));
        
        // 发送祝福消息
        player.sendSystemMessage(Component.literal(festival.message)
            .withStyle(festival.messageColor));
        
        // 发送空行
        player.sendSystemMessage(Component.literal(""));
        
        // 发送制作组署名
        player.sendSystemMessage(Component.literal("—— ")
            .withStyle(ChatFormatting.GRAY)
            .append(Component.literal("万法皆通制作组")
                .withStyle(ChatFormatting.AQUA, ChatFormatting.BOLD))
            .append(Component.literal(" 敬上")
                .withStyle(ChatFormatting.GRAY)));
        
        // 发送装饰线
        player.sendSystemMessage(Component.literal("═══════════════════════════════════")
            .withStyle(ChatFormatting.GRAY));
        
        // 发送空行
        player.sendSystemMessage(Component.literal(""));
    }
    
    /**
     * 手动触发节日祝福（用于测试）
     */
    public static void sendTestGreeting(ServerPlayer player, String festivalId) {
        Festival festival = FESTIVALS.get(festivalId);
        if (festival != null) {
            sendGreeting(player, festival);
            MaidSpellMod.LOGGER.info("Sent test greeting '{}' to player {}", 
                festival.title, player.getName().getString());
        } else {
            player.sendSystemMessage(Component.literal("未知的节日ID: " + festivalId)
                .withStyle(ChatFormatting.RED));
        }
    }
    
    /**
     * 获取所有可用的节日ID（用于测试命令）
     */
    public static String[] getAllFestivalIds() {
        return FESTIVALS.keySet().toArray(new String[0]);
    }
}

