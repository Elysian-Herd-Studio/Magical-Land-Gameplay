package top.csituka.magicaland.gameplay.mixin;

import net.minecraft.network.packet.c2s.play.PlayerMoveC2SPacket;
import net.minecraft.server.network.ServerPlayNetworkHandler;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.util.math.Vec3d;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import top.csituka.magicaland.gameplay.levitation.UnicornLevitationServer;

@Mixin(ServerPlayNetworkHandler.class)
public abstract class UnicornLevitationNetworkMixin {
    @Shadow public ServerPlayerEntity player;
    @Shadow private Vec3d requestedTeleportPos;
    @Shadow private boolean floating;
    @Inject(method = "onPlayerMove", at = @At(value = "INVOKE", target = "Lnet/minecraft/network/NetworkThreadUtils;forceMainThread(Lnet/minecraft/network/packet/Packet;Lnet/minecraft/network/listener/PacketListener;Lnet/minecraft/server/world/ServerWorld;)V", shift = At.Shift.AFTER), cancellable = true)
    private void magicaland$validateLevitation(PlayerMoveC2SPacket packet, CallbackInfo ci) {
        if (requestedTeleportPos == null && !UnicornLevitationServer.allowMove(player, packet)) {
            if (UnicornLevitationServer.physicsActive(player)) floating = false;
            ci.cancel();
        }
    }
    @Inject(method = "onPlayerMove", at = @At("RETURN"))
    private void magicaland$controlledFlight(PlayerMoveC2SPacket packet, CallbackInfo ci) {
        if (UnicornLevitationServer.physicsActive(player)) {
            floating = false;
            player.setSprinting(false);
        }
    }
}
