package top.elysianherd.magicaland.gameplay.mixin.client;

import net.minecraft.client.MinecraftClient;
import net.minecraft.entity.Entity;
import net.minecraft.util.math.Vec3d;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;
import top.elysianherd.magicaland.gameplay.client.pegasus.PegasusFlightClient;
import top.elysianherd.magicaland.gameplay.client.pegasus.PegasusFlightView;

@Mixin(Entity.class)
public abstract class PegasusLookMixin {
    @Inject(method = "changeLookDirection(DD)V", at = @At("HEAD"), cancellable = true)
    private void magicaland$pegasusLook(double horizontal, double vertical, CallbackInfo ci) {
        if ((Object) this == MinecraftClient.getInstance().player && PegasusFlightClient.look(horizontal, vertical)) ci.cancel();
    }
    @Inject(method = "getRotationVec(F)Lnet/minecraft/util/math/Vec3d;", at = @At("HEAD"), cancellable = true)
    private void magicaland$pegasusRay(float delta, CallbackInfoReturnable<Vec3d> cir) {
        if ((Object) this != MinecraftClient.getInstance().player) return;
        var attitude = PegasusFlightClient.camera(delta);
        if (attitude != null) cir.setReturnValue(PegasusFlightView.forward(attitude));
    }
}
