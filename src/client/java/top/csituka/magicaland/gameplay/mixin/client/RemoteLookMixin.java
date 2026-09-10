package top.csituka.magicaland.gameplay.mixin.client;

import net.minecraft.client.MinecraftClient;
import net.minecraft.entity.Entity;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import top.csituka.magicaland.gameplay.client.RemoteToolClient;

@Mixin(Entity.class)
public abstract class RemoteLookMixin {
    @Inject(method="changeLookDirection",at=@At("HEAD"),cancellable=true)
    private void remoteLook(double x,double y,CallbackInfo ci) {
        if ((Object)this==MinecraftClient.getInstance().player && RemoteToolClient.look(x,y)) ci.cancel();
    }
}
