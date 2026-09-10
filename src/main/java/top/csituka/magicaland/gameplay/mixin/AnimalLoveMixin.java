package top.csituka.magicaland.gameplay.mixin;

import net.minecraft.entity.passive.AbstractHorseEntity;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.item.ItemStack;
import net.minecraft.server.network.ServerPlayerEntity;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;
import top.csituka.magicaland.gameplay.easteregg.CarrotMisunderstanding;

@Mixin(AbstractHorseEntity.class)
public abstract class AnimalLoveMixin {
    @Inject(method = "receiveFood", at = @At(value = "INVOKE",
            target = "Lnet/minecraft/entity/passive/AbstractHorseEntity;lovePlayer(Lnet/minecraft/entity/player/PlayerEntity;)V",
            shift = At.Shift.AFTER))
    private void magicaland$afterSuccessfulFeed(PlayerEntity player, ItemStack food, CallbackInfoReturnable<Boolean> cir) {
        if (player instanceof ServerPlayerEntity serverPlayer)
            CarrotMisunderstanding.recordFeeding(serverPlayer, (AbstractHorseEntity) (Object) this, food);
    }
}
