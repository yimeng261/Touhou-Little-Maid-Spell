package com.github.yimeng261.maidspell;

import com.github.tartaricacid.touhoulittlemaid.entity.passive.EntityMaid;
import com.github.yimeng261.maidspell.api.AbstractSpellData;
import com.mojang.logging.LogUtils;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraftforge.event.entity.living.LivingDamageEvent;
import net.minecraftforge.event.entity.living.LivingHurtEvent;
import net.minecraftforge.event.entity.living.MobEffectEvent;

import org.slf4j.Logger;

import java.util.*;
import java.util.function.BiFunction;
import java.util.function.Function;

public class Global {

    public static final Logger LOGGER = LogUtils.getLogger();
    /**
     * 防止在玩家端回流时再次触发女仆端的 pre 逻辑而产生递归/重入
     */
    public static final ThreadLocal<Boolean> IN_REDIRECT = ThreadLocal.withInitial(() -> false);

    public static ArrayList<BiFunction<LivingHurtEvent,EntityMaid,Void>> common_damageProcessors = new ArrayList<>(){{
        add((event,maid)->{
            LivingEntity entity = event.getEntity();
            if(entity instanceof EntityMaid){
                event.setCanceled(true);
            }else if(entity instanceof Player){
                event.setCanceled(true);
            }
            return null;
        });
    }};

    /**
     * 一次性“发簪伤害重定向”票据：按玩家 UUID 归档，使用 FIFO 队列，匹配 DamageKey 并带有过期 tick
     */
    public static final HashMap<UUID, Deque<RedirectTicket>> hairpinTickets = new HashMap<>();

    public static ArrayList<BiFunction<LivingDamageEvent,Player,Void>> player_hurtProcessors_aft = new ArrayList<>(){{
        add((event,player)->{
            DamageSource source = event.getSource();
            Deque<RedirectTicket> queue = hairpinTickets.get(player.getUUID());
            if (queue == null || queue.isEmpty()) {
                return null;
            }

            long now = player.level().getGameTime();
            DamageKey key = DamageKey.build(player, source);

            // 清理过期并尝试匹配首个有效票据
            for (Iterator<RedirectTicket> it = queue.iterator(); it.hasNext(); ) {
                RedirectTicket ticket = it.next();
                if (ticket.expireTick < now) {
                    it.remove();
                    continue;
                }
                if (ticket.key.equals(key)) {
                    it.remove(); // 消费一次性票据
                    EntityMaid maid = Global.maidInfos
                            .computeIfAbsent(player.getUUID(), k -> new HashMap<>())
                            .getOrDefault(ticket.maidUUID, null);
                    if (maid != null) {
                        //LOGGER.debug("[hairpin] redirecting damage to maid: {} -> {}", player.getUUID(), maid.getUUID());
                        try {
                            IN_REDIRECT.set(true);
                            maid.hurt(source, event.getAmount());
                        } finally {
                            IN_REDIRECT.set(false);
                        }
                    } else {
                        LOGGER.debug("[hairpin] maid not found for ticket, maidUUID={}", ticket.maidUUID);
                    }
                    event.setCanceled(true);
                    break; // 一次匹配即退出
                }
            }
            return null;
        });
    }};

    public static final HashMap<UUID,HashMap<UUID,EntityMaid>> maidInfos = new HashMap<>();

    public static ArrayList<BiFunction<LivingHurtEvent,EntityMaid,Void>> common_hurtProcessors = new ArrayList<>();

    public static ArrayList<Function<AbstractSpellData.CoolDown, Void>> common_coolDownProcessors = new ArrayList<>();

    public static Map<String,BiFunction<LivingDamageEvent,EntityMaid,Void>> bauble_damageProcessors_aft = new HashMap<>();

    public static Map<String,BiFunction<LivingHurtEvent,EntityMaid,Void>> bauble_damageProcessors_pre = new HashMap<>();

    public static Map<String,BiFunction<LivingHurtEvent,EntityMaid,Void>> bauble_hurtProcessors_pre = new HashMap<>();

    public static Map<String,BiFunction<LivingDamageEvent,EntityMaid,Void>> bauble_hurtProcessors_aft = new HashMap<>();

    public static Map<String, Function<AbstractSpellData.CoolDown, Void>> bauble_coolDownProcessors = new HashMap<>();

    public static Map<String,BiFunction<MobEffectEvent.Added,EntityMaid,Void>> bauble_effectAddedProcessors = new HashMap<>();

    /**
     * 稳定匹配键：同一 tick、同一攻击者/直接体/伤害类型 视为同一伤害
     */
    public static final class DamageKey {
        public final UUID attackerUUID;     // 可能为 null
        public final UUID directUUID;       // 可能为 null
        public final String msgId;          // 伤害类型标识
        public final long tick;             // 世界时间 tick

        private DamageKey(UUID attackerUUID, UUID directUUID, String msgId, long tick) {
            this.attackerUUID = attackerUUID;
            this.directUUID = directUUID;
            this.msgId = msgId;
            this.tick = tick;
        }

        public static DamageKey build(LivingEntity victim, DamageSource source) {
            UUID a = source.getEntity() instanceof LivingEntity le ? le.getUUID() : null;
            UUID d = source.getDirectEntity() instanceof LivingEntity le ? le.getUUID() : null;
            String m = source.getMsgId();
            long t = victim.level().getGameTime();
            return new DamageKey(a, d, m, t);
        }

        @Override
        public boolean equals(Object o) {
            if (this == o) return true;
            if (o == null || getClass() != o.getClass()) return false;
            DamageKey damageKey = (DamageKey) o;
            return tick == damageKey.tick && Objects.equals(attackerUUID, damageKey.attackerUUID) && Objects.equals(directUUID, damageKey.directUUID) && Objects.equals(msgId, damageKey.msgId);
        }

        @Override
        public int hashCode() {
            return Objects.hash(attackerUUID, directUUID, msgId, tick);
        }
    }

    /**
     * 一次性重定向票据
     */
    public static final class RedirectTicket {
        public final UUID maidUUID;
        public final DamageKey key;
        public final long expireTick;

        public RedirectTicket(UUID maidUUID, DamageKey key, long expireTick) {
            this.maidUUID = maidUUID;
            this.key = key;
            this.expireTick = expireTick;
        }
    }
}
