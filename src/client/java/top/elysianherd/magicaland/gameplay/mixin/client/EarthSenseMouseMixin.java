package top.elysianherd.magicaland.gameplay.mixin.client;

import net.minecraft.client.MinecraftClient;
import net.minecraft.client.Mouse;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.ModifyArgs;
import org.spongepowered.asm.mixin.injection.invoke.arg.Args;
import top.elysianherd.magicaland.gameplay.client.RemoteToolClient;
import top.elysianherd.magicaland.gameplay.client.sense.EarthSenseClient;
import top.elysianherd.magicaland.gameplay.client.sense.EarthSenseViewMath;

@Mixin(Mouse.class)
public abstract class EarthSenseMouseMixin {
    @ModifyArgs(method = "updateMouse()V", at = @At(value = "INVOKE",
            target = "Lnet/minecraft/client/network/ClientPlayerEntity;changeLookDirection(DD)V"))
    private void magicaland$senseMouse(Args args) {
        var client = MinecraftClient.getInstance();
        if (!EarthSenseViewMath.applies(client.world != null, client.player != null
                        && client.getCameraEntity() == client.player
                        && client.gameRenderer.getCamera().getFocusedEntity() == client.player,
                        client.currentScreen != null, RemoteToolClient.active())
                || !client.mouse.isCursorLocked() || !client.isWindowFocused() || client.isPaused()) return;
        float opacity = EarthSenseClient.opacity(client.getTickDelta());
        args.set(0, EarthSenseViewMath.look(args.<Double>get(0), opacity));
        args.set(1, EarthSenseViewMath.look(args.<Double>get(1), opacity));
    }
}
