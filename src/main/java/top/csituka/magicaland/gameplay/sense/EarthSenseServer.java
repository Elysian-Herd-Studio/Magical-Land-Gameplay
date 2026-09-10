package top.csituka.magicaland.gameplay.sense;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import net.fabricmc.fabric.api.entity.event.v1.ServerLivingEntityEvents;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerLifecycleEvents;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerTickEvents;
import net.fabricmc.fabric.api.networking.v1.PacketByteBufs;
import net.fabricmc.fabric.api.networking.v1.ServerPlayConnectionEvents;
import net.fabricmc.fabric.api.networking.v1.ServerPlayNetworking;
import net.minecraft.entity.LivingEntity;
import net.minecraft.entity.mob.Angerable;
import net.minecraft.entity.mob.MobEntity;
import net.minecraft.entity.mob.Monster;
import net.minecraft.entity.passive.PassiveEntity;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.util.TypeFilter;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Box;
import net.minecraft.util.math.MathHelper;
import top.csituka.magicaland.gameplay.race.RaceDefinitions;
import top.csituka.magicaland.gameplay.race.RaceState;
import top.csituka.magicaland.gameplay.remote.RemoteToolServer;

public final class EarthSenseServer {
    private static final Map<UUID, Pending> PENDING = new ConcurrentHashMap<>();
    private static final Map<UUID, Lease> LEASES = new HashMap<>();
    private static final Map<UUID, Session> ACTIVE = new LinkedHashMap<>();
    private EarthSenseServer() {}

    public static boolean active(ServerPlayerEntity player) { return ACTIVE.containsKey(player.getUuid()); }
    public static double viewerQuality(ServerPlayerEntity player) {
        var session = ACTIVE.get(player.getUuid());
        return session != null && session.player == player ? session.motion.quality() : 0;
    }
    public static void stop(ServerPlayerEntity player) { close(player, "manual"); }

    public static void register() {
        ServerPlayNetworking.registerGlobalReceiver(EarthSenseProtocol.CONTROL, (server, player, handler, buf, sender) -> {
            try {
                var control = EarthSenseProtocol.readControl(buf);
                PENDING.compute(player.getUuid(), (id, previous) -> previous == null || previous.player != player
                        || control.token() > previous.control.token()
                        || control.token() == previous.control.token() && !control.enabled()
                        ? new Pending(player, control) : previous);
            } catch (RuntimeException ignored) {}
        });
        ServerTickEvents.END_SERVER_TICK.register(server -> {
            long tick = server.getOverworld().getTime();
            for (var entry : PENDING.entrySet()) if (PENDING.remove(entry.getKey(), entry.getValue())) {
                var pending = entry.getValue();
                if (connected(pending.player)) receive(pending.player, pending.control, tick);
            }
            for (var session : ACTIVE.values().toArray(Session[]::new)) tick(session, tick);
        });
        ServerLivingEntityEvents.AFTER_DEATH.register((entity, source) -> {
            if (entity instanceof ServerPlayerEntity player) close(player, "invalid");
        });
        ServerPlayConnectionEvents.DISCONNECT.register((handler, server) -> {
            UUID id = handler.player.getUuid();
            close(handler.player, "invalid");
            ACTIVE.remove(id); LEASES.remove(id); PENDING.remove(id);
        });
        ServerLifecycleEvents.SERVER_STOPPED.register(server -> { ACTIVE.clear(); LEASES.clear(); PENDING.clear(); });
    }

    private static void receive(ServerPlayerEntity player, EarthSenseProtocol.Control control, long tick) {
        Lease state = LEASES.computeIfAbsent(player.getUuid(), ignored -> new Lease());
        if (state.rules.expired(tick)) close(player, "timeout");
        long oldToken = state.rules.token();
        var action = state.rules.control(control.token(), control.enabled(), tick);
        if (oldToken != state.rules.token()) state.sequence = 0;
        if (action == EarthSenseLease.Action.IGNORE || action == EarthSenseLease.Action.RENEW) return;
        ACTIVE.remove(player.getUuid());
        if (action == EarthSenseLease.Action.STOP) { send(player, state, false, false, "manual", List.of()); return; }
        String denial = invalid(player);
        if (denial == null && ACTIVE.size() >= EarthSenseRules.MAX_ACTIVE) denial = "busy";
        var grid = new GroundGrid(player.getServerWorld(), 32);
        if (denial == null && (!player.isOnGround() || grid.support(player) == null)) denial = "stance";
        if (denial == null && player.hurtTime > 0) denial = "hurt";
        if (denial != null) { state.rules.close(); send(player, state, false, false, denial, List.of()); return; }
        var session = new Session(player, state, tick);
        ACTIVE.put(player.getUuid(), session);
    }

    private static boolean connected(ServerPlayerEntity player) {
        return player.getServer() != null && player.getServer().getPlayerManager().getPlayer(player.getUuid()) == player;
    }
    private static String invalid(ServerPlayerEntity player) {
        if (!connected(player) || !player.isAlive() || player.isRemoved() || player.isSpectator()) return "invalid";
        if (!RaceDefinitions.EARTH_PONY_ID.equals(RaceState.get(player.getServer()).race(player.getUuid()))) return "race";
        if (RemoteToolServer.active(player)) return "remote";
        if (player.hasVehicle() || player.isSleeping() || player.isTouchingWater() || player.isInLava()
                || player.getAbilities().flying || player.isFallFlying()) return "stance";
        if (!ServerPlayNetworking.canSend(player, EarthSenseProtocol.STATE)) return "invalid";
        return null;
    }
    private static void tick(Session session, long tick) {
        var player = session.player;
        String denial = invalid(player);
        if (denial == null && !session.dimension.equals(dimension(player))) denial = "dimension";
        if (denial == null && session.lease.rules.expired(tick)) denial = "timeout";
        if (denial != null) { close(player, denial); return; }
        boolean sampling = tick % EarthSenseRules.SAMPLE_TICKS == 0;
        var grid = new GroundGrid(player.getServerWorld(), sampling ? EarthSenseRules.MAX_PROBES : 32);
        var support = player.isOnGround() ? grid.support(player) : null;
        var velocity = player.getVelocity();
        denial = session.focus.denial(player.getX(), player.getY(), player.getZ(), player.getHealth(),
                player.getAbsorptionAmount(), player.hurtTime, player.getLastAttackedTime(), player.getAttacker() != null, support != null,
                velocity.x, velocity.y, velocity.z);
        if (denial != null) { close(player, denial); return; }
        session.motion.observe(player.getX(), player.getZ(), tick);
        if (!session.focus.ready(tick)) return;
        if (sampling) sample(session, grid, support, tick);
        else if (!session.announced) send(player, session.lease, true, true, "", List.of());
        session.announced = true;
    }
    private static void sample(Session session, GroundGrid grid, EarthSenseGround.Cell origin, long tick) {
        var player = session.player;
        double range = EarthSenseViewerMotion.range(session.motion.quality());
        var nearby = new ArrayList<LivingEntity>();
        player.getServerWorld().collectEntitiesByType(TypeFilter.instanceOf(LivingEntity.class),
                player.getBoundingBox().expand(range, 3, range),
                entity -> true, nearby, EarthSenseRules.MAX_CANDIDATES);
        var candidates = new ArrayList<Candidate>();
        var tracks = new HashMap<UUID, Track>();
        for (var entity : nearby) {
            if (entity == player || !entity.isAlive() || entity.isRemoved() || entity.isSpectator()
                    || entity.hasVehicle() || entity.isTouchingWater() || entity.isInLava() || entity.isFallFlying()) continue;
            double dx = entity.getX()-player.getX(), dy = entity.getY()-player.getY(), dz = entity.getZ()-player.getZ();
            double distance = Math.sqrt(dx*dx + dy*dy + dz*dz);
            if (distance > range) continue;
            boolean grounded = entity.isOnGround();
            Track previous = session.tracks.get(entity.getUuid());
            tracks.put(entity.getUuid(), new Track(entity.getX(), entity.getZ(), tick, grounded));
            if (!grounded) continue;
            var support = grid.support(entity);
            if (support == null) continue;
            double speed = previous == null ? Math.hypot(entity.getX()-entity.prevX, entity.getZ()-entity.prevZ)
                    : Math.hypot(entity.getX()-previous.x, entity.getZ()-previous.z) / Math.max(1, tick-previous.tick);
            int activity = EarthSenseRules.activity(speed, previous != null && !previous.grounded,
                    entity.isSneaking(), entity.isSprinting());
            var bounds = entity.getBoundingBox();
            var center = bounds.getCenter();
            candidates.add(new Candidate(support, new EarthSenseSignals.Observation(entity.getUuid(), kind(entity, player),
                    center.x, center.y, center.z, (float)Math.max(bounds.maxX-bounds.minX, bounds.maxZ-bounds.minZ),
                    (float)(bounds.maxY-bounds.minY), distance, activity)));
        }
        session.tracks = tracks;
        var connected = new ArrayList<EarthSenseSignals.Observation>();
        if (!candidates.isEmpty()) {
            var reach = EarthSenseGround.search(origin, grid, EarthSenseRules.MAX_NODES, EarthSenseRules.MAX_PROBES);
            for (var candidate : candidates) if (reach.contains(candidate.support)) connected.add(candidate.observation);
        }
        try {
            send(player, session.lease, true, true, "", session.signals.update(connected, tick, range));
        } catch (IllegalStateException exhausted) {
            close(player, "invalid");
        }
    }
    private static int kind(LivingEntity entity, ServerPlayerEntity observer) {
        boolean neutral = entity instanceof Angerable;
        boolean threatens = entity instanceof MobEntity mob && mob.getTarget() == observer
                || entity instanceof Angerable angry && angry.shouldAngerAt(observer);
        return EarthSenseRules.kind(entity instanceof PlayerEntity, threatens, neutral,
                entity instanceof Monster, entity instanceof PassiveEntity);
    }
    private static void close(ServerPlayerEntity player, String reason) {
        ACTIVE.remove(player.getUuid());
        Lease lease = LEASES.get(player.getUuid());
        if (lease == null || lease.rules.closed()) return;
        lease.rules.close();
        send(player, lease, false, false, reason, List.of());
    }
    private static String dimension(ServerPlayerEntity player) { return player.getWorld().getRegistryKey().getValue().toString(); }
    private static void send(ServerPlayerEntity player, Lease lease, boolean active, boolean grounded,
                             String reason, List<EarthSenseProtocol.Signal> signals) {
        if (lease.sequence == Integer.MAX_VALUE) {
            ACTIVE.remove(player.getUuid()); lease.rules.close(); return;
        }
        if (!ServerPlayNetworking.canSend(player, EarthSenseProtocol.STATE)) return;
        var packet = PacketByteBufs.create();
        EarthSenseProtocol.writeState(packet, new EarthSenseProtocol.State(lease.rules.token(), ++lease.sequence,
                dimension(player), active, grounded, reason, signals));
        ServerPlayNetworking.send(player, EarthSenseProtocol.STATE, packet);
    }

    private record Pending(ServerPlayerEntity player, EarthSenseProtocol.Control control) {}
    private static final class Lease { final EarthSenseLease rules = new EarthSenseLease(); int sequence; }
    private static final class Session {
        final ServerPlayerEntity player;
        final Lease lease;
        final String dimension;
        final EarthSenseSignals signals = new EarthSenseSignals();
        final EarthSenseFocus focus;
        final EarthSenseViewerMotion motion;
        Map<UUID, Track> tracks = Map.of();
        boolean announced;
        Session(ServerPlayerEntity player, Lease lease, long tick) {
            this.player = player; this.lease = lease; dimension = dimension(player);
            focus = new EarthSenseFocus(player.getHealth(),
                    player.getAbsorptionAmount(), player.getLastAttackedTime(), tick);
            motion = new EarthSenseViewerMotion(player.getX(), player.getZ(), tick,
                    Math.hypot(player.getX()-player.prevX, player.getZ()-player.prevZ));
        }
    }
    private record Track(double x, double z, long tick, boolean grounded) {}
    private record Candidate(EarthSenseGround.Cell support, EarthSenseSignals.Observation observation) {}

    private static final class GroundGrid implements EarthSenseGround.Conductor {
        private final ServerWorld world;
        private final int budget;
        private final Map<EarthSenseGround.Cell, List<EarthSenseGround.Shape>> cache = new HashMap<>();
        GroundGrid(ServerWorld world, int budget) { this.world = world; this.budget = budget; }
        private List<EarthSenseGround.Shape> boxes(EarthSenseGround.Cell cell) {
            var old = cache.get(cell);
            if (old != null) return old;
            if (cache.size() >= budget) return List.of();
            var pos = new BlockPos(cell.x(), cell.y(), cell.z());
            List<EarthSenseGround.Shape> boxes = List.of();
            if (world.isInBuildLimit(pos) && world.isChunkLoaded(pos)) {
                var state = world.getBlockState(pos);
                if (state.getFluidState().isEmpty()) boxes = state.getCollisionShape(world, pos).getBoundingBoxes().stream()
                        .filter(box -> box.maxX > box.minX && box.maxY > box.minY && box.maxZ > box.minZ)
                        .limit(32).map(box -> new EarthSenseGround.Shape(box.minX, box.minY, box.minZ, box.maxX, box.maxY, box.maxZ)).toList();
            }
            cache.put(cell, boxes);
            return boxes;
        }
        @Override public boolean test(int x, int y, int z) { return !boxes(new EarthSenseGround.Cell(x,y,z)).isEmpty(); }
        @Override public boolean connects(EarthSenseGround.Cell from, EarthSenseGround.Cell to) {
            for (var a : boxes(from)) for (var b : boxes(to)) if (EarthSenseGround.touches(a, from, b, to)) return true;
            return false;
        }
        EarthSenseGround.Cell support(LivingEntity entity) {
            Box bounds = entity.getBoundingBox();
            double[] xs = {entity.getX(), bounds.minX+.001, bounds.maxX-.001};
            double[] zs = {entity.getZ(), bounds.minZ+.001, bounds.maxZ-.001};
            int baseY = MathHelper.floor(bounds.minY-.001);
            for (int down = 0; down <= 1; down++) for (double x : xs) for (double z : zs) {
                var cell = new EarthSenseGround.Cell(MathHelper.floor(x), baseY-down, MathHelper.floor(z));
                for (var box : boxes(cell)) {
                    double top = box.maxY()+cell.y();
                    if (Math.abs(top-bounds.minY) <= .125 && box.maxX()+cell.x() > bounds.minX+.0001
                            && box.minX()+cell.x() < bounds.maxX-.0001 && box.maxZ()+cell.z() > bounds.minZ+.0001
                            && box.minZ()+cell.z() < bounds.maxZ-.0001) return cell;
                }
            }
            return null;
        }
    }
}
