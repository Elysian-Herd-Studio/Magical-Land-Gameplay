package top.csituka.magicaland.gameplay.client.echo;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import com.mojang.blaze3d.systems.RenderSystem;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientLifecycleEvents;
import net.fabricmc.fabric.api.resource.ResourceManagerHelper;
import net.fabricmc.fabric.api.resource.SimpleSynchronousResourceReloadListener;
import net.fabricmc.loader.api.FabricLoader;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.util.math.MatrixStack;
import net.minecraft.resource.ResourceManager;
import net.minecraft.resource.ResourceType;
import net.minecraft.util.Identifier;
import net.minecraft.util.math.Vec3d;
import org.joml.Matrix4f;
import org.joml.Vector3f;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import top.csituka.magicaland.api.client.Appearances;
import top.csituka.magicaland.gameplay.client.RemoteToolClient;
import top.csituka.magicaland.gameplay.client.RemoteVisualMath;
import top.csituka.magicaland.gameplay.remote.RemoteCapabilities;

public final class SpiritualEchoRenderer {
    private static final Logger LOGGER = LoggerFactory.getLogger("magicaland_gameplay/spiritual_echo");
    private static final SpiritualEchoTimeline TIMELINE = new SpiritualEchoTimeline();
    private record Frame(Object world, Object entity, Matrix4f inverseClip, Vec3d camera,
                         SpiritualEchoTimeline.Pulse pulse, SpiritualEchoTimeline.Pulse previous) {}
    private static Frame frame;
    private static Object world, entity;
    private static SpiritualEchoFilter filter;
    private static boolean initialized, disabled, logged;
    private static volatile boolean reloadPending;

    private SpiritualEchoRenderer() {}

    public static void init() {
        if (initialized) return;
        initialized = true;
        ResourceManagerHelper.get(ResourceType.CLIENT_RESOURCES).registerReloadListener(new SimpleSynchronousResourceReloadListener() {
            @Override public Identifier getFabricId() { return new Identifier("magicaland_gameplay", "spiritual_echo"); }
            @Override public void reload(ResourceManager manager) { reset(); }
        });
        ClientLifecycleEvents.CLIENT_STOPPING.register(client -> reset());
    }

    public static void reset() {
        reloadPending = true;
        if (RenderSystem.isOnRenderThread()) release();
        else RenderSystem.recordRenderCall(SpiritualEchoRenderer::release);
    }

    private static void release() {
        frame = null; world = entity = null; TIMELINE.reset();
        if (filter != null) { filter.close(); filter = null; }
    }

    public static void beginFrame() {
        frame = null;
        if (filter != null) filter.beginFrame();
    }

    public static void prepareWorld(MatrixStack matrices, float delta) {
        var client = MinecraftClient.getInstance();
        var tool = RemoteToolClient.camera();
        if (reloadPending || world != client.world || entity != tool) {
            release(); disabled = logged = reloadPending = false;
            world = client.world; entity = tool;
        }
        if (!RemoteCapabilities.SPIRITUAL_ECHO || !RemoteToolClient.controlling() || tool == null
                || tool != client.getCameraEntity() || client.world == null || client.player == null) return;
        var origin = tool.getCameraPosVec(delta);
        var pulse = TIMELINE.update((client.world.getTime() + RemoteVisualMath.clamp(delta)) / 20.0,
                RemoteToolClient.occlusion(), origin.x, origin.y, origin.z);
        if (pulse == null || !pulse.visible() || disabled) return;
        var inverse = new Matrix4f(RenderSystem.getProjectionMatrix()).mul(matrices.peek().getPositionMatrix()).invert();
        for (float value : inverse.get(new float[16])) if (!Float.isFinite(value)) return;
        frame = new Frame(client.world, tool, inverse, client.gameRenderer.getCamera().getPos(), pulse, TIMELINE.previous());
    }

    public static void captureDepth() {
        var client = MinecraftClient.getInstance();
        if (!current(client) || disabled) return;
        try {
            var loader = FabricLoader.getInstance();
            if (loader.isModLoaded("iris") || loader.isModLoaded("oculus") || loader.isModLoaded("optifabric")) {
                disable("Shader loader detected; spiritual echo needs a compatible world depth buffer", null); return;
            }
            var target = client.getFramebuffer();
            if (target.textureWidth <= 0 || target.textureHeight <= 0
                    || client.getWindow().getFramebufferWidth() <= 0 || client.getWindow().getFramebufferHeight() <= 0) return;
            if (filter == null) filter = new SpiritualEchoFilter(shader(client, "vsh"), shader(client, "fsh"));
            if (!filter.captureDepth(target.fbo, target.textureWidth, target.textureHeight))
                disable("Unsupported world depth buffer; spiritual echo disabled safely", null);
        } catch (IOException | RuntimeException error) {
            disable("Could not capture spiritual echo depth", error);
        }
    }

    public static void render(float occlusion) {
        var client = MinecraftClient.getInstance();
        if (disabled || filter == null || !current(client) || occlusion <= 0 || RemoteToolClient.returning()) return;
        try {
            var target = client.getFramebuffer();
            var pulse = frame.pulse;
            var origin = new Vector3f((float)(pulse.x() - frame.camera.x), (float)(pulse.y() - frame.camera.y),
                    (float)(pulse.z() - frame.camera.z));
            var previous = frame.previous;
            var previousOrigin = previous == null ? null : new Vector3f((float)(previous.x() - frame.camera.x),
                    (float)(previous.y() - frame.camera.y), (float)(previous.z() - frame.camera.z));
            if (!filter.render(target.fbo, target.textureWidth, target.textureHeight, frame.inverseClip, origin,
                    pulse.phase(), previousOrigin, previous == null ? -1 : previous.phase(),
                    occlusion, Appearances.magicColor(client.player.getUuid())))
                disable("Unsupported echo compositing target; world rendering was left intact", null);
        } catch (RuntimeException error) {
            disable("Could not draw spiritual echo", error);
        }
    }

    public static boolean unavailable() { return disabled; }

    private static boolean current(MinecraftClient client) {
        return frame != null && client.player != null && client.world == frame.world
                && client.getCameraEntity() == frame.entity && frame.entity == RemoteToolClient.camera()
                && RemoteToolClient.controlling();
    }

    private static String shader(MinecraftClient client, String extension) throws IOException {
        var id = new Identifier("magicaland_gameplay", "shaders/spiritual_echo." + extension);
        try (var stream = client.getResourceManager().getResourceOrThrow(id).getInputStream()) {
            byte[] bytes = stream.readNBytes(65_537);
            if (bytes.length > 65_536) throw new IOException("Oversized spiritual echo shader");
            return new String(bytes, StandardCharsets.UTF_8);
        }
    }

    private static void disable(String reason, Exception error) {
        disabled = true;
        frame = null;
        if (filter != null) { filter.close(); filter = null; }
        if (!logged) { LOGGER.warn(reason, error); logged = true; }
    }
}
