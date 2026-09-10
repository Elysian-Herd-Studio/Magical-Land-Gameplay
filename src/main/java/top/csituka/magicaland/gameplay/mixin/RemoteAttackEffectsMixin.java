package top.csituka.magicaland.gameplay.mixin;

import net.minecraft.entity.Entity;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.particle.ParticleTypes;
import net.minecraft.server.world.ServerWorld;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.ModifyArgs;
import org.spongepowered.asm.mixin.injection.Redirect;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.spongepowered.asm.mixin.injection.invoke.arg.Args;
import top.csituka.magicaland.gameplay.remote.RemoteActionContext;

@Mixin(PlayerEntity.class)
public abstract class RemoteAttackEffectsMixin {
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
        return tool==null?player.squaredDistanceTo(target):tool.squaredDistanceTo(target);
    }
}
