package top.csituka.magicaland.gameplay.mixin.client;

import net.minecraft.client.sound.Source;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import top.csituka.magicaland.gameplay.client.sense.EarthSenseAudio;

@Mixin(Source.class)
public abstract class EarthSenseSourceMixin implements EarthSenseAudio.SourceHandle {
    @Shadow @Final private int pointer;

    @Override public int magicaland$audioPointer() { return pointer; }

    @Inject(method = "close", at = @At("HEAD"))
    private void magicaland$releaseFilter(CallbackInfo ci) { EarthSenseAudio.sourceClosing(this); }
}
