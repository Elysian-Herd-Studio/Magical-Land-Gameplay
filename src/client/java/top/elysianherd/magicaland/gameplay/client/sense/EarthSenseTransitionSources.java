package top.elysianherd.magicaland.gameplay.client.sense;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/** 只关联本过渡声，防止取消后异步解码才启动旧声。 */
public final class EarthSenseTransitionSources {
    public interface CancellableSound { boolean magicaland$canStart(); }
    private static final Map<Object, CancellableSound> SOURCES = new ConcurrentHashMap<>();

    private EarthSenseTransitionSources() {}

    public static void register(Object source, CancellableSound sound) { SOURCES.put(source, sound); }
    public static boolean canPlay(Object source) {
        CancellableSound sound = SOURCES.get(source);
        return sound == null || sound.magicaland$canStart();
    }
    public static void closed(Object source) { SOURCES.remove(source); }
}
