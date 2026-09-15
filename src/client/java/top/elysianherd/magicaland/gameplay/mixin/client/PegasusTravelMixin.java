package top.elysianherd.magicaland.gameplay.mixin.client;

import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.util.math.Vec3d;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import top.elysianherd.magicaland.gameplay.client.pegasus.PegasusFlightClient;

@Mixin(PlayerEntity.class)
public abstract class PegasusTravelMixin {
    @Inject(method = "travel(Lnet/minecraft/util/math/Vec3d;)V", at = @At("HEAD"), cancellable = true)
    private void magicaland$pegasusTravel(Vec3d input, CallbackInfo ci) {
        if (PegasusFlightClient.travel((PlayerEntity) (Object) this)) ci.cancel();
    }
}
