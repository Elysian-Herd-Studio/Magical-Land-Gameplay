package top.csituka.magicaland.gameplay.client;

import net.fabricmc.fabric.api.client.rendering.v1.WorldRenderEvents;
import net.minecraft.client.MinecraftClient;
import net.minecraft.util.math.MathHelper;
import top.csituka.magicaland.gameplay.remote.RemoteToolEntity;

final class RemoteBodyRenderer {
    private RemoteBodyRenderer() {}
    static void register() {
        WorldRenderEvents.AFTER_ENTITIES.register(context -> {
            var client=MinecraftClient.getInstance();
            var player=client.player;
            if (player==null || !(context.camera().getFocusedEntity() instanceof RemoteToolEntity tool)
                    || tool.isRemoved() || !player.getUuid().equals(tool.owner()) || context.consumers()==null) return;
            var renderer=client.getEntityRenderDispatcher();
            var eye=context.camera().getPos();
            if (context.frustum()!=null && !renderer.shouldRender(player,context.frustum(),eye.x,eye.y,eye.z)) return;
            float delta=context.tickDelta();
            // 原版会跳过不是当前摄像机的本地玩家；仅在自己的投影视角补绘本体。
            renderer.render(player,MathHelper.lerp(delta,player.lastRenderX,player.getX())-eye.x,
                    MathHelper.lerp(delta,player.lastRenderY,player.getY())-eye.y,
                    MathHelper.lerp(delta,player.lastRenderZ,player.getZ())-eye.z,
                    MathHelper.lerpAngleDegrees(delta,player.prevYaw,player.getYaw()),delta,
                    context.matrixStack(),context.consumers(),renderer.getLight(player,delta));
        });
    }
}
