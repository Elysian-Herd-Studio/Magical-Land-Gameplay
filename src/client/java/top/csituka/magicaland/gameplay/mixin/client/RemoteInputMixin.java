package top.csituka.magicaland.gameplay.mixin.client;

import net.minecraft.client.input.Input;
import net.minecraft.client.input.KeyboardInput;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import top.csituka.magicaland.gameplay.client.RemoteToolClient;

@Mixin(KeyboardInput.class)
public abstract class RemoteInputMixin extends Input {
    @Inject(method="tick",at=@At("TAIL"))
    private void remoteInput(boolean slowDown,float factor,CallbackInfo ci) {
        if (!RemoteToolClient.blockBodyInput()) return;
        movementForward=movementSideways=0;
        pressingForward=pressingBack=pressingLeft=pressingRight=jumping=sneaking=false;
    }
}
