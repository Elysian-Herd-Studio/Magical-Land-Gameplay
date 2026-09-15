package top.elysianherd.magicaland.gameplay.client.pegasus;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.Iterator;
import java.util.Map;
import java.util.UUID;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayConnectionEvents;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking;
import net.fabricmc.fabric.api.client.rendering.v1.WorldRenderContext;
import net.fabricmc.fabric.api.client.rendering.v1.WorldRenderEvents;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.render.RenderLayer;
import net.minecraft.client.render.VertexConsumer;
import net.minecraft.client.render.VertexFormat;
import net.minecraft.client.render.VertexFormats;
import net.minecraft.client.sound.MovingSoundInstance;
import net.minecraft.client.sound.SoundInstance;
import net.minecraft.client.world.ClientWorld;
import net.minecraft.sound.SoundCategory;
import net.minecraft.sound.SoundEvents;
import net.minecraft.util.math.MathHelper;
import net.minecraft.util.math.Vec3d;
import org.joml.Matrix4f;
import top.elysianherd.magicaland.gameplay.config.GameplayClientConfig;
import top.elysianherd.magicaland.gameplay.pegasus.PegasusFlightMath;
import top.elysianherd.magicaland.gameplay.pegasus.PegasusFlightMath.Mode;
import top.elysianherd.magicaland.gameplay.pegasus.PegasusFlightProtocol;

public final class PegasusFlightEffects {
    public record Shake(float pitch, float yaw, float roll) { static final Shake NONE = new Shake(0, 0, 0); }
    private record Sample(Vec3d left, Vec3d right, long tick, float strength) {}
    private record Impact(Vec3d position, float strength, float radius, long tick) {}
    private static final Map<UUID, ArrayDeque<Sample>> TRAILS = new HashMap<>();
    private static final ArrayDeque<Impact> IMPACTS = new ArrayDeque<>();
    private static ClientWorld world;
    private static Wind wind;
    private static long ticks;
    private static boolean initialized;
    private PegasusFlightEffects() {}

    public static void init() {
        if (initialized) return;
        initialized = true;
        ClientPlayConnectionEvents.JOIN.register((handler, sender, client) -> clear());
        ClientPlayConnectionEvents.DISCONNECT.register((handler, client) -> clear());
        ClientPlayNetworking.registerGlobalReceiver(PegasusFlightProtocol.IMPACT, (client, handler, buffer, sender) -> {
            try {
                var impact = PegasusFlightProtocol.readImpact(buffer);
                client.execute(() -> {
                    if (client.getNetworkHandler() != handler || client.world == null
                            || !client.world.getRegistryKey().getValue().toString().equals(impact.dimension())) return;
                    if (world != client.world) { clear(); world = client.world; }
                    if (IMPACTS.size() >= 16) IMPACTS.removeFirst();
                    IMPACTS.addLast(new Impact(new Vec3d(impact.x(), impact.y(), impact.z()),
                            MathHelper.clamp(impact.strength(), 0, 1), MathHelper.clamp(impact.radius(), 1, 64), ticks));
                });
            } catch (RuntimeException ignored) { }
        });
        ClientTickEvents.END_CLIENT_TICK.register(PegasusFlightEffects::tick);
        WorldRenderEvents.AFTER_ENTITIES.register(PegasusFlightEffects::render);
    }

    private static void clear() {
        if (wind != null) wind.finish();
        wind = null; world = null; ticks = 0; TRAILS.clear(); IMPACTS.clear();
    }

    private static void tick(MinecraftClient client) {
        if (client.world != world) { clear(); world = client.world; }
        if (world == null || client.player == null || client.isPaused()) return;
        ticks++;
        IMPACTS.removeIf(impact -> ticks - impact.tick() > 20);
        for (var player : world.getPlayers()) {
            if (TRAILS.size() >= 128 && !TRAILS.containsKey(player.getUuid())) continue;
            var visual = PegasusFlightClient.visual(player.getUuid(), 1);
            if (visual == null || visual.mode() == Mode.OFF || player.isRemoved() || !player.isAlive()
                    || player.isInvisible() || player.squaredDistanceTo(client.player) > 128 * 128) continue;
            float strength = speedStrength(visual.velocity().length());
            if (strength <= 0) continue;
            var rotation = visual.attitude();
            var right = new org.joml.Quaternionf(rotation.x(), rotation.y(), rotation.z(), rotation.w())
                    .transform(new org.joml.Vector3f(.75f, 0, 0));
            Vec3d center = player.getPos().add(0, player.getHeight() * .55, 0);
            Vec3d side = new Vec3d(right.x, right.y, right.z);
            var samples = TRAILS.computeIfAbsent(player.getUuid(), id -> new ArrayDeque<>());
            if (!samples.isEmpty() && samples.getLast().left().squaredDistanceTo(center.add(side)) > 64) samples.clear();
            samples.addLast(new Sample(center.add(side), center.subtract(side), ticks, strength));
            while (samples.size() > 18) samples.removeFirst();
        }
        for (Iterator<Map.Entry<UUID, ArrayDeque<Sample>>> iterator = TRAILS.entrySet().iterator(); iterator.hasNext();) {
            var entry = iterator.next();
            entry.getValue().removeIf(sample -> ticks - sample.tick() > 18);
            var player = world.getPlayerByUuid(entry.getKey());
            if (entry.getValue().isEmpty() || player == null || player.isInvisible() || !player.isAlive()) iterator.remove();
        }
        var local = PegasusFlightClient.visual(client.player.getUuid(), 1);
        boolean audible = local != null && local.mode() != Mode.OFF && speedStrength(local.velocity().length()) > .02
                && GameplayClientConfig.flightWindVolume() > 0;
        if (audible && wind == null) { wind = new Wind(client); client.getSoundManager().play(wind); }
        if (wind != null && wind.isDone()) wind = null;
    }

    private static float speedStrength(double speed) {
        return (float) MathHelper.clamp((speed - .45) / (PegasusFlightMath.MAX_SPEED - .45), 0, 1);
    }

    public static Shake shake(float delta) {
        var client = MinecraftClient.getInstance();
        float setting = GameplayClientConfig.flightShakeStrength();
        if (world == null || client.world != world || client.player == null || setting <= 0 || client.isPaused()) return Shake.NONE;
        double time = ticks + MathHelper.clamp(delta, 0, 1), amplitude = 0;
        for (var impact : IMPACTS) {
            double age = time - impact.tick();
            double distance = client.player.getEyePos().distanceTo(impact.position());
            if (age < 0 || age >= 20 || distance >= impact.radius()) continue;
            double fade = 1 - age / 20, near = 1 - distance / impact.radius();
            amplitude += impact.strength() * near * near * fade * fade * 2.4;
        }
        var visual = PegasusFlightClient.visual(client.player.getUuid(), delta);
        if (visual != null && visual.mode() != Mode.OFF) amplitude += speedStrength(visual.velocity().length()) * .12;
        amplitude = Math.min(3, amplitude) * setting;
        return new Shake((float) (Math.sin(time * 2.7) * amplitude),
                (float) (Math.sin(time * 3.9 + .8) * amplitude * .55),
                (float) (Math.sin(time * 2.1 + 1.7) * amplitude * .35));
    }

    private static void render(WorldRenderContext context) {
        var client = MinecraftClient.getInstance();
        if (world == null || client.world != world || client.player == null || context.consumers() == null) return;
        var matrix = context.matrixStack().peek().getPositionMatrix();
        var camera = context.camera().getPos();
        var vertices = context.consumers().getBuffer(TrailLayer.GLOW);
        for (var entry : TRAILS.entrySet()) {
            if (entry.getKey().equals(client.player.getUuid()) && client.options.getPerspective().isFirstPerson()) continue;
            var points = new ArrayList<>(entry.getValue());
            for (int i = 1; i < points.size(); i++) {
                Sample a = points.get(i - 1), b = points.get(i);
                if (b.tick() - a.tick() > 2) continue;
                float fade = MathHelper.clamp(1 - (ticks + context.tickDelta() - a.tick()) / 18f, 0, 1);
                float opacity = b.strength() * fade * .34f;
                ribbon(vertices, matrix, camera, a.left(), b.left(), opacity, .035f + b.strength() * .055f);
                ribbon(vertices, matrix, camera, a.right(), b.right(), opacity, .035f + b.strength() * .055f);
            }
        }
        for (var impact : IMPACTS) {
            float age = (ticks + context.tickDelta() - impact.tick()) / 20f;
            if (age < 0 || age >= 1) continue;
            double radius = .5 + Math.sqrt(age) * (impact.radius() / 4 - .5);
            Vec3d center = impact.position().add(0, .06, 0);
            for (int i = 0; i < 48; i++) {
                double a = i * Math.PI / 24, b = (i + 1) * Math.PI / 24;
                ribbon(vertices, matrix, camera, center.add(Math.cos(a) * radius, 0, Math.sin(a) * radius),
                        center.add(Math.cos(b) * radius, 0, Math.sin(b) * radius),
                        (1 - age) * impact.strength() * .55f, .05f + (1 - age) * .12f);
            }
        }
    }

    private static void ribbon(VertexConsumer vertices, Matrix4f matrix, Vec3d camera, Vec3d from, Vec3d to,
                               float alpha, float width) {
        Vec3d direction = to.subtract(from);
        if (alpha <= .001 || direction.lengthSquared() < .000001) return;
        Vec3d normal = direction.crossProduct(camera.subtract(from));
        if (normal.lengthSquared() < .000001) normal = direction.crossProduct(new Vec3d(0, 1, 0));
        if (normal.lengthSquared() < .000001) normal = new Vec3d(1, 0, 0);
        normal = normal.normalize().multiply(width);
        for (int layer = 0; layer < 2; layer++) {
            Vec3d side = normal.multiply(layer == 0 ? 2.5 : 1);
            float opacity = alpha * (layer == 0 ? .22f : 1);
            vertex(vertices, matrix, from.add(side).subtract(camera), opacity);
            vertex(vertices, matrix, to.add(side).subtract(camera), opacity);
            vertex(vertices, matrix, to.subtract(side).subtract(camera), opacity);
            vertex(vertices, matrix, from.subtract(side).subtract(camera), opacity);
        }
    }

    private static void vertex(VertexConsumer vertices, Matrix4f matrix, Vec3d point, float alpha) {
        vertices.vertex(matrix, (float) point.x, (float) point.y, (float) point.z).color(.82f, .93f, 1, alpha).next();
    }

    private static final class Wind extends MovingSoundInstance {
        private final MinecraftClient client;
        private final ClientWorld source;
        Wind(MinecraftClient client) {
            super(SoundEvents.ITEM_ELYTRA_FLYING, SoundCategory.PLAYERS, SoundInstance.createRandom());
            this.client = client; source = client.world;
            repeat = true; repeatDelay = 0; relative = true; attenuationType = AttenuationType.NONE; volume = .001f;
        }
        @Override public boolean shouldAlwaysPlay() { return true; }
        @Override public void tick() {
            if (client.world != source || client.player == null || !client.player.isAlive()) { finish(); return; }
            var visual = PegasusFlightClient.visual(client.player.getUuid(), 1);
            float target = visual == null || visual.mode() == Mode.OFF ? 0
                    : speedStrength(visual.velocity().length()) * GameplayClientConfig.flightWindVolume() * .45f;
            volume += (target - volume) * .15f;
            pitch = .75f + (visual == null ? 0 : speedStrength(visual.velocity().length())) * .55f;
            if (target <= 0 && volume < .002) finish();
        }
        void finish() { setDone(); }
    }

    private static final class TrailLayer extends RenderLayer {
        static final RenderLayer GLOW = of("magicaland_pegasus_wind", VertexFormats.POSITION_COLOR,
                VertexFormat.DrawMode.QUADS, 8192, false, true, MultiPhaseParameters.builder()
                        .program(COLOR_PROGRAM).transparency(TRANSLUCENT_TRANSPARENCY).depthTest(LEQUAL_DEPTH_TEST)
                        .writeMaskState(COLOR_MASK).cull(DISABLE_CULLING).build(false));
        private TrailLayer() { super("magicaland_unused", VertexFormats.POSITION_COLOR, VertexFormat.DrawMode.QUADS,
                256, false, true, () -> {}, () -> {}); }
    }
}
