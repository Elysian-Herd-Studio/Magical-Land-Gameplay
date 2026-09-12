package top.csituka.magicaland.gameplay.levitation;

import top.csituka.magicaland.gameplay.levitation.UnicornLevitationBudget.Verdict;

public final class UnicornLevitationMovementGuard {
    private static final int WINDOW_TICKS = 40, MAX_CORRECTIONS = 3;
    private final long[] corrections = new long[MAX_CORRECTIONS];
    private int count;
    private long teleportTick = Long.MIN_VALUE;

    public Verdict observe(Verdict verdict, long tick) {
        if (verdict != Verdict.CORRECT) return verdict;
        if (teleportTick != Long.MIN_VALUE && tick >= teleportTick && tick - teleportTick < 2) return verdict;
        int retained = 0;
        for (int i = 0; i < count; i++) {
            long age = tick - corrections[i];
            if (age >= 0 && age < WINDOW_TICKS) corrections[retained++] = corrections[i];
        }
        count = retained;
        if (count == MAX_CORRECTIONS) return Verdict.REJECT;
        corrections[count++] = tick;
        return Verdict.CORRECT;
    }
    public void teleported(long tick) { teleportTick = tick; }
    public void reset() { count = 0; teleportTick = Long.MIN_VALUE; }
}
