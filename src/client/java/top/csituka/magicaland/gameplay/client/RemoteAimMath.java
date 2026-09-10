package top.csituka.magicaland.gameplay.client;

import org.joml.Matrix4fc;
import org.joml.Quaternionfc;
import org.joml.Vector3f;
import org.joml.Vector4f;

public final class RemoteAimMath {
    public record Point(float x, float y) {}
    private RemoteAimMath() {}

    public static Point project(Matrix4fc view, Matrix4fc projection,
            double x, double y, double z, double cameraX, double cameraY, double cameraZ) {
        var clip=new Vector4f((float)(x-cameraX),(float)(y-cameraY),(float)(z-cameraZ),1);
        view.transform(clip);projection.transform(clip);
        if (!clip.isFinite() || clip.w<=.00001f) return null;
        float nx=clip.x/clip.w, ny=clip.y/clip.w, nz=clip.z/clip.w;
        if (Math.abs(nx)>1 || Math.abs(ny)>1 || nz < -1 || nz > 1) return null;
        return new Point(nx*.5f+.5f,.5f-ny*.5f);
    }

    public static Vector3f itemSideOffset(Quaternionfc camera, boolean left) {
        var right=camera.transform(new Vector3f(-1,0,0));
        if (!right.isFinite() || right.lengthSquared()<.00001f) return new Vector3f();
        return right.normalize().mul(left?-.30f:.30f);
    }
}
