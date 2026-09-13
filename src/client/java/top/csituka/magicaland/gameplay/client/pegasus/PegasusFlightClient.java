package top.csituka.magicaland.gameplay.client.pegasus;

import java.util.LinkedHashMap;
import java.util.Map;
import java.util.UUID;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayConnectionEvents;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking;
import net.fabricmc.fabric.api.client.rendering.v1.HudRenderCallback;
import net.fabricmc.fabric.api.networking.v1.PacketByteBufs;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.input.Input;
import net.minecraft.client.network.ClientPlayerEntity;
import net.minecraft.client.world.ClientWorld;
import net.minecraft.entity.Entity;
import net.minecraft.entity.MovementType;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.text.Text;
import net.minecraft.util.math.MathHelper;
import net.minecraft.util.math.Vec3d;
import org.lwjgl.glfw.GLFW;
import top.csituka.magicaland.api.client.AppearanceOverrides;
import top.csituka.magicaland.api.client.FlightPose;
import top.csituka.magicaland.api.client.Registration;
import top.csituka.magicaland.gameplay.client.RemoteToolClient;
import top.csituka.magicaland.gameplay.client.race.RaceClient;
import top.csituka.magicaland.gameplay.config.GameplayClientConfig;
import top.csituka.magicaland.gameplay.pegasus.PegasusFlightMath;
import top.csituka.magicaland.gameplay.pegasus.PegasusFlightMath.*;
import top.csituka.magicaland.gameplay.pegasus.PegasusFlightProtocol;
import top.csituka.magicaland.gameplay.pegasus.PegasusFlightProtocol.*;
import top.csituka.magicaland.gameplay.race.RaceDefinitions;

public final class PegasusFlightClient {
    public record Visual(Mode mode, boolean boosting, boolean unlocked, Attitude attitude, Vec3d velocity,
                         float stamina, float reboundProgress) {}
    private record Seen(State state, Attitude previous, long tick) {}
    private record Prediction(Vec3d position, Vec3d velocity, Dynamics dynamics) {}
    private static final Map<UUID, Seen> STATES = new LinkedHashMap<>();
    private static final Map<Integer, Prediction> HISTORY = new LinkedHashMap<>();
    private static final PegasusFlightPerspective PERSPECTIVE = new PegasusFlightPerspective();
    private static ClientWorld world;
    private static ClientPlayerEntity owner;
    private static Registration poseRegistration;
    private static long tick, tokenCounter, token, lastReceived, lastSpace = Long.MIN_VALUE / 2;
    private static int sequence, lastCorrected, intentSequence, movedAge = Integer.MIN_VALUE;
    private static boolean initialized, wanted, allowed, gliding, unlocked, boosting, exhausted;
    private static Mode mode = Mode.OFF;
    private static float stamina = PegasusFlightMath.MAX_STAMINA;
    private static final Attitude IDENTITY = Attitude.fromYawPitch(0, 0);
    private static Attitude lookIntent = IDENTITY, previousLook = IDENTITY, previousBody = IDENTITY;
    private static Attitude bodyCorrection = IDENTITY, cameraTransition = IDENTITY, previousCameraTransition = IDENTITY;
    private static Dynamics dynamics = new Dynamics(IDENTITY, Motion.ZERO, 0);
    private static Motion angularCorrection = Motion.ZERO;
    private static float thrustCorrection;
    private static Vec3d correction = Vec3d.ZERO, velocityCorrection = Vec3d.ZERO;
    private static Control input;

    private PegasusFlightClient() {}

    public static void init() {
        if (initialized) return;
        initialized = true;
        ClientPlayConnectionEvents.JOIN.register((handler, sender, client) -> {
            clear();
            if (poseRegistration != null) poseRegistration.close();
            poseRegistration = AppearanceOverrides.registerFlightPose("magicaland_gameplay:pegasus", 0, PegasusFlightClient::pose);
        });
        ClientPlayConnectionEvents.DISCONNECT.register((handler, client) -> {
            clear();
            if (poseRegistration != null) poseRegistration.close();
            poseRegistration = null;
        });
        ClientPlayNetworking.registerGlobalReceiver(PegasusFlightProtocol.STATE, (client, handler, buffer, sender) -> {
            try {
                var state = PegasusFlightProtocol.readState(buffer);
                ClientWorld receivedWorld = client.world;
                client.execute(() -> {
                    if (client.getNetworkHandler() == handler && client.world == receivedWorld) receive(client, state);
                });
            } catch (RuntimeException ignored) {}
        });
        ClientTickEvents.START_CLIENT_TICK.register(PegasusFlightClient::tick);
        HudRenderCallback.EVENT.register(PegasusFlightClient::hud);
    }

    public static boolean available() {
        return RaceClient.ready() && RaceDefinitions.PEGASUS_ID.equals(RaceClient.view().ownRace())
                && ClientPlayNetworking.canSend(PegasusFlightProtocol.CONTROL);
    }
    private static boolean scope(MinecraftClient client) {
        var player = client.player;
        return available() && player != null && client.world != null && player.isAlive() && !player.isRemoved()
                && !player.isSpectator() && !player.hasVehicle() && !player.isSleeping() && !player.isFallFlying()
                && !player.isUsingRiptide() && !player.isTouchingWater() && !player.isInLava()
                && !RemoteToolClient.active() && client.getCameraEntity() == player;
    }
    private static boolean readable(MinecraftClient client) {
        return client.currentScreen == null && client.getOverlay() == null && client.isWindowFocused() && !client.isPaused();
    }
    public static boolean active() { return allowed && mode != Mode.OFF && scope(MinecraftClient.getInstance()); }
    public static boolean controlsNativeInput() { return (active() || wanted) && scope(MinecraftClient.getInstance()); }
    public static boolean ownsJumpGesture() { return scope(MinecraftClient.getInstance()) && readable(MinecraftClient.getInstance()); }

    public static boolean key(long window, int key, int scanCode, int action) {
        var client = MinecraftClient.getInstance();
        if (window != client.getWindow().getHandle() || !scope(client) || !readable(client)) return false;
        if (client.options.togglePerspectiveKey.matchesKey(key, scanCode) && action == GLFW.GLFW_PRESS) PERSPECTIVE.manualChange();
        if (client.options.jumpKey.matchesKey(key, scanCode) && action == GLFW.GLFW_PRESS) {
            long now = System.nanoTime();
            boolean twice = now - lastSpace > 0 && now - lastSpace < 300_000_000L;
            lastSpace = now;
            if (twice) {
                lastSpace = Long.MIN_VALUE / 2;
                wanted = !wanted;
                if (token == 0) begin(client);
                if (wanted && client.player.getAbilities().flying) {
                    client.player.getAbilities().flying = false;
                    client.player.sendAbilitiesUpdate();
                }
                send(client);
                intentSequence = sequence;
                return true;
            }
        }
        if (!active()) return false;
        if (key != GLFW.GLFW_KEY_LEFT_CONTROL && key != GLFW.GLFW_KEY_RIGHT_CONTROL && key != GLFW.GLFW_KEY_TAB) return false;
        if (action == GLFW.GLFW_PRESS) {
            Attitude oldCamera = camera(1);
            if (key == GLFW.GLFW_KEY_TAB) {
                boolean preference = !GameplayClientConfig.flightAerobatics();
                if (GameplayClientConfig.setFlightAerobatics(preference)) {
                    unlocked = gliding && preference; message(preference ? "unlocked" : "locked");
                } else client.player.sendMessage(Text.translatable("text.magicaland_gameplay.config.save_failed"), true);
            } else {
                gliding = !gliding; unlocked = gliding && GameplayClientConfig.flightAerobatics();
                message(gliding ? "glide" : "hover");
            }
            preserveCamera(oldCamera);
            send(client);
            intentSequence = sequence;
        }
        if (key == GLFW.GLFW_KEY_TAB) client.options.playerListKey.setPressed(false);
        else client.options.sprintKey.setPressed(false);
        return true;
    }

    private static void begin(MinecraftClient client) {
        token = ++tokenCounter; sequence = lastCorrected = intentSequence = 0; HISTORY.clear();
        allowed = false; mode = Mode.OFF; gliding = unlocked = false;
        lookIntent = previousLook = Attitude.fromYawPitch(client.player.getYaw(), client.player.getPitch());
        previousBody = Attitude.fromYawPitch(client.player.getYaw(), 0);
        dynamics = new Dynamics(previousBody, Motion.ZERO, 0);
        clearCorrections(); lastReceived = tick;
    }
    public static void stop() {
        var client = MinecraftClient.getInstance();
        if (wanted || allowed) { wanted = false; send(client); }
        finishPerspective(client);
        resetLocal();
    }
    public static void clear() {
        finishPerspective(MinecraftClient.getInstance());
        resetLocal(); STATES.clear(); world = null; owner = null; lastSpace = Long.MIN_VALUE / 2;
    }
    private static void resetLocal() {
        wanted = allowed = gliding = unlocked = boosting = exhausted = false; mode = Mode.OFF;
        HISTORY.clear(); clearCorrections(); cameraTransition = previousCameraTransition = IDENTITY; input = null;
        token = 0; sequence = lastCorrected = intentSequence = 0; movedAge = Integer.MIN_VALUE;
        lastSpace = Long.MIN_VALUE / 2;
    }
    private static void clearCorrections() {
        correction = velocityCorrection = Vec3d.ZERO;
        bodyCorrection = IDENTITY; angularCorrection = Motion.ZERO; thrustCorrection = 0;
    }
    private static void finishPerspective(MinecraftClient client) {
        if (client.options != null) client.options.setPerspective(PERSPECTIVE.finish(client.options.getPerspective(),
                RemoteToolClient.active()));
    }
    private static void tick(MinecraftClient client) {
        if (world != client.world || owner != client.player) { clear(); world = client.world; owner = client.player; }
        if (client.isPaused()) return;
        tick++;
        PERSPECTIVE.observe(client.options.getPerspective());
        STATES.entrySet().removeIf(entry -> tick - entry.getValue().tick() > 40);
        if (!scope(client)) { stop(); return; }
        if (token == 0) { begin(client); send(client); }
        if (!readable(client)) lastSpace = Long.MIN_VALUE / 2;
        if (tick - lastReceived > 60) { stop(); return; }
        previousLook = lookIntent; previousBody = dynamics.body();
        previousCameraTransition = cameraTransition;
        cameraTransition = PegasusFlightView.blend(cameraTransition, IDENTITY, .3f);
        if (!wanted && mode == Mode.OFF) {
            lookIntent = previousLook = Attitude.fromYawPitch(client.player.getYaw(), client.player.getPitch());
            if (client.player.isOnGround()) finishPerspective(client);
            if (tick % 5 == 0) send(client);
            return;
        }
        if (gliding && unlocked && mode != Mode.REBOUND) {
            if (readable(client)) {
                int roll = (RemoteToolClient.held(client.options.rightKey) ? 1 : 0) - (RemoteToolClient.held(client.options.leftKey) ? 1 : 0);
                lookIntent = PegasusFlightView.limitedLead(dynamics.body(), PegasusFlightView.roll(lookIntent, roll * 4), 70);
            }
        } else lookIntent = PegasusFlightView.level(lookIntent, .18f);
        syncLook(client.player);
        send(client);
    }
    private static void send(MinecraftClient client) {
        if (client.player == null || !ClientPlayNetworking.canSend(PegasusFlightProtocol.CONTROL) || token == 0) return;
        boolean read = readable(client) && scope(client);
        var keys = client.options;
        input = new Control(token, ++sequence, wanted, wanted && gliding, wanted && gliding && unlocked,
                read && RemoteToolClient.held(keys.forwardKey), read && RemoteToolClient.held(keys.backKey),
                read && RemoteToolClient.held(keys.leftKey), read && RemoteToolClient.held(keys.rightKey),
                read && RemoteToolClient.held(keys.jumpKey), read && RemoteToolClient.held(keys.sneakKey), lookIntent);
        var buffer = PacketByteBufs.create();
        PegasusFlightProtocol.writeControl(buffer, input);
        ClientPlayNetworking.send(PegasusFlightProtocol.CONTROL, buffer);
    }

    private static void receive(MinecraftClient client, State state) {
        if (client.world == null || client.player == null || !state.dimension().equals(dimension(client))) return;
        var old = STATES.get(state.actor());
        if (old != null && old.state().sequence() >= state.sequence()) return;
        STATES.put(state.actor(), new Seen(state, old == null ? state.attitude() : old.state().attitude(), tick));
        while (STATES.size() > 256) STATES.remove(STATES.keySet().iterator().next());
        if (!state.actor().equals(client.player.getUuid()) || state.token() != token) return;
        lastReceived = tick;
        if (state.ackInputSequence() < intentSequence) return;
        boolean first = mode == Mode.OFF && state.mode() != Mode.OFF;
        Mode prior = mode;
        Attitude oldCamera = camera(1);
        allowed = state.allowed(); mode = state.mode(); stamina = state.stamina(); boosting = state.boosting(); exhausted = state.exhausted();
        if (!allowed && !state.reason().equals("landed")) { finishPerspective(client); resetLocal(); return; }
        if (mode == Mode.OFF) {
            wanted = gliding = unlocked = boosting = false;
            HISTORY.clear(); clearCorrections(); cameraTransition = previousCameraTransition = IDENTITY;
            if (state.reason().equals("landed")) {
                finishPerspective(client); send(client); intentSequence = sequence;
            } else if (client.player.isOnGround()) finishPerspective(client);
            return;
        }
        if (mode == Mode.REBOUND || mode == Mode.LANDING) { gliding = false; unlocked = false; }
        if (first || mode == Mode.REBOUND && prior != Mode.REBOUND || mode == Mode.LANDING && prior != Mode.LANDING) {
            HISTORY.clear(); clearCorrections(); dynamics = state.dynamics(); previousBody = dynamics.body();
            client.player.setVelocity(state.velocityX(), state.velocityY(), state.velocityZ());
            if (first && mode != Mode.LANDING && !RemoteToolClient.active())
                client.options.setPerspective(PERSPECTIVE.begin(client.options.getPerspective(), GameplayClientConfig.automaticFlightThirdPerson()));
        } else if (state.ackInputSequence() > lastCorrected) {
            var predicted = HISTORY.get(state.ackInputSequence());
            if (predicted != null) {
                Vec3d error = new Vec3d(state.x(), state.y(), state.z()).subtract(predicted.position());
                if (error.lengthSquared() > 16) {
                    client.player.setPosition(state.x(), state.y(), state.z());
                    client.player.setVelocity(state.velocityX(), state.velocityY(), state.velocityZ());
                    dynamics = state.dynamics(); previousBody = dynamics.body();
                    clearCorrections(); HISTORY.clear();
                } else {
                    correction = error.lengthSquared() < .0004 ? Vec3d.ZERO : error;
                    velocityCorrection = new Vec3d(state.velocityX(), state.velocityY(), state.velocityZ()).subtract(predicted.velocity());
                    bodyCorrection = PegasusFlightView.difference(state.attitude(), predicted.dynamics().body());
                    angularCorrection = state.dynamics().angularVelocity().add(predicted.dynamics().angularVelocity().scale(-1));
                    thrustCorrection = state.dynamics().thrust() - predicted.dynamics().thrust();
                }
            }
        }
        if (mode != prior) preserveCamera(oldCamera);
        lastCorrected = Math.max(lastCorrected, state.ackInputSequence());
        HISTORY.keySet().removeIf(value -> value <= state.ackInputSequence());
    }

    public static void nativeInput(Input raw) {
        var client = MinecraftClient.getInstance();
        if (!controlsNativeInput() || client.player == null || client.player.input != raw) return;
        raw.jumping = raw.sneaking = false;
        raw.movementForward = raw.movementSideways = 0;
    }
    public static boolean travel(PlayerEntity player) {
        var client = MinecraftClient.getInstance();
        if (player != client.player || !active() || input == null) return false;
        if (movedAge == player.age) return true;
        movedAge = player.age;
        var current = player.getVelocity();
        var result = PegasusFlightMath.step(new Motion(current.x, current.y, current.z), mode, input, stamina, exhausted, dynamics);
        stamina = result.stamina(); boosting = result.boosting(); exhausted = result.exhausted();
        dynamics = result.dynamics();
        var adjustment = PegasusFlightView.blend(IDENTITY, bodyCorrection, .25f);
        dynamics = new Dynamics(PegasusFlightView.applyDifference(adjustment, dynamics.body()),
                dynamics.angularVelocity().add(angularCorrection.scale(.25)).capped(PegasusFlightMath.MAX_ANGULAR_SPEED),
                MathHelper.clamp(dynamics.thrust() + thrustCorrection * .25f, 0, 1));
        bodyCorrection = PegasusFlightView.blend(bodyCorrection, IDENTITY, .25f);
        angularCorrection = angularCorrection.scale(.75); thrustCorrection *= .75f;
        Vec3d next = new Vec3d(result.motion().x(), result.motion().y(), result.motion().z());
        next = next.add(velocityCorrection.multiply(.25)); velocityCorrection = velocityCorrection.multiply(.75);
        player.setVelocity(next); player.setSprinting(false);
        int steps = Math.max(1, (int) Math.ceil(next.length() / .35));
        for (int i = 0; i < steps; i++) {
            Vec3d part = next.multiply(1.0 / steps);
            Vec3d before = player.getPos();
            player.move(MovementType.SELF, part);
            Vec3d actual = player.getPos().subtract(before);
            boolean x = Math.abs(actual.x - part.x) > .00001, y = Math.abs(actual.y - part.y) > .00001,
                    z = Math.abs(actual.z - part.z) > .00001;
            if (x || y || z) {
                next = new Vec3d(x ? 0 : next.x, y ? 0 : next.y, z ? 0 : next.z);
                player.setVelocity(next);
                if (next.lengthSquared() < 1e-10) break;
            }
        }
        if (correction.lengthSquared() > .000001) {
            Vec3d offset = correction.multiply(.25);
            player.move(MovementType.SELF, offset); correction = correction.subtract(offset);
        }
        player.updateLimbs(false);
        syncLook(client.player);
        HISTORY.put(input.sequence(), new Prediction(player.getPos(), player.getVelocity(), dynamics));
        while (HISTORY.size() > 80) HISTORY.remove(HISTORY.keySet().iterator().next());
        return true;
    }
    public static boolean look(double horizontal, double vertical) {
        if (!active()) return false;
        var client = MinecraftClient.getInstance();
        if (readable(client) && mode != Mode.REBOUND) {
            lookIntent = PegasusFlightView.turn(lookIntent, horizontal, vertical, gliding && unlocked);
            if (gliding && unlocked) lookIntent = PegasusFlightView.limitedLead(dynamics.body(), lookIntent, 70);
            syncLook(client.player);
        }
        return true;
    }
    private static void syncLook(ClientPlayerEntity player) {
        Attitude view = camera(1);
        if (view == null) view = lookIntent;
        float yaw = PegasusFlightView.yaw(view), pitch = PegasusFlightView.pitch(view);
        player.setYaw(player.getYaw() + MathHelper.wrapDegrees(yaw - player.getYaw()));
        player.setPitch(pitch);
    }
    private static Attitude cameraTarget(float delta) {
        return PegasusFlightView.camera(PegasusFlightView.blend(previousBody, dynamics.body(), delta),
                PegasusFlightView.blend(previousLook, lookIntent, delta), gliding && unlocked && mode == Mode.GLIDE);
    }
    private static void preserveCamera(Attitude before) {
        if (before != null) cameraTransition = previousCameraTransition = PegasusFlightView.difference(before, cameraTarget(1));
    }
    public static Attitude camera(float delta) {
        return active() ? PegasusFlightView.applyDifference(PegasusFlightView.blend(previousCameraTransition, cameraTransition, delta), cameraTarget(delta)) : null;
    }
    public static State snapshot(UUID player) { var entry = STATES.get(player); return entry == null ? null : entry.state(); }
    public static Visual visual(UUID player, float delta) {
        var client = MinecraftClient.getInstance();
        var entry = STATES.get(player);
        if (entry == null || tick - entry.tick() > 40 || !entry.state().allowed() || entry.state().mode() == Mode.OFF
                || client.world == null || !entry.state().dimension().equals(dimension(client))) return null;
        var entity = client.world.getPlayerByUuid(player);
        if (entity == null || !entity.isAlive()) return null;
        State state = entry.state();
        boolean local = entity == client.player;
        if (local && !active()) return null;
        return new Visual(local ? mode : state.mode(), local ? boosting : state.boosting(), local ? unlocked : state.unlocked(),
                local ? PegasusFlightView.blend(previousBody, dynamics.body(), delta)
                        : PegasusFlightView.blend(entry.previous(), state.attitude(), MathHelper.clamp((tick - entry.tick() + delta) / 3f, 0, 1)),
                local ? entity.getVelocity() : new Vec3d(state.velocityX(), state.velocityY(), state.velocityZ()),
                local ? stamina : state.stamina(), state.mode() == Mode.REBOUND
                ? 1 - MathHelper.clamp((state.reboundTicks() - (tick - entry.tick() + delta)) / PegasusFlightMath.REBOUND_TICKS, 0, 1) : 0);
    }
    private static FlightPose pose(UUID player) {
        var visual = visual(player, MinecraftClient.getInstance().getTickDelta());
        if (visual == null) return null;
        boolean braking = owner != null && owner.getUuid().equals(player) && input != null && input.backward();
        FlightPose.Mode poseMode = visual.mode() == Mode.REBOUND ? FlightPose.Mode.REBOUND
                : visual.mode() == Mode.LANDING ? FlightPose.Mode.LANDING
                : visual.mode() == Mode.GLIDE ? braking ? FlightPose.Mode.BRAKE
                : visual.boosting() ? FlightPose.Mode.BOOST : FlightPose.Mode.GLIDE : FlightPose.Mode.NORMAL;
        var q = visual.attitude();
        return new FlightPose(q.x(), q.y(), q.z(), q.w(), poseMode,
                visual.mode() == Mode.LANDING ? 1.8f : visual.boosting() ? 1.5f : .8f, visual.reboundProgress());
    }
    private static void hud(DrawContext context, float delta) {
        var client = MinecraftClient.getInstance();
        if (!active() || client.options.hudHidden || client.currentScreen != null) return;
        int x = context.getScaledWindowWidth() / 2 - 28, y = context.getScaledWindowHeight() - 52;
        context.fill(x - 1, y - 1, x + 57, y + 5, 0xB0202930);
        context.fill(x, y, x + Math.round(56 * MathHelper.clamp(stamina / PegasusFlightMath.MAX_STAMINA, 0, 1)), y + 4,
                stamina < PegasusFlightMath.MAX_STAMINA * .2f ? 0xFFDD9078 : 0xFF8ECBE9);
    }
    private static String dimension(MinecraftClient client) { return client.world.getRegistryKey().getValue().toString(); }
    private static void message(String key) {
        var player = MinecraftClient.getInstance().player;
        if (player != null) player.sendMessage(Text.translatable("text.magicaland_gameplay.pegasus." + key), true);
    }
}
