package top.elysianherd.magicaland.gameplay.mixin.client;

import net.minecraft.client.input.Input;
import net.minecraft.client.input.KeyboardInput;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import top.elysianherd.magicaland.gameplay.client.pegasus.PegasusFlightClient;

@Mixin(KeyboardInput.class)
public abstract class PegasusInputMixin extends Input {
    @Inject(method = "tick(ZF)V", at = @At("TAIL"))
    private void magicaland$pegasusInput(boolean slowDown, float factor, CallbackInfo ci) {
        PegasusFlightClient.nativeInput(this);
    }
}
