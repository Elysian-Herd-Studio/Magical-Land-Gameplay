package top.elysianherd.magicaland.gameplay.sense;

public final class EarthSenseRules {
    public static final int RANGE = 14, SAMPLE_TICKS = 5, LEASE_TICKS = 60, FOCUS_TICKS = 2;
    public static final int MAX_ACTIVE = 8, MAX_CANDIDATES = 64, MAX_NODES = 2048, MAX_PROBES = 4096;
    public static final int HOSTILE = 0, PLAYER = 1, NEUTRAL = 2, FRIENDLY = 3;
    public static final int STILL = 0, WALK = 1, RUN = 2, LAND = 3, SNEAK = 4;
    private EarthSenseRules() {}

    public static int strength(double distance) {
        return strength(distance, RANGE);
    }
    public static int strength(double distance, double range) {
        if (!Double.isFinite(distance) || !Double.isFinite(range) || range <= 0 || range > RANGE
                || distance < 0 || distance > range) return 0;
        return distance <= range * .25 ? 4 : distance <= range * .5 ? 3 : distance <= range * .75 ? 2 : 1;
    }
    public static float activityLevel(int activity) {
        return switch (activity) { case SNEAK -> .28f; case WALK -> .65f; case RUN -> .9f; case LAND -> 1; default -> .12f; };
    }
    public static int interval(int activity) {
        return switch (activity) { case RUN -> 5; case WALK -> 10; case LAND -> 5; case SNEAK -> 25; default -> 40; };
    }
    public static int activity(double horizontalSpeed, boolean landing, boolean sneaking, boolean sprinting) {
        if (!Double.isFinite(horizontalSpeed) || horizontalSpeed < 0) return STILL;
        if (landing) return LAND;
        if (horizontalSpeed < .015) return STILL;
        if (sneaking) return SNEAK;
        return sprinting || horizontalSpeed >= .22 ? RUN : WALK;
    }
    public static int kind(boolean player, boolean threatensObserver, boolean neutral, boolean monster, boolean friendly) {
        if (player) return PLAYER;
        if (threatensObserver) return HOSTILE;
        if (neutral) return NEUTRAL;
        if (monster) return HOSTILE;
        return friendly ? FRIENDLY : NEUTRAL;
    }
}
