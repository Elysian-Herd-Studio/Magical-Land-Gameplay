package top.csituka.magicaland.gameplay.mixin;

import net.minecraft.server.network.ServerPlayNetworkHandler;
import net.minecraft.server.network.ServerPlayerEntity;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import top.csituka.magicaland.gameplay.remote.RemoteToolServer;

@Mixin(ServerPlayNetworkHandler.class)
public abstract class RemoteBodyActionMixin {
    @Shadow public ServerPlayerEntity player;
    @Inject(method={"onPlayerAction","onPlayerInteractItem","onPlayerInteractBlock","onPlayerInteractEntity","onUpdateSelectedSlot","onClickSlot"},at=@At("HEAD"))
    private void endRemoteBeforeBodyAction(CallbackInfo ci) {
        if (player.getServer()!=null && player.getServer().isOnThread()) RemoteToolServer.stop(player);
    }
}
