package top.elysianherd.magicaland.gameplay.mixin;

import net.minecraft.entity.ai.brain.sensor.TemptationsSensor;
import net.minecraft.entity.mob.PathAwareEntity;
import net.minecraft.recipe.Ingredient;
import net.minecraft.server.world.ServerWorld;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import top.elysianherd.magicaland.gameplay.remote.RemoteBrainTemptation;

@Mixin(TemptationsSensor.class)
public abstract class RemoteTemptationsSensorMixin {
    @Shadow @Final private Ingredient ingredient;

    @Inject(method = "sense(Lnet/minecraft/server/world/ServerWorld;Lnet/minecraft/entity/mob/PathAwareEntity;)V",
            at = @At("TAIL"))
    private void remoteFood(ServerWorld world, PathAwareEntity mob, CallbackInfo ci) {
        RemoteBrainTemptation.sense(mob, ingredient);
    }
}
