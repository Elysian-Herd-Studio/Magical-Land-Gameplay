package top.csituka.magicaland.gameplay.client.sense;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.zip.ZipFile;
import org.objectweb.asm.ClassReader;
import org.objectweb.asm.tree.ClassNode;
import org.objectweb.asm.tree.MethodInsnNode;

public final class EarthSenseRenderStructureTest {
    private static int checks;
    public static void main(String[] args) throws Exception {
        try (ZipFile mc = new ZipFile(args[0])) {
            var type = new ClassNode();
            new ClassReader(mc.getInputStream(mc.getEntry("net/minecraft/client/render/GameRenderer.class"))).accept(type, 0);
            var render = type.methods.stream().filter(m -> m.name.equals("render") && m.desc.equals("(FJZ)V")).findFirst().orElseThrow();
            int world = -1, post = -1, firstClear = -1, hud = -1, bind = -1;
            for (int i = 0; i < render.instructions.size(); i++) if (render.instructions.get(i) instanceof MethodInsnNode call) {
                if (call.owner.equals("net/minecraft/client/render/GameRenderer") && call.name.equals("renderWorld")) world = i;
                if (call.owner.equals("net/minecraft/client/gl/PostEffectProcessor") && call.name.equals("render")) post = i;
                if (call.owner.equals("net/minecraft/client/gl/Framebuffer") && call.name.equals("beginWrite") && firstClear < 0) bind = i;
                if (call.owner.equals("com/mojang/blaze3d/systems/RenderSystem") && call.name.equals("clear") && firstClear < 0) {
                    check(call.desc.equals("(IZ)V"), "exact injection descriptor"); firstClear = i;
                }
                if (call.owner.equals("net/minecraft/client/gui/hud/InGameHud") && call.name.equals("render")) hud = i;
            }
            check(world >= 0 && world < post && post < bind && bind < firstClear && firstClear < hud,
                    "world -> vanilla post -> main bind -> injection -> HUD in actual 1.20.1 bytecode");
            var worldRender = type.methods.stream().filter(m -> m.name.equals("renderWorld")
                    && m.desc.equals("(FJLnet/minecraft/client/util/math/MatrixStack;)V")).findFirst().orElseThrow();
            int capture = -1, hand = -1;
            for (int i = 0; i < worldRender.instructions.size(); i++) if (worldRender.instructions.get(i) instanceof MethodInsnNode call) {
                if (call.owner.equals("net/minecraft/client/render/WorldRenderer") && call.name.equals("render")) {
                    capture = i;
                    check(call.desc.equals("(Lnet/minecraft/client/util/math/MatrixStack;FJZLnet/minecraft/client/render/Camera;Lnet/minecraft/client/render/GameRenderer;Lnet/minecraft/client/render/LightmapTextureManager;Lorg/joml/Matrix4f;)V"), "exact world capture descriptor");
                }
                if (call.name.equals("renderHand")) hand = i;
            }
            check(capture >= 0 && hand > capture, "capture before hand changes projection");
        }
        Path repo = Path.of(args[1]);
        String renderer = Files.readString(repo.resolve("src/client/java/top/csituka/magicaland/gameplay/client/sense/EarthSenseRenderer.java"));
        String filter = Files.readString(repo.resolve("src/client/java/top/csituka/magicaland/gameplay/client/sense/EarthSenseFilter.java"));
        check(!renderer.contains("setPostProcessor") && !renderer.contains("loadPostProcessor") && !filter.contains("PostEffectProcessor"), "vanilla post processor never replaced");
        check(!filter.contains("GL_DEPTH_BUFFER_BIT") && !filter.contains("GL_STENCIL_BUFFER_BIT"), "filter never copies or clears world depth/stencil");
        check(!filter.contains("glClear("), "no scene clear");
        check(renderer.contains("ClientPlayConnectionEvents.DISCONNECT") && renderer.contains("requestRelease()"), "disconnect releases targets");
        check(renderer.contains("reloadPending") && renderer.contains("registerReloadListener"), "reload safely invalidates shader");
        check(renderer.contains("client.world != world"), "world replacement invalidates old visuals");
        check(renderer.contains("HudRenderCallback.EVENT.register") && renderer.contains("EarthSenseVisualMath.place(worldClip, worldView"), "world and edge clouds share actual camera projection");
        check(renderer.contains("targets.size() >= 32") && !renderer.contains("occupied[bearing]") && !renderer.contains("Math.floorMod"), "bounded clouds without quantized direction sectors");
        check(renderer.contains("isModLoaded(\"iris\")") && renderer.contains("soft HUD clouds remain available"), "conservative shader fallback");
        check(renderer.contains("if (disabled) for (var blob : frameBlobs)") && renderer.contains("softCloud(context, blob"), "fallback draws only when world pass disabled, never duplicate hints");
        check(!renderer.contains("sense.kind.") && !renderer.contains("circle(context") && !renderer.contains("legend(context"), "old legend and hard direction markers removed");
        check(renderer.contains("RenderLayer.getGui()") && renderer.contains("EarthSenseVisualMath.softness"), "fallback uses standard GUI rendering with transparent soft edge");
        check(!renderer.contains("getEntityById") && !renderer.contains("getOtherEntities") && !renderer.contains("playSound"), "only server-approved positions, no entity scan or sense sound");
        check(filter.contains("import static org.lwjgl.opengl.GL32C.*;") && filter.contains("if (samplersAvailable()) GL33C.glBindSampler"), "3.2 core and guarded optional sampler functions");
        check(renderer.contains("captureWorld") && renderer.contains("beginFrame()"), "same-frame world camera snapshot");
        check(renderer.contains("float signalOpacity = opacity * EarthSenseClient.signalQuality(delta)")
                && renderer.contains("cloud, signalOpacity)"), "movement quality fades sensed clouds");
        check(renderer.contains("float amount = opacity *") && renderer.contains("float blur = opacity *"),
                "movement does not repeatedly fade the focus filter");
        System.out.println("PASS earth sense rendering structure: " + checks);
    }
    private static void check(boolean value, String label) { checks++; if (!value) throw new AssertionError(label); }
}
