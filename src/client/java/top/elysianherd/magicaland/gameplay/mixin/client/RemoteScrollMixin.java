package top.elysianherd.magicaland.gameplay.mixin.client;

import net.minecraft.client.Mouse;
import net.minecraft.client.MinecraftClient;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import top.elysianherd.magicaland.gameplay.client.RemoteToolClient;

@Mixin(Mouse.class)
public abstract class RemoteScrollMixin {
    @Inject(method="onMouseScroll",at=@At("HEAD"),cancellable=true)
    private void remoteScroll(long window,double horizontal,double vertical,CallbackInfo ci) {
        if (RemoteToolClient.active() && MinecraftClient.getInstance().currentScreen==null) {
            RemoteToolClient.scroll(vertical);
            ci.cancel();
        }
    }
}
