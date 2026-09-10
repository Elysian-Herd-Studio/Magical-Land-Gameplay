package top.csituka.magicaland.gameplay.mixin.client;

import net.minecraft.client.sound.Source;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import top.csituka.magicaland.gameplay.client.sense.EarthSenseTransitionSources;

@Mixin(Source.class)
public abstract class EarthSenseTransitionSourceMixin {
    @Inject(method = "play", at = @At("HEAD"))
    private void magicaland$rejectCancelledTransition(CallbackInfo ci) {
        if (!EarthSenseTransitionSources.canPlay(this)) ((Source) (Object) this).setVolume(0);
    }

    @Inject(method = "play", at = @At("TAIL"))
    private void magicaland$stopCancelledTransition(CallbackInfo ci) {
        // INITIAL 声源直接 stop 不会结束；静音启动后停止，让原版正常回收。
        if (!EarthSenseTransitionSources.canPlay(this)) ((Source) (Object) this).stop();
    }

    @Inject(method = "close", at = @At("HEAD"))
    private void magicaland$clearTransition(CallbackInfo ci) { EarthSenseTransitionSources.closed(this); }
}
