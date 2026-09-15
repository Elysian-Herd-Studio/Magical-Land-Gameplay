package top.elysianherd.magicaland.gameplay.mixin;

import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.entity.damage.DamageSource;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;
import top.elysianherd.magicaland.gameplay.levitation.UnicornLevitationServer;

@Mixin(PlayerEntity.class)
public abstract class UnicornLevitationFallMixin {
    @Inject(method = "handleFallDamage", at = @At("HEAD"), cancellable = true)
    private void magicaland$protectedLanding(float distance, float multiplier, DamageSource source, CallbackInfoReturnable<Boolean> cir) {
        if ((Object) this instanceof ServerPlayerEntity player && UnicornLevitationServer.protectsFallDamage(player))
            cir.setReturnValue(false);
    }
}
