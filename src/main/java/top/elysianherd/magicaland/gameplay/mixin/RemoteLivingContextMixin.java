package top.elysianherd.magicaland.gameplay.mixin;

import net.minecraft.entity.LivingEntity;
import net.minecraft.entity.Entity;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.entity.attribute.EntityAttribute;
import net.minecraft.entity.attribute.EntityAttributes;
import net.minecraft.util.Hand;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.Redirect;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;
import top.elysianherd.magicaland.gameplay.remote.RemoteActionContext;

@Mixin(LivingEntity.class)
public abstract class RemoteLivingContextMixin {
    @Inject(method="getAttributeValue(Lnet/minecraft/entity/attribute/EntityAttribute;)D",at=@At("HEAD"),cancellable=true)
    private void remoteAttribute(EntityAttribute attribute,CallbackInfoReturnable<Double> ci) {
        if (!((Object)this instanceof PlayerEntity player)) return;
        var action=RemoteActionContext.forPlayer(player);
        if (action!=null && (attribute==EntityAttributes.GENERIC_ATTACK_DAMAGE || attribute==EntityAttributes.GENERIC_ATTACK_SPEED))
            ci.setReturnValue(action.attributeValue(attribute));
    }
    @Inject(method="sendToolBreakStatus",at=@At("HEAD"),cancellable=true)
    private void remoteToolBreak(Hand hand,CallbackInfo ci) {
        if (!((Object)this instanceof PlayerEntity player)) return;
        var action=RemoteActionContext.forPlayer(player);
        if (action!=null && hand==Hand.MAIN_HAND) { action.toolBroken(); ci.cancel(); }
    }
    @Redirect(method="damage",at=@At(value="INVOKE",target="Lnet/minecraft/entity/Entity;getX()D"))
    private double remoteDamageX(Entity entity) {
        var action=RemoteActionContext.current();
        return action!=null && entity==action.player?action.tool.getX():entity.getX();
    }
    @Redirect(method="damage",at=@At(value="INVOKE",target="Lnet/minecraft/entity/Entity;getZ()D"))
    private double remoteDamageZ(Entity entity) {
        var action=RemoteActionContext.current();
        return action!=null && entity==action.player?action.tool.getZ():entity.getZ();
    }
    @Redirect(method="knockback",at=@At(value="INVOKE",target="Lnet/minecraft/entity/LivingEntity;getX()D"))
    private double remoteShieldX(LivingEntity entity) {
        var action=RemoteActionContext.current();
        return action!=null && entity==action.player?action.tool.getX():entity.getX();
    }
    @Redirect(method="knockback",at=@At(value="INVOKE",target="Lnet/minecraft/entity/LivingEntity;getZ()D"))
    private double remoteShieldZ(LivingEntity entity) {
        var action=RemoteActionContext.current();
        return action!=null && entity==action.player?action.tool.getZ():entity.getZ();
    }
}
