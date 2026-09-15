package top.elysianherd.magicaland.gameplay.mixin.client;

import net.minecraft.client.MinecraftClient;
import net.minecraft.client.render.Camera;
import net.minecraft.client.render.GameRenderer;
import org.joml.Quaternionf;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.ModifyArg;
import org.spongepowered.asm.mixin.injection.Redirect;
import org.spongepowered.asm.mixin.injection.Slice;
import top.elysianherd.magicaland.gameplay.client.pegasus.PegasusFlightClient;
import top.elysianherd.magicaland.gameplay.client.pegasus.PegasusFlightEffects;

@Mixin(GameRenderer.class)
public abstract class PegasusWorldViewMixin {
    private static boolean magicaland$usesFlightView() {
        var client = MinecraftClient.getInstance();
        return PegasusFlightClient.active() || PegasusFlightEffects.shake(client.getTickDelta()).roll() != 0;
    }
    @ModifyArg(method = "renderWorld(FJLnet/minecraft/client/util/math/MatrixStack;)V",
            at = @At(value = "INVOKE", target = "Lnet/minecraft/client/util/math/MatrixStack;multiply(Lorg/joml/Quaternionf;)V"),
            slice = @Slice(from = @At(value = "INVOKE", target = "Lnet/minecraft/client/render/Camera;getPitch()F"),
                    to = @At(value = "INVOKE", target = "Lnet/minecraft/client/render/Camera;getYaw()F")), index = 0)
    private Quaternionf magicaland$fullFlightView(Quaternionf original) {
        if (!magicaland$usesFlightView()) return original;
        var camera = MinecraftClient.getInstance().gameRenderer.getCamera();
        return new Quaternionf().rotationY((float) Math.PI).mul(new Quaternionf(camera.getRotation()).conjugate());
    }
    @Redirect(method = "renderWorld(FJLnet/minecraft/client/util/math/MatrixStack;)V",
            at = @At(value = "INVOKE", target = "Lnet/minecraft/client/render/Camera;getYaw()F"))
    private float magicaland$rotationAlreadyApplied(Camera camera) {
        return magicaland$usesFlightView() ? -180 : camera.getYaw();
    }
}
