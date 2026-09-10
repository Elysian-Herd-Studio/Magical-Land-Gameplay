package top.csituka.magicaland.gameplay.client.sense;

import net.minecraft.sound.SoundCategory;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;

public final class EarthSenseLowPassTest {
    private static int checks;
    private static void check(boolean value, String message) {
        checks++;
        if (!value) throw new AssertionError(message);
    }
    private static final class Fake implements EarthSenseLowPass.Backend {
        long context = 1;
        boolean supported = true;
        int next = 1;
        int deleted;
        int failDetach = -1;
        final Set<Integer> alive = new HashSet<>();
        final Map<Integer, Integer> slots = new HashMap<>();
        final Map<Integer, Float> filters = new HashMap<>();
        public long context() { return context; }
        public boolean supported() { return supported; }
        public boolean sourceExists(int source) { return alive.contains(source); }
        public int createFilter() { int id = next++; filters.put(id, 1f); return id; }
        public void highFrequencyGain(int filter, float gain) { check(gain > 0 && gain <= 1, "passive HF gain"); filters.put(filter, gain); }
        public void attach(int source, int filter) {
            if (source == failDetach && filter == 0) throw new IllegalStateException("injected failure");
            check(alive.contains(source), "no stale source writes");
            check(filter == 0 || filters.containsKey(filter), "owned live filter");
            slots.put(source, filter);
        }
        public void deleteFilter(int filter) { check(filters.remove(filter) != null, "delete once"); deleted++; }
    }
    private static void advance(EarthSenseLowPass state, float target, int ticks) {
        for (int i = 0; i <= ticks; i++) state.update(target, 1_000_000_000L + i * 50_000_000L);
    }
    public static void main(String[] args) {
        for (int i = 0; i <= 10000; i++) {
            float gain = EarthSenseLowPass.highFrequencyGain(i / 10000f);
            check(gain <= 1 && gain >= 0.0449, "gain bounds");
            if (i > 0) check(gain <= EarthSenseLowPass.highFrequencyGain((i - 1) / 10000f), "monotone darkness");
        }
        check(EarthSenseLowPass.highFrequencyGain(0) == 1, "neutral gain");
        check(EarthSenseLowPass.clamp(Float.NaN) == 0 && EarthSenseLowPass.clamp(Float.POSITIVE_INFINITY) == 0, "invalid input neutral");
        for (SoundCategory category : SoundCategory.values()) {
            check(!EarthSenseAudio.accepts(category, true, "entity.pig.ambient"), "relative/UI excluded");
            check(!EarthSenseAudio.accepts(category, false, "ui.button.click"), "UI ID excluded");
            check(!EarthSenseAudio.accepts(category, false, "narrator.test"), "narration excluded");
        }
        check(EarthSenseAudio.accepts(SoundCategory.BLOCKS, false, "block.stone.step"), "world block accepted");
        check(EarthSenseAudio.accepts(SoundCategory.HOSTILE, false, "entity.zombie.ambient"), "world mob accepted");
        check(!EarthSenseAudio.accepts(SoundCategory.MASTER, false, "custom.menu"), "master excluded");
        check(!EarthSenseAudio.accepts(SoundCategory.MUSIC, false, "music.game"), "music excluded");
        check(!EarthSenseAudio.accepts(SoundCategory.VOICE, false, "custom.voice"), "voice excluded");
        Fake backend = new Fake();
        backend.alive.add(8);
        EarthSenseLowPass state = new EarthSenseLowPass(backend);
        Object owner = new Object();
        state.register(owner, 8);
        check(backend.filters.isEmpty(), "inactive registration does not allocate");
        advance(state, 0.6f, 40);
        check(Math.abs(state.amount() - 0.6f) < 0.00001, "smooth entry reaches target");
        check(backend.filters.size() == 1 && backend.slots.get(8) != 0, "existing sound filtered");
        int existing = backend.slots.get(8);
        state.register(owner, 8);
        check(backend.filters.size() == 1 && backend.slots.get(8) == existing, "repeated registration stable");
        float previous = state.amount();
        state.update(0, 3_000_000_000L);
        check(state.amount() == previous, "same timestamp does not integrate twice");
        backend.alive.add(9);
        Object newOwner = new Object();
        state.register(newOwner, 9);
        check(backend.slots.get(9) != 0, "new sound filtered immediately");
        for (int i = 1; i <= 40; i++) state.update(0, 3_000_000_000L + i * 50_000_000L);
        check(state.amount() == 0 && backend.filters.isEmpty(), "exit reaches transparent and releases filters");
        check(backend.slots.get(8) == 0 && backend.slots.get(9) == 0, "exit restores empty direct slots");
        state.update(1, 5_100_000_000L);
        state.remove(owner);
        check(backend.slots.get(8) == 0 && state.trackedSources() == 1, "source close cleanup");
        backend.alive.remove(9);
        state.update(1, 5_150_000_000L);
        check(backend.filters.isEmpty(), "deleted source never written and filter released");
        state.remove(newOwner);
        Object recycled = new Object();
        state.register(recycled, 8);
        state.remove(owner);
        check(state.trackedSources() == 1 && backend.slots.get(8) != 0, "old owner cannot remove reused integer source");
        int deletes = backend.deleted;
        backend.context = 2;
        backend.filters.clear(); backend.slots.clear();
        state.update(1, 6_000_000_000L);
        check(state.trackedSources() == 0 && backend.deleted == deletes, "device change discards old integer handles");
        backend.supported = false;
        state.register(owner, 8); state.update(1, 6_100_000_000L);
        check(backend.filters.isEmpty(), "unsupported EFX no-op");
        backend.supported = true;
        state.register(owner, 8); advance(state, 1, 40); state.clear();
        check(state.trackedSources() == 0 && backend.filters.isEmpty(), "reset releases state");
        for (int hz : new int[]{20, 30, 60, 144}) {
            Fake f = new Fake();
            EarthSenseLowPass s = new EarthSenseLowPass(f);
            s.update(0.6f, 1_000_000_000L);
            for (int i = 1; i <= hz; i++) s.update(0.6f, 1_000_000_000L + Math.round(i * 1.0e9 / hz));
            check(Math.abs(s.amount() - 0.6f) < 0.0001, "time based smoothing " + hz);
        }
        Fake failure = new Fake();
        failure.alive.add(1); failure.alive.add(2);
        EarthSenseLowPass broken = new EarthSenseLowPass(failure);
        broken.register(new Object(), 1); broken.register(new Object(), 2); advance(broken, 1, 40);
        failure.failDetach = 1;
        try { broken.failClosed(); } catch (IllegalStateException expected) { }
        check(failure.filters.isEmpty() && broken.trackedSources() == 0, "one cleanup failure does not leak remaining handles");
        broken.register(new Object(), 2);
        check(broken.trackedSources() == 0, "failed device held disabled until reset/context change");
        System.out.println("EarthSenseLowPassTest: " + checks + " checks passed");
    }
}
