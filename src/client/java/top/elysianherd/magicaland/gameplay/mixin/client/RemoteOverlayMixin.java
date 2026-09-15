package top.elysianherd.magicaland.gameplay.mixin.client;

import net.minecraft.client.MinecraftClient;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.render.GameRenderer;
import net.minecraft.client.util.math.MatrixStack;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import top.elysianherd.magicaland.gameplay.client.RemoteToolClient;
import top.elysianherd.magicaland.gameplay.client.RemoteToolHud;
import top.elysianherd.magicaland.gameplay.client.RemoteAim;
import top.elysianherd.magicaland.gameplay.client.echo.SpiritualEchoRenderer;

@Mixin(GameRenderer.class)
public abstract class RemoteOverlayMixin {
    @Inject(method="render",at=@At("HEAD"))
    private void remoteFrame(float delta,long startTime,boolean tick,CallbackInfo ci) {
        RemoteAim.clear();
        SpiritualEchoRenderer.beginFrame();
    }

    @Inject(method="renderWorld(FJLnet/minecraft/client/util/math/MatrixStack;)V",at=@At(value="INVOKE",
            target="Lnet/minecraft/client/render/WorldRenderer;render(Lnet/minecraft/client/util/math/MatrixStack;FJZLnet/minecraft/client/render/Camera;Lnet/minecraft/client/render/GameRenderer;Lnet/minecraft/client/render/LightmapTextureManager;Lorg/joml/Matrix4f;)V"))
    private void echoCamera(float delta,long startTime,MatrixStack matrices,CallbackInfo ci) {
        SpiritualEchoRenderer.prepareWorld(matrices,delta);
    }

    @Inject(method="renderWorld(FJLnet/minecraft/client/util/math/MatrixStack;)V",at=@At(value="INVOKE",
            target="Lnet/minecraft/client/render/WorldRenderer;render(Lnet/minecraft/client/util/math/MatrixStack;FJZLnet/minecraft/client/render/Camera;Lnet/minecraft/client/render/GameRenderer;Lnet/minecraft/client/render/LightmapTextureManager;Lorg/joml/Matrix4f;)V",shift=At.Shift.AFTER))
    private void echoDepth(float delta,long startTime,MatrixStack matrices,CallbackInfo ci) {
        SpiritualEchoRenderer.captureDepth();
    }

    @Inject(method="render",at=@At(value="INVOKE",target="Lnet/minecraft/client/render/DiffuseLighting;enableGuiDepthLighting()V",shift=At.Shift.AFTER))
    private void remoteVisibility(float delta,long startTime,boolean tick,CallbackInfo ci) {
        var client=MinecraftClient.getInstance();
        if (RemoteToolClient.active() && client.world!=null)
            RemoteToolHud.renderWorldOverlay(new DrawContext(client,client.getBufferBuilders().getEntityVertexConsumers()),delta);
    }
}
