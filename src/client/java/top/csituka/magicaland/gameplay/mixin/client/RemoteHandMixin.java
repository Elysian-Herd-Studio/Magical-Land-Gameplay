package top.csituka.magicaland.gameplay.mixin.client;

import net.minecraft.client.MinecraftClient;
import net.minecraft.client.network.ClientPlayerEntity;
import net.minecraft.client.render.GameRenderer;
import net.minecraft.client.render.VertexConsumerProvider;
import net.minecraft.client.render.item.HeldItemRenderer;
import net.minecraft.client.util.math.MatrixStack;
import net.minecraft.util.Hand;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Redirect;
import top.csituka.magicaland.client.api.FirstPersonItemView;
import top.csituka.magicaland.client.render.GlowingItem;
import top.csituka.magicaland.gameplay.remote.RemoteToolEntity;

@Mixin(GameRenderer.class)
public abstract class RemoteHandMixin {
    @Redirect(method="renderHand",at=@At(value="INVOKE",target="Lnet/minecraft/client/render/item/HeldItemRenderer;renderItem(FLnet/minecraft/client/util/math/MatrixStack;Lnet/minecraft/client/render/VertexConsumerProvider$Immediate;Lnet/minecraft/client/network/ClientPlayerEntity;I)V"))
    private void magicaland$remoteHand(HeldItemRenderer renderer,float delta,MatrixStack matrices,
            VertexConsumerProvider.Immediate buffers,ClientPlayerEntity player,int light) {
        var client=MinecraftClient.getInstance();
        if (!(client.getCameraEntity() instanceof RemoteToolEntity tool) || !player.getUuid().equals(tool.owner())) {
            renderer.renderItem(delta,matrices,buffers,player,light); return;
        }
        if (tool.stack().isEmpty()) return;
        try (var view=FirstPersonItemView.open(player,tool,tool.stack())) {
            GlowingItem.beginFirstPersonPass();
            try {
                ((RemoteHandInvoker)renderer).magicaland$renderFirstPersonItem(player,delta,tool.getPitch(delta),
                        Hand.MAIN_HAND,0,tool.stack(),0,matrices,buffers,client.getEntityRenderDispatcher().getLight(tool,delta));
            } finally { GlowingItem.endFirstPersonPass(buffers); }
        }
    }
}
