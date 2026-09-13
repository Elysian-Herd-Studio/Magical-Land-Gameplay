package top.csituka.magicaland.gameplay.pegasus;

import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import net.fabricmc.fabric.api.entity.event.v1.ServerLivingEntityEvents;
import net.fabricmc.fabric.api.entity.event.v1.ServerPlayerEvents;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerLifecycleEvents;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerTickEvents;
import net.fabricmc.fabric.api.networking.v1.PacketByteBufs;
import net.fabricmc.fabric.api.networking.v1.ServerPlayConnectionEvents;
import net.fabricmc.fabric.api.networking.v1.ServerPlayNetworking;
import net.minecraft.entity.LivingEntity;
import net.minecraft.entity.MovementType;
import net.minecraft.entity.passive.AbstractHorseEntity;
import net.minecraft.entity.passive.TameableEntity;
import net.minecraft.network.packet.c2s.play.PlayerMoveC2SPacket;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.sound.SoundCategory;
import net.minecraft.sound.SoundEvents;
import net.minecraft.util.hit.HitResult;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Box;
import net.minecraft.util.math.MathHelper;
import net.minecraft.util.math.Vec3d;
import net.minecraft.world.RaycastContext;
import top.csituka.magicaland.gameplay.levitation.UnicornLevitationGround;
import top.csituka.magicaland.gameplay.levitation.UnicornLevitationMath;
import top.csituka.magicaland.gameplay.race.RaceDefinitions;
import top.csituka.magicaland.gameplay.race.RaceState;
import top.csituka.magicaland.gameplay.remote.RemoteToolServer;
import static top.csituka.magicaland.gameplay.pegasus.PegasusFlightMath.*;

public final class PegasusFlightServer {
    private static final Map<UUID, Pending> PENDING = new ConcurrentHashMap<>();
    private static final Map<UUID, Session> SESSIONS = new HashMap<>();
    private static long nextState;
    // 首版延续现有能力测试开放规则，后续接天赋树。
    public static boolean impactUnlocked(ServerPlayerEntity player) { return isPegasus(player); }
    private PegasusFlightServer() {}

    public static void register() {
        ServerPlayNetworking.registerGlobalReceiver(PegasusFlightProtocol.CONTROL, (server, player, handler, buf, sender) -> {
            try {
                var control = PegasusFlightProtocol.readControl(buf);
                PENDING.compute(player.getUuid(), (id, old) -> old == null || old.player != player
                        || control.token() > old.control.token() || control.token() == old.control.token()
                        && control.sequence() > old.control.sequence() ? new Pending(player, control) : old);
            } catch (RuntimeException ignored) {}
        });
        ServerTickEvents.END_SERVER_TICK.register(server -> {
            long tick = server.getTicks();
            for (var entry : PENDING.entrySet()) if (PENDING.remove(entry.getKey(), entry.getValue())) {
                var pending = entry.getValue();
                if (connected(pending.player)) receive(pending.player, pending.control, tick);
            }
            for (var session : SESSIONS.values().toArray(Session[]::new)) tick(session, tick);
        });
        ServerLivingEntityEvents.AFTER_DEATH.register((entity, source) -> {
            if (entity instanceof ServerPlayerEntity player) stop(player, "invalid");
        });
        ServerPlayerEvents.AFTER_RESPAWN.register((oldPlayer, newPlayer, alive) -> {
            stop(oldPlayer, "invalid"); SESSIONS.remove(oldPlayer.getUuid()); PENDING.remove(oldPlayer.getUuid());
        });
        ServerPlayConnectionEvents.DISCONNECT.register((handler, server) -> {
            stop(handler.player, "invalid"); SESSIONS.remove(handler.player.getUuid()); PENDING.remove(handler.player.getUuid());
        });
        ServerLifecycleEvents.SERVER_STOPPING.register(server -> { SESSIONS.clear(); PENDING.clear(); });
    }
    public static boolean isPegasus(ServerPlayerEntity player) {
        return player.getServer() != null && RaceDefinitions.PEGASUS_ID.equals(RaceState.get(player.getServer()).race(player.getUuid()));
    }
    public static boolean active(ServerPlayerEntity player) { return physicsActive(player); }
    public static boolean physicsActive(ServerPlayerEntity player) {
        var s = session(player);
        return s != null && s.mode != Mode.OFF && !s.stopped && now(player) - s.lastInputTick <= 40
                && s.dimension.equals(dimension(player)) && valid(player);
    }
    public static void stop(ServerPlayerEntity player) { stop(player, "manual"); }
    public static void teleported(ServerPlayerEntity player) { stop(player, "teleport"); }
    public static boolean protectsFallDamage(ServerPlayerEntity player) {
        var s = session(player);
        if (s != null && s.moving) return true;
        return valid(player) && (s == null || s.mode != Mode.GLIDE && !s.input.gliding());
    }
    public static boolean allowMove(ServerPlayerEntity player, PlayerMoveC2SPacket packet) {
        var pending = PENDING.remove(player.getUuid());
        if (pending != null && pending.player == player && connected(player)) receive(player, pending.control, now(player));
        if (!physicsActive(player)) return true;
        float yaw = packet.getYaw(player.getYaw()), pitch = packet.getPitch(player.getPitch());
        if (!Float.isFinite(yaw) || !Float.isFinite(pitch) || !Double.isFinite(packet.getX(player.getX()))
                || !Double.isFinite(packet.getY(player.getY())) || !Double.isFinite(packet.getZ(player.getZ()))) return true;
        if (Float.isFinite(yaw) && Float.isFinite(pitch)) {
            player.setYaw(MathHelper.wrapDegrees(yaw)); player.setPitch(MathHelper.clamp(pitch, -90, 90));
        }
        player.updateLastActionTime();
        return false;
    }
    private static void receive(ServerPlayerEntity player, PegasusFlightProtocol.Control input, long tick) {
        Session s = session(player);
        if (s == null) { s = new Session(player, input, tick); SESSIONS.put(player.getUuid(), s); }
        else {
            if (input.token() < s.input.token() || input.token() == s.input.token() && input.sequence() <= s.input.sequence()) return;
            s.input = input; s.lastInputTick = tick;
        }
        if (!valid(player)) { stop(player, "invalid"); return; }
        if (!s.dimension.equals(dimension(player))) { s.dimension = dimension(player); stop(player, "dimension"); return; }
        if (!input.flying()) s.stopped = false;
        if (s.stopped) { send(s, false, s.stopReason, false); return; }
        if (!input.gliding()) s.glideBlocked = false;
        if (!input.flying()) {
            if (s.mode != Mode.LANDING) s.mode = Mode.OFF;
            s.reboundTicks = 0; s.boosting = false;
        } else if (s.mode != Mode.REBOUND) {
            if (s.mode == Mode.OFF || s.mode == Mode.LANDING) {
                if (s.motion.speed() < .01) s.motion = motion(player.getVelocity());
                s.motion = s.motion.capped(MAX_SPEED);
                if (player.isOnGround()) s.motion = new Motion(s.motion.x(), .30, s.motion.z());
                s.dynamics = Dynamics.resting(Attitude.fromYawPitch(player.getYaw(), 0));
            }
            s.mode = input.gliding() && !s.glideBlocked ? Mode.GLIDE : Mode.HOVER;
            disableVanillaFlight(player);
        }
    }
    private static void tick(Session s, long tick) {
        var player = s.player;
        if (!connected(player)) { SESSIONS.remove(player.getUuid(), s); return; }
        if (!valid(player) || !s.dimension.equals(dimension(player))) {
            if (!s.stopped || s.mode != Mode.OFF) stop(player, "invalid");
            return;
        }
        if (tick - s.lastInputTick > 40) { if (!s.stopped) stop(player, "timeout"); return; }
        if (s.stopped) return;
        if (s.mode == Mode.OFF) {
            long elapsed = tick - s.observedTick;
            Vec3d measured = elapsed > 0 ? player.getPos().subtract(s.position).multiply(1.0 / elapsed) : Vec3d.ZERO;
            s.motion = motion(measured.lengthSquared() > .0001 ? measured : player.getVelocity()).capped(MAX_SPEED);
            var v = s.motion;
            if (!s.input.gliding() && !player.isOnGround() && v.y() < -.3 && player.fallDistance > 2) {
                var support = UnicornLevitationGround.find(player.getWorld(), player, new UnicornLevitationMath.Motion(v.x(), v.y(), v.z()));
                if (support != null && support.kind() == UnicornLevitationGround.Kind.SOLID && support.distance() <= Math.min(5, 1.4 + -v.y() * 2)) {
                    s.mode = Mode.LANDING;
                    player.getWorld().playSound(null, player.getX(), player.getY(), player.getZ(), SoundEvents.ENTITY_ENDER_DRAGON_FLAP,
                            SoundCategory.PLAYERS, .25f, 1.8f);
                }
            }
        }
        if (s.mode == Mode.LANDING && player.isOnGround()) s.mode = Mode.OFF;
        var control = tick - s.lastInputTick > 6 ? s.input.neutral() : s.input;
        var step = step(s.motion, s.mode, control, s.stamina, s.exhausted, s.dynamics);
        s.motion = step.motion(); s.stamina = step.stamina(); s.boosting = step.boosting(); s.exhausted = step.exhausted(); s.dynamics = step.dynamics();
        if (s.mode != Mode.OFF) {
            disableVanillaFlight(player);
            player.setSprinting(false);
            move(s, tick);
            if (!player.isAlive() || s.stopped) return;
            if (s.mode == Mode.REBOUND && --s.reboundTicks <= 0) { s.mode = Mode.HOVER; s.reboundTicks = 0; }
            if (s.mode != Mode.GLIDE) player.fallDistance = 0;
            player.setVelocity(vector(s.motion));
        }
        s.contacts.removeIf(id -> {
            var entity = player.getServerWorld().getEntity(id);
            return entity == null || !entity.getBoundingBox().expand(.5).intersects(player.getBoundingBox());
        });
        s.position = player.getPos(); s.observedTick = tick;
        if (s.mode != Mode.OFF || tick % 5 == 0) send(s, true, "", tick % 3 == 0);
    }
    private static void move(Session s, long tick) {
        var player = s.player;
        Motion before = s.motion;
        int steps = Math.max(1, (int) Math.ceil(before.speed() / .35));
        boolean wasGliding = s.mode == Mode.GLIDE;
        float fallingDistance = player.fallDistance;
        boolean touchedGround = false;
        for (int i = 0; i < steps; i++) {
            Vec3d delta = vector(s.motion).multiply(1.0 / steps);
            Vec3d from = player.getPos();
            Box swept = player.getBoundingBox().stretch(delta).expand(.001);
            if (!loaded(player, swept) || !player.getWorld().getWorldBorder().contains(swept)) {
                s.motion = Motion.ZERO; break;
            }
            s.moving = true;
            try { player.move(MovementType.SELF, delta); }
            finally { s.moving = false; }
            Vec3d actual = player.getPos().subtract(from);
            if (wasGliding && impactUnlocked(player)) hitCreatures(s, from, player.getPos());
            boolean hitX = Math.abs(actual.x - delta.x) > 1e-5;
            boolean hitY = Math.abs(actual.y - delta.y) > 1e-5;
            boolean hitZ = Math.abs(actual.z - delta.z) > 1e-5;
            if (hitX || hitY || hitZ) {
                Motion incoming = s.motion;
                Motion blocked = new Motion(hitX ? incoming.x() : 0, hitY ? incoming.y() : 0, hitZ ? incoming.z() : 0);
                double normalSpeed = blocked.speed();
                boolean terrainCollision = player.getWorld().getBlockCollisions(player, swept).iterator().hasNext();
                touchedGround |= terrainCollision && hitY && delta.y < 0;
                s.motion = new Motion(hitX ? 0 : s.motion.x(), hitY ? 0 : s.motion.y(), hitZ ? 0 : s.motion.z());
                if (wasGliding && terrainCollision && impactUnlocked(player) && tick >= s.nextImpactTick && directImpact(normalSpeed, incoming.speed())) {
                    impact(s, normalSpeed, blocked.normalized().scale(-1), tick);
                    return;
                }
                if (s.mode == Mode.REBOUND && hitY && delta.y > 0) { s.mode = Mode.HOVER; s.reboundTicks = 0; }
                if (s.motion.speed() < 1e-8) break;
            }
            fallingDistance = Math.max(fallingDistance, player.fallDistance);
        }
        if (wasGliding && touchedGround) {
            player.handleFallDamage(fallingDistance, 1, player.getDamageSources().fall());
        }
        boolean mayLand = wasGliding || s.mode == Mode.LANDING || s.mode == Mode.HOVER && !s.input.ascend();
        if (touchedGround && mayLand) {
            Box support = player.getBoundingBox().offset(0, -.04, 0);
            if (s.motion.speed() <= IMPACT_SPEED && loaded(player, support)
                    && player.getWorld().getBlockCollisions(player, support).iterator().hasNext()) {
                player.setOnGround(true);
                player.setVelocity(vector(s.motion));
                stop(player, "landed");
            }
        }
    }
    private static void hitCreatures(Session s, Vec3d from, Vec3d to) {
        var player = s.player;
        double halfWidth = player.getWidth() / 2.0, halfHeight = player.getHeight() / 2.0;
        Vec3d start = from.add(0, halfHeight, 0), end = to.add(0, halfHeight, 0);
        Box sweep = new Box(start, end).expand(halfWidth + .05, halfHeight + .05, halfWidth + .05);
        int tested = 0;
        for (var victim : player.getWorld().getEntitiesByClass(LivingEntity.class, sweep, e -> canDamage(player, e))) {
            if (++tested > 64) break;
            if (s.contacts.contains(victim.getUuid())) continue;
            Box expanded = victim.getBoundingBox().expand(halfWidth, halfHeight, halfWidth);
            var contact = expanded.raycast(start, end);
            if (contact.isEmpty() && !expanded.contains(start)) continue;
            Vec3d otherVelocity = victim instanceof ServerPlayerEntity other && physicsActive(other)
                    ? vector(session(other).motion) : victim.getVelocity();
            Motion relative = s.motion.add(motion(otherVelocity).scale(-1));
            Motion normal = motion(victim.getBoundingBox().getCenter().subtract(start)).normalized();
            double closing = relative.dot(normal);
            if (!directCreatureImpact(closing, relative.speed())) continue;
            if (!visible(player, start, victim.getBoundingBox().getCenter())) continue;
            s.contacts.add(victim.getUuid());
            if (victim.damage(player.getDamageSources().playerAttack(player), (float) creatureDamage(closing))) {
                double force = Math.min(1.7, .35 + closing * .4);
                victim.addVelocity(normal.x() * force, Math.max(.2, normal.y() * force + .25), normal.z() * force);
                victim.velocityModified = true;
                s.motion = s.motion.scale(.74);
            }
        }
    }
    private static void impact(Session s, double speed, Motion normal, long tick) {
        var player = s.player;
        Vec3d center = player.getPos().add(0, .15, 0);
        double radius = impactRadius(speed);
        int tested = 0;
        for (var victim : player.getWorld().getEntitiesByClass(LivingEntity.class, new Box(center, center).expand(radius), e -> canDamage(player, e))) {
            if (++tested > 128) break;
            double distance = victim.getBoundingBox().getCenter().distanceTo(center);
            if (distance > radius || !visible(player, center, victim.getBoundingBox().getCenter())) continue;
            double weight = Math.max(.1, 1 - distance / radius);
            if (victim.damage(player.getDamageSources().playerAttack(player), (float) (impactDamage(speed) * weight))) {
                Vec3d away = victim.getPos().subtract(center).normalize();
                victim.addVelocity(away.x * weight * 1.6, .25 + weight * .7, away.z * weight * 1.6);
                victim.velocityModified = true;
            }
        }
        float strength = (float) Math.min(1, .25 + (speed - IMPACT_SPEED) / (MAX_SPEED - IMPACT_SPEED) * .75);
        var effect = new PegasusFlightProtocol.Impact(dimension(player), center.x, center.y, center.z, strength, (float) (radius * 4));
        for (var observer : player.getServer().getPlayerManager().getPlayerList()) {
            if (observer.getWorld() != player.getWorld() || observer.squaredDistanceTo(center) > effect.radius() * effect.radius()
                    || !ServerPlayNetworking.canSend(observer, PegasusFlightProtocol.IMPACT)) continue;
            var buf = PacketByteBufs.create(); PegasusFlightProtocol.writeImpact(buf, effect);
            ServerPlayNetworking.send(observer, PegasusFlightProtocol.IMPACT, buf);
        }
        player.getWorld().playSound(null, center.x, center.y, center.z, SoundEvents.ENTITY_GENERIC_EXPLODE,
                SoundCategory.PLAYERS, .5f + strength, .75f + strength * .2f);
        s.mode = Mode.REBOUND; s.glideBlocked = true; s.boosting = false; s.reboundTicks = REBOUND_TICKS;
        s.dynamics = Dynamics.resting(s.dynamics.body());
        s.nextImpactTick = tick + REBOUND_TICKS + 6;
        s.motion = new Motion(normal.x() * .6, .85 + Math.min(.7, (speed - IMPACT_SPEED) * .35), normal.z() * .6);
        player.fallDistance = 0;
        if (selfDamage(speed) > 0) player.damage(player.getDamageSources().flyIntoWall(), (float) selfDamage(speed));
        send(s, true, "impact", true);
    }
    private static boolean canDamage(ServerPlayerEntity player, LivingEntity victim) {
        if (victim == player || !victim.isAlive() || victim.isSpectator() || player.isTeammate(victim)) return false;
        if (victim instanceof ServerPlayerEntity other && (!player.getServer().isPvpEnabled() || !player.shouldDamagePlayer(other))) return false;
        if (victim instanceof TameableEntity tame && player.getUuid().equals(tame.getOwnerUuid())) return false;
        return !(victim instanceof AbstractHorseEntity horse) || !player.getUuid().equals(horse.getOwnerUuid());
    }
    private static boolean visible(ServerPlayerEntity player, Vec3d from, Vec3d to) {
        if (!loaded(player, new Box(from, to))) return false;
        return player.getWorld().raycast(new RaycastContext(from, to, RaycastContext.ShapeType.COLLIDER,
                RaycastContext.FluidHandling.NONE, player)).getType() == HitResult.Type.MISS;
    }
    private static boolean loaded(ServerPlayerEntity player, Box box) {
        return box.minY >= player.getWorld().getBottomY() && box.maxY <= player.getWorld().getTopY()
                && player.getWorld().isRegionLoaded(BlockPos.ofFloored(box.minX - 1, box.minY, box.minZ - 1),
                        BlockPos.ofFloored(box.maxX + 1, box.maxY, box.maxZ + 1));
    }
    private static boolean valid(ServerPlayerEntity player) {
        return connected(player) && player.isAlive() && !player.isRemoved() && isPegasus(player) && !player.isSpectator()
                && !player.hasVehicle() && !player.isSleeping() && !player.isTouchingWater() && !player.isInLava()
                && !player.isFallFlying() && !player.isUsingRiptide() && !RemoteToolServer.active(player)
                && ServerPlayNetworking.canSend(player, PegasusFlightProtocol.STATE);
    }
    private static void disableVanillaFlight(ServerPlayerEntity player) {
        if (player.getAbilities().flying) { player.getAbilities().flying = false; player.sendAbilitiesUpdate(); }
    }
    private static boolean connected(ServerPlayerEntity player) { return player.getServer() != null && player.getServer().getPlayerManager().getPlayer(player.getUuid()) == player; }
    private static Session session(ServerPlayerEntity player) { var s = SESSIONS.get(player.getUuid()); return s != null && s.player == player ? s : null; }
    private static long now(ServerPlayerEntity player) { return player.getServer().getTicks(); }
    private static String dimension(ServerPlayerEntity player) { return player.getWorld().getRegistryKey().getValue().toString(); }
    private static Motion motion(Vec3d v) { return new Motion(v.x, v.y, v.z); }
    private static Vec3d vector(Motion m) { return new Vec3d(m.x(), m.y(), m.z()); }
    private static void stop(ServerPlayerEntity player, String reason) {
        var s = session(player); if (s == null) return;
        s.mode = Mode.OFF; s.stopped = true; s.boosting = false; s.reboundTicks = 0; s.contacts.clear();
        s.stopReason = reason;
        s.dynamics = Dynamics.resting(s.dynamics.body());
        s.motion = motion(player.getVelocity()).capped(MAX_SPEED);
        s.position = player.getPos(); s.observedTick = now(player);
        send(s, false, reason, true);
    }
    private static void send(Session s, boolean allowed, String reason, boolean observers) {
        var player = s.player;
        if (player.getServer() == null) return;
        var state = new PegasusFlightProtocol.State(player.getUuid(), s.input.token(), ++nextState, s.input.sequence(), dimension(player), allowed,
                allowed ? s.mode : Mode.OFF, allowed && s.mode == Mode.GLIDE && s.input.unlocked(), s.stamina, s.exhausted, allowed && s.boosting, s.dynamics,
                player.getX(), player.getY(), player.getZ(), s.motion.x(), s.motion.y(), s.motion.z(), s.reboundTicks, reason);
        for (var observer : player.getServer().getPlayerManager().getPlayerList()) {
            if (observer != player && (!observers || observer.getWorld() != player.getWorld() || observer.squaredDistanceTo(player) > 128 * 128)) continue;
            if (!ServerPlayNetworking.canSend(observer, PegasusFlightProtocol.STATE)) continue;
            var buf = PacketByteBufs.create(); PegasusFlightProtocol.writeState(buf, state); ServerPlayNetworking.send(observer, PegasusFlightProtocol.STATE, buf);
        }
    }
    private record Pending(ServerPlayerEntity player, PegasusFlightProtocol.Control control) {}
    private static final class Session {
        final ServerPlayerEntity player;
        final Set<UUID> contacts = new HashSet<>();
        PegasusFlightProtocol.Control input;
        String dimension;
        String stopReason = "manual";
        long lastInputTick, nextImpactTick, observedTick;
        Vec3d position;
        Mode mode = Mode.OFF;
        Motion motion = Motion.ZERO;
        Dynamics dynamics;
        float stamina = MAX_STAMINA;
        int reboundTicks;
        boolean boosting, exhausted, stopped, glideBlocked, moving;
        Session(ServerPlayerEntity player, PegasusFlightProtocol.Control input, long tick) {
            this.player = player; this.input = input; dimension = dimension(player); lastInputTick = observedTick = tick; position = player.getPos();
            dynamics = Dynamics.resting(Attitude.fromYawPitch(player.getYaw(), 0));
        }
    }
}
