package top.csituka.magicaland.gameplay.mixin.client;

import net.minecraft.client.network.ClientPlayNetworkHandler;
import net.minecraft.client.network.ClientPlayerEntity;
import net.minecraft.network.packet.c2s.play.ClientCommandC2SPacket;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import top.csituka.magicaland.gameplay.client.sense.EarthSenseClient;

@Mixin(ClientPlayerEntity.class)
public abstract class EarthSensePlayerInputMixin implements EarthSenseClient.SneakState {
    @Shadow private boolean lastSneaking;
    @Shadow @Final public ClientPlayNetworkHandler networkHandler;

    @Override public void magicaland$restoreSneak(boolean held) {
        if (lastSneaking == held) return;
        var mode = held ? ClientCommandC2SPacket.Mode.PRESS_SHIFT_KEY : ClientCommandC2SPacket.Mode.RELEASE_SHIFT_KEY;
        networkHandler.sendPacket(new ClientCommandC2SPacket((ClientPlayerEntity) (Object) this, mode));
        lastSneaking = held;
    }
}
