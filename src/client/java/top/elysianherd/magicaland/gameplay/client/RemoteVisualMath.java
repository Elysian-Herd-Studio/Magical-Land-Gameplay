package top.elysianherd.magicaland.gameplay.client;

public final class RemoteVisualMath {
    private RemoteVisualMath() {}

    public static float clamp(float value) { return Math.max(0, Math.min(1, value)); }
    public static float smooth(float value) { float t = clamp(value); return t*t*(3-2*t); }
    public static int slot(int selected, int steps, int capacity) {
        if (capacity < 1 || capacity > 9) throw new IllegalArgumentException("Invalid hotbar capacity");
        return Math.floorMod(selected + steps, capacity);
    }
    public static int hotbarWidth(int capacity) { return capacity * 20 + 2; }

    public static float approachOcclusion(float current, float target, double seconds) {
        if (!Float.isFinite(target)) target = 1;
        if (!Double.isFinite(seconds) || seconds <= 0) return current;
        if (target >= .999f) return clamp(current + (float)(seconds / .04));
        float result = current + (clamp(target) - current) * (float)-Math.expm1(-Math.min(seconds, .1) / .07);
        if (target <= .001f && result <= .015f) return 0;
        return clamp(result);
    }

    public static float vignette(float nx, float ny, float occlusion) {
        float blocked = clamp(occlusion);
        if (blocked <= 0) return 0;
        if (blocked >= 1) return 1;
        float radius = (float)Math.sqrt(nx*nx + ny*ny);
        float opening = 1.4f - 1.72f * blocked;
        return smooth((radius - opening) / .32f);
    }

    public static float cycle(double elapsed, int duration, boolean repeating) {
        if (!Double.isFinite(elapsed) || elapsed < 0 || duration < 1) return 0;
        if (!repeating && elapsed >= duration) return 0;
        return (float)((repeating ? elapsed % duration : elapsed) / duration);
    }
}
