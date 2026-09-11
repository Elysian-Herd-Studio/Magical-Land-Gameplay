package top.csituka.magicaland.gameplay.mixin;

import java.util.function.Function;
import net.minecraft.entity.LivingEntity;
import net.minecraft.entity.ai.brain.EntityLookTarget;
import net.minecraft.entity.ai.brain.MemoryModuleType;
import net.minecraft.entity.ai.brain.WalkTarget;
import net.minecraft.entity.ai.brain.task.TemptTask;
import net.minecraft.entity.mob.PathAwareEntity;
import net.minecraft.server.world.ServerWorld;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;
import top.csituka.magicaland.gameplay.remote.RemoteBrainTemptation;
import top.csituka.magicaland.gameplay.remote.RemoteToolEntity;

@Mixin(TemptTask.class)
public abstract class RemoteTemptTaskMixin {
    @Shadow @Final private Function<LivingEntity, Double> stopDistanceGetter;
    @Shadow protected abstract float getSpeed(PathAwareEntity mob);

    @Inject(method = "shouldKeepRunning(Lnet/minecraft/server/world/ServerWorld;Lnet/minecraft/entity/mob/PathAwareEntity;J)Z",
            at = @At("RETURN"), cancellable = true)
    private void remoteValid(ServerWorld world, PathAwareEntity mob, long time, CallbackInfoReturnable<Boolean> ci) {
        if (RemoteBrainTemptation.assigned(mob) && RemoteBrainTemptation.target(mob) == null) {
            RemoteBrainTemptation.forget(mob);
            ci.setReturnValue(false);
        }
    }

    @Inject(method = "keepRunning(Lnet/minecraft/server/world/ServerWorld;Lnet/minecraft/entity/mob/PathAwareEntity;J)V",
            at = @At("HEAD"), cancellable = true)
    private void remoteFollow(ServerWorld world, PathAwareEntity mob, long time, CallbackInfo ci) {
        if (!RemoteBrainTemptation.assigned(mob)) return;
        RemoteToolEntity tool = RemoteBrainTemptation.target(mob);
        var brain = mob.getBrain();
        if (tool == null) {
            RemoteBrainTemptation.forget(mob);
            brain.forget(MemoryModuleType.LOOK_TARGET);
            brain.forget(MemoryModuleType.WALK_TARGET);
        } else {
            brain.remember(MemoryModuleType.LOOK_TARGET, new EntityLookTarget(tool, true));
            double stopDistance = stopDistanceGetter.apply(mob);
            if (mob.squaredDistanceTo(tool) < stopDistance * stopDistance) brain.forget(MemoryModuleType.WALK_TARGET);
            else brain.remember(MemoryModuleType.WALK_TARGET,
                    new WalkTarget(new EntityLookTarget(tool, false), getSpeed(mob), 2));
        }
        ci.cancel();
    }
}
