package top.elysianherd.magicaland.gameplay.levitation;

public final class UnicornLevitationMath {
    public enum Mode { OFF, ASCEND, LANDING, SURFACE, HOVER, RECOVER }
    public record Motion(double x, double y, double z) {
        public static final Motion ZERO = new Motion(0, 0, 0);
        public Motion {
            if (!Double.isFinite(x) || !Double.isFinite(y) || !Double.isFinite(z))
                throw new IllegalArgumentException("Non-finite velocity");
        }
        public double horizontalSpeed() { return Math.hypot(x, z); }
    }

    public static final double ASCEND_SPEED = .16, HORIZONTAL_SPEED = .065;
    public static final double BOOST_HORIZONTAL_SPEED = .216;
    public static final double LIFT_ACCELERATION = .08, BRAKE_ACCELERATION = .10;
    public static final double HOVER_BRAKE = .02;
    public static final double HORIZONTAL_ACCELERATION = .025, SURFACE_TOLERANCE = .125;
    public static final double SURFACE_CLEARANCE = .15;
    public static final double SURFACE_MAX_RISE = .4;
    public static final double MAX_PROBE_DEPTH = 96;

    private UnicornLevitationMath() {}

    /** 返回受控移动本刻的速度；OFF 原样保留，调用方负责原版重力与碰撞。 */
    public static Motion step(Motion velocity, float yaw, float forward, float sideways,
                              Mode mode, double feetY, double surfaceY) {
        if (velocity == null || mode == null) throw new IllegalArgumentException("Missing motion state");
        if (mode == Mode.OFF) return velocity;
        double angle = Math.toRadians(Float.isFinite(yaw) ? yaw : 0);
        double f = axis(forward), s = axis(sideways), length = Math.max(1, Math.hypot(f, s));
        double speed = mode == Mode.HOVER || mode == Mode.SURFACE ? BOOST_HORIZONTAL_SPEED : HORIZONTAL_SPEED;
        double targetX = (Math.cos(angle) * s - Math.sin(angle) * f) / length * speed;
        double targetZ = (Math.sin(angle) * s + Math.cos(angle) * f) / length * speed;
        double dx = targetX - velocity.x, dz = targetZ - velocity.z;
        double distance = Math.hypot(dx, dz);
        double scale = distance > HORIZONTAL_ACCELERATION ? HORIZONTAL_ACCELERATION / distance : 1;
        double targetY, acceleration;
        if (mode == Mode.ASCEND || mode == Mode.RECOVER) {
            targetY = ASCEND_SPEED;
            acceleration = LIFT_ACCELERATION;
        } else if (mode == Mode.HOVER) {
            targetY = 0;
            acceleration = HOVER_BRAKE;
        } else if (!Double.isFinite(feetY) || !Double.isFinite(surfaceY)) {
            return velocity;
        } else if (mode == Mode.LANDING) {
            double clearance = Math.max(0, feetY - surfaceY);
            double safeDown = Math.sqrt(BRAKE_ACCELERATION * BRAKE_ACCELERATION
                    + 2 * BRAKE_ACCELERATION * Math.max(0, clearance - .03)) - BRAKE_ACCELERATION;
            targetY = Math.max(velocity.y, -safeDown);
            if (clearance <= .08 && velocity.y > -.02) targetY = -.02;
            acceleration = BRAKE_ACCELERATION;
        } else {
            double error = surfaceY + SURFACE_CLEARANCE - feetY;
            double safeDown = Math.sqrt(BRAKE_ACCELERATION * BRAKE_ACCELERATION
                    + 2 * BRAKE_ACCELERATION * Math.max(0, -error)) - BRAKE_ACCELERATION;
            targetY = clamp(error * .8, -safeDown, ASCEND_SPEED);
            if (velocity.y < -.08) targetY = Math.max(velocity.y, targetY);
            acceleration = BRAKE_ACCELERATION;
        }
        double vertical = velocity.y + clamp(targetY - velocity.y, -acceleration, acceleration);
        return new Motion(velocity.x + dx * scale, vertical, velocity.z + dz * scale);
    }

    /** ascendHeld 已由会话验证长按阈值；地面稳定数刻的退出去抖由会话负责。 */
    public static Mode chooseMode(boolean eligible, boolean ascendHeld, boolean grounded, Mode previous,
                                  double verticalVelocity, double clearance, boolean fluidSurface) {
        return chooseMode(eligible, ascendHeld, false, grounded, previous, verticalVelocity, clearance, fluidSurface, 0);
    }

    /** fluidSurface 必须来自已通过实体遮挡/深水排除的 Ground 支撑探测。 */
    public static Mode chooseMode(boolean eligible, boolean ascendHeld, boolean horizontalBoost, boolean grounded,
                                  Mode previous, double verticalVelocity, double clearance, boolean fluidSurface,
                                  double fallDistance) {
        if (!eligible || !Double.isFinite(verticalVelocity)) return Mode.OFF;
        if (ascendHeld) {
            if (!horizontalBoost) return Mode.ASCEND;
            if (verticalVelocity < -.01 || previous == Mode.RECOVER && verticalVelocity < .12) return Mode.RECOVER;
            return Mode.HOVER;
        }
        if (!Double.isFinite(clearance)) return Mode.OFF;
        if (clearance < -(fluidSurface ? SURFACE_MAX_RISE : SURFACE_TOLERANCE)) return Mode.OFF;
        double surfaceReach = previous == Mode.SURFACE ? 1.25 : SURFACE_CLEARANCE + .25;
        if (fluidSurface && verticalVelocity < -.01)
            surfaceReach = Math.max(surfaceReach, stoppingDistance(verticalVelocity) + SURFACE_CLEARANCE + .35);
        if (fluidSurface && clearance <= surfaceReach && verticalVelocity <= ASCEND_SPEED + .000001) return Mode.SURFACE;
        if (grounded) return Mode.OFF;
        boolean dangerous = verticalVelocity <= -.8 || Double.isFinite(fallDistance)
                && fallDistance > 0 && fallDistance + Math.max(0, clearance) > 3;
        if (verticalVelocity < -.01 && (dangerous || previous == Mode.LANDING)
                && clearance <= stoppingDistance(verticalVelocity) + .35) return Mode.LANDING;
        if (previous == Mode.LANDING && clearance <= .35 && verticalVelocity <= .03) return Mode.LANDING;
        return Mode.OFF;
    }

    /** 有限净向上加速度下的保守刹停距离，包含一刻输入/探测余量。 */
    public static double stoppingDistance(double verticalVelocity) {
        if (!Double.isFinite(verticalVelocity)) return MAX_PROBE_DEPTH;
        double down = Math.max(0, -verticalVelocity);
        return down * down / (2 * BRAKE_ACCELERATION) + down;
    }

    public static double probeDepth(double verticalVelocity) {
        return clamp(stoppingDistance(verticalVelocity) + .75, 1.25, MAX_PROBE_DEPTH);
    }

    private static double axis(float value) { return Float.isFinite(value) ? clamp(value, -1, 1) : 0; }
    private static double clamp(double value, double min, double max) { return Math.max(min, Math.min(max, value)); }
}
