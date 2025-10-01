package com.github.yimeng261.maidspell.item.common.WindSeekingBell;

import net.minecraft.server.level.ServerLevel;

import java.util.concurrent.locks.Lock;
import java.util.concurrent.locks.ReentrantLock;

/**
 * 分段锁工具类
 * 用于提高并发性能，避免全局锁的性能瓶颈
 * 
 * 工作原理：
 * - 将锁空间分割为多个段
 * - 根据ServerLevel的哈希值映射到对应的锁段
 * - 同一世界的结构检查使用同一个锁，保证StructureCheck线程安全
 * - 不同世界（主世界、下界、末地）可以并发执行
 */
public class StripedLock {
    
    private final Lock[] locks;
    private final int stripeMask;
    
    /**
     * 创建分段锁
     * @param stripeCount 锁段数量，必须是2的幂次方
     */
    public StripedLock(int stripeCount) {
        // 确保 stripeCount 是2的幂次方
        if (Integer.bitCount(stripeCount) != 1) {
            throw new IllegalArgumentException("Stripe count must be a power of 2");
        }
        
        this.locks = new Lock[stripeCount];
        for (int i = 0; i < stripeCount; i++) {
            this.locks[i] = new ReentrantLock();
        }
        
        this.stripeMask = stripeCount - 1;
    }
    
    /**
     * 根据ServerLevel获取对应的锁
     * 同一世界的所有结构检查使用同一个锁，保证StructureCheck线程安全
     * 
     * @param level ServerLevel实例
     * @return 对应的锁对象
     */
    public Lock getLock(ServerLevel level) {
        // 使用ServerLevel的System.identityHashCode来分配锁
        // 这确保同一个ServerLevel实例总是使用同一个锁
        int hash = System.identityHashCode(level);
        int index = hash & stripeMask;
        return locks[index];
    }
    
    
    /**
     * 执行需要锁保护的操作
     * 自动获取锁、执行操作、释放锁
     * 
     * @param level ServerLevel实例
     * @param action 要执行的操作
     * @param <T> 返回值类型
     * @return 操作的返回值
     */
    public <T> T executeWithLock(ServerLevel level, java.util.function.Supplier<T> action) {
        Lock lock = getLock(level);
        lock.lock();
        try {
            return action.get();
        } finally {
            lock.unlock();
        }
    }
    
    /**
     * 获取锁段数量
     * @return 锁段数量
     */
    public int getStripeCount() {
        return locks.length;
    }
}
