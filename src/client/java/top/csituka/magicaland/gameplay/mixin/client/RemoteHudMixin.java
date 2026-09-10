package top.csituka.magicaland.gameplay.mixin.client;

import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.gui.hud.InGameHud;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import top.csituka.magicaland.gameplay.client.RemoteToolClient;

@Mixin(InGameHud.class)
public abstract class RemoteHudMixin {
    @Inject(method="renderHotbar",at=@At("HEAD"),cancellable=true)
    private void remoteHotbar(float delta,DrawContext context,CallbackInfo ci) {
        if (RemoteToolClient.active()) ci.cancel();
    }
    @Inject(method="renderCrosshair",at=@At("HEAD"),cancellable=true)
    private void remoteCrosshair(DrawContext context,CallbackInfo ci) {
        if (RemoteToolClient.active()) ci.cancel();
    }
}
