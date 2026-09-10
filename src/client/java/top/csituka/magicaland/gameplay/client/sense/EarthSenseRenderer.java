package top.csituka.magicaland.gameplay.client.sense;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;
import com.mojang.blaze3d.systems.RenderSystem;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayConnectionEvents;
import net.fabricmc.fabric.api.client.rendering.v1.HudRenderCallback;
import net.fabricmc.fabric.api.resource.ResourceManagerHelper;
import net.fabricmc.fabric.api.resource.SimpleSynchronousResourceReloadListener;
import net.fabricmc.loader.api.FabricLoader;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.render.RenderLayer;
import net.minecraft.client.render.VertexConsumer;
import net.minecraft.client.util.math.MatrixStack;
import net.minecraft.resource.ResourceManager;
import net.minecraft.resource.ResourceType;
import net.minecraft.text.Text;
import net.minecraft.util.Identifier;
import net.minecraft.util.math.Vec3d;
import org.joml.Matrix4f;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import top.csituka.magicaland.gameplay.config.GameplayClientConfig;

public final class EarthSenseRenderer {
    private static final Logger LOGGER = LoggerFactory.getLogger("magicaland_gameplay/earth_sense");
    private static final EarthSenseVisualMath.Clouds CLOUDS = new EarthSenseVisualMath.Clouds();
    private static final EarthSenseVisualMath.Placements PLACEMENTS = new EarthSenseVisualMath.Placements();
    private static List<EarthSenseVisualMath.Cloud> clouds = List.of();
    private static List<EarthSenseVisualMath.Blob> frameBlobs = List.of();
    private static Matrix4f worldClip;
    private static Matrix4f worldView;
    private static Vec3d worldCamera;
    private static Object capturedWorld;
    private static EarthSenseFilter filter;
    private static Object world;
    private static boolean initialized, disabled, logged;
    private static volatile boolean reloadPending;

    private EarthSenseRenderer() {}

    public static void init() {
        if (initialized) return;
        initialized = true;
        HudRenderCallback.EVENT.register(EarthSenseRenderer::renderHud);
        ClientPlayConnectionEvents.DISCONNECT.register((handler, client) -> reset());
        ResourceManagerHelper.get(ResourceType.CLIENT_RESOURCES).registerReloadListener(new SimpleSynchronousResourceReloadListener() {
            @Override public Identifier getFabricId() { return new Identifier("magicaland_gameplay", "earth_sense"); }
            @Override public void reload(ResourceManager manager) { requestRelease(); }
        });
    }

    /** Client/render thread. Exiting normally uses EarthSenseClient's opacity fade instead. */
    public static void reset() {
        CLOUDS.clear(); PLACEMENTS.clear(); clouds = List.of(); beginFrame();
        world = null;
        requestRelease();
    }

    public static void beginFrame() { worldClip = null; worldView = null; worldCamera = null; capturedWorld = null; frameBlobs = List.of(); }

    public static void captureWorld(MatrixStack matrices, float delta) {
        var client = MinecraftClient.getInstance();
        if (client.world == null || EarthSenseClient.opacity(delta) <= 0) return;
        worldView = new Matrix4f(matrices.peek().getPositionMatrix());
        worldClip = new Matrix4f(RenderSystem.getProjectionMatrix()).mul(worldView);
        worldCamera = client.gameRenderer.getCamera().getPos();
        capturedWorld = client.world;
    }

    private static void requestRelease() {
        reloadPending = true;
        if (RenderSystem.isOnRenderThread()) release();
        else RenderSystem.recordRenderCall(EarthSenseRenderer::release);
    }

    public static void renderWorld(float delta) {
        MinecraftClient client = MinecraftClient.getInstance();
        try {
            if (reloadPending || client.world != world) {
                release();
                CLOUDS.clear(); PLACEMENTS.clear(); clouds = List.of();
                disabled = logged = false;
                reloadPending = false;
                world = client.world;
            }
            if (client.world == null || client.player == null) return;
            float opacity = EarthSenseVisualMath.clamp(EarthSenseClient.opacity(delta));
            if (opacity <= 0) { CLOUDS.clear(); PLACEMENTS.clear(); clouds = List.of(); release(); return; }
            CLOUDS.epoch(EarthSenseClient.signalEpoch(), EarthSenseClient.focusing());
            var targets = new ArrayList<EarthSenseVisualMath.Target>();
            for (var signal : EarthSenseClient.signals()) {
                if (targets.size() >= 32) break;
                targets.add(new EarthSenseVisualMath.Target(signal.id(), signal.kind(), signal.x(), signal.y(), signal.z(),
                        signal.width(), signal.height(), signal.activity(), signal.strength(), signal.pulse()));
            }
            clouds = CLOUDS.update(targets, client.world.getTime() + EarthSenseVisualMath.clamp(delta));
            PLACEMENTS.begin(EarthSenseClient.signalEpoch(), EarthSenseClient.focusing(),
                    client.world.getTime() + EarthSenseVisualMath.clamp(delta), clouds);
            var blobs = new ArrayList<EarthSenseVisualMath.Blob>();
            float signalOpacity = opacity * EarthSenseClient.signalQuality(delta);
            var ordered = new ArrayList<>(clouds);
            if (worldCamera != null) ordered.sort(java.util.Comparator.comparingDouble((EarthSenseVisualMath.Cloud c) ->
                    worldCamera.squaredDistanceTo(c.target().x(), c.target().y(), c.target().z())).reversed());
            for (var cloud : ordered) {
                var blob = EarthSenseVisualMath.blob(PLACEMENTS.apply(cloud.target().id(), project(cloud)), cloud, signalOpacity);
                if (blob != null) blobs.add(blob);
            }
            frameBlobs = blobs;
            float amount = opacity * EarthSenseVisualMath.clamp(GameplayClientConfig.earthSenseFilterStrength());
            float blur = opacity * EarthSenseVisualMath.clamp(GameplayClientConfig.earthSenseBlurStrength());
            if (disabled) return;
            // A conservative fallback: shader-loader presence, even with its pack disabled, keeps only the HUD.
            var loader = FabricLoader.getInstance();
            if (loader.isModLoaded("iris") || loader.isModLoaded("oculus") || loader.isModLoaded("optifabric")) {
                disable("Shader loader detected; soft HUD clouds remain available", null);
                return;
            }
            var target = client.getFramebuffer();
            if (target.textureWidth <= 0 || target.textureHeight <= 0 || client.getWindow().getFramebufferWidth() <= 0
                    || client.getWindow().getFramebufferHeight() <= 0) { release(); return; }
            if (filter == null) filter = new EarthSenseFilter(shader(client, "", "vsh"), shader(client, "", "fsh"),
                    shader(client, "_blob", "vsh"), shader(client, "_blob", "fsh"));
            if (amount == 0 && blur == 0 && blobs.isEmpty()) return;
            if (!filter.render(target.fbo, target.textureWidth, target.textureHeight, amount, blur, blobs))
                disable("Unsupported world framebuffer; soft HUD clouds remain available", null);
        } catch (IOException | RuntimeException error) {
            disable("Earth sense filter disabled safely; soft HUD clouds remain available", error);
        }
    }

    private static EarthSenseVisualMath.Projected project(EarthSenseVisualMath.Cloud cloud) {
        if (worldClip == null || worldCamera == null || capturedWorld != MinecraftClient.getInstance().world) return null;
        var target = cloud.target();
        return EarthSenseVisualMath.place(worldClip, worldView, target.x() - worldCamera.x, target.y() - worldCamera.y,
                target.z() - worldCamera.z, target.width(), target.height());
    }

    private static String shader(MinecraftClient client, String suffix, String extension) throws IOException {
        var id = new Identifier("magicaland_gameplay", "shaders/earth_sense" + suffix + "." + extension);
        try (var stream = client.getResourceManager().getResourceOrThrow(id).getInputStream()) {
            byte[] bytes = stream.readNBytes(65_537);
            if (bytes.length > 65_536) throw new IOException("Oversized earth sense shader");
            return new String(bytes, StandardCharsets.UTF_8);
        }
    }

    private static void disable(String reason, Exception error) {
        disabled = true;
        release();
        if (!logged) {
            logged = true;
            if (error == null) LOGGER.warn(reason); else LOGGER.warn(reason, error);
        }
    }

    private static void release() {
        EarthSenseFilter previous = filter;
        filter = null;
        if (previous != null) previous.close();
    }

    public static void renderHud(DrawContext context, float delta) {
        MinecraftClient client = MinecraftClient.getInstance();
        if (client.world == null || client.player == null || client.options.hudHidden) return;
        float opacity = EarthSenseVisualMath.clamp(EarthSenseClient.opacity(delta));
        if (opacity <= 0) return;
        if (opacity < .02f) return; // Vanilla text treats alpha below four as fully opaque.
        int width = client.getWindow().getScaledWidth(), height = client.getWindow().getScaledHeight();
        if (width < 80 || height < 80) return;
        if (disabled) for (var blob : frameBlobs) softCloud(context, blob, width, height);
        status(context, client, opacity, width);
    }

    private static void status(DrawContext context, MinecraftClient client, float opacity, int width) {
        int color = color(opacity * .8f), y = 10, right = Math.min(width - 12, 290);
        Text status = Text.translatable("text.magicaland_gameplay.sense." + (EarthSenseClient.grounded() ? "active" : "grounding"));
        for (var line : client.textRenderer.wrapLines(status, right - 12)) {
            context.drawTextWithShadow(client.textRenderer, line, 12, y, color); y += 10;
        }
        if (disabled) {
            Text warning = Text.translatable("text.magicaland_gameplay.sense.filter_unavailable");
            for (var line : client.textRenderer.wrapLines(warning, right - 12)) {
                context.drawTextWithShadow(client.textRenderer, line, 12, y, color); y += 10;
            }
        }
    }

    private static int color(float alpha) { return (Math.round(EarthSenseVisualMath.clamp(alpha) * 255) << 24) | 0xF2F1E8; }
    private static void softCloud(DrawContext context, EarthSenseVisualMath.Blob blob, int width, int height) {
        VertexConsumer vertices = context.getVertexConsumers().getBuffer(RenderLayer.getGui());
        var matrix = context.getMatrices().peek().getPositionMatrix();
        for (int ring = 0; ring < 10; ring++) for (int segment = 0; segment < 24; segment++) {
            float inner = ring / 10f, outer = (ring + 1) / 10f;
            double first = segment * Math.PI / 12, second = (segment + 1) * Math.PI / 12;
            cloudVertex(vertices, matrix, blob, width, height, inner, first);
            cloudVertex(vertices, matrix, blob, width, height, outer, first);
            cloudVertex(vertices, matrix, blob, width, height, outer, second);
            cloudVertex(vertices, matrix, blob, width, height, inner, second);
        }
    }

    private static void cloudVertex(VertexConsumer vertices, Matrix4f matrix, EarthSenseVisualMath.Blob blob,
                                    int width, int height, float radius, double angle) {
        float qy = radius * (float) Math.sin(angle);
        float qx = radius * (float) Math.cos(angle) / (1 + .055f * (float) Math.sin(qy * 5 + blob.pulse()));
        float alpha = blob.alpha() * EarthSenseVisualMath.softness(radius, blob.activity(), blob.pulse());
        vertices.vertex(matrix, (blob.x() + qx * blob.radiusX()) * width,
                        (1 - blob.y() - qy * blob.radiusY()) * height, 0)
                .color(blob.red(), blob.green(), blob.blue(), alpha).next();
    }
}
