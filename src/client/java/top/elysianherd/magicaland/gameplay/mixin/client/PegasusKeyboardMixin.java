package top.elysianherd.magicaland.gameplay.mixin.client;

import net.minecraft.client.Keyboard;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import top.elysianherd.magicaland.gameplay.client.pegasus.PegasusFlightClient;

@Mixin(Keyboard.class)
public abstract class PegasusKeyboardMixin {
    @Inject(method = "onKey(JIIII)V", at = @At("HEAD"), cancellable = true)
    private void magicaland$pegasusKey(long window, int key, int scanCode, int action, int modifiers, CallbackInfo ci) {
        if (PegasusFlightClient.key(window, key, scanCode, action)) ci.cancel();
    }
}
