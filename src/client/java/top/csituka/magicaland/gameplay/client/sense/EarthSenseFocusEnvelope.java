package top.csituka.magicaland.gameplay.client.sense;

/** 入场快速靠近目标，退出平滑收尾；中途反向不跳变。 */
public final class EarthSenseFocusEnvelope {
    public static final double ENTER_TICKS = 9, EXIT_TICKS = 5;
    private double changedAt, previous = Double.NaN;
    private float from, target;

    public void reset() { changedAt = 0; previous = Double.NaN; from = target = 0; }

    public void observe(double ticks, float desired) {
        if (!Double.isFinite(ticks)) { reset(); return; }
        if (Double.isFinite(previous) && ticks < previous) reset();
        float next = clamp(desired);
        if (target != next) {
            from = sample(ticks);
            target = next;
            changedAt = ticks;
        }
        previous = ticks;
    }

    public float sample(double ticks) {
        if (!Double.isFinite(ticks)) return 0;
        boolean entering = target > from;
        float t = clamp((float) ((ticks - changedAt) / (entering ? ENTER_TICKS : EXIT_TICKS)));
        float ease = entering ? 1 - (1 - t) * (1 - t) * (1 - t) : t * t * (3 - 2 * t);
        return clamp(from + (target - from) * ease);
    }

    public static float clamp(float value) { return Float.isFinite(value) ? Math.max(0, Math.min(1, value)) : 0; }
}
