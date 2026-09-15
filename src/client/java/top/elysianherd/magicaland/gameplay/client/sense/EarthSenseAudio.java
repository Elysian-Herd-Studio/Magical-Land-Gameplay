package top.elysianherd.magicaland.gameplay.client.sense;

import com.mojang.logging.LogUtils;
import net.fabricmc.loader.api.FabricLoader;
import net.minecraft.client.sound.SoundExecutor;
import net.minecraft.client.sound.SoundInstance;
import net.minecraft.sound.SoundCategory;
import org.lwjgl.openal.AL;
import org.lwjgl.openal.AL10;
import org.lwjgl.openal.ALC10;
import org.lwjgl.openal.EXTEfx;
import org.slf4j.Logger;

import java.util.Set;

public final class EarthSenseAudio {
    public interface SourceHandle { int magicaland$audioPointer(); }

    private static final Logger LOGGER = LogUtils.getLogger();
    private static final Set<String> CONFLICTING_MODS = Set.of(
            "sound_physics_remastered", "soundphysics", "sound_physics", "soundfilters",
            "sound_filters", "dynamic_sound_filters", "dynamicsoundfilters", "ambientsounds",
            "presencefootsteps", "enhanced_audio");
    private static final EarthSenseLowPass FILTERS = new EarthSenseLowPass(new OpenAlBackend());
    private static volatile SoundExecutor executor;
    private static volatile float target;
    private static volatile boolean enabled;
    private static boolean initialized;
    private static boolean warned;

    private EarthSenseAudio() {}

    public static void init() {
        if (initialized) return;
        initialized = true;
        String conflict = CONFLICTING_MODS.stream().filter(FabricLoader.getInstance()::isModLoaded).sorted().findFirst().orElse(null);
        enabled = conflict == null;
        if (conflict != null) LOGGER.info("陆马感知：检测到声音模组 {}，保留其原声，不叠加闷化。", conflict);
    }

    public static void tick(float amount) { target = enabled ? EarthSenseLowPass.clamp(amount) : 0; }

    public static void reset() {
        target = 0;
        SoundExecutor queue = executor;
        if (queue != null) queue.execute(() -> safely(FILTERS::clear));
    }

    public static void bind(SoundExecutor queue) { executor = queue; }

    public static boolean accepts(SoundInstance sound) {
        return enabled && accepts(sound.getCategory(), sound.isRelative(), sound.getId().getPath());
    }

    public static boolean accepts(SoundCategory category, boolean relative, String path) {
        if (relative || path.startsWith("ui.") || path.startsWith("ui/") || path.contains("narrator")) return false;
        return category == SoundCategory.BLOCKS || category == SoundCategory.HOSTILE
                || category == SoundCategory.NEUTRAL || category == SoundCategory.PLAYERS
                || category == SoundCategory.WEATHER || category == SoundCategory.AMBIENT;
    }

    public static void register(Object source) {
        if (enabled && onSoundThread() && source instanceof SourceHandle handle) {
            safely(() -> FILTERS.register(source, handle.magicaland$audioPointer()));
        }
    }

    public static void update() {
        if (enabled && onSoundThread()) safely(() -> FILTERS.update(target, System.nanoTime()));
    }

    public static void sourceClosing(Object source) {
        SoundExecutor queue = executor;
        if (enabled && queue != null) queue.submitAndJoin(() -> safely(() -> FILTERS.remove(source)));
    }

    public static void beforeStopAll(SoundExecutor queue) {
        // 原版随后会 restart 队列、销毁 context；必须先在声音线程完成清理。
        if (enabled) queue.submitAndJoin(() -> safely(FILTERS::clear));
    }

    private static boolean onSoundThread() { return executor != null && executor.isOnThread(); }

    private static void safely(Runnable action) {
        try {
            action.run();
        } catch (RuntimeException | LinkageError error) {
            if (!warned) {
                warned = true;
                LOGGER.warn("陆马感知：当前声音设备无法使用闷化，已保留原声。", error);
            }
            try { FILTERS.failClosed(); } catch (RuntimeException | LinkageError ignored) { }
        }
    }

    static final class OpenAlBackend implements EarthSenseLowPass.Backend {
        @Override public long context() { return ALC10.alcGetCurrentContext(); }
        @Override public boolean supported() {
            boolean supported = AL.getCapabilities().ALC_EXT_EFX;
            if (!supported && !warned) {
                warned = true;
                LOGGER.info("陆马感知：当前声音设备不支持 EFX 低通，已保留原声。");
            }
            return supported;
        }
        @Override public boolean sourceExists(int source) { return AL10.alIsSource(source); }

        @Override public int createFilter() {
            checkError();
            int filter = EXTEfx.alGenFilters();
            checkError();
            if (filter == 0 || !EXTEfx.alIsFilter(filter)) throw new IllegalStateException("No EFX filter allocated");
            try {
                EXTEfx.alFilteri(filter, EXTEfx.AL_FILTER_TYPE, EXTEfx.AL_FILTER_LOWPASS);
                EXTEfx.alFilterf(filter, EXTEfx.AL_LOWPASS_GAIN, 1);
                checkError();
                return filter;
            } catch (RuntimeException error) {
                EXTEfx.alDeleteFilters(filter);
                throw error;
            }
        }

        @Override public void highFrequencyGain(int filter, float gain) {
            EXTEfx.alFilterf(filter, EXTEfx.AL_LOWPASS_GAINHF, gain);
            checkError();
        }
        @Override public void attach(int source, int filter) {
            AL10.alSourcei(source, EXTEfx.AL_DIRECT_FILTER, filter);
            checkError();
        }
        @Override public void deleteFilter(int filter) { if (EXTEfx.alIsFilter(filter)) EXTEfx.alDeleteFilters(filter); }

        private static void checkError() {
            int error = AL10.alGetError();
            if (error != AL10.AL_NO_ERROR) throw new IllegalStateException("OpenAL error " + error);
        }
    }
}
