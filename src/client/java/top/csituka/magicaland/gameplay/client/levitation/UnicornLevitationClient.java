package top.csituka.magicaland.gameplay.client.levitation;

import java.util.LinkedHashMap;
import java.util.Map;
import java.util.UUID;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayConnectionEvents;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking;
import net.fabricmc.fabric.api.networking.v1.PacketByteBufs;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.input.Input;
import net.minecraft.client.world.ClientWorld;
import net.minecraft.entity.MovementType;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.text.Text;
import net.minecraft.util.math.Vec3d;
import top.csituka.magicaland.api.client.AppearanceOverrides;
import top.csituka.magicaland.api.client.Registration;
import top.csituka.magicaland.gameplay.client.RemoteToolClient;
import top.csituka.magicaland.gameplay.client.race.RaceClient;
import top.csituka.magicaland.gameplay.client.sense.EarthSenseClient;
import top.csituka.magicaland.gameplay.levitation.UnicornLevitationGround;
import top.csituka.magicaland.gameplay.levitation.UnicornLevitationMath;
import top.csituka.magicaland.gameplay.levitation.UnicornLevitationMath.Mode;
import top.csituka.magicaland.gameplay.levitation.UnicornLevitationMath.Motion;
import top.csituka.magicaland.gameplay.levitation.UnicornLevitationProtocol;

public final class UnicornLevitationClient {
    private static final UnicornLevitationSession SESSION = new UnicornLevitationSession();
    private static final Map<UUID, Visual> VISUALS = new LinkedHashMap<>();
    private static Registration flightOverride, magicOverride;
    private static ClientWorld world;
    private static PlayerEntity owner;
    private static long tick, lastSent = -100, lastState;
    private static int retry, movedAge = Integer.MIN_VALUE;
    private static boolean initialized, nativeControl;
    private static Mode predicted = Mode.OFF;

    private UnicornLevitationClient() {}
    public static void init() {
        if (initialized) return;
        initialized = true;
        ClientPlayConnectionEvents.JOIN.register((handler, sender, client) -> {
            clear(); closeOverride();
            flightOverride = AppearanceOverrides.registerFlightActivity("magicaland_gameplay:unicorn_levitation", 0,
                    UnicornLevitationClient::flightVisual);
            magicOverride = AppearanceOverrides.registerMagicActivity("magicaland_gameplay:unicorn_levitation", 0,
                    UnicornLevitationClient::flightVisual);
        });
        ClientPlayConnectionEvents.DISCONNECT.register((handler, client) -> { clear(); closeOverride(); });
        ClientPlayNetworking.registerGlobalReceiver(UnicornLevitationProtocol.STATE, (client, handler, buffer, sender) -> {
            try {
                var state = UnicornLevitationProtocol.readState(buffer);
                client.execute(() -> {
                    if (client.getNetworkHandler() == handler) receive(client, state);
                });
            } catch (RuntimeException ignored) {}
        });
        ClientTickEvents.START_CLIENT_TICK.register(UnicornLevitationClient::tick);
    }
    public static boolean available() {
        return RaceClient.canUseUnicornAbility() && ClientPlayNetworking.canSend(UnicornLevitationProtocol.CONTROL);
    }
    public static Text unavailable() {
        if (!RaceClient.canUseUnicornAbility()) return RaceClient.abilityUnavailable();
        if (!ClientPlayNetworking.canSend(UnicornLevitationProtocol.CONTROL)) return text("server");
        return text("unavailable");
    }
    public static boolean armed() { return SESSION.armed(); }
    public static boolean active() { return predicted != Mode.OFF && scope(MinecraftClient.getInstance()); }

    public static void toggle() {
        var client = MinecraftClient.getInstance();
        if (armed()) { stop(); return; }
        if (!scope(client, true) || retry > 0) {
            if (client.player != null) client.player.sendMessage(unavailable(), true);
            return;
        }
        EarthSenseClient.stop();
        if (client.player.getAbilities().flying) {
            client.player.getAbilities().flying = false;
            client.player.sendAbilitiesUpdate();
        }
        SESSION.begin(true); predicted = Mode.OFF; send(readInput(client));
        client.player.sendMessage(text("hint"), true);
    }
    public static void stop() {
        if (!SESSION.enabled() || !SESSION.armed()) return;
        SESSION.disable(); predicted = Mode.OFF; nativeControl = false; send(readInput(MinecraftClient.getInstance()));
    }
    public static void suspend() {
        if (SESSION.enabled()) { SESSION.disable(); send(UnicornLevitationInput.NONE); }
        predicted = Mode.OFF; nativeControl = false;
    }
    public static void clear() {
        SESSION.clear(); VISUALS.clear(); world = null; owner = null;
        retry = 0; nativeControl = false; tick = lastState = 0; lastSent = -100;
        predicted = Mode.OFF; movedAge = Integer.MIN_VALUE;
    }
    private static void closeOverride() {
        if (flightOverride != null) flightOverride.close();
        if (magicOverride != null) magicOverride.close();
        flightOverride = magicOverride = null;
    }
    private static boolean scope(MinecraftClient client) {
        return scope(client, false);
    }
    private static boolean scope(MinecraftClient client, boolean permitNativeFlightExit) {
        var player = client.player;
        return player != null && client.world != null && player.getWorld() == client.world && available()
                && player.isAlive() && !player.isRemoved() && !player.isSpectator()
                && (permitNativeFlightExit || !player.getAbilities().flying)
                && !player.hasVehicle() && !player.isSleeping() && !player.isFallFlying() && !player.isUsingRiptide()
                && !player.isSubmergedInWater() && player.hurtTime == 0
                && client.getCameraEntity() == player
                && !RemoteToolClient.active() && !EarthSenseClient.active() && !EarthSenseClient.pending();
    }
    private static void tick(MinecraftClient client) {
        if (world != client.world || owner != client.player) {
            suspend(); clear(); world = client.world; owner = client.player;
        }
        if (client.isPaused()) return;
        tick++;
        VISUALS.entrySet().removeIf(entry -> tick - entry.getValue().tick > 20);
        if (!scope(client)) { suspend(); return; }
        SESSION.tick();
        if (retry > 0) retry--;
        if (SESSION.expired() && SESSION.enabled()) { suspend(); retry = 20; }
        if (SESSION.enabled()) {
            var input = readInput(client);
            if (tick - lastSent >= 5 || input.needsUpdate(SESSION.input())) send(input);
        }
    }
    public static void captureInput(Input input) {
        var client = MinecraftClient.getInstance();
        if (client.player == null || client.player.input != input) return;
        if (!scope(client)) { suspend(); return; }
        preparePlayerInput(client.player);
        if (SESSION.enabled()) {
            var raw = readInput(client);
            if (raw.needsUpdate(SESSION.input())) send(raw);
        }
    }
    public static boolean preparePlayerInput(PlayerEntity player) {
        var client = MinecraftClient.getInstance();
        if (player != client.player) return false;
        nativeControl = scope(client) && SESSION.allowed() && predictMode(player, readInput(client)) != Mode.OFF;
        return nativeControl;
    }
    public static void applyNativeInput(Input input) {
        var client = MinecraftClient.getInstance();
        if (client.player == null || client.player.input != input) return;
        UnicornLevitationNativeInput.apply(input, readInput(client), suppressesNativeSneak(), controlsNativeInput());
    }
    public static boolean controlsNativeInput() { return nativeControl && scope(MinecraftClient.getInstance()); }
    public static boolean suppressesNativeSneak() {
        var client = MinecraftClient.getInstance();
        return scope(client) && (nativeControl || SESSION.armed() && readInput(client).space());
    }
    public static boolean blocksNativeFlight() {
        return scope(MinecraftClient.getInstance()) && (SESSION.armed() || nativeControl || predicted != Mode.OFF);
    }
    public static boolean blocksSprinting() {
        var client = MinecraftClient.getInstance();
        return scope(client) && SESSION.allowed() && SESSION.armed()
                && (nativeControl || predicted != Mode.OFF || predictMode(client.player, readInput(client)) != Mode.OFF);
    }
    private static UnicornLevitationInput readInput(MinecraftClient client) {
        if (client.player == null || client.currentScreen != null || client.getOverlay() != null
                || client.isPaused() || !client.isWindowFocused()) return UnicornLevitationInput.NONE;
        var options = client.options;
        return new UnicornLevitationInput(options.jumpKey.isPressed(), options.sneakKey.isPressed(),
                options.forwardKey.isPressed(), options.backKey.isPressed(), options.leftKey.isPressed(),
                options.rightKey.isPressed(), client.player.getYaw());
    }
    private static void send(UnicornLevitationInput input) {
        if (!ClientPlayNetworking.canSend(UnicornLevitationProtocol.CONTROL)) return;
        var buffer = PacketByteBufs.create();
        UnicornLevitationProtocol.writeControl(buffer, SESSION.packet(input));
        ClientPlayNetworking.send(UnicornLevitationProtocol.CONTROL, buffer); lastSent = tick;
    }
    private static void receive(MinecraftClient client, UnicornLevitationProtocol.State state) {
        if (client.world == null || client.player == null || !state.dimension().equals(dimension(client))
                || state.sequence() <= lastState) return;
        lastState = state.sequence();
        if (state.actor().equals(client.player.getUuid())) {
            boolean wasEnabled = SESSION.enabled();
            if (!SESSION.accept(state, dimension(client))) return;
            if (!state.allowed()) {
                SESSION.disable(); predicted = Mode.OFF; nativeControl = false;
                if (wasEnabled) retry = Math.max(retry, 20);
            } else if (UnicornLevitationProtocol.MOVEMENT_CORRECTION.equals(state.reason())
                    && SESSION.allowed() && SESSION.armed() && scope(client)) {
                client.player.setVelocity(state.velocityX(), state.velocityY(), state.velocityZ());
            }
        }
        VISUALS.put(state.actor(), new Visual(state, tick));
        while (VISUALS.size() > 256) VISUALS.remove(VISUALS.keySet().iterator().next());
    }

    public static boolean travel(PlayerEntity player) {
        var client = MinecraftClient.getInstance();
        if (player != client.player) return false;
        if (!scope(client) || !SESSION.allowed()) { predicted = Mode.OFF; nativeControl = false; return false; }
        var current = player.getVelocity();
        if (!finite(current)) { suspend(); return false; }
        var velocity = new Motion(current.x, current.y, current.z);
        var input = readInput(client);
        var support = UnicornLevitationGround.find(player.getWorld(), player, velocity);
        var mode = SESSION.predict(input, player.isOnGround(), predicted, current.y,
                support == null ? Double.NaN : support.distance(),
                support != null && support.kind() != UnicornLevitationGround.Kind.SOLID, player.fallDistance);
        predicted = mode;
        nativeControl = mode != Mode.OFF;
        if (mode != Mode.OFF) player.setSprinting(false);
        if (mode == Mode.OFF) return false;
        if (movedAge == player.age) return true;
        movedAge = player.age;
        var next = UnicornLevitationMath.step(velocity, input.yaw(), input.forwardAxis(player.isUsingItem()),
                input.sideAxis(player.isUsingItem()), mode, player.getY(), support == null ? Double.NaN : support.y());
        player.setVelocity(next.x(), next.y(), next.z());
        player.move(MovementType.SELF, player.getVelocity());
        // Entity.move retains collision resolution; never reset fall distance or grant vanilla flight.
        if (player.verticalCollision) player.setVelocity(player.getVelocity().multiply(1, 0, 1));
        player.updateLimbs(false);
        return true;
    }
    private static Mode predictMode(PlayerEntity player, UnicornLevitationInput input) {
        var velocity = player.getVelocity();
        if (!finite(velocity)) return Mode.OFF;
        var support = UnicornLevitationGround.find(player.getWorld(), player, new Motion(velocity.x, velocity.y, velocity.z));
        return SESSION.predict(input, player.isOnGround(), predicted, velocity.y,
                support == null ? Double.NaN : support.distance(),
                support != null && support.kind() != UnicornLevitationGround.Kind.SOLID, player.fallDistance);
    }
    private static boolean finite(Vec3d value) {
        return Double.isFinite(value.x) && Double.isFinite(value.y) && Double.isFinite(value.z);
    }
    private static boolean flightVisual(UUID id) {
        var client = MinecraftClient.getInstance();
        if (client.world == null) return false;
        var visual = VISUALS.get(id);
        if (visual == null || tick - visual.tick > 20 || !visual.state.allowed()
                || visual.state.mode() == Mode.OFF || !visual.state.dimension().equals(dimension(client))) return false;
        var player = client.world.getPlayerByUuid(id);
        if (player == null || !player.isAlive() || player.isRemoved()) return false;
        return player != client.player || scope(client) && SESSION.allowed()
                && SESSION.armed() && ((visual.state.mode() != Mode.ASCEND && visual.state.mode() != Mode.HOVER && visual.state.mode() != Mode.RECOVER)
                || SESSION.mode(readInput(client)) != Mode.OFF);
    }
    private static String dimension(MinecraftClient client) { return client.world.getRegistryKey().getValue().toString(); }
    private static Text text(String suffix) { return Text.translatable("text.magicaland_gameplay.levitation." + suffix); }
    private record Visual(UnicornLevitationProtocol.State state, long tick) {}
}
