package top.csituka.magicaland.gameplay.mixin.client;

import com.llamalad7.mixinextras.injector.ModifyExpressionValue;
import net.minecraft.client.network.AbstractClientPlayerEntity;
import net.minecraft.item.ItemStack;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Redirect;
import top.csituka.magicaland.gameplay.client.RemoteMagicAudio;
import top.csituka.magicaland.gameplay.remote.TelekinesisToken;

@Mixin(targets = "top.csituka.magicaland.client.sound.MagicHeldItemSounds", remap = false)
public abstract class RemoteMagicHeldSoundMixin {
    @ModifyExpressionValue(method = "tick", at = @At(value = "FIELD",
            target = "Ltop/csituka/magicaland/client/config/Config;magicSounds:Z"), require = 0, expect = 1)
    private static boolean magicaland$magicSoundSetting(boolean enabled) {
        RemoteMagicAudio.soundEnabled(enabled);
        return enabled;
    }

    @Redirect(method = "tick", at = @At(value = "INVOKE",
            target = "Lnet/minecraft/client/network/AbstractClientPlayerEntity;getMainHandStack()Lnet/minecraft/item/ItemStack;",
            remap = true), require = 0, expect = 1)
    private static ItemStack magicaland$unreservedMainHand(AbstractClientPlayerEntity player) {
        var stack = player.getMainHandStack();
        return TelekinesisToken.isToken(stack) ? ItemStack.EMPTY : stack;
    }

    @Redirect(method = "tick", at = @At(value = "INVOKE",
            target = "Lnet/minecraft/client/network/AbstractClientPlayerEntity;getOffHandStack()Lnet/minecraft/item/ItemStack;",
            remap = true), require = 0, expect = 1)
    private static ItemStack magicaland$unreservedOffHand(AbstractClientPlayerEntity player) {
        var stack = player.getOffHandStack();
        return TelekinesisToken.isToken(stack) ? ItemStack.EMPTY : stack;
    }
}
