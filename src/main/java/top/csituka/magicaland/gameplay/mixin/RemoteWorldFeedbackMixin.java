package top.csituka.magicaland.gameplay.mixin;

import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.network.packet.s2c.play.PlaySoundS2CPacket;
import net.minecraft.network.packet.s2c.play.WorldEventS2CPacket;
import net.minecraft.registry.entry.RegistryEntry;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.sound.SoundCategory;
import net.minecraft.sound.SoundEvent;
import net.minecraft.util.math.BlockPos;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import top.csituka.magicaland.gameplay.remote.RemoteActionContext;

@Mixin(ServerWorld.class)
public abstract class RemoteWorldFeedbackMixin {
    @Inject(method="syncWorldEvent",at=@At("TAIL"))
    private void magicaland$remoteEvent(PlayerEntity excluded,int event,BlockPos pos,int data,CallbackInfo ci) {
        var action=RemoteActionContext.current();
        if (action!=null && action.player==excluded && action.tool.getWorld()==(Object)this)
            action.player.networkHandler.sendPacket(new WorldEventS2CPacket(event,pos,data,false));
    }
    @Inject(method="playSound(Lnet/minecraft/entity/player/PlayerEntity;DDDLnet/minecraft/registry/entry/RegistryEntry;Lnet/minecraft/sound/SoundCategory;FFJ)V",at=@At("TAIL"))
    private void magicaland$remoteSound(PlayerEntity excluded,double x,double y,double z,RegistryEntry<SoundEvent> sound,
            SoundCategory category,float volume,float pitch,long seed,CallbackInfo ci) {
        var action=RemoteActionContext.current();
        if (action==null || action.tool.getWorld()!=(Object)this) return;
        double radius=sound.value().getDistanceToTravel(volume);
        boolean missed=excluded==action.player || action.player.squaredDistanceTo(x,y,z)>=radius*radius;
        if (missed && action.tool.squaredDistanceTo(x,y,z)<radius*radius)
            action.player.networkHandler.sendPacket(new PlaySoundS2CPacket(sound,category,x,y,z,volume,pitch,seed));
    }
}
