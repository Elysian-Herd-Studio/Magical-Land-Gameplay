package top.elysianherd.magicaland.gameplay.client;

import java.util.Comparator;
import java.util.List;
import java.util.Set;
import java.util.UUID;

final class RemoteMagicAudioMix {
    static final int MAX_LOOPS = 6;
    static final double RANGE = 8;
    static final float BASE_VOLUME = .05f;
    record Source(UUID id, double distance) {}
    private RemoteMagicAudioMix() {}

    static List<Source> select(List<Source> sources, Set<UUID> playing) {
        return sources.stream().filter(source -> source.id() != null && Double.isFinite(source.distance())
                        && source.distance() >= 0 && source.distance() < RANGE)
                .sorted(Comparator.comparingDouble((Source source) -> source.distance() - (playing.contains(source.id()) ? .35 : 0))
                        .thenComparing(Source::id)).limit(MAX_LOOPS).toList();
    }

    static float volume(List<Source> sources) {
        double audible = sources.stream().mapToDouble(source -> Math.max(0, 1 - source.distance() / RANGE)).sum();
        return (float) (BASE_VOLUME / Math.max(1, audible * .6));
    }
}
