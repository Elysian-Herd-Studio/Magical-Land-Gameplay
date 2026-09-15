package top.elysianherd.magicaland.gameplay.mixin.client;

import net.minecraft.client.render.GameRenderer;
import net.minecraft.client.util.math.MatrixStack;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import top.elysianherd.magicaland.gameplay.client.sense.EarthSenseRenderer;

@Mixin(GameRenderer.class)
public abstract class EarthSenseWorldMixin {
    @Inject(method = "render(FJZ)V", at = @At("HEAD"))
    private void magicaland$senseFrame(float delta, long startTime, boolean tick, CallbackInfo ci) {
        EarthSenseRenderer.beginFrame();
    }

    @Inject(method = "renderWorld(FJLnet/minecraft/client/util/math/MatrixStack;)V", at = @At(value = "INVOKE",
            target = "Lnet/minecraft/client/render/WorldRenderer;render(Lnet/minecraft/client/util/math/MatrixStack;FJZLnet/minecraft/client/render/Camera;Lnet/minecraft/client/render/GameRenderer;Lnet/minecraft/client/render/LightmapTextureManager;Lorg/joml/Matrix4f;)V"))
    private void magicaland$senseCamera(float delta, long startTime, MatrixStack matrices, CallbackInfo ci) {
        EarthSenseRenderer.captureWorld(matrices, delta);
    }

    // 1.20.1: after world, outlines and the vanilla post effect; before GUI depth clear and HUD.
    @Inject(method = "render(FJZ)V", at = @At(value = "INVOKE",
            target = "Lcom/mojang/blaze3d/systems/RenderSystem;clear(IZ)V", ordinal = 0))
    private void magicaland$senseWorld(float delta, long startTime, boolean tick, CallbackInfo ci) {
        if (tick) EarthSenseRenderer.renderWorld(delta);
    }
}
