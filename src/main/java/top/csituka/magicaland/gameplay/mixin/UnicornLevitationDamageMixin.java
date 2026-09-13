package top.csituka.magicaland.gameplay.mixin;

import net.minecraft.entity.LivingEntity;
import net.minecraft.entity.damage.DamageSource;
import net.minecraft.server.network.ServerPlayerEntity;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;
import top.csituka.magicaland.gameplay.levitation.UnicornLevitationServer;

@Mixin(LivingEntity.class)
public abstract class UnicornLevitationDamageMixin {
    @Inject(method = "damage(Lnet/minecraft/entity/damage/DamageSource;F)Z", at = @At("RETURN"))
    private void magicaland$interruptLevitation(DamageSource source, float amount, CallbackInfoReturnable<Boolean> cir) {
        if (amount > 0 && cir.getReturnValueZ() && (Object) this instanceof ServerPlayerEntity player)
            UnicornLevitationServer.damaged(player);
    }
}
