package net.minecraft.util.math;
public record Vec3d(double x, double y, double z) {
    public double squaredDistanceTo(Vec3d other) {
        return (x-other.x)*(x-other.x) + (y-other.y)*(y-other.y) + (z-other.z)*(z-other.z);
    }
}
