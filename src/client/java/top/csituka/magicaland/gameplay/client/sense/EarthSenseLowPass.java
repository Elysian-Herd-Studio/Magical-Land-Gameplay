package top.csituka.magicaland.gameplay.client.sense;

import java.util.IdentityHashMap;
import java.util.Map;

/** 只由声音线程访问；直接滤波槽无法查询，冲突检查由接入层负责。 */
public final class EarthSenseLowPass {
    public interface Backend {
        long context();
        boolean supported();
        boolean sourceExists(int source);
        int createFilter();
        void highFrequencyGain(int filter, float gain);
        void attach(int source, int filter);
        void deleteFilter(int filter);
    }

    private static final class Entry {
        final int source;
        int filter;

        Entry(int source) { this.source = source; }
    }

    private final Backend backend;
    private final Map<Object, Entry> sources = new IdentityHashMap<>();
    private long context;
    private long lastNanos;
    private float amount;
    private boolean failed;

    public EarthSenseLowPass(Backend backend) { this.backend = backend; }

    public static float clamp(float value) {
        return Float.isFinite(value) ? Math.max(0, Math.min(1, value)) : 0;
    }

    public static float highFrequencyGain(float value) {
        return (float) Math.pow(0.045, clamp(value));
    }

    public void register(Object owner, int source) {
        if (!prepare() || sources.containsKey(owner)) return;
        Entry entry = new Entry(source);
        sources.put(owner, entry);
        apply(entry);
    }

    public void update(float target, long nowNanos) {
        if (!prepare()) return;
        double elapsed = lastNanos == 0 ? 0 : Math.max(0, Math.min(0.1, (nowNanos - lastNanos) / 1.0e9));
        lastNanos = nowNanos;
        float desired = clamp(target);
        amount += (desired - amount) * (float) (1 - Math.exp(-elapsed / 0.10));
        if (Math.abs(desired - amount) < 0.0005f) amount = desired;
        for (Entry entry : sources.values()) apply(entry);
    }

    public void remove(Object owner) {
        if (!prepare()) return;
        Entry entry = sources.remove(owner);
        if (entry != null) release(entry);
    }

    public void clear() {
        RuntimeException firstFailure = null;
        try {
            if (context != 0 && backend.context() == context && backend.supported()) {
                for (Entry entry : sources.values()) {
                    try { release(entry); }
                    catch (RuntimeException error) { if (firstFailure == null) firstFailure = error; }
                }
            }
        } finally {
            sources.clear();
            amount = 0;
            lastNanos = 0;
            failed = false;
        }
        if (firstFailure != null) throw firstFailure;
    }

    private boolean prepare() {
        long current = backend.context();
        if (current != context) {
            // 旧 context 的整数句柄不能拿到新设备删除。
            sources.clear();
            context = current;
            amount = 0;
            lastNanos = 0;
            failed = false;
        }
        return current != 0 && !failed && backend.supported();
    }

    private void apply(Entry entry) {
        if (!backend.sourceExists(entry.source)) {
            delete(entry);
            return;
        }
        if (amount <= 0) {
            release(entry);
            return;
        }
        if (entry.filter == 0) entry.filter = backend.createFilter();
        if (entry.filter == 0) return;
        backend.highFrequencyGain(entry.filter, highFrequencyGain(amount));
        // EFX 在附加时复制参数，修改 filter 后必须重新附加。
        backend.attach(entry.source, entry.filter);
    }

    private void release(Entry entry) {
        if (entry.filter == 0) return;
        try {
            if (backend.sourceExists(entry.source)) backend.attach(entry.source, 0);
        } finally { delete(entry); }
    }

    private void delete(Entry entry) {
        if (entry.filter != 0) backend.deleteFilter(entry.filter);
        entry.filter = 0;
    }

    public void failClosed() {
        try { clear(); } finally { failed = true; }
    }

    int trackedSources() { return sources.size(); }
    float amount() { return amount; }
}
