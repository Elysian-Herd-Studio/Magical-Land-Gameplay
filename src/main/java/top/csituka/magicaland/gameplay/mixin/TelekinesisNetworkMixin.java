package top.csituka.magicaland.gameplay.mixin;

import net.minecraft.network.packet.c2s.play.ClickSlotC2SPacket;
import net.minecraft.network.packet.c2s.play.CreativeInventoryActionC2SPacket;
import net.minecraft.network.packet.c2s.play.PlayerActionC2SPacket;
import net.minecraft.network.packet.c2s.play.PlayerInteractEntityC2SPacket;
import net.minecraft.network.packet.c2s.play.PlayerInteractBlockC2SPacket;
import net.minecraft.network.packet.c2s.play.PlayerInteractItemC2SPacket;
import net.minecraft.server.network.ServerPlayNetworkHandler;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.util.Hand;
import net.minecraft.util.math.Vec3d;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import top.csituka.magicaland.gameplay.remote.TelekinesisToken;

@Mixin(ServerPlayNetworkHandler.class)
public abstract class TelekinesisNetworkMixin {
    @Shadow public ServerPlayerEntity player;
    @Unique private boolean magicaland$serverThread() { return player.getServer() != null && player.getServer().isOnThread(); }
    @Unique private void magicaland$deny(CallbackInfo ci) { TelekinesisToken.resync(player); ci.cancel(); }
    @Unique private boolean magicaland$reserved(Hand hand) { return TelekinesisToken.isToken(player.getStackInHand(hand)); }

    @Inject(method="onClickSlot", at=@At("HEAD"), cancellable=true)
    private void reservedClick(ClickSlotC2SPacket packet, CallbackInfo ci) {
        if (magicaland$serverThread() && TelekinesisToken.blockedClick(player.currentScreenHandler, packet.getSlot(),
                packet.getButton(), packet.getActionType(), player)) magicaland$deny(ci);
    }
    @Inject(method="onCreativeInventoryAction", at=@At("HEAD"), cancellable=true)
    private void reservedCreative(CreativeInventoryActionC2SPacket packet, CallbackInfo ci) {
        if (!magicaland$serverThread()) return;
        int slot = packet.getSlot();
        if (TelekinesisToken.isToken(packet.getItemStack()) || slot >= 0 && slot < player.playerScreenHandler.slots.size()
                && TelekinesisToken.isToken(player.playerScreenHandler.getSlot(slot).getStack())) magicaland$deny(ci);
    }
    @Inject(method="onPlayerAction", at=@At("HEAD"), cancellable=true)
    private void reservedAction(PlayerActionC2SPacket packet, CallbackInfo ci) {
        if (!magicaland$serverThread()) return;
        boolean block = switch (packet.getAction()) {
            case SWAP_ITEM_WITH_OFFHAND -> magicaland$reserved(Hand.MAIN_HAND) || magicaland$reserved(Hand.OFF_HAND);
            case DROP_ITEM, DROP_ALL_ITEMS, START_DESTROY_BLOCK, STOP_DESTROY_BLOCK -> magicaland$reserved(Hand.MAIN_HAND);
            default -> false;
        };
        if (block) {
            if (packet.getAction() == PlayerActionC2SPacket.Action.START_DESTROY_BLOCK
                    || packet.getAction() == PlayerActionC2SPacket.Action.STOP_DESTROY_BLOCK)
                player.networkHandler.updateSequence(packet.getSequence());
            magicaland$deny(ci);
        }
    }
    @Inject(method="onPlayerInteractEntity", at=@At("HEAD"), cancellable=true)
    private void reservedEntity(PlayerInteractEntityC2SPacket packet, CallbackInfo ci) {
        if (!magicaland$serverThread()) return;
        boolean[] blocked = {false};
        packet.handle(new PlayerInteractEntityC2SPacket.Handler() {
            public void interact(Hand hand) { blocked[0] = magicaland$reserved(hand); }
            public void interactAt(Hand hand, Vec3d pos) { blocked[0] = magicaland$reserved(hand); }
            public void attack() { blocked[0] = magicaland$reserved(Hand.MAIN_HAND); }
        });
        if (blocked[0]) magicaland$deny(ci);
    }
    @Inject(method="onPlayerInteractItem", at=@At("HEAD"), cancellable=true)
    private void reservedUse(PlayerInteractItemC2SPacket packet, CallbackInfo ci) {
        if (magicaland$serverThread() && magicaland$reserved(packet.getHand())) {
            player.networkHandler.updateSequence(packet.getSequence());
            magicaland$deny(ci);
        }
    }
    @Inject(method="onPlayerInteractBlock", at=@At("HEAD"), cancellable=true)
    private void reservedBlock(PlayerInteractBlockC2SPacket packet, CallbackInfo ci) {
        if (magicaland$serverThread() && magicaland$reserved(packet.getHand())) {
            player.networkHandler.updateSequence(packet.getSequence());
            magicaland$deny(ci);
        }
    }
}
