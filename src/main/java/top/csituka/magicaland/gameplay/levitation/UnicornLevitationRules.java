package top.csituka.magicaland.gameplay.levitation;

public final class UnicornLevitationRules {
    public static final int CHARGE_TICKS = 7, LEASE_TICKS = 20, MAX_SESSIONS = 32, MAX_MOVE_PACKETS = 5;
    public static final int MOVE_PACKET_BURST = 20;
    public static final double MAX_MANA = 100;
    private long token, lastInput;
    private int sequence = -1, charge;
    private boolean open, holding, airborneStart;
    public UnicornLevitationRules() {}
    public UnicornLevitationRules(double ignoredLegacyMana) {}
    public boolean input(long nextToken, int nextSequence, boolean enabled, long tick) {
        if (nextToken <= 0 || nextSequence < 0 || nextToken < token
                || nextToken == token && (nextSequence <= sequence || enabled && !open)) return false;
        if (nextToken > token) { token = nextToken; charge = 0; holding = false; }
        sequence = nextSequence; lastInput = tick; open = enabled;
        if (!open) { charge = 0; holding = false; }
        return true;
    }
    public boolean expired(long tick) { return open && (tick < lastInput || tick - lastInput > LEASE_TICKS); }
    public boolean protectsFall(boolean armed, long tick) { return open && armed && !expired(tick); }
    public static boolean groundedPress(boolean wasSpace, boolean space, boolean grounded, boolean pending) {
        return space && (wasSpace ? pending : grounded);
    }
    public boolean ready(boolean armed, boolean space, boolean onGround) {
        if (!open || !armed || !space) { release(); return false; }
        if (!holding) { holding = true; airborneStart = !onGround; }
        if (airborneStart) { charge = CHARGE_TICKS; return true; }
        charge = Math.min(CHARGE_TICKS, charge + 1);
        return charge >= CHARGE_TICKS;
    }
    public void release() { charge = 0; holding = false; }
    public void close() { open = false; charge = 0; holding = false; }
    public long token() { return token; }
    public int sequence() { return sequence; }
    public boolean open() { return open; }
    public double mana() { return MAX_MANA; }

}
