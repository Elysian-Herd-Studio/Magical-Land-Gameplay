package top.elysianherd.magicaland.gameplay.client;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.UUID;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientEntityEvents;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientLifecycleEvents;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayConnectionEvents;
import net.fabricmc.fabric.api.resource.ResourceManagerHelper;
import net.fabricmc.fabric.api.resource.SimpleSynchronousResourceReloadListener;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.network.ClientPlayerEntity;
import net.minecraft.client.sound.MovingSoundInstance;
import net.minecraft.client.sound.SoundInstance;
import net.minecraft.client.sound.SoundManager;
import net.minecraft.client.world.ClientWorld;
import net.minecraft.resource.ResourceManager;
import net.minecraft.resource.ResourceType;
import net.minecraft.sound.SoundCategory;
import net.minecraft.sound.SoundEvent;
import net.minecraft.util.Identifier;
import top.elysianherd.magicaland.gameplay.remote.RemoteToolEntity;

public final class RemoteMagicAudio {
    private static final SoundEvent AURA = SoundEvent.of(new Identifier("magicaland", "magic.aura"));
    private static final Map<UUID, RemoteToolEntity> ENTITIES = new HashMap<>();
    private static final Map<UUID, Voice> VOICES = new HashMap<>();
    private static ClientWorld world;
    private static ClientPlayerEntity player;
    private static boolean initialized;
    private static volatile boolean magicSounds = true;
    private RemoteMagicAudio() {}

    public static void soundEnabled(boolean enabled) {
        magicSounds = enabled;
        if (!enabled) stopVoices(MinecraftClient.getInstance().getSoundManager());
    }

    public static void init() {
        if (initialized) return;
        initialized = true;
        ClientEntityEvents.ENTITY_LOAD.register((entity, loadedWorld) -> {
            if (entity instanceof RemoteToolEntity tool && loadedWorld == MinecraftClient.getInstance().world)
                ENTITIES.put(tool.getUuid(), tool);
        });
        ClientEntityEvents.ENTITY_UNLOAD.register((entity, unloadedWorld) -> {
            if (entity instanceof RemoteToolEntity tool && ENTITIES.remove(tool.getUuid(), tool)) {
                var voice = VOICES.remove(tool.getUuid());
                if (voice != null) voice.finish(MinecraftClient.getInstance().getSoundManager());
            }
        });
        ClientTickEvents.END_CLIENT_TICK.register(RemoteMagicAudio::tick);
        ClientPlayConnectionEvents.DISCONNECT.register((handler, client) -> clear(client));
        ClientLifecycleEvents.CLIENT_STOPPING.register(RemoteMagicAudio::clear);
        ResourceManagerHelper.get(ResourceType.CLIENT_RESOURCES).registerReloadListener(new SimpleSynchronousResourceReloadListener() {
            @Override public Identifier getFabricId() { return new Identifier("magicaland_gameplay", "remote_magic_audio"); }
            @Override public void reload(ResourceManager manager) {
                var client = MinecraftClient.getInstance();
                client.execute(() -> clear(client));
            }
        });
    }

    private static void tick(MinecraftClient client) {
        if (world != client.world || player != client.player) {
            clear(client);
            world = client.world; player = client.player;
            if (world != null) for (var entity : world.getEntities())
                if (entity instanceof RemoteToolEntity tool) ENTITIES.put(tool.getUuid(), tool);
        }
        if (world == null || player == null || !player.isAlive() || !magicSounds
                || client.options.getSoundVolume(SoundCategory.MASTER) <= 0
                || client.options.getSoundVolume(SoundCategory.PLAYERS) <= 0) {
            stopVoices(client.getSoundManager()); return;
        }
        if (client.isPaused()) return;
        var listener = client.gameRenderer.getCamera().getPos();
        var sources = new ArrayList<RemoteMagicAudioMix.Source>();
        ENTITIES.values().removeIf(tool -> tool.isRemoved() || tool.getWorld() != world);
        for (var tool : ENTITIES.values()) {
            if (!eligible(client, tool)) continue;
            double distance = tool.getPos().add(0, .1, 0).distanceTo(listener);
            sources.add(new RemoteMagicAudioMix.Source(tool.getUuid(), distance));
        }
        var selected = RemoteMagicAudioMix.select(sources, VOICES.keySet());
        var selectedIds = new HashSet<UUID>();
        for (var source : selected) selectedIds.add(source.id());
        var sounds = client.getSoundManager();
        var iterator = VOICES.entrySet().iterator();
        while (iterator.hasNext()) {
            var entry = iterator.next();
            var voice = entry.getValue();
            if (!selectedIds.contains(entry.getKey()) || ENTITIES.get(entry.getKey()) != voice.entity
                    || voice.isDone() || voice.age >= 20 && voice.age % 20 == 0 && !sounds.isPlaying(voice)) {
                voice.finish(sounds); iterator.remove();
            }
        }
        float volume = RemoteMagicAudioMix.volume(selected);
        for (var source : selected) {
            var voice = VOICES.get(source.id());
            if (voice == null) {
                voice = new Voice(ENTITIES.get(source.id()));
                voice.update(volume); VOICES.put(source.id(), voice); sounds.play(voice);
            } else voice.update(volume);
        }
    }

    private static boolean eligible(MinecraftClient client, RemoteToolEntity tool) {
        if (tool == null || tool.owner() == null || tool.isRemoved() || !tool.isAlive() || tool.getWorld() != client.world
                || client.world.getEntityById(tool.getId()) != tool) return false;
        var owner = client.world.getPlayerByUuid(tool.owner());
        return owner == null || owner.isAlive();
    }

    private static void stopVoices(SoundManager sounds) {
        for (var voice : VOICES.values()) voice.finish(sounds);
        VOICES.clear();
    }

    private static void clear(MinecraftClient client) {
        stopVoices(client.getSoundManager()); ENTITIES.clear(); world = null; player = null;
    }

    private static final class Voice extends MovingSoundInstance {
        final RemoteToolEntity entity;
        int age, unattended;
        Voice(RemoteToolEntity entity) {
            super(AURA, SoundCategory.PLAYERS, SoundInstance.createRandom());
            this.entity = entity;
            repeat = true; repeatDelay = 0; pitch = 1; volume = 0;
            relative = false; attenuationType = AttenuationType.LINEAR;
        }
        void update(float targetVolume) {
            age++; unattended = 0;
            x = entity.getX(); y = entity.getY() + .1; z = entity.getZ();
            volume += (targetVolume - volume) * .25f;
        }
        @Override public boolean shouldAlwaysPlay() { return true; }
        @Override public boolean canPlay() {
            var client = MinecraftClient.getInstance();
            return magicSounds && !isDone() && client.player != null && client.player.isAlive() && eligible(client, entity);
        }
        @Override public void tick() {
            var client = MinecraftClient.getInstance();
            if (!canPlay()) { setDone(); return; }
            if (client.isPaused()) return;
            if (++unattended > 3) setDone();
        }
        void finish(SoundManager sounds) { setDone(); volume = 0; sounds.stop(this); }
    }
}
