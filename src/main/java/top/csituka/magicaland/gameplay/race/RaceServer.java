package top.csituka.magicaland.gameplay.race;

import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import net.fabricmc.fabric.api.command.v2.CommandRegistrationCallback;
import net.fabricmc.fabric.api.entity.event.v1.ServerPlayerEvents;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerLifecycleEvents;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerTickEvents;
import net.fabricmc.fabric.api.networking.v1.PacketByteBufs;
import net.fabricmc.fabric.api.networking.v1.ServerPlayConnectionEvents;
import net.fabricmc.fabric.api.networking.v1.ServerPlayNetworking;
import net.minecraft.command.argument.EntityArgumentType;
import net.minecraft.command.argument.IdentifierArgumentType;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.sound.SoundCategory;
import net.minecraft.sound.SoundEvent;
import net.minecraft.text.Text;
import net.minecraft.util.Identifier;
import top.csituka.magicaland.gameplay.remote.RemoteToolServer;
import static net.minecraft.server.command.CommandManager.*;

public final class RaceServer {
    private static final Map<UUID, Long> REQUESTS = new ConcurrentHashMap<>();
    private static final Map<UUID, Boolean> PERMISSIONS = new HashMap<>();
    private static final SoundEvent CHANGE_SOUND = SoundEvent.of(new Identifier("magicaland", "magic.cast"));
    private RaceServer() {}

    public static void register() {
        RaceItems.register();
        ServerPlayNetworking.registerGlobalReceiver(RaceProtocol.REQUEST, (server, player, handler, buf, sender) -> {
            if (buf.isReadable() || !allowPacket(player.getUuid())) return;
            server.execute(() -> { if (connected(player)) sync(player); });
        });
        ServerPlayNetworking.registerGlobalReceiver(RaceProtocol.CHOOSE, (server, player, handler, buf, sender) -> {
            try {
                if (buf.readableBytes() > 536 || !allowPacket(player.getUuid())) return;
                long requestId = buf.readLong();
                if (requestId <= 0) return;
                String target = buf.readString(128);
                long revision = buf.readLong();
                if (buf.isReadable()) return;
                server.execute(() -> {
                    if (!connected(player)) return;
                    String denial = revision != RaceState.get(server).rules().revision() ? "stale"
                            : tryChange(player, target, RacePolicy.ChangeCause.SELECT, false);
                    if (denial != null) reject(player, denial);
                    result(player, requestId, denial);
                });
            } catch (RuntimeException ignored) {}
        });
        ServerPlayNetworking.registerGlobalReceiver(RaceProtocol.RULES, (server, player, handler, buf, sender) -> {
            try {
                if (!allowPacket(player.getUuid())) return;
                long requestId = buf.readLong();
                if (requestId <= 0) return;
                RaceRules requested = RaceProtocol.readRules(buf);
                server.execute(() -> {
                    if (!connected(player)) return;
                    var state = RaceState.get(server);
                    String denial = RacePolicy.rulesDenial(canManage(player), state.rules(), requested);
                    if (denial != null) { reject(player, denial); result(player, requestId, denial); return; }
                    state.rules(requested.nextRevision());
                    broadcast(server, null);
                    player.sendMessage(Text.translatable("text.magicaland_gameplay.race.rules_saved"), false);
                    result(player, requestId, null);
                });
            } catch (RuntimeException ignored) {}
        });
        ServerPlayConnectionEvents.JOIN.register((handler, sender, server) -> broadcast(server, null));
        ServerPlayConnectionEvents.DISCONNECT.register((handler, server) -> {
            REQUESTS.remove(handler.player.getUuid());
            PERMISSIONS.remove(handler.player.getUuid());
            broadcast(server, handler.player.getUuid());
        });
        ServerPlayerEvents.COPY_FROM.register((oldPlayer, newPlayer, alive) -> {
            if (oldPlayer.getCommandTags().contains(RemoteToolServer.GRANT)) newPlayer.addCommandTag(RemoteToolServer.GRANT);
        });
        ServerPlayerEvents.AFTER_RESPAWN.register((oldPlayer, newPlayer, alive) -> sync(newPlayer));
        ServerTickEvents.END_SERVER_TICK.register(server -> {
            if (server.getTicks() % 20 != 0) return;
            for (var player : server.getPlayerManager().getPlayerList()) {
                boolean allowed = canManage(player);
                Boolean old = PERMISSIONS.put(player.getUuid(), allowed);
                if (old == null || old != allowed) sync(player);
            }
        });
        ServerLifecycleEvents.SERVER_STOPPED.register(server -> { REQUESTS.clear(); PERMISSIONS.clear(); });
        CommandRegistrationCallback.EVENT.register((dispatcher, registry, environment) -> {
            dispatcher.register(literal("magicaland").then(literal("race")
                    .requires(source -> source.hasPermissionLevel(2))
                    .then(literal("set").then(argument("players", EntityArgumentType.players())
                            .then(argument("race", IdentifierArgumentType.identifier()).suggests((context, builder) -> {
                                RaceDefinitions.all().forEach(race -> builder.suggest(race.id()));
                                return builder.buildFuture();
                            }).executes(context -> {
                                int changed = 0;
                                for (var player : EntityArgumentType.getPlayers(context, "players")) {
                                    RemoteToolServer.stop(player);
                                    if (change(player, IdentifierArgumentType.getIdentifier(context, "race").toString(),
                                            RacePolicy.ChangeCause.ADMIN, false)) changed++;
                                }
                                return changed;
                            }))))));
        });
    }

    private static boolean allowPacket(UUID player) {
        long now = System.nanoTime();
        Long previous = REQUESTS.put(player, now);
        return previous == null || now - previous >= 50_000_000L;
    }

    private static boolean connected(ServerPlayerEntity player) {
        return player.getServer() != null && player.getServer().getPlayerManager().getPlayer(player.getUuid()) == player;
    }

    public static boolean canManage(ServerPlayerEntity player) {
        var server = player.getServer();
        return server != null && (player.getCommandSource().hasPermissionLevel(2)
                || server.isSingleplayer() && server.isHost(player.getGameProfile()));
    }

    public static boolean isUnicorn(ServerPlayerEntity player) {
        return player.getServer() != null && RaceDefinitions.UNICORN_ID.equals(RaceState.get(player.getServer()).race(player.getUuid()));
    }

    public static boolean safeToChange(ServerPlayerEntity player, boolean drinking) {
        return player.isAlive() && !player.isSpectator() && player.isOnGround() && !player.hasVehicle()
                && !player.isSleeping() && !player.getAbilities().flying && !player.isTouchingWater()
                && !player.isInLava() && player.hurtTime == 0 && !RemoteToolServer.active(player)
                && (drinking || !player.isUsingItem()) && player.currentScreenHandler == player.playerScreenHandler;
    }

    public static String denial(ServerPlayerEntity player, String target, RacePolicy.ChangeCause cause, boolean drinking) {
        var state = RaceState.get(player.getServer());
        return RacePolicy.denial(state.race(player.getUuid()), target, state.rules(), cause, safeToChange(player, drinking));
    }

    public static boolean change(ServerPlayerEntity player, String target, RacePolicy.ChangeCause cause, boolean drinking) {
        String denial = tryChange(player, target, cause, drinking);
        if (denial != null) reject(player, denial);
        return denial == null;
    }

    private static String tryChange(ServerPlayerEntity player, String target, RacePolicy.ChangeCause cause, boolean drinking) {
        String denial = denial(player, target, cause, drinking);
        if (denial != null) return denial;
        RemoteToolServer.stop(player);
        top.csituka.magicaland.gameplay.levitation.UnicornLevitationServer.stop(player);
        top.csituka.magicaland.gameplay.pegasus.PegasusFlightServer.stop(player);
        RaceState.get(player.getServer()).race(player.getUuid(), target);
        if (RaceDefinitions.UNICORN_ID.equals(target)) player.addCommandTag(RemoteToolServer.GRANT);
        else player.removeScoreboardTag(RemoteToolServer.GRANT);
        broadcast(player.getServer(), null);
        for (var observer : player.getServer().getPlayerManager().getPlayerList()) {
            if (observer.getWorld() != player.getWorld() || !ServerPlayNetworking.canSend(observer, RaceProtocol.EFFECT)) continue;
            var effect = PacketByteBufs.create().writeUuid(player.getUuid());
            ServerPlayNetworking.send(observer, RaceProtocol.EFFECT, effect);
        }
        player.getWorld().playSound(null, player.getX(), player.getY(), player.getZ(), CHANGE_SOUND,
                SoundCategory.PLAYERS, .25f, 1f);
        player.sendMessage(Text.translatable("text.magicaland_gameplay.race.changed",
                Text.translatable(RaceDefinitions.find(target).translationKey())), false);
        return null;
    }

    private static void result(ServerPlayerEntity player, long requestId, String denial) {
        if (!ServerPlayNetworking.canSend(player, RaceProtocol.RESULT)) return;
        var buf = PacketByteBufs.create();
        RaceProtocol.writeResult(buf, new RaceProtocol.Result(requestId, denial == null ? "" : denial));
        ServerPlayNetworking.send(player, RaceProtocol.RESULT, buf);
    }

    public static void reject(ServerPlayerEntity player, String reason) {
        player.sendMessage(Text.translatable("text.magicaland_gameplay.race.error." + reason), false);
        sync(player);
    }

    private static Map<UUID, String> online(MinecraftServer server, UUID excluded) {
        var players = new LinkedHashMap<UUID, String>();
        var state = RaceState.get(server);
        for (var player : server.getPlayerManager().getPlayerList()) {
            String race = state.race(player.getUuid());
            if (!player.getUuid().equals(excluded) && !race.isEmpty() && players.size() < RaceProtocol.MAX_PLAYERS)
                players.put(player.getUuid(), race);
        }
        return players;
    }

    public static void sync(ServerPlayerEntity player) {
        send(player, online(player.getServer(), null));
    }

    private static void broadcast(MinecraftServer server, UUID excluded) {
        var players = online(server, excluded);
        for (var player : server.getPlayerManager().getPlayerList())
            if (!player.getUuid().equals(excluded)) send(player, players);
    }

    private static void send(ServerPlayerEntity player, Map<UUID, String> players) {
        if (!ServerPlayNetworking.canSend(player, RaceProtocol.STATE)) return;
        var state = RaceState.get(player.getServer());
        var buf = PacketByteBufs.create();
        RaceProtocol.writeView(buf, new RaceProtocol.View(state.rules(), state.race(player.getUuid()), canManage(player), players));
        ServerPlayNetworking.send(player, RaceProtocol.STATE, buf);
    }
}
