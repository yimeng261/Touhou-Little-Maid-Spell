package com.github.yimeng261.maidspell.network.message;

import com.github.tartaricacid.touhoulittlemaid.client.gui.entity.maid.backpack.IBackpackContainerScreen;
import com.github.yimeng261.maidspell.client.event.MaidBackpackEnderPocketIntegration;
import com.github.yimeng261.maidspell.client.gui.EnderPocketScreen;
import com.github.yimeng261.maidspell.item.bauble.enderPocket.EnderPocketService;
import net.minecraft.client.Minecraft;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.api.distmarker.OnlyIn;

import java.util.List;

/**
 * 客户端专用的末影腰包消息处理类
 * 用于隔离客户端专用代码，避免在服务端加载时出现类加载错误
 */
@OnlyIn(Dist.CLIENT)
public class EnderPocketClientHandler {
    
    /**
     * 处理客户端的末影腰包消息
     */
    public static void handleClientMessage(EnderPocketMessage.Type type, 
                                         List<EnderPocketService.EnderPocketMaidInfo> maidInfos, 
                                         boolean fromMaidBackpack) {
        Minecraft mc = Minecraft.getInstance();
        if (mc.player == null) return;
        
        switch (type) {
            case RESPONSE_MAID_LIST:
                // 更新女仆背包集成的数据
                MaidBackpackEnderPocketIntegration.updateEnderPocketData(maidInfos);
                
                // 根据请求来源和当前界面决定显示方式
                if (fromMaidBackpack) {
                    // 来自女仆背包界面的请求
                    if (mc.screen instanceof IBackpackContainerScreen) {
                        // 重新初始化界面以更新按钮
                        mc.screen.init(mc, mc.getWindow().getGuiScaledWidth(), mc.getWindow().getGuiScaledHeight());
                    }
                } else {
                    // 直接打开末影腰包选择界面
                    mc.setScreen(new EnderPocketScreen(maidInfos));
                }
                break;
                
            case SERVER_PUSH_UPDATE:
                // 服务器主动推送的数据更新
                MaidBackpackEnderPocketIntegration.updateEnderPocketData(maidInfos);
                
                // 如果当前在女仆背包界面，刷新界面
                if (mc.screen instanceof IBackpackContainerScreen) {
                    mc.screen.init(mc, mc.getWindow().getGuiScaledWidth(), mc.getWindow().getGuiScaledHeight());
                }
                break;
                
            default:
                // 其他情况不在客户端处理
                break;
        }
    }
}
