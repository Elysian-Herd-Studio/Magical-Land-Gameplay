package top.elysianherd.magicaland.gameplay.sense;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/** 身份只用于服务端关联；发出的短号仅在当前会话内有效。 */
public final class EarthSenseSignals {
    public record Observation(UUID key, int kind, double x, double y, double z, float width, float height,
                              double distance, int activity) {}
    private final Map<UUID, Track> tracks = new HashMap<>();
    private int nextId;
    public EarthSenseSignals() { this(1); }
    EarthSenseSignals(int firstId) { nextId=firstId; }
    public List<EarthSenseProtocol.Signal> update(List<Observation> observations, long tick) {
        return update(observations, tick, EarthSenseRules.RANGE);
    }
    public List<EarthSenseProtocol.Signal> update(List<Observation> observations, long tick, double range) {
        var selected = observations.stream().filter(EarthSenseSignals::valid)
                .filter(observation -> EarthSenseRules.strength(observation.distance, range) > 0)
                .sorted(Comparator.comparingDouble(Observation::distance).thenComparing(Observation::key))
                .limit(EarthSenseProtocol.MAX_SIGNALS).toList();
        var current = new HashSet<UUID>();
        var result = new ArrayList<EarthSenseProtocol.Signal>(selected.size());
        for (var observation:selected) {
            if (!current.add(observation.key)) continue;
            Track track=tracks.get(observation.key);
            if (track==null) {
                if (nextId>EarthSenseProtocol.MAX_SIGNAL_ID) throw new IllegalStateException("Sense IDs exhausted");
                track=new Track(nextId++); tracks.put(observation.key,track);
            }
            if (observation.activity==EarthSenseRules.LAND || tick>=track.nextPulse
                    || EarthSenseRules.interval(observation.activity)<EarthSenseRules.interval(track.activity)) {
                track.pulse=(track.pulse+1)&255;
                track.nextPulse=tick+EarthSenseRules.interval(observation.activity);
            }
            track.activity=observation.activity;
            result.add(new EarthSenseProtocol.Signal(track.id,observation.kind,quarter(observation.x),
                    quarter(observation.y),quarter(observation.z),size(observation.width,EarthSenseProtocol.MAX_WIDTH),
                    size(observation.height,EarthSenseProtocol.MAX_HEIGHT),EarthSenseRules.activityLevel(observation.activity),
                    EarthSenseRules.strength(observation.distance, range),track.pulse));
        }
        tracks.keySet().retainAll(current);
        return List.copyOf(result);
    }
    public static double quarter(double value) { return Math.round(value*4)/4d; }
    private static float size(float value,float maximum) {
        return (float)Math.max(EarthSenseProtocol.MIN_SIZE,Math.min(maximum,quarter(value)));
    }
    private static boolean valid(Observation observation) {
        return observation!=null && observation.key!=null && observation.kind>=0 && observation.kind<=3
                && Double.isFinite(observation.x) && Double.isFinite(observation.y) && Double.isFinite(observation.z)
                && Math.abs(observation.x)<=EarthSenseProtocol.MAX_POSITION && Math.abs(observation.y)<=EarthSenseProtocol.MAX_POSITION
                && Math.abs(observation.z)<=EarthSenseProtocol.MAX_POSITION && Float.isFinite(observation.width)
                && Float.isFinite(observation.height) && observation.width>0 && observation.height>0
                && EarthSenseRules.strength(observation.distance)>0;
    }
    private static final class Track { final int id; int pulse, activity; long nextPulse; Track(int id){this.id=id;} }
}
