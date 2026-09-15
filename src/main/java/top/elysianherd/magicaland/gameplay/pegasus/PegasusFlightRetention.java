package top.elysianherd.magicaland.gameplay.pegasus;

import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import net.minecraft.entity.Entity;
import net.minecraft.entity.SpawnGroup;
import net.minecraft.entity.mob.MobEntity;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.util.math.Vec3d;

public final class PegasusFlightRetention {
    private static final double CAPTURE_RADIUS = 48, AREA_RADIUS = 64;
    private static final int MAX_MOBS = 64, DURATION_TICKS = 1200;
    private static final Map<UUID, Flight> FLIGHTS = new HashMap<>();
    private static final Map<UUID, Set<UUID>> WATCHERS = new HashMap<>();

    private PegasusFlightRetention() {}

    public static void begin(ServerPlayerEntity player) {
        var previous = FLIGHTS.get(player.getUuid());
        if (previous != null) end(previous.player());
        if (!PegasusFlightServer.physicsActive(player)) return;
        var world = player.getServerWorld();
        var nearby = world.getEntitiesByClass(MobEntity.class, player.getBoundingBox().expand(CAPTURE_RADIUS),
                mob -> mob.isAlive() && !mob.isRemoved() && !mob.isPersistent()
                        && mob.getType().getSpawnGroup() == SpawnGroup.MONSTER
                        && player.squaredDistanceTo(mob) <= CAPTURE_RADIUS * CAPTURE_RADIUS);
        nearby.sort(Comparator.comparingDouble(player::squaredDistanceTo));
        Set<UUID> targets = new HashSet<>();
        for (int i = 0; i < Math.min(MAX_MOBS, nearby.size()); i++) {
            UUID id = nearby.get(i).getUuid();
            targets.add(id);
            WATCHERS.computeIfAbsent(id, ignored -> new HashSet<>()).add(player.getUuid());
        }
        if (!targets.isEmpty()) FLIGHTS.put(player.getUuid(), new Flight(player, world, player.getPos(),
                player.getServer().getTicks(), Set.copyOf(targets)));
    }

    public static void tick(ServerPlayerEntity player) {
        var flight = FLIGHTS.get(player.getUuid());
        if (flight != null && flight.player() == player && !valid(flight)) end(player);
    }

    public static void end(ServerPlayerEntity player) {
        var flight = FLIGHTS.get(player.getUuid());
        if (flight == null || flight.player() != player) return;
        FLIGHTS.remove(player.getUuid());
        for (UUID target : flight.targets()) {
            var watchers = WATCHERS.get(target);
            if (watchers == null) continue;
            watchers.remove(player.getUuid());
            if (watchers.isEmpty()) WATCHERS.remove(target);
        }
    }

    public static void clear() { FLIGHTS.clear(); WATCHERS.clear(); }

    public static double adjustDistance(MobEntity mob, double distanceSquared) {
        if (mob.getWorld().isClient || !mob.isAlive() || mob.isRemoved()) return distanceSquared;
        var watchers = WATCHERS.get(mob.getUuid());
        if (watchers == null) return distanceSquared;
        for (UUID watcher : watchers) {
            var flight = FLIGHTS.get(watcher);
            if (flight != null && flight.targets().contains(mob.getUuid()) && mob.getWorld() == flight.world()
                    && withinArea(mob, flight.origin()) && valid(flight)) {
                // 仅改变原版距离消失判定，不写入永久持久化标记。
                return Math.min(distanceSquared, 31 * 31);
            }
        }
        return distanceSquared;
    }

    private static boolean valid(Flight flight) {
        var player = flight.player();
        if (player.getServer() == null || player.getWorld() != flight.world()) return false;
        long elapsed = (long) player.getServer().getTicks() - flight.started();
        return elapsed >= 0 && elapsed < DURATION_TICKS && withinArea(player, flight.origin())
                && PegasusFlightServer.physicsActive(player);
    }

    private static boolean withinArea(Entity entity, Vec3d origin) {
        double dx = entity.getX() - origin.x, dz = entity.getZ() - origin.z;
        return dx * dx + dz * dz <= AREA_RADIUS * AREA_RADIUS;
    }

    private record Flight(ServerPlayerEntity player, ServerWorld world, Vec3d origin, long started, Set<UUID> targets) {}
}
