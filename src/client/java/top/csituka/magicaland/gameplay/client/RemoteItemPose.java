package top.csituka.magicaland.gameplay.client;

import org.joml.Quaternionf;
import org.joml.Quaternionfc;

public final class RemoteItemPose {
    private static final float RAD=(float)(Math.PI/180);
    private RemoteItemPose() {}

    public static Quaternionf tool(float yaw, float pitch, float swing, boolean left, boolean flat) {
        float side=left?-1:1;
        float progress=Math.max(0,Math.min(1,swing));
        float arc=(float)Math.sin(Math.sqrt(progress)*Math.PI);
        float strike=(float)Math.sin(progress*Math.PI);
        float lean=Math.max(-18,Math.min(18,pitch*.3f));
        return new Quaternionf().rotateY(-yaw*RAD).rotateX(lean*RAD)
                .rotateY(side*(18-arc*35)*RAD).rotateZ(-side*arc*18*RAD)
                .rotateX((45+strike*65)*RAD).rotateZ(flat?45*RAD:0);
    }

    public static Quaternionf billboard(Quaternionfc camera, float swing, boolean left) {
        float progress=Math.max(0,Math.min(1,swing));
        float arc=(float)Math.sin(Math.sqrt(progress)*Math.PI);
        return new Quaternionf(camera).rotateY((float)Math.PI).rotateZ((left?1:-1)*arc*18*RAD);
    }

    public static Quaternionf upright(float yaw) {
        return new Quaternionf().rotateY((180-yaw)*RAD);
    }

    public static Quaternionf withoutDisplayRotation(Quaternionfc desired, float x, float y, float z, boolean left) {
        float side=left?-1:1;
        return new Quaternionf(desired).mul(new Quaternionf().rotationXYZ(x*RAD,side*y*RAD,side*z*RAD).invert());
    }
}
