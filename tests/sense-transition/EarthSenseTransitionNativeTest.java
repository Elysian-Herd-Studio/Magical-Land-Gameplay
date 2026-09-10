package top.csituka.magicaland.gameplay.client.sense;

import net.minecraft.client.sound.OggAudioStream;
import org.lwjgl.openal.*;
import java.nio.ByteBuffer;
import java.nio.file.Files;
import java.nio.file.Path;

/** 真正的 MC 解码和内存 loopback，不向任何扬声器输出。 */
public final class EarthSenseTransitionNativeTest {
    private static int checks;
    private static void check(boolean value, String message) {
        checks++;
        if (!value) throw new AssertionError(message);
    }
    public static void main(String[] args) throws Exception {
        Path folder = Path.of(args[0], "src/main/resources/assets/magicaland_gameplay/sounds/sense");
        ALC.createCapabilities(0);
        long device = SOFTLoopback.alcLoopbackOpenDeviceSOFT((CharSequence) null);
        ALCCapabilities caps = ALC.createCapabilities(device);
        long context = ALC10.alcCreateContext(device, new int[]{ALC10.ALC_FREQUENCY, 48000,
                SOFTLoopback.ALC_FORMAT_CHANNELS_SOFT, SOFTLoopback.ALC_STEREO_SOFT,
                SOFTLoopback.ALC_FORMAT_TYPE_SOFT, SOFTLoopback.ALC_FLOAT_SOFT, 0});
        check(context != 0 && ALC10.alcMakeContextCurrent(context), "memory-only context");
        AL.createCapabilities(caps);
        try {
            for (String name : new String[]{"enter", "exit"}) {
                int source = AL10.alGenSources();
                int buffer = AL10.alGenBuffers();
                try (var stream = new OggAudioStream(Files.newInputStream(folder.resolve(name + ".ogg")))) {
                    var format = stream.getFormat();
                    ByteBuffer pcm = stream.getBuffer();
                    check(format.getChannels() == 1 && format.getSampleRate() == 48000 && format.getSampleSizeInBits() == 16, "actual game decoder format");
                    check(Math.abs(pcm.remaining() / 2.0 / 48000 - 0.65) < 0.003, "actual game decoder duration");
                    AL10.alBufferData(buffer, AL10.AL_FORMAT_MONO16, pcm, 48000);
                    AL10.alSourcei(source, AL10.AL_BUFFER, buffer);
                    AL10.alSourceStop(source);
                    check(AL10.alGetSourcei(source, AL10.AL_SOURCE_STATE) == AL10.AL_INITIAL, "stop alone cannot reclaim initial source");
                    AL10.alSourcef(source, AL10.AL_GAIN, 0);
                    AL10.alSourcePlay(source);
                    float[] output = new float[4800 * 2];
                    SOFTLoopback.alcRenderSamplesSOFT(device, output, 4800);
                    for (float sample : output) check(sample == 0, "cancelled late start is completely silent");
                    AL10.alSourceStop(source);
                    check(AL10.alGetSourcei(source, AL10.AL_SOURCE_STATE) == AL10.AL_STOPPED, "muted start+stop allows vanilla channel reclaim");
                    check(AL10.alGetError() == AL10.AL_NO_ERROR, "native operations valid");
                } finally {
                    AL10.alDeleteSources(source);
                    AL10.alDeleteBuffers(buffer);
                }
            }
        } finally {
            ALC10.alcMakeContextCurrent(0);
            ALC10.alcDestroyContext(context);
            ALC10.alcCloseDevice(device);
        }
        System.out.println("EarthSenseTransitionNativeTest: " + checks + " checks passed");
    }
}
