package top.elysianherd.magicaland.gameplay.client.levitation;

public record UnicornLevitationInput(boolean space, boolean sneak, boolean forward, boolean backward,
                                     boolean left, boolean right, float yaw) {
    public static final UnicornLevitationInput NONE = new UnicornLevitationInput(false, false, false, false, false, false, 0);
    public UnicornLevitationInput { yaw = Float.isFinite(yaw) ? (float)Math.IEEEremainder(yaw, 360) : 0; }
    private float factor(boolean usingItem) { return usingItem ? .2f : 1; }
    public float forwardAxis(boolean usingItem) { return ((forward ? 1 : 0) - (backward ? 1 : 0)) * factor(usingItem); }
    public float sideAxis(boolean usingItem) { return ((left ? 1 : 0) - (right ? 1 : 0)) * factor(usingItem); }
    public boolean sameKeys(UnicornLevitationInput other) {
        return other != null && space == other.space && sneak == other.sneak && forward == other.forward
                && backward == other.backward && left == other.left && right == other.right;
    }
    public boolean needsUpdate(UnicornLevitationInput other) {
        return !sameKeys(other) || Math.abs(Math.IEEEremainder(yaw - other.yaw, 360)) > 2;
    }
}
