package top.csituka.magicaland.gameplay.mixin.client;

import com.mojang.authlib.GameProfile;
import net.minecraft.client.input.Input;
import net.minecraft.client.network.AbstractClientPlayerEntity;
import net.minecraft.client.network.ClientPlayerEntity;
import net.minecraft.client.world.ClientWorld;
import net.minecraft.entity.player.PlayerAbilities;
import org.objectweb.asm.Opcodes;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.Redirect;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import top.csituka.magicaland.gameplay.client.levitation.UnicornLevitationClient;
import top.csituka.magicaland.gameplay.client.levitation.UnicornLevitationNativeInput;

@Mixin(ClientPlayerEntity.class)
public abstract class UnicornLevitationPlayerInputMixin extends AbstractClientPlayerEntity {
    @Shadow public Input input;
    @Shadow private boolean inSneakingPose;

    protected UnicornLevitationPlayerInputMixin(ClientWorld world, GameProfile profile) {
        super(world, profile);
    }

    @Inject(method = "tickMovement()V", at = @At("HEAD"))
    private void magicaland$prepareLevitationInput(CallbackInfo ci) {
        UnicornLevitationClient.preparePlayerInput((ClientPlayerEntity)(Object)this);
        if (UnicornLevitationClient.suppressesNativeSneak()) {
            input.sneaking = false;
            inSneakingPose = false;
        }
        if (UnicornLevitationClient.controlsNativeInput()) input.jumping = false;
        if (UnicornLevitationClient.blocksNativeFlight()) abilityResyncCountdown = 0;
    }

    @Redirect(method = "tickMovement()V", at = @At(value = "FIELD", opcode = Opcodes.GETFIELD,
            target = "Lnet/minecraft/entity/player/PlayerAbilities;allowFlying:Z"))
    private boolean magicaland$keepFlightPermission(PlayerAbilities abilities) {
        return UnicornLevitationNativeInput.allowNativeFlight(abilities.allowFlying, UnicornLevitationClient.blocksNativeFlight());
    }

    @Redirect(method = "tickMovement()V", at = @At(value = "FIELD", opcode = Opcodes.GETFIELD,
            target = "Lnet/minecraft/client/input/Input;jumping:Z"))
    private boolean magicaland$controlledJump(Input input) {
        return input.jumping && !UnicornLevitationClient.controlsNativeInput();
    }

    @Inject(method = "tickMovement()V", at = @At(value = "INVOKE",
            target = "Lnet/minecraft/client/network/AbstractClientPlayerEntity;tickMovement()V"))
    private void magicaland$clearAutoJumpBeforeTravel(CallbackInfo ci) {
        if (UnicornLevitationClient.suppressesNativeSneak()) input.sneaking = false;
        if (UnicornLevitationClient.controlsNativeInput()) input.jumping = false;
        if (UnicornLevitationClient.blocksSprinting()) setSprinting(false);
    }

    @Inject(method = "canSprint()Z", at = @At("HEAD"), cancellable = true)
    private void magicaland$levitationCannotSprint(org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable<Boolean> cir) {
        if (UnicornLevitationClient.blocksSprinting()) cir.setReturnValue(false);
    }
}
