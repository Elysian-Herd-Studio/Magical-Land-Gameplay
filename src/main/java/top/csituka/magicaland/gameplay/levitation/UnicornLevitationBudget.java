package top.csituka.magicaland.gameplay.levitation;

public final class UnicornLevitationBudget {
    public enum Verdict { ACCEPT, CORRECT, REJECT }
    private static final double JITTER = .12, EPSILON = .00001;
    private static final double SOFT_HORIZONTAL_EXCESS = .25, SOFT_VERTICAL_EXCESS = .20;
    private static final int BUFFER_TICKS = 3;
    private static final int INPUT_PHASE_TICKS = 2;
    private static final double HORIZONTAL_MARGIN = JITTER + INPUT_PHASE_TICKS * UnicornLevitationMath.BOOST_HORIZONTAL_SPEED;
    private static final double UP_MARGIN = JITTER + INPUT_PHASE_TICKS * UnicornLevitationMath.ASCEND_SPEED;
    private UnicornLevitationMath.Motion expected;
    private double x, y, z, horizontal, up, down, forecastHorizontal, forecastUp, forecastDown;
    private long tick = Long.MIN_VALUE, packetTick = Long.MIN_VALUE;
    private int packets;

    public UnicornLevitationBudget(double x, double y, double z, UnicornLevitationMath.Motion initial) {
        this.x = x; this.y = y; this.z = z;
        double speed = initial.horizontalSpeed(), scale = speed > .4 ? .4 / speed : 1;
        expected = new UnicornLevitationMath.Motion(initial.x() * scale, Math.max(-128, Math.min(.5, initial.y())), initial.z() * scale);
        horizontal = HORIZONTAL_MARGIN; up = UP_MARGIN; down = JITTER;
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
        // 输入和位置包可跨两刻；固定余量不随切换、停稳或收包重新发放。
        int grant = first ? 2 : 1;
        horizontal = Math.min(HORIZONTAL_MARGIN + BUFFER_TICKS * h, horizontal + grant * h);
        up = Math.min(UP_MARGIN + BUFFER_TICKS * u, up + grant * u);
        down = Math.min(JITTER + BUFFER_TICKS * d, down + grant * d);
    }
    public boolean accept(long now, double nextX, double nextY, double nextZ) {
        return validate(now, nextX, nextY, nextZ) == Verdict.ACCEPT;
    }
    public Verdict validate(long now, double nextX, double nextY, double nextZ) {
        if (!Double.isFinite(nextX) || !Double.isFinite(nextY) || !Double.isFinite(nextZ)) return Verdict.REJECT;
        if (packetTick != now) { packetTick = now; packets = 0; }
        if (++packets > UnicornLevitationRules.MAX_MOVE_PACKETS) return Verdict.REJECT;
        double h = Math.hypot(nextX - x, nextZ - z), u = Math.max(0, nextY - y), d = Math.max(0, y - nextY);
        // 位移包可早于 END tick；借用下一物理刻，负余额由随后补给偿还。
        double excessH = h - Math.max(0, horizontal + forecastHorizontal);
        double excessU = u - Math.max(0, up + forecastUp), excessD = d - Math.max(0, down + forecastDown);
        if (excessH > EPSILON || excessU > EPSILON || excessD > EPSILON)
            return excessH <= SOFT_HORIZONTAL_EXCESS && excessU <= SOFT_VERTICAL_EXCESS && excessD <= SOFT_VERTICAL_EXCESS
                    ? Verdict.CORRECT : Verdict.REJECT;
        horizontal -= h; up -= u; down -= d;
        x = nextX; y = nextY; z = nextZ; return Verdict.ACCEPT;
    }
    public void reanchor(double confirmedX, double confirmedY, double confirmedZ) {
        if (!Double.isFinite(confirmedX) || !Double.isFinite(confirmedY) || !Double.isFinite(confirmedZ))
            throw new IllegalArgumentException("Non-finite correction position");
        x = confirmedX; y = confirmedY; z = confirmedZ;
    }
    public UnicornLevitationMath.Motion expected() { return expected; }
}
