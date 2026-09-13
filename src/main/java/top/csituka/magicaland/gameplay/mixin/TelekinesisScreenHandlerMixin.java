package top.csituka.magicaland.gameplay.mixin;

import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.screen.ScreenHandler;
import net.minecraft.screen.slot.SlotActionType;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import top.csituka.magicaland.gameplay.remote.TelekinesisToken;

@Mixin(ScreenHandler.class)
public abstract class TelekinesisScreenHandlerMixin {
    @Inject(method="onSlotClick", at=@At("HEAD"), cancellable=true)
    private void reservedClick(int slot, int button, SlotActionType action, PlayerEntity player, CallbackInfo ci) {
        if (TelekinesisToken.blockedClick((ScreenHandler)(Object)this, slot, button, action, player)) {
            TelekinesisToken.resync(player); ci.cancel();
        }
    }
}
