package com.github.yimeng261.maidspell.spell.manager;

import com.github.tartaricacid.touhoulittlemaid.entity.passive.EntityMaid;
import com.github.yimeng261.maidspell.Config;
import com.mojang.logging.LogUtils;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.scores.PlayerTeam;
import net.minecraft.world.scores.Scoreboard;
import org.slf4j.Logger;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

/**
 * 女仆结盟管理器
 * 使用原版Minecraft的Team系统来管理女仆与玩家的结盟状态
 * 确保在法术战斗时能正确识别友军
 */
public class AllianceManager {
    private static final Logger LOGGER = LogUtils.getLogger();

    // 存储女仆的临时队伍：女仆UUID -> 队伍名称
    private static final Map<UUID, String> maidTeamMap = new HashMap<>();

    // 队伍名称前缀
    private static final String TEAM_PREFIX = "maidspell_alliance_";

    /**
     * 设置女仆与玩家的结盟状态
     * @param maid 女仆实体
     * @param allied 是否结盟
     */
    public static void setMaidAlliance(EntityMaid maid, boolean allied) {
        if (maid == null || !(maid.level() instanceof ServerLevel)) {
            return;
        }

        if(maid.getOwner() == null || !(maid.getOwner() instanceof Player)){
            return;
        }

        Player owner = (Player) maid.getOwner();
        ServerLevel level = (ServerLevel) maid.level();
        Scoreboard scoreboard = level.getScoreboard();
        UUID maidUUID = maid.getUUID();


        if (allied && Config.autoAllianceEnabled) {
            // 创建或获取队伍
            String teamName = TEAM_PREFIX + maidUUID.toString().substring(0, 8);
            PlayerTeam team = scoreboard.getPlayerTeam(teamName);
            if (team == null) {
                team = scoreboard.addPlayerTeam(teamName);
                // 设置队伍属性
                team.setAllowFriendlyFire(false); // 禁止友军伤害
                team.setSeeFriendlyInvisibles(true); // 可以看到友军隐身
            }

            try {
                scoreboard.addPlayerToTeam(maid.getName().getString(), team);
                scoreboard.addPlayerToTeam(owner.getName().getString(), team);

                maidTeamMap.put(maidUUID, teamName);
                LOGGER.debug("女仆 {} 已与玩家 {} 结盟 (队伍: {})",
                    maid.getName().getString(),
                    owner.getName().getString(),
                    teamName);
            } catch (Exception e) {
                LOGGER.error("设置女仆结盟时出错", e);
            }
        } else {
            // 解除结盟
            String teamName = maidTeamMap.remove(maidUUID);
            if (teamName != null) {
                try {
                    PlayerTeam team = scoreboard.getPlayerTeam(teamName);
                    if (team != null) {
                        // 从队伍中移除
                        scoreboard.removePlayerFromTeam(maid.getName().getString());
                        scoreboard.removePlayerFromTeam(owner.getName().getString());
                        maidTeamMap.remove(maidUUID);
                        scoreboard.removePlayerTeam(team);
                        LOGGER.debug("女仆 {} 已与玩家 {} 解除结盟 (队伍: {})",
                            maid.getName().getString(),
                            owner.getName().getString(),
                            teamName);
                    }


                } catch (Exception e) {
                    LOGGER.error("解除女仆结盟时出错", e);
                }
            }
        }
    }


    /**
     * 获取所有结盟状态
     * @deprecated 推荐使用 isAllied(UUID) 来检查单个女仆的结盟状态，避免不必要的 HashMap 复制
     */
    @Deprecated
    public static Map<UUID, String> getAllianceStatus() {
        return new HashMap<>(maidTeamMap);
    }

    /**
     * 检查指定女仆是否已结盟
     * @param maidId 女仆UUID
     * @return 如果女仆已结盟返回 true
     */
    public static boolean isAllied(UUID maidId) {
        return maidTeamMap.containsKey(maidId);
    }

    /**
     * 获取结盟状态的只读视图（不复制）
     * @return 结盟状态的不可修改视图
     */
    public static Map<UUID, String> getAllianceStatusView() {
        return java.util.Collections.unmodifiableMap(maidTeamMap);
    }
}