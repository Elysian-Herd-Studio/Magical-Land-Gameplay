package top.elysianherd.magicaland.gameplay.mixin.client;

import net.minecraft.client.gui.screen.Screen;
import net.minecraft.item.ItemStack;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.ModifyVariable;
import top.elysianherd.magicaland.gameplay.client.telekinesis.TelekinesisSlotRenderer;

@Mixin(Screen.class)
public abstract class TelekinesisSlotTooltipMixin {
    @ModifyVariable(method = "getTooltipFromItem(Lnet/minecraft/client/MinecraftClient;Lnet/minecraft/item/ItemStack;)Ljava/util/List;",
            at = @At("HEAD"), argsOnly = true, ordinal = 0)
    private static ItemStack magicaland$toolTooltip(ItemStack stack) {
        return TelekinesisSlotRenderer.replacement(stack);
    }
}
