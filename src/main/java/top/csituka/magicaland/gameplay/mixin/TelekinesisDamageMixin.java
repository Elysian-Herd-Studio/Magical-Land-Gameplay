package top.csituka.magicaland.gameplay.mixin;

import net.minecraft.entity.LivingEntity;
import net.minecraft.entity.damage.DamageSource;
import net.minecraft.server.network.ServerPlayerEntity;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;
import top.csituka.magicaland.gameplay.remote.TelekinesisThreats;

@Mixin(LivingEntity.class)
public abstract class TelekinesisDamageMixin {
    @Inject(method = "damage(Lnet/minecraft/entity/damage/DamageSource;F)Z", at = @At("RETURN"))
    private void magicaland$recordAttacker(DamageSource source, float amount, CallbackInfoReturnable<Boolean> cir) {
        if (amount > 0 && cir.getReturnValueZ() && (Object) this instanceof ServerPlayerEntity player
                && source.getAttacker() instanceof LivingEntity attacker)
            TelekinesisThreats.record(player, attacker);
    }
}
