package top.elysianherd.magicaland.gameplay.client.sense;

import java.util.List;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayConnectionEvents;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking;
import net.fabricmc.fabric.api.networking.v1.PacketByteBufs;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.input.Input;
import net.minecraft.client.world.ClientWorld;
import net.minecraft.text.Text;
import net.minecraft.util.math.MathHelper;
import top.elysianherd.magicaland.gameplay.client.AbilityWheelScreen;
import top.elysianherd.magicaland.gameplay.client.RemoteToolClient;
import top.elysianherd.magicaland.gameplay.client.race.RaceClient;
import top.elysianherd.magicaland.gameplay.config.GameplayClientConfig;
import top.elysianherd.magicaland.gameplay.race.RaceDefinitions;
import top.elysianherd.magicaland.gameplay.sense.EarthSenseProtocol;
import top.elysianherd.magicaland.gameplay.sense.EarthSenseViewerMotion;

public final class EarthSenseClient {
    public interface SneakState { void magicaland$restoreSneak(boolean held); }
    private static final EarthSenseSession SESSION = new EarthSenseSession();
    private static final EarthSenseFocusEnvelope ENVELOPE = new EarthSenseFocusEnvelope();
    private static Input focusedInput;
    private static ClientWorld world;
    private static int heartbeat;
    private static long visualTicks;
    private static EarthSenseViewerMotion viewerMotion;
    private static Object motionPlayer;
    private static float quality = 1, previousQuality = 1;

    private EarthSenseClient() {}

    public static void init() {
        EarthSenseRenderer.init();
        EarthSenseAudio.init();
        ClientPlayConnectionEvents.JOIN.register((handler, sender, client) -> reset());
        ClientPlayConnectionEvents.DISCONNECT.register((handler, client) -> reset());
        ClientPlayNetworking.registerGlobalReceiver(EarthSenseProtocol.STATE, (client, handler, buf, sender) -> {
            try {
                var state = EarthSenseProtocol.readState(buf);
                client.execute(() -> {
                    if (client.getNetworkHandler() != handler || client.world == null) return;
                    if (world != client.world) {
                        reset();
                        world = client.world;
                        return;
                    }
                    if (!SESSION.accept(state, client.world.getRegistryKey().getValue().toString())) return;
                    EarthSenseTransitionSound.tick(SESSION.active(), SESSION.token());
                    if (!state.active()) restoreFocusInput();
                    if (!state.active() && !state.reason().isEmpty() && !state.reason().equals("manual"))
                        message("error." + state.reason());
                });
            } catch (RuntimeException ignored) {}
        });
        ClientTickEvents.END_CLIENT_TICK.register(EarthSenseClient::tick);
    }

    public static boolean available() {
        return RaceClient.ready() && RaceDefinitions.EARTH_PONY_ID.equals(RaceClient.view().ownRace());
    }
    public static boolean active() { return SESSION.active(); }
    public static boolean pending() { return SESSION.wanted() && !SESSION.active(); }
    public static boolean focusing() { return SESSION.wanted() || SESSION.active(); }
    public static long signalEpoch() { return SESSION.token(); }
    public static boolean grounded() { return SESSION.grounded(); }
    public static String statusReason() { return SESSION.reason(); }
    public static List<EarthSenseProtocol.Signal> signals() { return SESSION.signals(); }
    public static float opacity(float delta) { return ENVELOPE.sample(visualTicks + EarthSenseFocusEnvelope.clamp(delta)); }
    public static float signalQuality(float delta) {
        return MathHelper.lerp(EarthSenseFocusEnvelope.clamp(delta), previousQuality, quality);
    }

    public static Text unavailable() {
        return !RaceClient.ready() ? RaceClient.unavailable() : Text.translatable("text.magicaland_gameplay.sense.error.race");
    }

    public static void toggle() {
        if (SESSION.wanted() || SESSION.active()) { stop(); return; }
        if (!available()) { var client = MinecraftClient.getInstance(); if (client.player != null) client.player.sendMessage(unavailable(), true); return; }
        if (!ClientPlayNetworking.canSend(EarthSenseProtocol.CONTROL)) { message("error.unsupported"); return; }
        if (RemoteToolClient.active()) { message("error.remote"); return; }
        var client = MinecraftClient.getInstance();
        if (client.player == null || EarthSenseFocusInput.requestedJump(client.player.input)) { message("error.stance"); return; }
        SESSION.begin(true);
        heartbeat = 0;
        send(true);
    }

    public static void stop() {
        restoreFocusInput();
        if (!SESSION.wanted() && !SESSION.active()) return;
        SESSION.begin(false);
        EarthSenseTransitionSound.tick(false, SESSION.token());
        send(false);
    }

    public static void applyFocusInput(Input input, boolean slowDown, float factor) {
        var screen = MinecraftClient.getInstance().currentScreen;
        if (screen instanceof AbilityWheelScreen) {
            if (focusing() && input != null) {
                input.movementForward = input.movementSideways = 0;
                input.pressingForward = input.pressingBack = input.pressingLeft = input.pressingRight = false;
                input.jumping = false;
                input.sneaking = true;
                focusedInput = input;
            }
            return;
        }
        if (screen != null) { stop(); return; }
        if (EarthSenseFocusInput.apply(input, focusing(), slowDown, factor)) stop();
        else if (focusing() && input != null) focusedInput = input;
    }

    private static void restoreFocusInput() {
        Input previous = focusedInput;
        focusedInput = null;
        var client = MinecraftClient.getInstance();
        if (previous == null || client.player == null || client.player.input != previous) return;
        boolean held = client.options.sneakKey.isPressed();
        previous.sneaking = held;
        if (client.getNetworkHandler() == client.player.networkHandler && client.player instanceof SneakState state)
            state.magicaland$restoreSneak(held);
    }

    private static void send(boolean enabled) {
        if (!ClientPlayNetworking.canSend(EarthSenseProtocol.CONTROL)) return;
        var buf = PacketByteBufs.create();
        EarthSenseProtocol.writeControl(buf, new EarthSenseProtocol.Control(SESSION.token(), enabled));
        ClientPlayNetworking.send(EarthSenseProtocol.CONTROL, buf);
    }

    private static void tick(MinecraftClient client) {
        if (world != client.world) {
            EarthSenseTransitionSound.reset();
            stop();
            reset();
            world = client.world;
        }
        if (client.player == null || client.world == null) {
            EarthSenseAudio.reset();
            EarthSenseTransitionSound.reset();
            return;
        }
        if (SESSION.wanted()) {
            if (!client.player.isAlive() || client.player.isSpectator() || !client.isWindowFocused()
                    || client.isPaused() || (client.currentScreen != null && !(client.currentScreen instanceof AbilityWheelScreen))
                    || !available()
                    || RemoteToolClient.active() || client.player.hurtTime > 0) stop();
            else if (!(client.currentScreen instanceof AbilityWheelScreen)
                    && EarthSenseFocusInput.requestedJump(client.player.input)) stop();
            else if (SESSION.tickExpired()) { stop(); message("error.timeout"); }
            else if (++heartbeat >= 20) { heartbeat = 0; send(true); }
        }
        previousQuality = quality;
        if (focusing()) {
            if (viewerMotion == null || motionPlayer != client.player) {
                viewerMotion = new EarthSenseViewerMotion(client.player.getX(), client.player.getZ(), client.player.age,
                        client.player.getVelocity().horizontalLength());
                motionPlayer = client.player;
                previousQuality = quality = (float) viewerMotion.quality();
            } else quality = (float) viewerMotion.observe(client.player.getX(), client.player.getZ(), client.player.age);
        } else { viewerMotion = null; motionPlayer = null; }
        float target = active() ? grounded() ? 1 : .3f : 0;
        ENVELOPE.observe(++visualTicks, target);
        EarthSenseTransitionSound.tick(SESSION.active(), SESSION.token());
        EarthSenseAudio.tick(ENVELOPE.sample(visualTicks) * GameplayClientConfig.earthSenseAudioStrength());
    }

    private static void message(String key) {
        var player = MinecraftClient.getInstance().player;
        if (player != null) player.sendMessage(Text.translatable("text.magicaland_gameplay.sense." + key), true);
    }

    private static void reset() {
        restoreFocusInput();
        SESSION.clear();
        world = null;
        heartbeat = 0;
        visualTicks = 0;
        ENVELOPE.reset();
        viewerMotion = null;
        motionPlayer = null;
        quality = previousQuality = 1;
        EarthSenseRenderer.reset();
        EarthSenseAudio.reset();
        EarthSenseTransitionSound.reset();
    }
}
