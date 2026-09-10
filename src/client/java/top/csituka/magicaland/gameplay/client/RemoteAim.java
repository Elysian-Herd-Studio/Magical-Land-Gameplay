package top.csituka.magicaland.gameplay.client;

import net.fabricmc.fabric.api.client.rendering.v1.WorldRenderContext;
import net.fabricmc.fabric.api.client.rendering.v1.WorldRenderEvents;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.option.Perspective;
import net.minecraft.client.world.ClientWorld;
import top.csituka.magicaland.gameplay.remote.RemoteToolEntity;

public final class RemoteAim {
    private record Frame(ClientWorld world, RemoteToolEntity tool, Perspective perspective, RemoteAimMath.Point point) {}
    private static Frame frame;
    private static boolean initialized;
    private RemoteAim() {}

    public static void init() {
        if (initialized) return;
        initialized=true;
        WorldRenderEvents.START.register(RemoteAim::capture);
    }

    public static void clear() { frame=null; }

    private static void capture(WorldRenderContext context) {
        clear();
        var client=MinecraftClient.getInstance();
        var tool=RemoteToolClient.camera();
        if (!RemoteToolClient.controlling() || tool==null || client.getCameraEntity()!=tool
                || client.world!=tool.getWorld() || client.crosshairTarget==null || tool.isRemoved()) return;
        var target=client.crosshairTarget.getPos();
        var camera=context.camera().getPos();
        // 世界矩阵包含实际拉远后的镜头，投影保留受伤/视角晃动，射线仍由原逻辑提供。
        var point=RemoteAimMath.project(context.matrixStack().peek().getPositionMatrix(),context.projectionMatrix(),
                target.x,target.y,target.z,camera.x,camera.y,camera.z);
        frame=new Frame(client.world,tool,client.options.getPerspective(),point);
    }

    public static RemoteAimMath.Point point() {
        var client=MinecraftClient.getInstance();
        var current=frame;
        return current!=null && current.world==client.world && current.tool==RemoteToolClient.camera()
                && current.tool==client.getCameraEntity() && current.perspective==client.options.getPerspective()
                ? current.point : null;
    }
}
