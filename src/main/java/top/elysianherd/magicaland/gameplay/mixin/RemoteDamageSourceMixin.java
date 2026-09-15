package top.elysianherd.magicaland.gameplay.mixin;

import net.minecraft.entity.damage.DamageSource;
import net.minecraft.entity.damage.DamageTypes;
import net.minecraft.util.math.Vec3d;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;
import top.elysianherd.magicaland.gameplay.remote.RemoteActionContext;

@Mixin(DamageSource.class)
public abstract class RemoteDamageSourceMixin {
    @Inject(method="getPosition",at=@At("HEAD"),cancellable=true)
    private void remotePosition(CallbackInfoReturnable<Vec3d> ci) {
        var action=RemoteActionContext.current(); var source=(DamageSource)(Object)this;
        if (action!=null && source.getAttacker()==action.player && source.isOf(DamageTypes.PLAYER_ATTACK))
            ci.setReturnValue(action.tool.getPos());
    }
}
