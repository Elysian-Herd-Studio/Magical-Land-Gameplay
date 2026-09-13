package top.csituka.magicaland.gameplay.mixin.client;

import com.mojang.authlib.GameProfile;
import net.minecraft.client.input.Input;
import net.minecraft.client.network.AbstractClientPlayerEntity;
import net.minecraft.client.network.ClientPlayerEntity;
import net.minecraft.client.world.ClientWorld;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;
import top.csituka.magicaland.gameplay.client.pegasus.PegasusFlightClient;

@Mixin(ClientPlayerEntity.class)
public abstract class PegasusPlayerInputMixin extends AbstractClientPlayerEntity {
    @Shadow public Input input;
    @Shadow private boolean inSneakingPose;
    protected PegasusPlayerInputMixin(ClientWorld world, GameProfile profile) { super(world, profile); }

    @Inject(method = "tickMovement()V", at = @At("HEAD"))
    private void magicaland$pegasusNativeFlight(CallbackInfo ci) {
        if (PegasusFlightClient.ownsJumpGesture()) abilityResyncCountdown = 0;
        if (PegasusFlightClient.controlsNativeInput()) {
            input.sneaking = input.jumping = false;
            inSneakingPose = false;
            getAbilities().flying = false;
        }
    }
    @Inject(method = "tickMovement()V", at = @At(value = "INVOKE",
            target = "Lnet/minecraft/client/network/AbstractClientPlayerEntity;tickMovement()V"))
    private void magicaland$pegasusNoAutoJump(CallbackInfo ci) {
        if (!PegasusFlightClient.controlsNativeInput()) return;
        input.sneaking = input.jumping = false;
        setSprinting(false);
    }
    @Inject(method = "canSprint()Z", at = @At("HEAD"), cancellable = true)
    private void magicaland$pegasusSprint(CallbackInfoReturnable<Boolean> cir) {
        if (PegasusFlightClient.controlsNativeInput()) cir.setReturnValue(false);
    }
}
