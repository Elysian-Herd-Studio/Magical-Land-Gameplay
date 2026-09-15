package top.elysianherd.magicaland.gameplay.client.sense;

import net.minecraft.client.input.Input;

public final class EarthSenseFocusInput {
    private EarthSenseFocusInput() {}

    public static boolean requestedJump(Input input) { return input != null && input.jumping; }

    public static boolean apply(Input input, boolean focusing, boolean slowDown, float factor) {
        if (!focusing || input == null) return false;
        if (requestedJump(input)) return true;
        // KeyboardInput 已应用 slowDown；首刻未蹲下时才补同一原版系数。
        float crouch = Float.isFinite(factor) ? Math.max(0, Math.min(1, factor)) : .3f;
        float multiplier = .8f * (slowDown ? 1 : crouch);
        input.movementForward = finiteAxis(input.movementForward) * multiplier;
        input.movementSideways = finiteAxis(input.movementSideways) * multiplier;
        input.sneaking = true;
        return false;
    }

    private static float finiteAxis(float value) { return Float.isFinite(value) ? value : 0; }
}
