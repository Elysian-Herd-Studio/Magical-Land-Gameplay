package top.elysianherd.magicaland.gameplay.mixin;

import net.minecraft.entity.ai.TargetPredicate;
import net.minecraft.entity.ai.goal.TemptGoal;
import net.minecraft.entity.mob.PathAwareEntity;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.recipe.Ingredient;
import net.minecraft.util.math.MathHelper;
import net.minecraft.util.math.Vec3d;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;
import top.elysianherd.magicaland.gameplay.remote.RemoteToolEntity;
import top.elysianherd.magicaland.gameplay.remote.RemoteToolServer;

@Mixin(TemptGoal.class)
public abstract class RemoteTemptGoalMixin {
    @Shadow @Final protected PathAwareEntity mob;
    @Shadow @Final private Ingredient food;
    @Shadow @Final private TargetPredicate predicate;
    @Shadow @Final private double speed;
    @Shadow protected PlayerEntity closestPlayer;
    @Shadow private int cooldown;
    @Shadow private boolean active;
    @Shadow private double lastPlayerPitch,lastPlayerYaw;
    @Shadow protected abstract boolean canBeScared();
    @Shadow public abstract void start();
    @Unique private RemoteToolEntity magicaland$food;
    @Unique private Vec3d magicaland$lastPosition;
    @Unique private float magicaland$lastYaw,magicaland$lastPitch;
    @Unique private boolean magicaland$coolingDown;

    @Inject(method="canStart",at=@At("HEAD"))
    private void remoteCooldown(CallbackInfoReturnable<Boolean> ci) { magicaland$coolingDown=cooldown>0; }
    @Inject(method="canStart",at=@At("RETURN"),cancellable=true)
    private void remoteFood(CallbackInfoReturnable<Boolean> ci) {
        magicaland$food=null;
        if (ci.getReturnValueZ() || magicaland$coolingDown) return;
        magicaland$food=RemoteToolServer.temptingTool(mob,food);
        if (magicaland$food==null) return;
        closestPlayer=mob.getWorld().getPlayerByUuid(magicaland$food.owner());
        if (closestPlayer==null) { magicaland$food=null; return; }
        magicaland$remember(); ci.setReturnValue(true);
    }
    @Inject(method="start",at=@At("HEAD"),cancellable=true)
    private void remoteStart(CallbackInfo ci) {
        if (magicaland$food==null) return;
        magicaland$remember(); active=true; ci.cancel();
    }
    @Inject(method="shouldContinue",at=@At("HEAD"),cancellable=true)
    private void remoteContinue(CallbackInfoReturnable<Boolean> ci) {
        if (magicaland$food==null) return;
        PlayerEntity player=mob.getWorld().getClosestPlayer(predicate,mob);
        if (player!=null) {
            magicaland$food=null; magicaland$lastPosition=null; closestPlayer=player;
            lastPlayerPitch=player.getPitch(); lastPlayerYaw=player.getYaw();
            start(); ci.setReturnValue(true); return;
        }
        boolean valid=RemoteToolServer.isTempting(magicaland$food,mob,food);
        if (valid && canBeScared() && mob.squaredDistanceTo(magicaland$food)<36) {
            valid=magicaland$lastPosition!=null && magicaland$food.getPos().squaredDistanceTo(magicaland$lastPosition)<=.01
                    && Math.abs(MathHelper.wrapDegrees(magicaland$food.getYaw()-magicaland$lastYaw))<=5
                    && Math.abs(magicaland$food.getPitch()-magicaland$lastPitch)<=5;
        }
        if (valid) {
            if (mob.squaredDistanceTo(magicaland$food)>=36) magicaland$lastPosition=magicaland$food.getPos();
            magicaland$lastYaw=magicaland$food.getYaw(); magicaland$lastPitch=magicaland$food.getPitch();
        }
        ci.setReturnValue(valid);
    }
    @Inject(method="tick",at=@At("HEAD"),cancellable=true)
    private void remoteFollow(CallbackInfo ci) {
        if (magicaland$food==null) return;
        mob.getLookControl().lookAt(magicaland$food,mob.getMaxHeadRotation()+20,mob.getMaxLookPitchChange());
        if (mob.squaredDistanceTo(magicaland$food)<6.25) mob.getNavigation().stop();
        else mob.getNavigation().startMovingTo(magicaland$food,speed);
        ci.cancel();
    }
    @Inject(method="stop",at=@At("TAIL"))
    private void remoteStop(CallbackInfo ci) { magicaland$food=null; magicaland$lastPosition=null; }
    @Unique private void magicaland$remember() {
        magicaland$lastPosition=magicaland$food.getPos();
        magicaland$lastYaw=magicaland$food.getYaw(); magicaland$lastPitch=magicaland$food.getPitch();
    }
}
