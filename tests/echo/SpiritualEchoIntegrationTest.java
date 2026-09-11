package top.csituka.magicaland.gameplay.client.echo;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.zip.ZipFile;
import org.objectweb.asm.ClassReader;
import org.objectweb.asm.tree.ClassNode;
import org.objectweb.asm.tree.MethodInsnNode;

public final class SpiritualEchoIntegrationTest {
    private static int checks;
    public static void main(String[] args) throws Exception {
        try (var mc = new ZipFile(args[0])) {
            var type = new ClassNode();
            new ClassReader(mc.getInputStream(mc.getEntry("net/minecraft/client/render/GameRenderer.class"))).accept(type, 0);
            var world = type.methods.stream().filter(m -> m.name.equals("renderWorld")
                    && m.desc.equals("(FJLnet/minecraft/client/util/math/MatrixStack;)V")).findFirst().orElseThrow();
            int rendered = -1, handClear = -1, hand = -1;
            for (int i = 0; i < world.instructions.size(); i++) if (world.instructions.get(i) instanceof MethodInsnNode call) {
                if (call.owner.equals("net/minecraft/client/render/WorldRenderer") && call.name.equals("render")) {
                    check(rendered == -1, "one exact world-render depth capture point"); rendered = i;
                    check(call.desc.equals("(Lnet/minecraft/client/util/math/MatrixStack;FJZLnet/minecraft/client/render/Camera;Lnet/minecraft/client/render/GameRenderer;Lnet/minecraft/client/render/LightmapTextureManager;Lorg/joml/Matrix4f;)V"), "world hook matches installed Minecraft");
                }
                if (rendered >= 0 && call.owner.equals("com/mojang/blaze3d/systems/RenderSystem") && call.name.equals("clear")) handClear = i;
                if (call.name.equals("renderHand")) hand = i;
            }
            check(rendered >= 0 && handClear > rendered && hand > handClear, "world depth captured before hand clears it");
            var render = type.methods.stream().filter(m -> m.name.equals("render") && m.desc.equals("(FJZ)V")).findFirst().orElseThrow();
            int scene = -1, gui = -1, hud = -1;
            for (int i = 0; i < render.instructions.size(); i++) if (render.instructions.get(i) instanceof MethodInsnNode call) {
                if (call.name.equals("renderWorld")) scene = i;
                if (call.name.equals("enableGuiDepthLighting")) gui = i;
                if (call.owner.equals("net/minecraft/client/gui/hud/InGameHud") && call.name.equals("render")) hud = i;
            }
            check(scene >= 0 && gui > scene && hud > gui, "compositor runs after world but before HUD");
        }
        var repo = Path.of(args[1]);
        String client = "src/client/java/top/csituka/magicaland/gameplay/";
        String renderer = Files.readString(repo.resolve(client + "client/echo/SpiritualEchoRenderer.java"));
        String mixin = Files.readString(repo.resolve(client + "mixin/client/RemoteOverlayMixin.java"));
        String hud = Files.readString(repo.resolve(client + "client/RemoteToolHud.java"));
        String controls = Files.readString(repo.resolve(client + "client/RemoteToolClient.java"));
        String fragment = Files.readString(repo.resolve("src/client/resources/assets/magicaland_gameplay/shaders/spiritual_echo.fsh"));
        check(mixin.contains("shift=At.Shift.AFTER") && mixin.contains("SpiritualEchoRenderer.captureDepth()"), "depth captured immediately after world");
        check(mixin.contains("SpiritualEchoRenderer.beginFrame()") && renderer.contains("filter.beginFrame()"), "stale depth invalidated each frame");
        check(renderer.contains("client.world == frame.world") && renderer.contains("client.getCameraEntity() == frame.entity"), "frame belongs to current world and remote camera");
        check(renderer.contains("registerReloadListener") && renderer.contains("CLIENT_STOPPING") && hud.contains("SpiritualEchoRenderer.reset()"), "reload shutdown and session cleanup release targets");
        check(renderer.contains("getProjectionMatrix()") && renderer.contains(".invert()"), "world reconstruction uses actual camera matrices");
        check(renderer.contains("pulse.x() - frame.camera.x"), "pulse origin stays in world space during camera motion");
        check(renderer.contains("TIMELINE.previous()") && renderer.contains("previous.x() - frame.camera.x"),
                "previous emitted wave keeps its own world origin across cycle and camera changes");
        check(fragment.contains("max(pulseSignal(center, PulseOrigin, PhaseSeconds)")
                && fragment.contains("pulseSignal(center, PreviousPulseOrigin, PreviousPhaseSeconds)"),
                "adjacent wave tails share one depth pass without additive accumulation");
        check(!renderer.contains("loadPostProcessor") && !renderer.contains("setPostProcessor"), "vanilla post effect untouched");
        check(hud.contains("context.draw();\n        if (recall==0) SpiritualEchoRenderer.render(visualOcclusion);"), "echo composites over black overlay, never over return fade");
        check(controls.contains("RemoteCapabilities.SPIRITUAL_ECHO || camera.occlusion()<.999f"), "full-tier blind actions still animate");
        check(hud.contains("RemoteCapabilities.SPIRITUAL_ECHO ? 1"), "reticle remains visible during echo");
        check(hud.contains("RemoteCapabilities.MAX_RANGE*.85"), "distance hint follows actual range");
        check(fragment.contains("texture") && !fragment.contains("getBlockState"), "geometry shader has no block identity query");
        System.out.println("PASS SpiritualEchoIntegrationTest: " + checks);
    }
    private static void check(boolean condition, String label) {
        checks++; if (!condition) throw new AssertionError(label);
    }
}
