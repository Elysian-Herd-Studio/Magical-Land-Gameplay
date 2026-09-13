package top.csituka.magicaland.gameplay.mixin;

import java.util.Set;
import net.minecraft.network.packet.c2s.play.PlayerMoveC2SPacket;
import net.minecraft.network.packet.s2c.play.PositionFlag;
import net.minecraft.server.network.ServerPlayNetworkHandler;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.util.math.Vec3d;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import top.csituka.magicaland.gameplay.pegasus.PegasusFlightServer;

@Mixin(ServerPlayNetworkHandler.class)
public abstract class PegasusFlightNetworkMixin {
    @Shadow public ServerPlayerEntity player;
    @Shadow private Vec3d requestedTeleportPos;
    @Shadow private boolean floating;
    @Shadow private int floatingTicks;
    @Inject(method = "onPlayerMove", at = @At(value = "INVOKE", target = "Lnet/minecraft/network/NetworkThreadUtils;forceMainThread(Lnet/minecraft/network/packet/Packet;Lnet/minecraft/network/listener/PacketListener;Lnet/minecraft/server/world/ServerWorld;)V", shift = At.Shift.AFTER), cancellable = true)
    private void magicaland$flightMovement(PlayerMoveC2SPacket packet, CallbackInfo ci) {
        if (requestedTeleportPos == null && !PegasusFlightServer.allowMove(player, packet)) {
            floating = false; floatingTicks = 0; ci.cancel();
        }
    }
    @Inject(method = "tick", at = @At("HEAD"))
    private void magicaland$flightNotFloating(CallbackInfo ci) {
        if (PegasusFlightServer.physicsActive(player)) { floating = false; floatingTicks = 0; }
    }
    @Inject(method = "requestTeleport(DDDFFLjava/util/Set;)V", at = @At("HEAD"))
    private void magicaland$flightTeleport(double x, double y, double z, float yaw, float pitch, Set<PositionFlag> flags, CallbackInfo ci) {
        PegasusFlightServer.teleported(player);
    }
}
