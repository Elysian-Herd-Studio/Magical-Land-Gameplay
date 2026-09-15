package top.elysianherd.magicaland.gameplay.mixin.client;

import net.minecraft.client.input.Input;
import net.minecraft.client.input.KeyboardInput;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import top.elysianherd.magicaland.gameplay.client.levitation.UnicornLevitationClient;

@Mixin(KeyboardInput.class)
public abstract class UnicornLevitationInputMixin extends Input {
    @Inject(method = "tick(ZF)V", at = @At("HEAD"))
    private void magicaland$levitationInput(boolean slowDown, float factor, CallbackInfo ci) {
        UnicornLevitationClient.captureInput(this);
    }
    @Inject(method = "tick(ZF)V", at = @At("TAIL"))
    private void magicaland$levitationNativeInput(boolean slowDown, float factor, CallbackInfo ci) {
        UnicornLevitationClient.applyNativeInput(this);
    }
}
