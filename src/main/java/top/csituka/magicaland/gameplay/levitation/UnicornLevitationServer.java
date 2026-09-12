package top.csituka.magicaland.gameplay.levitation;

import java.util.HashMap;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import net.fabricmc.fabric.api.entity.event.v1.ServerLivingEntityEvents;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerLifecycleEvents;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerTickEvents;
import net.fabricmc.fabric.api.networking.v1.PacketByteBufs;
import net.fabricmc.fabric.api.networking.v1.ServerPlayConnectionEvents;
import net.fabricmc.fabric.api.networking.v1.ServerPlayNetworking;
import net.minecraft.network.packet.c2s.play.PlayerMoveC2SPacket;
import net.minecraft.network.packet.s2c.play.PositionFlag;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.util.math.Vec3d;
import top.csituka.magicaland.gameplay.race.RaceServer;
import top.csituka.magicaland.gameplay.remote.RemoteToolServer;
import static top.csituka.magicaland.gameplay.levitation.UnicornLevitationMath.Mode;

public final class UnicornLevitationServer {
    private static final Map<UUID, Pending> PENDING = new ConcurrentHashMap<>();
    private static final Map<UUID, Session> SESSIONS = new HashMap<>();
    private static long nextState;
    private UnicornLevitationServer() {}

    public static void register() {
        ServerPlayNetworking.registerGlobalReceiver(UnicornLevitationProtocol.CONTROL, (server, player, handler, buf, sender) -> {
            try {
                var control = UnicornLevitationProtocol.readControl(buf);
                PENDING.compute(player.getUuid(), (id, old) -> old == null || old.player != player
                        || control.token() > old.control.token()
                        || control.token() == old.control.token() && (control.sequence() > old.control.sequence()
                        && (old.control.enabled() || !control.enabled())) ? new Pending(player, control) : old);
            } catch (RuntimeException ignored) {}
        });
        ServerTickEvents.END_SERVER_TICK.register(server -> {
            long tick = server.getOverworld().getTime();
            for (var entry : PENDING.entrySet()) if (PENDING.remove(entry.getKey(), entry.getValue())) {
                var pending = entry.getValue();
                if (connected(pending.player)) receive(pending.player, pending.control, tick);
            }
            for (var session : SESSIONS.values().toArray(Session[]::new)) tick(session, tick);
        });
        ServerLivingEntityEvents.AFTER_DEATH.register((entity, source) -> {
            if (entity instanceof ServerPlayerEntity player) close(player, "invalid");
        });
        ServerPlayConnectionEvents.DISCONNECT.register((handler, server) -> {
            close(handler.player, "invalid"); SESSIONS.remove(handler.player.getUuid()); PENDING.remove(handler.player.getUuid());
        });
        ServerLifecycleEvents.SERVER_STOPPING.register(server -> {
            for (var session : SESSIONS.values().toArray(Session[]::new)) close(session.player, "invalid");
            SESSIONS.clear(); PENDING.clear();
        });
    }

    public static boolean active(ServerPlayerEntity player) {
        var session = SESSIONS.get(player.getUuid());
        return session != null && session.player == player && session.rules.open()
                && session.input.armed();
    }
    public static void stop(ServerPlayerEntity player) { close(player, "manual"); }
    public static boolean physicsActive(ServerPlayerEntity player) {
        var session = SESSIONS.get(player.getUuid());
        return session != null && session.player == player && session.rules.open() && session.input.armed() && session.mode != Mode.OFF
                && !session.rules.expired(now(player)) && session.dimension.equals(dimension(player))
                && invalid(player) == null && player.hurtTime == 0;
    }
    public static boolean allowMove(ServerPlayerEntity player, PlayerMoveC2SPacket packet) {
        var pending = PENDING.remove(player.getUuid());
        if (pending != null && pending.player == player && connected(player)) receive(player, pending.control, now(player));
        if (!physicsActive(player)) return true;
        var session = SESSIONS.get(player.getUuid());
        double x = packet.getX(player.getX()), y = packet.getY(player.getY()), z = packet.getZ(player.getZ());
        if (!Double.isFinite(x) || !Double.isFinite(y) || !Double.isFinite(z)) return true;
        var verdict = session.budget == null ? UnicornLevitationBudget.Verdict.REJECT
                : session.movementGuard.observe(session.budget.validate(now(player), x, y, z), now(player));
        if (verdict == UnicornLevitationBudget.Verdict.ACCEPT) return true;
        if (verdict == UnicornLevitationBudget.Verdict.REJECT) close(player, "movement");
        // requestTeleport 接收绝对角度；ROT 只控制发包时换算为相对角度。
        session.correctingMovement = true;
        try {
            player.networkHandler.requestTeleport(player.getX(), player.getY(), player.getZ(), player.getYaw(), player.getPitch(), PositionFlag.ROT);
        } finally { session.correctingMovement = false; }
        if (verdict == UnicornLevitationBudget.Verdict.CORRECT) {
            session.budget.reanchor(player.getX(), player.getY(), player.getZ());
            session.motion = session.budget.expected();
            send(session, true, UnicornLevitationProtocol.MOVEMENT_CORRECTION, true);
        }
        return false;
    }
    public static void teleported(ServerPlayerEntity player, Set<PositionFlag> flags) {
        var session = SESSIONS.get(player.getUuid());
        if (session == null || session.player != player || !session.rules.open() || session.correctingMovement
                || !session.dimension.equals(dimension(player))) return;
        session.position = player.getPos(); session.observedTick = now(player);
        session.grounded = player.isOnGround(); session.spaceGrounded = false;
        session.movementGuard.teleported(now(player));
        if (session.budget != null) {
            session.budget.teleport(player.getX(), player.getY(), player.getZ(),
                    flags.contains(PositionFlag.X), flags.contains(PositionFlag.Y), flags.contains(PositionFlag.Z));
            session.motion = session.budget.expected();
        } else session.motion = UnicornLevitationMath.Motion.ZERO;
    }
    public static boolean protectsFallDamage(ServerPlayerEntity player) {
        var session = SESSIONS.get(player.getUuid());
        return session != null && session.player == player && session.rules.protectsFall(session.input.armed(), now(player))
                && session.dimension.equals(dimension(player))
                && invalid(player) == null;
    }

    private static void receive(ServerPlayerEntity player, UnicornLevitationProtocol.Control input, long tick) {
        var session = SESSIONS.get(player.getUuid());
        if (session == null || session.player != player) {
            if (session != null) session.rules.close();
            session = new Session(player, new UnicornLevitationRules(), tick);
            SESSIONS.put(player.getUuid(), session);
        }
        if (session.rules.expired(tick)) close(player, "timeout");
        boolean wasOpen = session.rules.open();
        if (!session.rules.input(input.token(), input.sequence(), input.enabled(), tick)) return;
        session.spaceGrounded = UnicornLevitationRules.groundedPress(session.input != null && session.input.space(), input.space(),
                player.isOnGround() || session.grounded && tick - session.observedTick <= 2, session.spaceGrounded);
        session.input = input;
        if (!input.space()) session.rules.release();
        if (!input.enabled()) { close(player, "manual"); return; }
        String denial = invalid(player);
        if (denial == null && player.hurtTime > 0) denial = "hurt";
        if (denial == null && !wasOpen && SESSIONS.values().stream().filter(s -> s != SESSIONS.get(player.getUuid()) && s.rules.open()).count()
                >= UnicornLevitationRules.MAX_SESSIONS) denial = "busy";
        if (denial != null) { close(player, denial); return; }
        if (!wasOpen) {
            session.movementGuard.reset();
            session.dimension = dimension(player); session.position = player.getPos(); session.observedTick = tick;
            session.motion = motion(player.getVelocity()); session.mode = Mode.OFF; session.budget = null;
            session.health = player.getHealth(); session.absorption = player.getAbsorptionAmount();
        }
        // Releasing space is acknowledged before any further charging decision.
        if (!input.armed()) { session.mode = Mode.OFF; session.budget = null; }
        if (!input.space() && (session.mode == Mode.ASCEND || session.mode == Mode.HOVER || session.mode == Mode.RECOVER)) {
            session.mode = Mode.OFF;
            session.budget = null;
        }
        send(session, true, "");
    }
    private static void tick(Session session, long tick) {
        var player = session.player;
        if (!connected(player)) { SESSIONS.remove(player.getUuid(), session); return; }
        if (session.rules.open()) {
            String denial = invalid(player);
            if (denial == null && !session.dimension.equals(dimension(player))) denial = "dimension";
            if (denial == null && session.rules.expired(tick)) denial = "timeout";
            if (denial == null && (player.hurtTime > 0 || player.getHealth() < session.health || player.getAbsorptionAmount() < session.absorption)) denial = "hurt";
            if (denial != null) { close(player, denial); return; }
            long elapsed = tick - session.observedTick;
            if (elapsed > 0) {
                Vec3d measured = player.getPos().subtract(session.position).multiply(1.0 / elapsed);
                session.motion = motion(measured); session.position = player.getPos(); session.observedTick = tick;
            }
            var previous = session.mode;
            var controlMotion = session.budget == null ? session.motion : session.budget.expected();
            var support = UnicornLevitationGround.find(player.getWorld(), player, controlMotion);
            boolean held = session.rules.ready(session.input.armed(), session.input.space(), player.isOnGround() || session.spaceGrounded);
            session.spaceGrounded = false;
            session.mode = UnicornLevitationMath.chooseMode(session.input.armed(), held, session.input.sneak(), player.isOnGround(), previous, controlMotion.y(),
                    support == null ? Double.NaN : support.distance(), support != null && support.kind() != UnicornLevitationGround.Kind.SOLID, player.fallDistance);
            if (session.mode == Mode.OFF) session.budget = null;
            else {
                player.setSprinting(false);
                if (session.budget == null) session.budget = new UnicornLevitationBudget(player.getX(), player.getY(), player.getZ(), session.motion);
                float scale = player.isUsingItem() ? .2f : 1;
                float forward = ((session.input.forward() ? 1 : 0) - (session.input.backward() ? 1 : 0)) * scale;
                float sideways = ((session.input.left() ? 1 : 0) - (session.input.right() ? 1 : 0)) * scale;
                session.budget.advance(tick, session.input.yaw(), forward, sideways, session.mode,
                        player.getY(), support == null ? Double.NaN : support.y());
            }
            session.health = player.getHealth(); session.absorption = player.getAbsorptionAmount();
            session.grounded = player.isOnGround();
            if (previous != session.mode || tick % 5 == 0) send(session, true, "");
        }
    }
    private static boolean connected(ServerPlayerEntity player) {
        return player.getServer() != null && player.getServer().getPlayerManager().getPlayer(player.getUuid()) == player;
    }
    private static String invalid(ServerPlayerEntity player) {
        if (!connected(player) || !player.isAlive() || player.isRemoved() || player.isSpectator()) return "invalid";
        if (!RaceServer.isUnicorn(player)) return "race";
        if (RemoteToolServer.active(player)) return "remote";
        if (player.hasVehicle() || player.isSleeping() || player.isFallFlying() || player.isUsingRiptide()
                || player.isSubmergedInWater() || player.getAbilities().flying) return "stance";
        if (!ServerPlayNetworking.canSend(player, UnicornLevitationProtocol.STATE)) return "invalid";
        return null;
    }
    private static void close(ServerPlayerEntity player, String reason) {
        var session = SESSIONS.get(player.getUuid());
        if (session == null || session.player != player) return;
        session.rules.close(); session.mode = Mode.OFF; session.budget = null;
        session.spaceGrounded = false;
        if (session.rules.token() > 0) send(session, false, reason);
    }
    private static void send(Session session, boolean allowed, String reason) {
        send(session, allowed, reason, false);
    }
    private static void send(Session session, boolean allowed, String reason, boolean ownerOnly) {
        var player = session.player;
        if (player.getServer() == null) return;
        var state = new UnicornLevitationProtocol.State(player.getUuid(), session.rules.token(), ++nextState,
                session.rules.sequence(), dimension(player), allowed, allowed && session.input.armed(), session.mode,
                (float) session.rules.mana(), session.motion.x(), session.motion.y(), session.motion.z(), reason);
        for (var observer : player.getServer().getPlayerManager().getPlayerList()) {
            if (ownerOnly && observer != player) continue;
            if (observer != player && (observer.getWorld() != player.getWorld() || observer.squaredDistanceTo(player) > 128 * 128)) continue;
            if (!ServerPlayNetworking.canSend(observer, UnicornLevitationProtocol.STATE)) continue;
            var buf = PacketByteBufs.create(); UnicornLevitationProtocol.writeState(buf, state);
            ServerPlayNetworking.send(observer, UnicornLevitationProtocol.STATE, buf);
        }
    }
    private static long now(ServerPlayerEntity player) { return player.getServer().getOverworld().getTime(); }
    private static String dimension(ServerPlayerEntity player) { return player.getWorld().getRegistryKey().getValue().toString(); }
    private static UnicornLevitationMath.Motion motion(Vec3d value) {
        return new UnicornLevitationMath.Motion(clamp(value.x), clamp(value.y), clamp(value.z));
    }
    private static double clamp(double value) { return Double.isFinite(value) ? Math.max(-128, Math.min(128, value)) : 0; }
    private record Pending(ServerPlayerEntity player, UnicornLevitationProtocol.Control control) {}
    private static final class Session {
        final ServerPlayerEntity player;
        final UnicornLevitationRules rules;
        final UnicornLevitationMovementGuard movementGuard = new UnicornLevitationMovementGuard();
        UnicornLevitationProtocol.Control input;
        String dimension;
        Mode mode = Mode.OFF;
        UnicornLevitationMath.Motion motion;
        Vec3d position;
        UnicornLevitationBudget budget;
        long observedTick;
        boolean grounded, spaceGrounded, correctingMovement;
        float health, absorption;
        Session(ServerPlayerEntity player, UnicornLevitationRules rules, long tick) {
            this.player = player; this.rules = rules; dimension = dimension(player); position = player.getPos();
            observedTick = tick; motion = motion(player.getVelocity());
            health = player.getHealth(); absorption = player.getAbsorptionAmount();
            grounded = player.isOnGround();
        }
    }
}
