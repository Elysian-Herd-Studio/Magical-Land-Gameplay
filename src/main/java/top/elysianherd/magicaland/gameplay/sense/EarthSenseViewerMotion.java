package top.elysianherd.magicaland.gameplay.sense;

/** 只观察实际水平位移；轻推不打断，也不抵消外力。 */
public final class EarthSenseViewerMotion {
    public static final double STILL_SPEED = .015, CROUCH_SPEED = .05, FAST_SPEED = .10;
    public static final double MIN_QUALITY = 4d / EarthSenseRules.RANGE;
    public static final int DIM_TICKS = 2, RECOVER_TICKS = 10;
    private double x, z, quality;
    private long tick;

    public EarthSenseViewerMotion(double x, double z, long tick) { this(x, z, tick, 0); }
    public EarthSenseViewerMotion(double x, double z, long tick, double initialSpeed) {
        this.x = x; this.z = z; this.tick = tick;
        quality = viewerQuality(initialSpeed);
    }
    public double quality() { return quality; }
    public double observe(double currentX, double currentZ, long currentTick) {
        if (currentTick == tick) return quality;
        if (!Double.isFinite(currentX) || !Double.isFinite(currentZ) || !Double.isFinite(x)
                || !Double.isFinite(z) || currentTick < tick) quality = MIN_QUALITY;
        else {
            double elapsed = (double) currentTick - tick;
            double target = viewerQuality(Math.hypot(currentX - x, currentZ - z) / elapsed);
            double step = (1 - MIN_QUALITY) * Math.min(elapsed, RECOVER_TICKS)
                    / (target < quality ? DIM_TICKS : RECOVER_TICKS);
            quality += Math.max(-step, Math.min(step, target - quality));
        }
        x = currentX; z = currentZ; tick = currentTick;
        return quality;
    }
    public static double viewerQuality(double speed) {
        if (!Double.isFinite(speed) || speed < 0) return MIN_QUALITY;
        if (speed <= STILL_SPEED) return 1;
        double crouch = 6d / EarthSenseRules.RANGE;
        if (speed <= CROUCH_SPEED) return mix(1, crouch, (speed - STILL_SPEED) / (CROUCH_SPEED - STILL_SPEED));
        return mix(crouch, MIN_QUALITY, (speed - CROUCH_SPEED) / (FAST_SPEED - CROUCH_SPEED));
    }
    public static double range(double quality) {
        return EarthSenseRules.RANGE * (Double.isFinite(quality) ? Math.max(MIN_QUALITY, Math.min(1, quality)) : MIN_QUALITY);
    }
    private static double mix(double start, double end, double t) {
        t = Math.max(0, Math.min(1, t));
        return start + (end - start) * t * t * (3 - 2 * t);
    }
}
