package top.csituka.magicaland.gameplay.client.pegasus;

import net.minecraft.util.math.MathHelper;
import net.minecraft.util.math.Vec3d;
import org.joml.Quaternionf;
import top.csituka.magicaland.gameplay.pegasus.PegasusFlightMath.Attitude;

public final class PegasusFlightView {
    private static final float DEG = (float) (Math.PI / 180);
    private PegasusFlightView() {}

    public static Quaternionf quaternion(Attitude value) { return new Quaternionf(value.x(), value.y(), value.z(), value.w()); }
    public static Attitude attitude(Quaternionf value) {
        value.normalize();
        return new Attitude(value.x(), value.y(), value.z(), value.w());
    }
    public static Vec3d forward(Attitude value) {
        var direction = quaternion(value).transform(0, 0, 1, new org.joml.Vector3f());
        return new Vec3d(direction.x, direction.y, direction.z);
    }
    public static float yaw(Attitude value) {
        var forward = forward(value);
        return (float) Math.toDegrees(Math.atan2(-forward.x, forward.z));
    }
    public static float pitch(Attitude value) {
        return (float) Math.toDegrees(-Math.asin(MathHelper.clamp(forward(value).y, -1, 1)));
    }
    public static Attitude turn(Attitude value, double horizontal, double vertical, boolean free) {
        float yawDelta = (float) MathHelper.clamp(horizontal * .15, -90, 90);
        float pitchDelta = (float) MathHelper.clamp(vertical * .15, -90, 90);
        if (free) return attitude(quaternion(value).rotateY(-yawDelta * DEG).rotateX(pitchDelta * DEG));
        var flat = quaternion(Attitude.fromYawPitch(yaw(value), pitch(value)));
        var roll = flat.conjugate().mul(quaternion(value));
        return attitude(quaternion(Attitude.fromYawPitch(yaw(value) + yawDelta,
                MathHelper.clamp(pitch(value) + pitchDelta, -85, 85))).mul(roll));
    }
    public static Attitude roll(Attitude value, float degrees) { return attitude(quaternion(value).rotateZ(degrees * DEG)); }
    public static Attitude level(Attitude value, float amount) {
        return blend(value, Attitude.fromYawPitch(yaw(value), MathHelper.clamp(pitch(value), -85, 85)), amount);
    }
    public static Attitude blend(Attitude from, Attitude to, float amount) {
        return attitude(quaternion(from).slerp(quaternion(to), MathHelper.clamp(amount, 0, 1)));
    }
    public static Attitude limitedLead(Attitude body, Attitude target, float degrees) {
        float dot = Math.abs(quaternion(body).dot(quaternion(target)));
        double angle = 2 * Math.acos(MathHelper.clamp(dot, 0, 1));
        return angle <= degrees * DEG ? target : blend(body, target, (float) (degrees * DEG / angle));
    }
    public static Attitude camera(Attitude body, Attitude look, boolean aerobatics) {
        if (!aerobatics) return look;
        var relative = attitude(quaternion(body).conjugate().mul(quaternion(look)));
        var offset = Attitude.fromYawPitch(MathHelper.clamp(yaw(relative), -12, 12),
                MathHelper.clamp(pitch(relative), -10, 10));
        return attitude(quaternion(body).mul(quaternion(offset)));
    }
    public static Attitude difference(Attitude actual, Attitude predicted) {
        return attitude(quaternion(actual).mul(quaternion(predicted).conjugate()));
    }
    public static Attitude applyDifference(Attitude difference, Attitude value) {
        return attitude(quaternion(difference).mul(quaternion(value)));
    }
}
