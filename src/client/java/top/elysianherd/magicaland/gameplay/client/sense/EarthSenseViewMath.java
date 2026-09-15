package top.elysianherd.magicaland.gameplay.client.sense;

public final class EarthSenseViewMath {
    public static final double FOV_DEGREES = 6, LOOK_SCALE = .85;
    private EarthSenseViewMath() {}
    public static boolean applies(boolean world, boolean playerCamera, boolean screenOpen, boolean otherAbility) {
        return world && playerCamera && !screenOpen && !otherAbility;
    }
    public static double fov(double vanilla, float opacity) {
        if (!Double.isFinite(vanilla) || vanilla <= 0 || vanilla >= 175) return vanilla;
        return vanilla + Math.min(FOV_DEGREES, 175-vanilla) * amount(opacity);
    }
    public static double look(double vanillaDelta, float opacity) {
        return vanillaDelta * (1 - (1-LOOK_SCALE) * amount(opacity));
    }
    private static float amount(float opacity) { return Float.isFinite(opacity) ? Math.max(0, Math.min(1, opacity)) : 0; }
}
