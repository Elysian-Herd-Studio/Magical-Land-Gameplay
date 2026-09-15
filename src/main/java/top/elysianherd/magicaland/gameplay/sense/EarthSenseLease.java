package top.elysianherd.magicaland.gameplay.sense;

/** 每次连接保留关闭 token，迟到续租不能重新开启。 */
public final class EarthSenseLease {
    public enum Action { IGNORE, START, RENEW, STOP }
    private long token, heartbeat;
    private boolean closed = true;
    public long token() { return token; }
    public boolean closed() { return closed; }
    public Action control(long incoming, boolean enabled, long tick) {
        if (incoming <= 0 || incoming < token) return Action.IGNORE;
        if (incoming == token) {
            if (closed) return Action.IGNORE;
            if (!enabled) { closed = true; return Action.STOP; }
            heartbeat = tick;
            return Action.RENEW;
        }
        token = incoming;
        heartbeat = tick;
        closed = !enabled;
        return enabled ? Action.START : Action.STOP;
    }
    public boolean expired(long tick) { return !closed && tick - heartbeat >= EarthSenseRules.LEASE_TICKS; }
    public void close() { closed = true; }
}
