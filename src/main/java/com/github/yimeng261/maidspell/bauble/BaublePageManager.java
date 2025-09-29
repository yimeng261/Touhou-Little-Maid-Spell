package com.github.yimeng261.maidspell.bauble;

import com.github.yimeng261.maidspell.Config;
import net.minecraft.world.entity.Entity;

import java.util.HashMap;
import java.util.Map;

/**
 * 饰品页面管理器
 * 管理每个女仆的饰品界面翻页状态
 */
public class BaublePageManager {
    
    // 每页显示的槽位数（3x3）
    public static final int SLOTS_PER_PAGE = 9;
    
    // 存储每个女仆的当前页面
    private static final Map<Integer, Integer> maidCurrentPage = new HashMap<>();
    
    /**
     * 获取指定女仆的当前页面
     */
    public static int getCurrentPage(Entity maid) {
        return maidCurrentPage.getOrDefault(maid.getId(), 0);
    }
    
    /**
     * 设置指定女仆的当前页面
     */
    public static void setCurrentPage(Entity maid, int page) {
        int maxPage = getMaxPage();
        if (page < 0) {
            page = 0;
        } else if (page > maxPage) {
            page = maxPage;
        }
        maidCurrentPage.put(maid.getId(), page);
    }
    
    /**
     * 翻到下一页
     */
    public static boolean nextPage(Entity maid) {
        int currentPage = getCurrentPage(maid);
        int maxPage = getMaxPage();
        if (currentPage < maxPage) {
            setCurrentPage(maid, currentPage + 1);
            return true;
        }
        return false;
    }
    
    /**
     * 翻到上一页
     */
    public static boolean previousPage(Entity maid) {
        int currentPage = getCurrentPage(maid);
        if (currentPage > 0) {
            setCurrentPage(maid, currentPage - 1);
            return true;
        }
        return false;
    }
    
    /**
     * 获取最大页数（从0开始计算）
     */
    public static int getMaxPage() {
        int totalSlots = Config.baubleSlotCounts;
        return Math.max(0, (totalSlots - 1) / SLOTS_PER_PAGE);
    }
    
    /**
     * 获取总页数（用于显示）
     */
    public static int getTotalPages() {
        return getMaxPage() + 1;
    }
    
    /**
     * 获取当前页面应该显示的槽位起始索引
     */
    public static int getPageStartSlot(Entity maid) {
        return getCurrentPage(maid) * SLOTS_PER_PAGE;
    }
    
    /**
     * 获取当前页面应该显示的槽位数量
     */
    public static int getSlotsInCurrentPage(Entity maid) {
        int currentPage = getCurrentPage(maid);
        int totalSlots = Config.baubleSlotCounts;
        int startSlot = currentPage * SLOTS_PER_PAGE;
        int remainingSlots = totalSlots - startSlot;
        return Math.min(SLOTS_PER_PAGE, remainingSlots);
    }
    
    /**
     * 检查指定页面中的槽位索引是否有效
     */
    public static boolean isValidSlotInPage(Entity maid, int slotInPage) {
        return slotInPage >= 0 && slotInPage < getSlotsInCurrentPage(maid);
    }
    
    /**
     * 将页面内的槽位索引转换为全局槽位索引
     */
    public static int pageSlotToGlobalSlot(Entity maid, int slotInPage) {
        return getPageStartSlot(maid) + slotInPage;
    }
    
    /**
     * 将全局槽位索引转换为页面和页面内索引
     */
    public static PageSlotInfo globalSlotToPageSlot(int globalSlot) {
        int page = globalSlot / SLOTS_PER_PAGE;
        int slotInPage = globalSlot % SLOTS_PER_PAGE;
        return new PageSlotInfo(page, slotInPage);
    }
    
    /**
     * 清理指定女仆的页面数据（当女仆被移除时调用）
     */
    public static void cleanupMaid(Entity maid) {
        maidCurrentPage.remove(maid.getId());
    }
    
    /**
     * 页面槽位信息
     */
    public static class PageSlotInfo {
        public final int page;
        public final int slotInPage;
        
        public PageSlotInfo(int page, int slotInPage) {
            this.page = page;
            this.slotInPage = slotInPage;
        }
    }
}
