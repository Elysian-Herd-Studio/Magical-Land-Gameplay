package top.csituka.magicaland.gameplay.sense;

/** 保留接地确认与真实受伤退出；正常移动和无伤轻推不打断。 */
public final class EarthSenseFocus {
    private final long started;
    private final int attacked;
    private float health, absorption;

    public EarthSenseFocus(float health, float absorption, int attacked, long tick) {
        this.health=health; this.absorption=absorption; this.attacked=attacked; started=tick;
    }
    public boolean ready(long tick) { return tick-started >= EarthSenseRules.FOCUS_TICKS; }
    public String denial(double currentX, double currentY, double currentZ, float currentHealth,
                         float currentAbsorption, int hurtTime, int lastAttacked, boolean hasAttacker, boolean grounded,
                         double vx, double vy, double vz) {
        if (!finite(currentX,currentY,currentZ,vx,vy,vz) || !Float.isFinite(currentHealth)
                || !Float.isFinite(currentAbsorption) || !Float.isFinite(health) || !Float.isFinite(absorption)) return "invalid";
        if (hurtTime > 0 || hasAttacker && lastAttacked != attacked || currentHealth < health-.0001f
                || currentAbsorption < absorption-.0001f) return "hurt";
        if (!grounded) return "airborne";
        health=currentHealth; absorption=currentAbsorption;
        return null;
    }
    private static boolean finite(double... values) { for (double value:values) if (!Double.isFinite(value)) return false; return true; }
}
