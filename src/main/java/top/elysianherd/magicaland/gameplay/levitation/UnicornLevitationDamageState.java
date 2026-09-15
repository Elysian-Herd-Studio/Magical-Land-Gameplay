package top.elysianherd.magicaland.gameplay.levitation;

public final class UnicornLevitationDamageState {
    private float health;
    private int hurtTime;
    private boolean observed;
    private long interruptedAt = Long.MIN_VALUE;

    public boolean observe(float nextHealth, int nextHurtTime, long tick) {
        boolean hit = observed && (nextHealth < health || nextHurtTime > hurtTime);
        health = nextHealth; hurtTime = nextHurtTime; observed = true;
        if (hit) interrupted(tick);
        return hit;
    }
    public void interrupted(long tick) { interruptedAt = tick; }
    public boolean settling(long tick) {
        return interruptedAt != Long.MIN_VALUE && tick >= interruptedAt && tick - interruptedAt <= 10;
    }
    public void clear() { observed = false; interruptedAt = Long.MIN_VALUE; }
}
