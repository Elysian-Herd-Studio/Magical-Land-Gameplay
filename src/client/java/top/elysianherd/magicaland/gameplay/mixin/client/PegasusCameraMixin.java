package top.elysianherd.magicaland.gameplay.mixin.client;

import net.minecraft.client.MinecraftClient;
import net.minecraft.client.render.Camera;
import net.minecraft.entity.Entity;
import net.minecraft.util.math.MathHelper;
import net.minecraft.world.BlockView;
import org.joml.Quaternionf;
import org.joml.Vector3f;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import top.elysianherd.magicaland.gameplay.client.pegasus.PegasusFlightClient;
import top.elysianherd.magicaland.gameplay.client.pegasus.PegasusFlightEffects;
import top.elysianherd.magicaland.gameplay.client.pegasus.PegasusFlightView;

@Mixin(Camera.class)
public abstract class PegasusCameraMixin {
    @Shadow @Final private Quaternionf rotation;
    @Shadow @Final private Vector3f horizontalPlane;
    @Shadow @Final private Vector3f verticalPlane;
    @Shadow @Final private Vector3f diagonalPlane;
    @Shadow private float yaw;
    @Shadow private float pitch;

    @Inject(method = "update(Lnet/minecraft/world/BlockView;Lnet/minecraft/entity/Entity;ZZF)V",
            at = @At(value = "INVOKE", target = "Lnet/minecraft/client/render/Camera;setRotation(FF)V", shift = At.Shift.AFTER))
    private void magicaland$pegasusCamera(BlockView world, Entity focused, boolean thirdPerson, boolean inverse,
                                         float delta, CallbackInfo ci) {
        var client = MinecraftClient.getInstance();
        var pose = focused == client.player ? PegasusFlightClient.camera(delta) : null;
        if (pose == null) return;
        rotation.set(PegasusFlightView.quaternion(pose));
        if (thirdPerson && inverse) rotation.rotateY((float) Math.PI);
        magicaland$pegasusBasis();
    }
    @Inject(method = "update(Lnet/minecraft/world/BlockView;Lnet/minecraft/entity/Entity;ZZF)V", at = @At("TAIL"))
    private void magicaland$pegasusShake(BlockView world, Entity focused, boolean thirdPerson, boolean inverse,
                                        float delta, CallbackInfo ci) {
        var shake = PegasusFlightEffects.shake(delta);
        if (shake.pitch() == 0 && shake.yaw() == 0 && shake.roll() == 0) return;
        float degree = (float) (Math.PI / 180);
        rotation.rotateY(-shake.yaw() * degree).rotateX(shake.pitch() * degree).rotateZ(shake.roll() * degree).normalize();
        magicaland$pegasusBasis();
    }
    private void magicaland$pegasusBasis() {
        horizontalPlane.set(0, 0, 1).rotate(rotation);
        verticalPlane.set(0, 1, 0).rotate(rotation);
        diagonalPlane.set(1, 0, 0).rotate(rotation);
        yaw = (float) Math.toDegrees(Math.atan2(-horizontalPlane.x, horizontalPlane.z));
        pitch = (float) Math.toDegrees(-Math.asin(MathHelper.clamp(horizontalPlane.y, -1, 1)));
    }
}
