package top.csituka.magicaland.gameplay.mixin;

import com.llamalad7.mixinextras.injector.ModifyExpressionValue;
import net.minecraft.entity.mob.MobEntity;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import top.csituka.magicaland.gameplay.pegasus.PegasusFlightRetention;

@Mixin(MobEntity.class)
public abstract class PegasusFlightDespawnMixin {
    @ModifyExpressionValue(method = "checkDespawn", at = @At(value = "INVOKE",
            target = "Lnet/minecraft/entity/Entity;squaredDistanceTo(Lnet/minecraft/entity/Entity;)D"))
    private double magicaland$retainFlightEncounter(double distanceSquared) {
        return PegasusFlightRetention.adjustDistance((MobEntity) (Object) this, distanceSquared);
    }
}
