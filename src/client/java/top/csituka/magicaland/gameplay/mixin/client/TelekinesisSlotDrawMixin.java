package top.csituka.magicaland.gameplay.mixin.client;

import net.minecraft.client.font.TextRenderer;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.entity.LivingEntity;
import net.minecraft.item.ItemStack;
import net.minecraft.world.World;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import top.csituka.magicaland.gameplay.client.telekinesis.TelekinesisSlotRenderer;

@Mixin(DrawContext.class)
public abstract class TelekinesisSlotDrawMixin {
    @Shadow private void drawItem(LivingEntity entity, World world, ItemStack stack, int x, int y, int seed, int depth) {
        throw new AssertionError();
    }

    @Inject(method = "drawItem(Lnet/minecraft/entity/LivingEntity;Lnet/minecraft/world/World;Lnet/minecraft/item/ItemStack;IIII)V",
            at = @At("HEAD"), cancellable = true)
    private void magicaland$toolIcon(LivingEntity entity, World world, ItemStack stack, int x, int y,
                                     int seed, int depth, CallbackInfo ci) {
        var replacement = TelekinesisSlotRenderer.replacement(stack);
        if (replacement == stack) return;
        TelekinesisSlotRenderer.glow((DrawContext) (Object) this, x, y);
        drawItem(entity, world, replacement, x, y, seed, depth);
        ci.cancel();
    }

    @Inject(method = "drawItemInSlot(Lnet/minecraft/client/font/TextRenderer;Lnet/minecraft/item/ItemStack;IILjava/lang/String;)V",
            at = @At("HEAD"), cancellable = true)
    private void magicaland$toolDurability(TextRenderer text, ItemStack stack, int x, int y, String label, CallbackInfo ci) {
        var replacement = TelekinesisSlotRenderer.replacement(stack);
        if (replacement == stack) return;
        ((DrawContext) (Object) this).drawItemInSlot(text, replacement, x, y, label);
        ci.cancel();
    }
}
