package top.csituka.magicaland.gameplay.client.race;

import java.util.Map;
import java.util.LinkedHashMap;
import java.util.UUID;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayConnectionEvents;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking;
import net.fabricmc.fabric.api.client.rendering.v1.ColorProviderRegistry;
import net.fabricmc.fabric.api.networking.v1.PacketByteBufs;
import net.minecraft.client.MinecraftClient;
import net.minecraft.text.Text;
import net.minecraft.registry.Registries;
import net.minecraft.util.Identifier;
import top.csituka.magicaland.api.client.AnatomyOverride;
import top.csituka.magicaland.api.client.AppearanceOverrides;
import top.csituka.magicaland.api.client.AppearanceVisuals;
import top.csituka.magicaland.api.client.Registration;
import top.csituka.magicaland.gameplay.client.AbilityClient;
import top.csituka.magicaland.gameplay.client.levitation.UnicornLevitationClient;
import top.csituka.magicaland.gameplay.client.RemoteToolClient;
import top.csituka.magicaland.gameplay.race.RaceDefinitions;
import top.csituka.magicaland.gameplay.race.RaceProtocol;
import top.csituka.magicaland.gameplay.race.RaceRules;

public final class RaceClient {
    private static RaceProtocol.View view;
    private static Map<UUID, String> players = Map.of();
    private static final Map<Long, RaceProtocol.Result> RESULTS = new LinkedHashMap<>();
    private static Registration anatomyOverride;
    private static boolean promptPending;
    private static int requestTicks;
    private static long version, requestSequence, lastRequestId;

    private RaceClient() {}

    public static void init() {
        for (var race : RaceDefinitions.all()) {
            int color = RaceDefinitions.UNICORN_ID.equals(race.id()) ? 0xB897EE
                    : RaceDefinitions.PEGASUS_ID.equals(race.id()) ? 0x85C9EA : 0xDAAA65;
            var id = new Identifier(race.id());
            var item = Registries.ITEM.get(new Identifier(id.getNamespace(), id.getPath() + "_race_potion"));
            ColorProviderRegistry.ITEM.register((stack, tintIndex) -> tintIndex == 0 ? color : -1, item);
        }
        ClientPlayConnectionEvents.JOIN.register((handler, sender, client) -> {
            clear();
            promptPending = true;
            anatomyOverride = AppearanceOverrides.registerAnatomy("magicaland_gameplay:race", 0, RaceClient::anatomy);
        });
        ClientPlayConnectionEvents.DISCONNECT.register((handler, client) -> clear());
        ClientPlayNetworking.registerGlobalReceiver(RaceProtocol.STATE, (client, handler, buffer, sender) -> {
            try {
                RaceProtocol.View incoming = RaceProtocol.readView(buffer);
                if (buffer.isReadable()) return;
                client.execute(() -> {
                    if (client.getNetworkHandler() != handler) return;
                    if (!java.util.Objects.equals(view == null ? null : view.ownRace(), incoming.ownRace())) {
                        UnicornLevitationClient.suspend();
                        UnicornLevitationClient.clear();
                    }
                    AbilityClient.raceChanged(view == null ? null : view.ownRace(), incoming.ownRace());
                    view = incoming;
                    players = Map.copyOf(incoming.players());
                    version++;
                    if (hasRace()) promptPending = false;
                });
            } catch (RuntimeException ignored) {}
        });
        ClientPlayNetworking.registerGlobalReceiver(RaceProtocol.EFFECT, (client, handler, buffer, sender) -> {
            try {
                UUID player = buffer.readUuid();
                if (buffer.isReadable()) return;
                client.execute(() -> {
                    if (client.getNetworkHandler() == handler) AppearanceVisuals.playTransformation(player);
                });
            } catch (RuntimeException ignored) {}
        });
        ClientPlayNetworking.registerGlobalReceiver(RaceProtocol.RESULT, (client, handler, buffer, sender) -> {
            try {
                RaceProtocol.Result incoming = RaceProtocol.readResult(buffer);
                if (buffer.isReadable()) return;
                client.execute(() -> {
                    if (client.getNetworkHandler() != handler || incoming.requestId() > lastRequestId) return;
                    RESULTS.put(incoming.requestId(), incoming);
                    while (RESULTS.size() > 64) RESULTS.remove(RESULTS.keySet().iterator().next());
                });
            } catch (RuntimeException ignored) {}
        });
        ClientTickEvents.END_CLIENT_TICK.register(client -> {
            if (client.player == null || client.world == null) return;
            if (view == null && requestTicks-- <= 0) {
                requestTicks = 100;
                request();
            }
            if (promptPending && ready() && !hasRace() && client.currentScreen == null
                    && client.player.isAlive() && !RemoteToolClient.active()) {
                promptPending = false;
                client.setScreen(new RaceSelectionScreen(null));
            }
        });
    }

    private static void clear() {
        AbilityClient.reset();
        UnicornLevitationClient.clear();
        if (anatomyOverride != null) anatomyOverride.close();
        anatomyOverride = null;
        view = null;
        players = Map.of();
        RESULTS.clear();
        lastRequestId = 0;
        promptPending = false;
        requestTicks = 0;
        version++;
    }

    private static AnatomyOverride anatomy(UUID player) {
        if (view == null || view.rules().freeAppearance()) return null;
        String id = players.get(player);
        var definition = id == null ? null : RaceDefinitions.find(id);
        return definition == null ? null : new AnatomyOverride(definition.hasHorn(), definition.hasWings());
    }

    public static boolean connected() { return MinecraftClient.getInstance().getNetworkHandler() != null; }
    public static boolean supported() { return connected() && ClientPlayNetworking.canSend(RaceProtocol.REQUEST); }
    public static boolean ready() { return supported() && view != null; }
    public static RaceProtocol.View view() { return view; }
    public static void openedSelection() { promptPending = false; }
    public static long version() { return version; }
    public static long lastRequestId() { return lastRequestId; }
    public static RaceProtocol.Result result(long requestId) { return RESULTS.get(requestId); }
    public static boolean hasRace() { return view != null && view.ownRace() != null && !view.ownRace().isEmpty(); }
    public static boolean canManage() { return ready() && view.canManage(); }
    public static boolean canUseUnicornAbility() {
        return ready() && RaceDefinitions.UNICORN_ID.equals(view.ownRace());
    }
    public static boolean canChoose(String id) {
        return ready() && view.rules().enabledRaces().contains(id) && RaceDefinitions.find(id) != null
                && (!hasRace() || (RaceDefinitions.find(view.ownRace()) != null && view.rules().mode() == RaceRules.ChangeMode.FREE))
                && !id.equals(view.ownRace());
    }
    public static Text name(String id) {
        if (id == null || id.isEmpty()) return text("unselected");
        if (RaceDefinitions.find(id) == null) return text("unknown", id);
        return Text.translatable("race." + id.replace(':', '.'));
    }
    public static Text text(String suffix, Object... args) {
        return Text.translatable("text.magicaland_gameplay.race." + suffix, args);
    }
    public static Text unavailable() {
        return text(!connected() ? "no_server" : !supported() ? "unsupported" : "loading");
    }
    public static Text abilityUnavailable() {
        return !ready() ? unavailable() : text(hasRace() ? "unicorn_only" : "choose_first");
    }
    public static void request() {
        if (supported()) ClientPlayNetworking.send(RaceProtocol.REQUEST, PacketByteBufs.create());
    }
    public static boolean choose(String id, long revision) {
        if (!canChoose(id) || !ClientPlayNetworking.canSend(RaceProtocol.CHOOSE) || requestSequence == Long.MAX_VALUE) return false;
        var packet = PacketByteBufs.create();
        long requestId = ++requestSequence;
        packet.writeLong(requestId);
        packet.writeString(id, 128);
        packet.writeLong(revision);
        ClientPlayNetworking.send(RaceProtocol.CHOOSE, packet);
        lastRequestId = requestId;
        return true;
    }
    public static boolean saveRules(RaceRules rules) {
        if (!canManage() || rules.enabledRaces().isEmpty() || !ClientPlayNetworking.canSend(RaceProtocol.RULES)
                || requestSequence == Long.MAX_VALUE) return false;
        var packet = PacketByteBufs.create();
        long requestId = ++requestSequence;
        packet.writeLong(requestId);
        RaceProtocol.writeRules(packet, rules);
        ClientPlayNetworking.send(RaceProtocol.RULES, packet);
        lastRequestId = requestId;
        return true;
    }
}
