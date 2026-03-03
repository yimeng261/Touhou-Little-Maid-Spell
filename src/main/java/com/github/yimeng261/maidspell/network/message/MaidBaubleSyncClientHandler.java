package com.github.yimeng261.maidspell.network.message;

import com.github.tartaricacid.touhoulittlemaid.entity.passive.EntityMaid;
import net.minecraft.client.Minecraft;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.world.entity.Entity;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.api.distmarker.OnlyIn;
import org.slf4j.Logger;
import com.mojang.logging.LogUtils;

import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/**
 * 女仆饰品同步客户端处理器
 * 处理从服务器接收的女仆饰品数据
 */
@OnlyIn(Dist.CLIENT)
public class MaidBaubleSyncClientHandler {
    private static final Logger LOGGER = LogUtils.getLogger();

    // 缓存待同步的饰品数据，用于处理实体还未加载的情况
    private static final Map<UUID, CompoundTag> PENDING_SYNC = new ConcurrentHashMap<>();

    /**
     * 处理饰品同步消息
     * @param maidUUID 女仆UUID
     * @param baubleData 饰品数据NBT
     */
    public static void handleBaubleSync(UUID maidUUID, CompoundTag baubleData) {
        LOGGER.info("[饰品同步] 收到女仆 {} 的饰品同步请求", maidUUID);

        Minecraft mc = Minecraft.getInstance();
        if (mc.level == null) {
            LOGGER.warn("[饰品同步] 客户端世界为空，无法同步");
            return;
        }

        // 尝试查找女仆实体
        EntityMaid maid = findMaidEntity(maidUUID);

        if (maid != null) {
            // 找到了女仆，立即同步
            if (baubleData != null) {
                maid.getMaidBauble().deserializeNBT(baubleData);
                LOGGER.info("[饰品同步] 成功同步女仆 {} 的饰品数据", maidUUID);
            }
        } else {
            // 没找到女仆，缓存数据等待后续处理
            LOGGER.warn("[饰品同步] 暂时找不到女仆实体 {}，将数据缓存", maidUUID);
            PENDING_SYNC.put(maidUUID, baubleData);
        }
    }

    /**
     * 尝试应用待同步的数据
     * 应该在渲染层或其他合适的时机调用
     */
    public static void tryApplyPendingSync(EntityMaid maid) {
        UUID maidUUID = maid.getUUID();
        CompoundTag baubleData = PENDING_SYNC.remove(maidUUID);

        if (baubleData != null) {
            maid.getMaidBauble().deserializeNBT(baubleData);
            LOGGER.info("[饰品同步] 延迟同步成功：女仆 {}", maidUUID);
        }
    }

    /**
     * 查找女仆实体
     */
    private static EntityMaid findMaidEntity(UUID maidUUID) {
        Minecraft mc = Minecraft.getInstance();
        if (mc.level == null) {
            return null;
        }

        // 遍历所有实体查找
        for (Entity entity : mc.level.entitiesForRendering()) {
            if (entity instanceof EntityMaid maid && maid.getUUID().equals(maidUUID)) {
                return maid;
            }
        }

        return null;
    }
}
