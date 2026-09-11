package top.csituka.magicaland.gameplay.levitation;

public final class UnicornLevitationBudget {
    private static final double JITTER = .12, EPSILON = .00001;
    private static final int BUFFER_TICKS = 3;
    private UnicornLevitationMath.Motion expected;
    private double x, y, z, horizontal, up, down, forecastHorizontal, forecastUp, forecastDown;
    private long tick = Long.MIN_VALUE, packetTick = Long.MIN_VALUE;
    private int packets;

    public UnicornLevitationBudget(double x, double y, double z, UnicornLevitationMath.Motion initial) {
        this.x = x; this.y = y; this.z = z;
        double speed = initial.horizontalSpeed(), scale = speed > .4 ? .4 / speed : 1;
        expected = new UnicornLevitationMath.Motion(initial.x() * scale, Math.max(-128, Math.min(.5, initial.y())), initial.z() * scale);
        horizontal = JITTER; up = JITTER; down = JITTER;
    }
    public void advance(long nextTick, float yaw, float forward, float sideways, UnicornLevitationMath.Mode mode,
                        double feetY, double surfaceY) {
        advance(nextTick, yaw, forward, sideways, mode, feetY, surfaceY, false);
    }
    public void advance(long nextTick, float yaw, float forward, float sideways, UnicornLevitationMath.Mode mode,
                        double feetY, double surfaceY, boolean horizontalBoost) {
        if (nextTick <= tick) return;
        boolean first = tick == Long.MIN_VALUE; tick = nextTick;
        expected = UnicornLevitationMath.step(expected, yaw, forward, sideways, mode, feetY, surfaceY, horizontalBoost);
        var forecast = UnicornLevitationMath.step(expected, yaw, forward, sideways, mode, feetY + expected.y(), surfaceY, horizontalBoost);
        forecastHorizontal = forecast.horizontalSpeed(); forecastUp = Math.max(0, forecast.y()); forecastDown = Math.max(0, -forecast.y());
        double h = expected.horizontalSpeed();
        double u = Math.max(0, expected.y()), d = Math.max(0, -expected.y());
        // 补给本刻实际计算的步长，避免切换时长期消耗固定容差。
        // Lag credit is capped, and accepted client movement never increases its refill rate.
        int grant = first ? 2 : 1;
        horizontal = Math.min(JITTER + BUFFER_TICKS * h, horizontal + grant * h);
        up = Math.min(JITTER + BUFFER_TICKS * u, up + grant * u);
        down = Math.min(JITTER + BUFFER_TICKS * d, down + grant * d);
    }
    public boolean accept(long now, double nextX, double nextY, double nextZ) {
        if (!Double.isFinite(nextX) || !Double.isFinite(nextY) || !Double.isFinite(nextZ)) return false;
        if (packetTick != now) { packetTick = now; packets = 0; }
        if (++packets > UnicornLevitationRules.MAX_MOVE_PACKETS) return false;
        double h = Math.hypot(nextX - x, nextZ - z), u = Math.max(0, nextY - y), d = Math.max(0, y - nextY);
        // 位移包可早于 END tick；借用下一物理刻，负余额由随后补给偿还。
        if (h > horizontal + forecastHorizontal + EPSILON || u > up + forecastUp + EPSILON || d > down + forecastDown + EPSILON) return false;
        horizontal -= h; up -= u; down -= d;
        x = nextX; y = nextY; z = nextZ; return true;
    }
    public UnicornLevitationMath.Motion expected() { return expected; }
}
