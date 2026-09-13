package top.csituka.magicaland.gameplay.pegasus;

public final class PegasusFlightMath {
    public enum Mode { OFF, HOVER, GLIDE, REBOUND, LANDING }
    public static final double MAX_SPEED = 3.8, IMPACT_SPEED = 1.25, DANGER_SPEED = 2.8, CREATURE_SPEED = .85;
    public static final float MAX_STAMINA = 1000;
    public static final double MAX_ANGULAR_SPEED = .12, ANGULAR_ACCELERATION = .022;
    public static final int REBOUND_TICKS = 22;
    public record Motion(double x, double y, double z) {
        public static final Motion ZERO = new Motion(0, 0, 0);
        public Motion {
            if (!Double.isFinite(x) || !Double.isFinite(y) || !Double.isFinite(z)) throw new IllegalArgumentException("Nonfinite flight motion");
        }
        public double speed() { return Math.sqrt(dot(this)); }
        public Motion add(Motion b) { return new Motion(x + b.x, y + b.y, z + b.z); }
        public Motion scale(double n) { return new Motion(x * n, y * n, z * n); }
        public double dot(Motion b) { return x * b.x + y * b.y + z * b.z; }
        public Motion cross(Motion b) { return new Motion(y * b.z - z * b.y, z * b.x - x * b.z, x * b.y - y * b.x); }
        public Motion normalized() { double length = speed(); return length < 1e-8 ? ZERO : scale(1 / length); }
        public Motion capped(double limit) { double length = speed(); return length > limit ? scale(limit / length) : this; }
    }
    public record Attitude(float x, float y, float z, float w) {
        public Attitude {
            double length = Math.sqrt((double) x * x + (double) y * y + (double) z * z + (double) w * w);
            if (!Double.isFinite(length) || length < .5 || length > 1.5) throw new IllegalArgumentException("Invalid flight attitude");
            x /= length; y /= length; z /= length; w /= length;
        }
        public Motion forward() { return new Motion(2 * (x * z + w * y), 2 * (y * z - w * x), 1 - 2 * (x * x + y * y)).normalized(); }
        public Motion up() { return new Motion(2 * (x * y - w * z), 1 - 2 * (x * x + z * z), 2 * (y * z + w * x)).normalized(); }
        public Motion rotate(Motion value) {
            Motion imaginary = new Motion(x, y, z);
            Motion twiceCross = imaginary.cross(value).scale(2);
            return value.add(twiceCross.scale(w)).add(imaginary.cross(twiceCross));
        }
        public Attitude multiply(Attitude b) {
            return new Attitude(w*b.x+x*b.w+y*b.z-z*b.y, w*b.y-x*b.z+y*b.w+z*b.x,
                    w*b.z+x*b.y-y*b.x+z*b.w, w*b.w-x*b.x-y*b.y-z*b.z);
        }
        public Attitude inverse() { return new Attitude(-x, -y, -z, w); }
        public static Attitude rotation(Motion vector) {
            double angle = vector.speed();
            if (angle < 1e-9) return new Attitude(0, 0, 0, 1);
            double scale = Math.sin(angle / 2) / angle;
            return new Attitude((float)(vector.x()*scale), (float)(vector.y()*scale), (float)(vector.z()*scale), (float)Math.cos(angle/2));
        }
        public static Attitude fromYawPitch(float yaw, float pitch) {
            double y = Math.toRadians(-yaw) / 2, p = Math.toRadians(pitch) / 2;
            return new Attitude((float) (Math.cos(y) * Math.sin(p)), (float) (Math.sin(y) * Math.cos(p)),
                    (float) (-Math.sin(y) * Math.sin(p)), (float) (Math.cos(y) * Math.cos(p)));
        }
    }
    public record Dynamics(Attitude body, Motion angularVelocity, float thrust) {
        public Dynamics {
            if (body == null || angularVelocity == null || angularVelocity.speed() > .5
                    || !Float.isFinite(thrust) || thrust < 0 || thrust > 1) throw new IllegalArgumentException("Invalid flight dynamics");
        }
        public static Dynamics resting(Attitude body) { return new Dynamics(body, Motion.ZERO, 0); }
    }
    public record Step(Motion motion, float stamina, boolean boosting, boolean exhausted, Dynamics dynamics) {}
    private PegasusFlightMath() {}
    public static Step step(Motion velocity, Mode mode, PegasusFlightProtocol.Control input, float stamina) {
        return step(velocity, mode, input, stamina, false);
    }
    public static Step step(Motion velocity, Mode mode, PegasusFlightProtocol.Control input, float stamina, boolean exhausted) {
        return step(velocity, mode, input, stamina, exhausted, Dynamics.resting(input.attitude()));
    }
    public static Step step(Motion velocity, Mode mode, PegasusFlightProtocol.Control input, float stamina, boolean exhausted, Dynamics dynamics) {
        stamina = Math.max(0, Math.min(MAX_STAMINA, stamina));
        if (stamina <= .001f) exhausted = true;
        if (!input.forward() && stamina >= 10) exhausted = false;
        Dynamics turned = turnBody(dynamics, targetBody(mode, input, dynamics), mode, input, velocity.speed());
        Motion next = velocity;
        boolean boost = false;
        float thrust = 0;
        if (mode == Mode.GLIDE) {
            Motion forward = turned.body().forward();
            double speed = velocity.speed();
            double steering = .18 / (1 + speed * .35) * Math.min(1, Math.max(.2, speed / .45));
            next = turnVelocity(velocity, forward, steering, turned.body().up());
            double drag = .0016 + .0116 * Math.pow(speed / 3.4, 2);
            next = next.scale(1 - drag).add(new Motion(0, -.024, 0));
            float requested = input.forward() && !input.backward() && !exhausted ? 1 : 0;
            thrust = approach(dynamics.thrust(), requested, requested > dynamics.thrust() ? .05f : .10f);
            thrust = Math.min(thrust, stamina / .7f);
            if (input.backward()) { next = next.scale(.90); thrust = 0; }
            else if (thrust > .001f) {
                next = next.add(forward.scale(.045 * thrust)); stamina = Math.max(0, stamina - .7f * thrust); boost = true;
            }
            if (stamina <= .001f) exhausted = true;
            if (!boost) stamina = Math.min(MAX_STAMINA, stamina + 1.6f);
        } else if (mode == Mode.HOVER) {
            Motion facing = input.attitude().forward();
            Motion forward = new Motion(facing.x(), 0, facing.z()).normalized();
            if (forward.speed() < .1) forward = new Motion(0, 0, 1);
            Motion left = new Motion(forward.z(), 0, -forward.x());
            Motion desired = forward.scale((input.forward() ? 1 : 0) - (input.backward() ? 1 : 0))
                    .add(left.scale((input.left() ? 1 : 0) - (input.right() ? 1 : 0))).normalized().scale(.48)
                    .add(new Motion(0, ((input.ascend() ? 1 : 0) - (input.descend() ? 1 : 0)) * .34, 0));
            double horizontalResponse = desired.x()*desired.x() + desired.z()*desired.z() > 1e-8 ? .18 : .12;
            double verticalResponse = Math.abs(desired.y()) > 1e-8 ? .20 : .16;
            next = new Motion(velocity.x() + (desired.x() - velocity.x()) * horizontalResponse,
                    velocity.y() + (desired.y() - velocity.y()) * verticalResponse,
                    velocity.z() + (desired.z() - velocity.z()) * horizontalResponse);
            stamina = Math.min(MAX_STAMINA, stamina + 2.2f);
        } else if (mode == Mode.REBOUND) {
            next = new Motion(velocity.x() * .88, Math.max(.10, velocity.y() * .91), velocity.z() * .88);
            stamina = Math.min(MAX_STAMINA, stamina + 1.6f);
        } else if (mode == Mode.LANDING) {
            next = new Motion(velocity.x() * .85, Math.min(-.16, velocity.y() * .45), velocity.z() * .85);
            stamina = Math.min(MAX_STAMINA, stamina + 2.2f);
        } else stamina = Math.min(MAX_STAMINA, stamina + 3);
        return new Step(next.capped(MAX_SPEED), stamina, boost, exhausted, new Dynamics(turned.body(), turned.angularVelocity(), thrust));
    }
    private static float approach(float value, float target, float amount) { return value < target ? Math.min(target, value + amount) : Math.max(target, value - amount); }
    private static Attitude targetBody(Mode mode, PegasusFlightProtocol.Control input, Dynamics dynamics) {
        if (mode == Mode.GLIDE && input.unlocked()) return input.attitude();
        Motion forward = input.attitude().forward();
        float yaw = yaw(input.attitude());
        if (mode == Mode.HOVER && !input.forward() && !input.backward() && !input.left() && !input.right()) {
            float oldYaw = yaw(dynamics.body());
            double difference = wrapDegrees(yaw - oldYaw);
            boolean following = Math.abs(difference) > 35 || dynamics.angularVelocity().speed() > 1e-5 && Math.abs(difference) > 25;
            yaw = following ? oldYaw + (float)(difference - Math.copySign(25, difference)) : oldYaw;
        }
        float pitch = mode == Mode.GLIDE ? (float)Math.toDegrees(Math.asin(Math.max(-1, Math.min(1, -forward.y())))) : 0;
        return Attitude.fromYawPitch(yaw, Math.max(-80, Math.min(80, pitch)));
    }
    private static float yaw(Attitude attitude) {
        Motion forward = attitude.forward();
        if (forward.x()*forward.x()+forward.z()*forward.z() > 1e-8)
            return (float)Math.toDegrees(Math.atan2(-forward.x(), forward.z()));
        Motion right = attitude.up().cross(forward);
        return (float)Math.toDegrees(Math.atan2(right.z(), right.x()));
    }
    private static double wrapDegrees(double angle) { double wrapped = angle % 360; return wrapped >= 180 ? wrapped - 360 : wrapped < -180 ? wrapped + 360 : wrapped; }
    public static double rudderAuthority(double speed) {
        return Math.max(.12, .55 / (1 + Math.pow(Math.max(0, speed) / 1.8, 2)));
    }
    private static Dynamics turnBody(Dynamics dynamics, Attitude target, Mode mode, PegasusFlightProtocol.Control input, double speed) {
        if (mode == Mode.GLIDE) return turnGlidingBody(dynamics, target, input.unlocked(), speed);
        Attitude difference = target.multiply(dynamics.body().inverse());
        double sign = difference.w() < 0 ? -1 : 1;
        Motion axis = new Motion(difference.x()*sign, difference.y()*sign, difference.z()*sign);
        double angle = 2 * Math.atan2(axis.speed(), Math.max(0, difference.w()*sign));
        if (angle < 1e-5 && dynamics.angularVelocity().speed() < 1e-5) return new Dynamics(target, Motion.ZERO, dynamics.thrust());
        axis = axis.normalized();
        double wantedSpeed = Math.min(MAX_ANGULAR_SPEED, angle * .25);
        Motion wanted = axis.scale(wantedSpeed);
        Motion angular = dynamics.angularVelocity().add(wanted.add(dynamics.angularVelocity().scale(-1)).capped(ANGULAR_ACCELERATION)).capped(MAX_ANGULAR_SPEED);
        Attitude body = Attitude.rotation(angular).multiply(dynamics.body());
        return new Dynamics(body, angular, dynamics.thrust());
    }
    private static Dynamics turnGlidingBody(Dynamics dynamics, Attitude target, boolean free, double speed) {
        Attitude body = dynamics.body();
        Motion wanted;
        if (free) {
            Attitude difference = body.inverse().multiply(target);
            double sign = difference.w() < 0 ? -1 : 1;
            Motion axis = new Motion(difference.x()*sign, difference.y()*sign, difference.z()*sign);
            double angle = 2 * Math.atan2(axis.speed(), Math.max(0, difference.w()*sign));
            wanted = axis.normalized().scale(angle * .25);
        } else {
            Motion heading = body.forward(), desired = target.forward();
            Motion axis = heading.cross(desired);
            double angle = Math.acos(Math.max(-1, Math.min(1, heading.dot(desired))));
            double yawError = wrapDegrees(yaw(target) - yaw(body));
            if (axis.speed() < 1e-8 && angle > 1) axis = new Motion(0, -Math.copySign(1, yawError), 0);
            Motion steering = body.inverse().rotate(axis.normalized().scale(angle * .25));
            float pitch = (float)Math.toDegrees(Math.asin(Math.max(-1, Math.min(1, -heading.y()))));
            Attitude roll = Attitude.fromYawPitch(yaw(body), pitch).inverse().multiply(body);
            double bank = wrapDegrees(Math.toDegrees(2 * Math.atan2(roll.z(), roll.w())));
            double desiredBank = Math.max(-65, Math.min(65, yawError * 1.4));
            double coordination = Math.min(1, MAX_ANGULAR_SPEED / Math.max(1e-9, Math.abs(steering.x())));
            coordination = Math.min(coordination, MAX_ANGULAR_SPEED / Math.max(1e-9, Math.abs(steering.y())));
            steering = steering.scale(coordination);
            // 局部俯仰与偏航同比限幅，保持原定的航向变化平面。
            wanted = new Motion(steering.x(), steering.y(), Math.toRadians(wrapDegrees(desiredBank - bank)) * .25);
        }
        double yawLimit = MAX_ANGULAR_SPEED * (free ? rudderAuthority(speed) : 1);
        wanted = new Motion(clamp(wanted.x(), MAX_ANGULAR_SPEED), clamp(wanted.y(), yawLimit), clamp(wanted.z(), MAX_ANGULAR_SPEED));
        Motion worldWanted = body.rotate(wanted).capped(MAX_ANGULAR_SPEED);
        Motion angular = dynamics.angularVelocity().add(worldWanted.add(dynamics.angularVelocity().scale(-1)).capped(ANGULAR_ACCELERATION)).capped(MAX_ANGULAR_SPEED);
        if (!free) {
            // 转向与绕前轴倾斜分别积分，避免倾斜带出额外抬头。
            Motion heading = body.forward(), desired = target.forward();
            Motion axis = heading.cross(desired).normalized();
            double angle = Math.acos(Math.max(-1, Math.min(1, heading.dot(desired))));
            if (axis.speed() < 1e-8 && angle > 1) axis = new Motion(0, -Math.copySign(1, wrapDegrees(yaw(target) - yaw(body))), 0);
            Motion steering = axis.scale(clamp(angular.dot(axis), angle));
            double banking = angular.dot(heading);
            Attitude turning = Attitude.rotation(steering).multiply(body);
            Attitude banked = turning.multiply(Attitude.rotation(new Motion(0, 0, banking)));
            return new Dynamics(banked, angular, dynamics.thrust());
        }
        return new Dynamics(Attitude.rotation(angular).multiply(body), angular, dynamics.thrust());
    }
    private static double clamp(double value, double magnitude) { return Math.max(-magnitude, Math.min(magnitude, value)); }
    public static Motion turnVelocity(Motion velocity, Motion heading, double maxAngle, Motion upHint) {
        double speed = velocity.speed();
        if (speed < 1e-9 || heading.speed() < 1e-9) return velocity;
        Motion direction = velocity.scale(1 / speed), target = heading.normalized();
        double dot = Math.max(-1, Math.min(1, direction.dot(target)));
        double angle = Math.acos(dot);
        if (angle <= maxAngle) return target.scale(speed);
        Motion axis = direction.cross(target);
        if (axis.speed() < 1e-7) {
            axis = direction.cross(upHint);
            if (axis.speed() < 1e-7) axis = direction.cross(Math.abs(direction.y()) < .9 ? new Motion(0,1,0) : new Motion(1,0,0));
        }
        axis = axis.normalized();
        double cosine = Math.cos(maxAngle), sine = Math.sin(maxAngle);
        return direction.scale(cosine).add(axis.cross(direction).scale(sine))
                .add(axis.scale(axis.dot(direction) * (1-cosine))).normalized().scale(speed);
    }
    public static double impactDamage(double speed) { return Math.min(36, 4 + Math.max(0, speed - IMPACT_SPEED) * 12); }
    public static double impactRadius(double speed) { return Math.min(12, 3 + Math.max(0, speed - IMPACT_SPEED) * 3.5); }
    public static double selfDamage(double speed) { return Math.min(18, Math.max(0, speed - DANGER_SPEED) * 15); }
    public static double creatureDamage(double closingSpeed) { return Math.min(24, Math.max(0, closingSpeed - CREATURE_SPEED) * 10 + 2); }
    public static boolean directImpact(double normalSpeed, double speed) { return normalSpeed >= IMPACT_SPEED && normalSpeed >= speed * .55; }
    public static boolean directCreatureImpact(double closingSpeed, double relativeSpeed) { return closingSpeed >= CREATURE_SPEED && closingSpeed >= relativeSpeed * .65; }
}
