package net.minecraft.util.math;
public final class MathHelper {
    public static float wrapDegrees(float degrees) {
        degrees %= 360;
        if (degrees >= 180) degrees -= 360;
        if (degrees < -180) degrees += 360;
        return degrees;
    }
}
