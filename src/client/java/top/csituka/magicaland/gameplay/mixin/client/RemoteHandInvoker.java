package top.csituka.magicaland.gameplay.mixin.client;

import net.minecraft.client.network.AbstractClientPlayerEntity;
import net.minecraft.client.render.VertexConsumerProvider;
import net.minecraft.client.render.item.HeldItemRenderer;
import net.minecraft.client.util.math.MatrixStack;
import net.minecraft.item.ItemStack;
import net.minecraft.util.Hand;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Invoker;

@Mixin(HeldItemRenderer.class)
public interface RemoteHandInvoker {
    @Invoker("renderFirstPersonItem")
    void magicaland$renderFirstPersonItem(AbstractClientPlayerEntity player,float delta,float pitch,Hand hand,
            float swing,ItemStack stack,float equip,MatrixStack matrices,VertexConsumerProvider buffers,int light);
}
