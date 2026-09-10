package top.csituka.magicaland.gameplay.client.sense;

import org.lwjgl.openal.*;
import java.util.ArrayList;
import java.util.List;

/** 只渲染到内存，不打开扬声器、不启动 Minecraft。 */
public final class EarthSenseAudioLoopbackTest {
    private static int checks;
    private static final int RATE = 48000;
    private static void check(boolean ok, String message) {
        checks++;
        if (!ok) throw new AssertionError(message);
    }
    private static final class Recording implements EarthSenseLowPass.Backend {
        final EarthSenseAudio.OpenAlBackend actual = new EarthSenseAudio.OpenAlBackend();
        final List<Integer> created = new ArrayList<>();
        public long context() { return actual.context(); }
        public boolean supported() { return actual.supported(); }
        public boolean sourceExists(int source) { return actual.sourceExists(source); }
        public int createFilter() { int result = actual.createFilter(); created.add(result); return result; }
        public void highFrequencyGain(int filter, float gain) { actual.highFrequencyGain(filter, gain); }
        public void attach(int source, int filter) { actual.attach(source, filter); }
        public void deleteFilter(int filter) { actual.deleteFilter(filter); }
    }
    private static float[] render(long device, int samples) {
        float[] result = new float[samples * 2];
        SOFTLoopback.alcRenderSamplesSOFT(device, result, samples);
        return result;
    }
    private static double amplitude(float[] samples, int frequency) {
        double sin = 0, cos = 0;
        int count = samples.length / 2;
        for (int i = 0; i < count; i++) {
            double angle = 2 * Math.PI * frequency * i / RATE;
            sin += samples[i * 2] * Math.sin(angle);
            cos += samples[i * 2] * Math.cos(angle);
        }
        return Math.hypot(sin, cos) * 2 / count;
    }
    public static void main(String[] args) {
        ALC.createCapabilities(0);
        long device = SOFTLoopback.alcLoopbackOpenDeviceSOFT((CharSequence) null);
        check(device != 0, "loopback device available");
        ALCCapabilities capabilities = ALC.createCapabilities(device);
        long context = ALC10.alcCreateContext(device, new int[]{
                ALC10.ALC_FREQUENCY, RATE,
                SOFTLoopback.ALC_FORMAT_CHANNELS_SOFT, SOFTLoopback.ALC_STEREO_SOFT,
                SOFTLoopback.ALC_FORMAT_TYPE_SOFT, SOFTLoopback.ALC_FLOAT_SOFT, 0});
        check(context != 0 && ALC10.alcMakeContextCurrent(context), "loopback context current");
        AL.createCapabilities(capabilities);
        check(AL.getCapabilities().ALC_EXT_EFX, "real EFX supported");
        Recording backend = new Recording();
        EarthSenseLowPass state = new EarthSenseLowPass(backend);
        int source = AL10.alGenSources();
        int buffer = AL10.alGenBuffers();
        try {
            short[] pcm = new short[RATE];
            for (int i = 0; i < pcm.length; i++) pcm[i] = (short) (8000 * (
                    Math.sin(2 * Math.PI * 250 * i / RATE) + Math.sin(2 * Math.PI * 10000 * i / RATE)));
            AL10.alBufferData(buffer, AL10.AL_FORMAT_MONO16, pcm, RATE);
            AL10.alSourcei(source, AL10.AL_BUFFER, buffer);
            AL10.alSourcei(source, AL10.AL_LOOPING, AL10.AL_TRUE);
            AL10.alSourcei(source, AL10.AL_SOURCE_RELATIVE, AL10.AL_TRUE);
            AL10.alSourcef(source, AL10.AL_GAIN, 0.37f);
            AL10.alSourcePlay(source);
            render(device, RATE / 10);
            float[] original = render(device, RATE / 4);
            Object owner = new Object();
            state.register(owner, source);
            for (int i = 0; i <= 40; i++) state.update(0.6f, 1_000_000_000L + i * 50_000_000L);
            check(backend.created.size() == 1, "one real filter per source");
            int filter = backend.created.get(0);
            check(EXTEfx.alGetFilteri(filter, EXTEfx.AL_FILTER_TYPE) == EXTEfx.AL_FILTER_LOWPASS, "real lowpass type");
            check(EXTEfx.alGetFilterf(filter, EXTEfx.AL_LOWPASS_GAIN) == 1, "no full-band amplification");
            check(Math.abs(AL10.alGetSourcef(source, AL10.AL_GAIN) - 0.37f) < 0.00001, "source volume unchanged");
            render(device, RATE / 10);
            float[] muffled = render(device, RATE / 4);
            double lowRatio = amplitude(muffled, 250) / amplitude(original, 250);
            double highRatio = amplitude(muffled, 10000) / amplitude(original, 10000);
            check(lowRatio > 0.7 && lowRatio <= 1.02, "low frequencies retained without boost: " + lowRatio);
            check(highRatio > 0 && highRatio < 0.4, "high frequencies reduced: " + highRatio);
            state.clear();
            check(!EXTEfx.alIsFilter(filter), "real filter released");
            check(Math.abs(AL10.alGetSourcef(source, AL10.AL_GAIN) - 0.37f) < 0.00001, "restore keeps user volume");
            render(device, RATE / 10);
            float[] restored = render(device, RATE / 4);
            double restoredRatio = amplitude(restored, 10000) / amplitude(original, 10000);
            check(Math.abs(restoredRatio - 1) < 0.01, "empty-slot restore restores spectrum: " + restoredRatio);
            // 证实不能用 getter 检测第三方滤波，生产代码不能依赖它。
            AL10.alGetSourcei(source, EXTEfx.AL_DIRECT_FILTER);
            check(AL10.alGetError() == AL10.AL_INVALID_ENUM, "direct filter really is write-only");
            check(AL10.alGetError() == AL10.AL_NO_ERROR, "no residual OpenAL error");
            System.out.println("EarthSenseAudioLoopbackTest: " + checks + " checks passed; 250Hz=" + lowRatio + ", 10kHz=" + highRatio + ", restored=" + restoredRatio);
        } finally {
            state.clear();
            AL10.alSourceStop(source);
            AL10.alDeleteSources(source);
            AL10.alDeleteBuffers(buffer);
            ALC10.alcMakeContextCurrent(0);
            ALC10.alcDestroyContext(context);
            ALC10.alcCloseDevice(device);
        }
    }
}
