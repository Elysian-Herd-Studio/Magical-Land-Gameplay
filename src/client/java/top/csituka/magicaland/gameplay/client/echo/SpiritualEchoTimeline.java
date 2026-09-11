package top.csituka.magicaland.gameplay.client.echo;

public final class SpiritualEchoTimeline {
    public static final double PERIOD = 3, EXPANSION = .7, AFTERGLOW = PERIOD, ATTACK = .035, RADIUS = 8;
    public record Pulse(float phase, double x, double y, double z) {
        public boolean visible() { return phase >= 0 && phase < EXPANSION + AFTERGLOW; }
    }
    private double start, previousStart, lastTime;
    private Pulse pulse, previous;

    public void reset() { pulse = previous = null; start = previousStart = lastTime = 0; }
    public Pulse previous() { return previous; }

    public Pulse update(double now, float occlusion, double x, double y, double z) {
        if (!Double.isFinite(now) || !Double.isFinite(x) || !Double.isFinite(y) || !Double.isFinite(z)
                || !Float.isFinite(occlusion) || occlusion <= .001f) { reset(); return null; }
        if (pulse != null && now < lastTime) reset();
        if (pulse == null) {
            if (occlusion < .999f) return null;
            start = now; pulse = new Pulse(0, x, y, z);
        } else if (now - start >= PERIOD) {
            previousStart = start;
            previous = new Pulse((float)(now - start), pulse.x, pulse.y, pulse.z);
            start = now; pulse = new Pulse(0, x, y, z);
        }
        lastTime = now;
        pulse = new Pulse((float)(now - start), pulse.x, pulse.y, pulse.z);
        if (previous != null) {
            previous = new Pulse((float)(now - previousStart), previous.x, previous.y, previous.z);
            if (!previous.visible()) previous = null;
        }
        return pulse;
    }

    public static float surface(double distance, double phase) {
        if (!Double.isFinite(distance) || !Double.isFinite(phase) || distance < 0 || distance > RADIUS) return 0;
        double age = phase - distance / RADIUS * EXPANSION;
        if (age <= 0 || age >= AFTERGLOW) return 0;
        return (float)(smoothstep(0, ATTACK, age) * (1 - smoothstep(ATTACK, AFTERGLOW, age)));
    }
    private static double smoothstep(double from, double to, double value) {
        double t = Math.max(0, Math.min(1, (value - from) / (to - from)));
        return t * t * (3 - 2 * t);
    }
}
