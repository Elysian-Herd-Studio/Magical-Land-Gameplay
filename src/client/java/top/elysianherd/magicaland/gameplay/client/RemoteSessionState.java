package top.elysianherd.magicaland.gameplay.client;

/** Client ownership and packet ordering, independent of rendering and the game world. */
public final class RemoteSessionState {
    public enum Phase { IDLE, REQUESTING, CONNECTING, CONTROLLING, RETURNING }
    public enum Update { IGNORE, ACCEPT, ENDED, CANCEL_LATE_START }
    private Phase phase = Phase.IDLE;
    private long nextRequest, request, session;
    private int entity = -1, stateSequence = -1, inputSequence;

    public Phase phase() { return phase; }
    public boolean active() { return phase != Phase.IDLE; }
    public long request() { return request; }
    public long session() { return session; }
    public int entity() { return entity; }
    public int nextInput() { return ++inputSequence; }

    public long begin() {
        if (active()) throw new IllegalStateException("A projection is already active");
        request = ++nextRequest;
        session = 0; entity = -1; stateSequence = -1; inputSequence = 0;
        phase = Phase.REQUESTING;
        return request;
    }

    public Update accept(long requestToken, long sessionToken, int entityId, int sequence) {
        if (!active() || requestToken != request || sequence < 0 || sessionToken < 0 || entityId < -1) return Update.IGNORE;
        if (session != 0 && sessionToken != session) return Update.IGNORE;
        if (entityId >= 0 && sessionToken <= 0) return Update.IGNORE;
        if (sequence <= stateSequence) return Update.IGNORE;
        if (entityId >= 0 && entity >= 0 && entity != entityId) return Update.IGNORE;
        if (phase == Phase.RETURNING) {
            if (entityId >= 0 && session == 0) return Update.CANCEL_LATE_START;
            return Update.IGNORE;
        }
        session = sessionToken; stateSequence = sequence;
        if (entityId < 0) { phase = Phase.RETURNING; return Update.ENDED; }
        entity = entityId;
        if (phase == Phase.REQUESTING) phase = Phase.CONNECTING;
        return Update.ACCEPT;
    }

    public void connected() {
        if (phase == Phase.CONNECTING) phase = Phase.CONTROLLING;
    }

    public void returning() { if (active()) phase = Phase.RETURNING; }
    public void clear() {
        phase = Phase.IDLE; session = 0; entity = -1;
        stateSequence = -1; inputSequence = 0;
    }
}
