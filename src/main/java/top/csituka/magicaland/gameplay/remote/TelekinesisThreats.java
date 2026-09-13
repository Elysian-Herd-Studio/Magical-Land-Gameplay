package top.csituka.magicaland.gameplay.remote;

import net.minecraft.entity.LivingEntity;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.server.network.ServerPlayerEntity;

import java.util.LinkedHashMap;
import java.util.UUID;
import java.util.WeakHashMap;

public final class TelekinesisThreats {
    private static final int RECENT_TICKS = 100, MAX_ATTACKERS = 32;
    private static final WeakHashMap<ServerPlayerEntity, LinkedHashMap<UUID, Integer>> ATTACKERS = new WeakHashMap<>();

    private TelekinesisThreats() {}

    public static void record(ServerPlayerEntity player, LivingEntity attacker) {
        if (attacker == player || attacker.getWorld() != player.getWorld()) return;
        var recent = ATTACKERS.computeIfAbsent(player, ignored -> new LinkedHashMap<>());
        recent.values().removeIf(tick -> expired(player.age, tick));
        recent.remove(attacker.getUuid());
        recent.put(attacker.getUuid(), player.age);
        while (recent.size() > MAX_ATTACKERS) recent.remove(recent.keySet().iterator().next());
    }

    public static boolean recent(PlayerEntity viewer, LivingEntity target) {
        if (!(viewer instanceof ServerPlayerEntity player)) return false;
        var recent = ATTACKERS.get(player);
        if (recent == null) return false;
        recent.values().removeIf(tick -> expired(player.age, tick));
        if (recent.isEmpty()) { ATTACKERS.remove(player); return false; }
        return recent.containsKey(target.getUuid());
    }

    private static boolean expired(int now, int tick) {
        int elapsed = now - tick;
        return elapsed < 0 || elapsed >= RECENT_TICKS;
    }
}
