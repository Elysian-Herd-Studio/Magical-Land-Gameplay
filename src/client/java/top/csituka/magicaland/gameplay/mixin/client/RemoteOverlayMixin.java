package top.csituka.magicaland.gameplay.mixin.client;

import net.minecraft.client.MinecraftClient;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.render.GameRenderer;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import top.csituka.magicaland.gameplay.client.RemoteToolClient;
import top.csituka.magicaland.gameplay.client.RemoteToolHud;

@Mixin(GameRenderer.class)
public abstract class RemoteOverlayMixin {
    @Inject(method="render",at=@At(value="INVOKE",target="Lnet/minecraft/client/render/DiffuseLighting;enableGuiDepthLighting()V",shift=At.Shift.AFTER))
    private void remoteVisibility(float delta,long startTime,boolean tick,CallbackInfo ci) {
        var client=MinecraftClient.getInstance();
        if (RemoteToolClient.active() && client.world!=null)
            RemoteToolHud.renderWorldOverlay(new DrawContext(client,client.getBufferBuilders().getEntityVertexConsumers()),delta);
    }
}
