package top.csituka.magicaland.gameplay.client.telekinesis;

import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.UUID;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayConnectionEvents;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking;
import net.fabricmc.fabric.api.networking.v1.PacketByteBufs;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.network.ClientPlayerEntity;
import net.minecraft.client.world.ClientWorld;
import net.minecraft.entity.Entity;
import net.minecraft.item.ItemStack;
import net.minecraft.text.Text;
import top.csituka.magicaland.api.client.AppearanceOverrides;
import top.csituka.magicaland.api.client.Registration;
import top.csituka.magicaland.gameplay.client.AbilityClient;
import top.csituka.magicaland.gameplay.client.RemoteToolClient;
import top.csituka.magicaland.gameplay.client.race.RaceClient;
import top.csituka.magicaland.gameplay.remote.RemoteToolEntity;
import top.csituka.magicaland.gameplay.remote.TelekinesisProtocol;
import top.csituka.magicaland.gameplay.remote.TelekinesisProtocol.*;
import top.csituka.magicaland.gameplay.remote.TelekinesisRules;
import top.csituka.magicaland.gameplay.remote.TelekinesisToken;

public final class TelekinesisClient {
    private static final String OWNER = "magicaland_gameplay:telekinesis";
    private static final TelekinesisGesture GESTURE = new TelekinesisGesture();
    private static final Set<UUID> magicOwners = new HashSet<>();
    private static List<TaskView> tasks = List.of();
    private static long sequence, lastState, pendingSequence, acknowledgedSequence;
    private static int tick, pendingUntil, lastRequestTick = -100;
    private static ClientWorld world, pressedWorld;
    private static ClientPlayerEntity pressedPlayer;
    private static ItemStack pressedMain = ItemStack.EMPTY, pressedOff = ItemStack.EMPTY;
    private static int pressedSlot;
    private static Registration magic, handVisibility;
    private static boolean initialized;

    private TelekinesisClient() {}

    public static void init() {
        if (initialized) return;
        initialized = true;
        ClientPlayConnectionEvents.JOIN.register((handler, sender, client) -> {
            clear(); sequence = lastState = 0;
            if (magic != null) magic.close();
            if (handVisibility != null) handVisibility.close();
            magic = AppearanceOverrides.registerMagicActivity(OWNER, 0, magicOwners::contains);
            handVisibility = AppearanceOverrides.registerMainHandVisibility(OWNER, 0, owner -> {
                var current = MinecraftClient.getInstance().world;
                var player = current == null ? null : current.getPlayerByUuid(owner);
                return player != null && TelekinesisToken.isToken(player.getMainHandStack())
                        ? AppearanceOverrides.Visibility.HIDDEN : AppearanceOverrides.Visibility.DEFAULT;
            });
        });
        ClientPlayConnectionEvents.DISCONNECT.register((handler, client) -> {
            clear();
            if (magic != null) magic.close();
            if (handVisibility != null) handVisibility.close();
            magic = handVisibility = null;
        });
        ClientPlayNetworking.registerGlobalReceiver(TelekinesisProtocol.STATE, (client, handler, buffer, sender) -> {
            try {
                State state = TelekinesisProtocol.readState(buffer);
                ClientWorld packetWorld = client.world;
                client.execute(() -> {
                    if (client.getNetworkHandler() != handler || client.world != packetWorld || client.player == null
                            || state.sequence() <= lastState) return;
                    world = packetWorld; lastState = state.sequence(); tasks = List.copyOf(state.tasks());
                    acknowledgedSequence = Math.max(acknowledgedSequence, state.acknowledgedSequence());
                    if (!pending()) pendingUntil = 0;
                    if (!state.reason().isEmpty()) feedback(state.reason());
                });
            } catch (RuntimeException ignored) {}
        });
        ClientTickEvents.END_CLIENT_TICK.register(client -> {
            if (world != client.world) {
                tasks = List.of(); pendingUntil = 0; pendingSequence = 0; world = client.world; cancelInput();
            }
            if (!client.isPaused()) tick++;
            if (client.player == null || !client.player.isAlive()) {
                tasks = List.of(); pendingSequence = 0; cancelInput();
            }
            if (pending() && pendingUntil > 0 && tick >= pendingUntil) {
                pendingUntil = 0; feedback("waiting");
            }
            magicOwners.clear();
            if (client.world != null) for (Entity entity : client.world.getEntities())
                if (entity instanceof RemoteToolEntity tool && tool.autonomous() && !tool.isRemoved() && tool.owner() != null)
                    magicOwners.add(tool.owner());
        });
        TelekinesisTargetMarker.init();
    }

    private static void clear() {
        tasks = List.of(); tick = pendingUntil = 0; pendingSequence = acknowledgedSequence = 0;
        lastRequestTick = -100; world = null; magicOwners.clear(); cancelInput();
    }

    public static boolean available() {
        return RaceClient.canUseUnicornAbility() && ClientPlayNetworking.canSend(TelekinesisProtocol.CONTROL);
    }
    public static boolean enabled() { return !tasks.isEmpty(); }
    public static boolean pending() { return pendingSequence > acknowledgedSequence; }
    public static int count() { return tasks.size(); }
    public static List<TaskView> views() { return tasks; }

    public static ItemStack taskStack(long id) {
        for (TaskView view : tasks) if (view.id() == id) return view.renderStack();
        return ItemStack.EMPTY;
    }

    public static long selectedTaskId() {
        TaskView view = selectedView();
        return view == null ? 0 : view.id();
    }

    private static TaskView selectedView() {
        var player = MinecraftClient.getInstance().player;
        if (player == null) return null;
        ItemStack stack = player.getMainHandStack();
        if (!TelekinesisToken.isToken(stack) || !player.getUuid().equals(TelekinesisToken.owner(stack))) return null;
        int slot = player.getInventory().selectedSlot;
        if (TelekinesisToken.sourceSlot(stack) != slot) return null;
        for (TaskView view : tasks)
            if (view.id() == TelekinesisToken.taskId(stack) && view.sourceSlot() == slot) return view;
        return null;
    }

    private static boolean acceptsInput(MinecraftClient client) {
        return AbilityClient.selected() == 3 && available() && client.player != null && client.world != null
                && client.player.isAlive() && !client.player.isSpectator() && client.currentScreen == null
                && client.isWindowFocused() && !client.isPaused() && !RemoteToolClient.active()
                && client.getCameraEntity() == client.player;
    }

    private static boolean sameInput(MinecraftClient client) {
        return client.player == pressedPlayer && client.world == pressedWorld
                && client.player.getInventory().selectedSlot == pressedSlot
                && ItemStack.areEqual(pressedMain, client.player.getMainHandStack())
                && ItemStack.areEqual(pressedOff, client.player.getOffHandStack());
    }

    public static void activationInput(boolean pressed, boolean held) {
        var client = MinecraftClient.getInstance();
        if (!acceptsInput(client) || pending()) { cancelInput(); GESTURE.accept(held); return; }
        if (!GESTURE.accept(held)) return;
        if (GESTURE.pressed() && !sameInput(client)) { cancelInput(); return; }
        long now = System.nanoTime();
        if (pressed && !GESTURE.pressed() && tick - lastRequestTick >= 2) {
            ItemStack stack = client.player.getMainHandStack();
            boolean token = TelekinesisToken.isToken(stack);
            if (token) {
                var view = selectedView();
                if (view == null || view.phase() == Phase.RETURNING) return;
            } else if (!TelekinesisRules.supports(stack)) { feedback("tool"); return; }
            else if (TelekinesisRules.needsRepair(stack)) { feedback("repair"); return; }
            else if (count() >= TelekinesisRules.MAX_TASKS) { feedback("limit"); return; }
            pressedPlayer = client.player; pressedWorld = client.world; pressedSlot = client.player.getInventory().selectedSlot;
            pressedMain = stack.copy(); pressedOff = client.player.getOffHandStack().copy();
            GESTURE.begin(now, token);
        }
        if (held || !GESTURE.pressed()) return;
        var release = GESTURE.release(now);
        if (release == TelekinesisGesture.Release.SHORT) {
            var view = selectedView();
            if (view != null) stop(view.id());
            else if (!TelekinesisToken.isToken(pressedMain)) dispatch(Mode.AUTO, null);
        } else if (release == TelekinesisGesture.Release.DIRECT) {
            var target = TelekinesisAim.sample(client, pressedMain);
            if (target != null) dispatch(Mode.DIRECT, target);
        }
        clearPressed();
    }

    public static boolean aiming() {
        var client = MinecraftClient.getInstance();
        return GESTURE.aiming(System.nanoTime()) && acceptsInput(client) && !pending() && sameInput(client);
    }

    public static TelekinesisAim.Target aimTarget() {
        return aiming() ? TelekinesisAim.sample(MinecraftClient.getInstance(), pressedMain) : null;
    }

    public static void cancelInput() {
        GESTURE.cancel(); clearPressed();
    }

    private static void clearPressed() {
        pressedPlayer = null; pressedWorld = null;
        pressedMain = pressedOff = ItemStack.EMPTY;
    }

    private static void dispatch(Mode mode, TelekinesisAim.Target target) {
        if (mode == Mode.DIRECT && target == null) return;
        if (TelekinesisRules.needsRepair(pressedMain)) { feedback("repair"); return; }
        Task task = mode == Mode.DIRECT ? target.block() == null ? Task.GUARD : Task.GATHER
                : TelekinesisRules.automaticTask(pressedMain);
        if (task == null) { feedback("aim"); return; }
        send(Operation.START, 0, pressedSlot, task, mode, target);
    }

    public static void stop() {
        cancelInput();
        if (enabled() || pending()) send(Operation.STOP, 0, -1, Task.GUARD, Mode.AUTO, null);
    }

    public static void stop(long id) {
        for (TaskView view : tasks) if (view.id() == id && view.phase() != Phase.RETURNING) {
            send(Operation.STOP, id, view.sourceSlot(), view.task(), view.mode(), null); return;
        }
    }

    private static void send(Operation operation, long id, int sourceSlot, Task task, Mode mode, TelekinesisAim.Target target) {
        if (!ClientPlayNetworking.canSend(TelekinesisProtocol.CONTROL)) return;
        var buffer = PacketByteBufs.create();
        var control = new Control(++sequence, id, sourceSlot, operation, task, mode, Preference.VALUE, "",
                target == null ? null : target.block(), target == null ? -1 : target.entityId());
        TelekinesisProtocol.writeControl(buffer, control);
        ClientPlayNetworking.send(TelekinesisProtocol.CONTROL, buffer);
        pendingSequence = sequence; pendingUntil = tick + 100; lastRequestTick = tick;
    }

    private static void feedback(String reason) {
        var player = MinecraftClient.getInstance().player;
        if (player != null) {
            String key = "text.magicaland_gameplay.telekinesis." + reason;
            player.sendMessage("aim".equals(reason) ? Text.translatable(key, RemoteToolClient.activationKey())
                    : Text.translatable(key), true);
        }
    }
}
