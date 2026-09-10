package top.csituka.magicaland.gameplay.mixin.client;

import net.minecraft.client.MinecraftClient;
import net.minecraft.client.render.GameRenderer;
import net.minecraft.entity.LivingEntity;
import net.minecraft.entity.projectile.ProjectileUtil;
import net.minecraft.util.hit.HitResult;
import net.minecraft.world.RaycastContext;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import top.csituka.magicaland.gameplay.client.RemoteToolClient;

@Mixin(GameRenderer.class)
public abstract class RemoteTargetMixin {
    @Inject(method="updateTargetedEntity",at=@At("TAIL"))
    private void remoteTarget(float delta,CallbackInfo ci) {
        var client=MinecraftClient.getInstance();
        var tool=RemoteToolClient.camera();
        if (!RemoteToolClient.controlling() || tool==null || client.world==null) return;
        var from=tool.getCameraPosVec(delta);
        var to=from.add(tool.getRotationVec(delta).multiply(3));
        var block=client.world.raycast(new RaycastContext(from,to,RaycastContext.ShapeType.OUTLINE,RaycastContext.FluidHandling.NONE,tool));
        double reach=block.getType()==HitResult.Type.MISS ? 9 : from.squaredDistanceTo(block.getPos());
        var entity=ProjectileUtil.raycast(tool,from,to,tool.getBoundingBox().stretch(to.subtract(from)).expand(1),
                target -> target!=client.player && target instanceof LivingEntity && target.isAlive()
                        && !target.isSpectator() && target.canHit(),reach);
        client.crosshairTarget=entity==null ? block : entity;
        client.targetedEntity=entity==null ? null : entity.getEntity();
    }
}
