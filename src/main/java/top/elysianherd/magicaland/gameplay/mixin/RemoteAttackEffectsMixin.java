package top.elysianherd.magicaland.gameplay.mixin;

import net.minecraft.entity.Entity;
import net.minecraft.entity.LivingEntity;
import net.minecraft.entity.damage.DamageSource;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.util.ActionResult;
import net.minecraft.util.Hand;
import net.minecraft.util.hit.EntityHitResult;
import net.minecraft.util.math.Vec3d;
import net.fabricmc.fabric.api.event.player.AttackEntityCallback;
import net.minecraft.particle.ParticleTypes;
import net.minecraft.server.world.ServerWorld;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;
import org.objectweb.asm.Opcodes;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.ModifyArgs;
import org.spongepowered.asm.mixin.injection.Redirect;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.spongepowered.asm.mixin.injection.invoke.arg.Args;
import top.elysianherd.magicaland.gameplay.remote.RemoteActionContext;
import top.elysianherd.magicaland.gameplay.remote.RemoteToolServer;
import top.elysianherd.magicaland.gameplay.remote.TelekinesisServer;

@Mixin(PlayerEntity.class)
public abstract class RemoteAttackEffectsMixin {
    @Unique
    private static boolean magicaland$automaticAttack(PlayerEntity player) {
        var tool=RemoteActionContext.toolFor(player);
        return tool!=null && tool.autonomous();
    }
    @Redirect(method="attack",at=@At(value="INVOKE",target="Lnet/minecraft/entity/player/PlayerEntity;isSprinting()Z"),require=2,allow=2)
    private boolean magicaland$automaticSprint(PlayerEntity player) {
        return !magicaland$automaticAttack(player) && player.isSprinting();
    }
    @Redirect(method="attack",at=@At(value="FIELD",target="Lnet/minecraft/entity/player/PlayerEntity;fallDistance:F",opcode=Opcodes.GETFIELD),require=1,allow=1)
    private float magicaland$automaticFallDistance(PlayerEntity player) {
        return magicaland$automaticAttack(player)?0:player.fallDistance;
    }
    @Redirect(method="attack",at=@At(value="INVOKE",target="Lnet/minecraft/entity/player/PlayerEntity;setVelocity(Lnet/minecraft/util/math/Vec3d;)V"),require=1,allow=1)
    private void magicaland$automaticRecoil(PlayerEntity player,Vec3d velocity) {
        if (!magicaland$automaticAttack(player)) player.setVelocity(velocity);
    }
    @Redirect(method="attack",at=@At(value="INVOKE",target="Lnet/minecraft/entity/player/PlayerEntity;setSprinting(Z)V"),require=1,allow=1)
    private void magicaland$automaticStopSprint(PlayerEntity player,boolean sprinting) {
        if (!magicaland$automaticAttack(player)) player.setSprinting(sprinting);
    }
    @Inject(method="spawnSweepAttackParticles",at=@At("HEAD"),cancellable=true)
    private void magicaland$remoteSweep(CallbackInfo ci) {
        var tool=RemoteActionContext.toolFor((PlayerEntity)(Object)this);
        if (tool==null) return;
        var forward=tool.getRotationVec(1);
        var pos=tool.getEyePos().add(forward.multiply(.8));
        ((ServerWorld)tool.getWorld()).spawnParticles(ParticleTypes.SWEEP_ATTACK,pos.x,pos.y,pos.z,0,forward.x,forward.y,forward.z,0);
        ci.cancel();
    }
    @ModifyArgs(method="attack",at=@At(value="INVOKE",target="Lnet/minecraft/world/World;playSound(Lnet/minecraft/entity/player/PlayerEntity;DDDLnet/minecraft/sound/SoundEvent;Lnet/minecraft/sound/SoundCategory;FF)V"))
    private void magicaland$remoteAttackSound(Args args) {
        var tool=RemoteActionContext.toolFor((PlayerEntity)(Object)this);
        if (tool!=null) { args.set(1,tool.getX()); args.set(2,tool.getY()); args.set(3,tool.getZ()); }
    }
    @Redirect(method="attack",at=@At(value="INVOKE",target="Lnet/minecraft/entity/player/PlayerEntity;getYaw()F"))
    private float magicaland$remoteAttackYaw(PlayerEntity player) {
        var tool=RemoteActionContext.toolFor(player);
        return tool==null?player.getYaw():tool.getYaw();
    }
    @Redirect(method="attack",at=@At(value="INVOKE",target="Lnet/minecraft/entity/player/PlayerEntity;squaredDistanceTo(Lnet/minecraft/entity/Entity;)D"))
    private double magicaland$remoteSweepRange(PlayerEntity player,Entity target) {
        var tool=RemoteActionContext.toolFor(player);
        if (tool!=null && player instanceof ServerPlayerEntity owner
                && (!RemoteToolServer.canAttack(owner,tool,target)
                || AttackEntityCallback.EVENT.invoker().interact(owner,tool.getWorld(),Hand.MAIN_HAND,target,new EntityHitResult(target))!=ActionResult.PASS))
            return Double.MAX_VALUE;
        return tool==null?player.squaredDistanceTo(target):tool.squaredDistanceTo(target);
    }
    @Redirect(method="attack",at=@At(value="INVOKE",target="Lnet/minecraft/entity/Entity;damage(Lnet/minecraft/entity/damage/DamageSource;F)Z"))
    private boolean magicaland$remoteHit(Entity target,DamageSource source,float amount) {
        boolean result=target.damage(source,amount);
        var action=RemoteActionContext.forPlayer((PlayerEntity)(Object)this);
        if (action!=null) action.recordHit(result);
        else if (result && amount>0 && (Object)this instanceof ServerPlayerEntity player && target instanceof LivingEntity living)
            TelekinesisServer.assist(player,living);
        return result;
    }
}
