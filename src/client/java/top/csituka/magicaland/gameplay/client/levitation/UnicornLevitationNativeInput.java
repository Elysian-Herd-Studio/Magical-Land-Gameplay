package top.csituka.magicaland.gameplay.client.levitation;

import net.minecraft.client.input.Input;

public final class UnicornLevitationNativeInput {
    private UnicornLevitationNativeInput() {}
    /** KeyboardInput 已应用潜行倍率；控制时恢复键差，物品使用倍率留给后续原版逻辑。 */
    public static void apply(Input input, UnicornLevitationInput raw, boolean suppressSneak, boolean controlling) {
        if (controlling) input.jumping = false;
        if (!suppressSneak) return;
        input.sneaking = false;
        input.movementForward = raw.forwardAxis(false);
        input.movementSideways = raw.sideAxis(false);
    }
    public static boolean allowNativeFlight(boolean permission, boolean controllingOrPrepared) {
        return permission && !controllingOrPrepared;
    }
}
