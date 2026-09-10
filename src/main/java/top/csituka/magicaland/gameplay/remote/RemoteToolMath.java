package top.csituka.magicaland.gameplay.remote;

public final class RemoteToolMath {
    public static final double RANGE = 16, SPEED = .3;
    private RemoteToolMath() {}
    public static float wrap(float angle) {
        angle %= 360;
        return angle >= 180 ? angle - 360 : angle < -180 ? angle + 360 : angle;
    }
    public static float approach(float from, float to, float step) {
        return from + Math.max(-step, Math.min(step, wrap(to - from)));
    }
    public static double[] movement(float yaw, float pitch, int keys) {
        double y = Math.toRadians(yaw), p = Math.toRadians(pitch);
        int forward = ((keys & 1) != 0 ? 1 : 0) - ((keys & 2) != 0 ? 1 : 0);
        int side = ((keys & 4) != 0 ? 1 : 0) - ((keys & 8) != 0 ? 1 : 0);
        int up = ((keys & 16) != 0 ? 1 : 0) - ((keys & 32) != 0 ? 1 : 0);
        double x = -Math.sin(y) * Math.cos(p) * forward + Math.cos(y) * side;
        double z = Math.cos(y) * Math.cos(p) * forward + Math.sin(y) * side;
        double vertical = -Math.sin(p) * forward + up;
        double length = Math.sqrt(x*x + z*z + vertical*vertical);
        double scale = SPEED / Math.max(1, length);
        return new double[] {x*scale, vertical*scale, z*scale};
    }
    public static float[] facing(double x, double y, double z, float body, float head, float pitch) {
        if (x*x+y*y+z*z<1e-6) return new float[] {body,head,pitch};
        float yaw = (float)Math.toDegrees(Math.atan2(-x, z));
        float targetPitch = (float)-Math.toDegrees(Math.atan2(y, Math.hypot(x, z)));
        float error=wrap(yaw-head);
        float nextHead = Math.abs(error)>14 ? approach(head,yaw-Math.signum(error)*10,6) : head;
        float nextBody = Math.abs(wrap(nextHead-body)) > 45 ? approach(body,nextHead,4) : body;
        nextHead = nextBody + Math.max(-75, Math.min(75, wrap(nextHead-nextBody)));
        float pitchError=targetPitch-pitch;
        float nextPitch=Math.abs(pitchError)>10 ? approach(pitch,targetPitch-Math.signum(pitchError)*7,4) : pitch;
        return new float[] {nextBody, nextHead, Math.max(-60,Math.min(60,nextPitch))};
    }
}
