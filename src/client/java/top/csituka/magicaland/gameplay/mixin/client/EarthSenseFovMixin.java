package top.csituka.magicaland.gameplay.mixin.client;

import net.minecraft.client.MinecraftClient;
import net.minecraft.client.render.Camera;
import net.minecraft.client.render.GameRenderer;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;
import top.csituka.magicaland.gameplay.client.RemoteToolClient;
import top.csituka.magicaland.gameplay.client.sense.EarthSenseClient;
import top.csituka.magicaland.gameplay.client.sense.EarthSenseViewMath;

@Mixin(GameRenderer.class)
public abstract class EarthSenseFovMixin {
    @Inject(method = "getFov(Lnet/minecraft/client/render/Camera;FZ)D", at = @At("RETURN"), cancellable = true)
    private void magicaland$senseFov(Camera camera, float delta, boolean changingFov, CallbackInfoReturnable<Double> cir) {
        var client = MinecraftClient.getInstance();
        if (!changingFov || ((GameRenderer)(Object)this).isRenderingPanorama()
                || !EarthSenseViewMath.applies(client.world != null, client.player != null
                        && camera.getFocusedEntity() == client.player && client.getCameraEntity() == client.player,
                        client.currentScreen != null, RemoteToolClient.active()) || client.player.isUsingSpyglass()) return;
        double original = cir.getReturnValueD();
        double adjusted = EarthSenseViewMath.fov(original, EarthSenseClient.opacity(delta));
        if (adjusted != original) cir.setReturnValue(adjusted);
    }
}
