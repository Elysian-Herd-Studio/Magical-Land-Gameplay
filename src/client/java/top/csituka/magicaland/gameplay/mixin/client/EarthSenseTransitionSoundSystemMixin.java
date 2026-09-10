package top.csituka.magicaland.gameplay.mixin.client;

import net.minecraft.client.sound.Channel;
import net.minecraft.client.sound.SoundInstance;
import net.minecraft.client.sound.SoundSystem;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import top.csituka.magicaland.gameplay.client.sense.EarthSenseTransitionSources;
import java.util.Map;

@Mixin(SoundSystem.class)
public abstract class EarthSenseTransitionSoundSystemMixin {
    @Shadow @Final private Map<SoundInstance, Channel.SourceManager> sources;

    @Inject(method = "play(Lnet/minecraft/client/sound/SoundInstance;)V", at = @At(value = "INVOKE",
            target = "Lnet/minecraft/client/sound/Channel$SourceManager;run(Ljava/util/function/Consumer;)V", ordinal = 0))
    private void magicaland$trackTransition(SoundInstance sound, CallbackInfo ci) {
        if (!(sound instanceof EarthSenseTransitionSources.CancellableSound transition)) return;
        Channel.SourceManager manager = sources.get(sound);
        if (manager != null) manager.run(source -> EarthSenseTransitionSources.register(source, transition));
    }
}
