package top.csituka.magicaland.gameplay.mixin.client;

import net.minecraft.client.sound.Channel;
import net.minecraft.client.sound.SoundExecutor;
import net.minecraft.client.sound.SoundInstance;
import net.minecraft.client.sound.SoundSystem;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import top.csituka.magicaland.gameplay.client.sense.EarthSenseAudio;

import java.util.Map;

@Mixin(SoundSystem.class)
public abstract class EarthSenseSoundSystemMixin {
    @Shadow @Final private SoundExecutor taskQueue;
    @Shadow @Final private Channel channel;
    @Shadow @Final private Map<SoundInstance, Channel.SourceManager> sources;
    @Shadow private boolean started;

    @Inject(method = "<init>", at = @At("TAIL"))
    private void magicaland$bindAudio(CallbackInfo ci) { EarthSenseAudio.bind(taskQueue); }

    @Inject(method = "play(Lnet/minecraft/client/sound/SoundInstance;)V", at = @At(value = "INVOKE",
            target = "Lnet/minecraft/client/sound/Channel$SourceManager;run(Ljava/util/function/Consumer;)V", ordinal = 0))
    private void magicaland$registerWorldSound(SoundInstance sound, CallbackInfo ci) {
        if (!EarthSenseAudio.accepts(sound)) return;
        Channel.SourceManager manager = sources.get(sound);
        if (manager != null) manager.run(EarthSenseAudio::register);
    }

    @Inject(method = "tick(Z)V", at = @At("TAIL"))
    private void magicaland$updateAudio(boolean paused, CallbackInfo ci) {
        if (!started) return;
        sources.forEach((sound, manager) -> {
            if (EarthSenseAudio.accepts(sound)) manager.run(EarthSenseAudio::register);
        });
        channel.execute(unused -> EarthSenseAudio.update());
    }

    @Inject(method = "stopAll", at = @At("HEAD"))
    private void magicaland$releaseBeforeRestart(CallbackInfo ci) {
        if (started) EarthSenseAudio.beforeStopAll(taskQueue);
    }
}
