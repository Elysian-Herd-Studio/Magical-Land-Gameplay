package top.csituka.magicaland.gameplay.mixin;

import net.minecraft.entity.damage.DamageSource;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.util.math.Vec3d;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;
import top.csituka.magicaland.gameplay.pegasus.PegasusFlightServer;

@Mixin(PlayerEntity.class)
public abstract class PegasusFlightPlayerMixin {
    @Inject(method = "travel", at = @At("HEAD"), cancellable = true)
    private void magicaland$flightTravel(Vec3d input, CallbackInfo ci) {
        if ((Object) this instanceof ServerPlayerEntity player && PegasusFlightServer.physicsActive(player)) ci.cancel();
    }
    @Inject(method = "handleFallDamage", at = @At("HEAD"), cancellable = true)
    private void magicaland$wingLanding(float distance, float multiplier, DamageSource source, CallbackInfoReturnable<Boolean> cir) {
        if ((Object) this instanceof ServerPlayerEntity player && PegasusFlightServer.protectsFallDamage(player)) cir.setReturnValue(false);
    }
}
