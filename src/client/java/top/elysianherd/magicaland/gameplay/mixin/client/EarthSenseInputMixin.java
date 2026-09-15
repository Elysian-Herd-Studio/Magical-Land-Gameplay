package top.elysianherd.magicaland.gameplay.mixin.client;

import net.minecraft.client.input.Input;
import net.minecraft.client.input.KeyboardInput;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import top.elysianherd.magicaland.gameplay.client.sense.EarthSenseClient;

@Mixin(KeyboardInput.class)
public abstract class EarthSenseInputMixin extends Input {
    @Inject(method = "tick", at = @At("TAIL"))
    private void magicaland$senseInput(boolean slowDown, float factor, CallbackInfo ci) {
        EarthSenseClient.applyFocusInput(this, slowDown, factor);
    }
}
