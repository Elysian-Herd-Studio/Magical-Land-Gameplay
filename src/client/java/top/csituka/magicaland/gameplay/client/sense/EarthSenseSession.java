package top.csituka.magicaland.gameplay.client.sense;

import java.util.List;
import top.csituka.magicaland.gameplay.sense.EarthSenseProtocol;

public final class EarthSenseSession {
    private long token;
    private int sequence = -1, age;
    private boolean wanted, active, grounded;
    private String reason = "";
    private List<EarthSenseProtocol.Signal> signals = List.of();

    public long begin(boolean enabled) {
        token = Math.addExact(token, 1);
        wanted = enabled;
        active = grounded = false;
        sequence = -1;
        age = 0;
        reason = "";
        if (enabled) signals = List.of();
        return token;
    }

    public boolean accept(EarthSenseProtocol.State state, String dimension) {
        if (state.token() != token || state.sequence() <= sequence || !state.dimension().equals(dimension)
                || state.active() && !wanted) return false;
        sequence = state.sequence();
        age = 0;
        active = state.active();
        grounded = state.grounded();
        reason = state.reason();
        if (!active) wanted = false;
        else if (grounded) signals = List.copyOf(state.signals());
        return true;
    }

    public boolean tickExpired() { return wanted && ++age > 60; }
    public void clear() {
        begin(false);
        signals = List.of();
    }
    public long token() { return token; }
    public boolean wanted() { return wanted; }
    public boolean active() { return active; }
    public boolean grounded() { return grounded; }
    public String reason() { return reason; }
    public List<EarthSenseProtocol.Signal> signals() { return signals; }
}
