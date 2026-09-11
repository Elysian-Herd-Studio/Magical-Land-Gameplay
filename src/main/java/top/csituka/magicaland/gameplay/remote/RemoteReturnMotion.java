package top.csituka.magicaland.gameplay.remote;

public final class RemoteReturnMotion {
    public static final double SPEED=.45;
    private RemoteReturnMotion() {}
    public static double[] approach(double vx,double vy,double vz,double dx,double dy,double dz) {
        double distance=Math.sqrt(dx*dx+dy*dy+dz*dz);
        if (!Double.isFinite(distance) || !Double.isFinite(vx) || !Double.isFinite(vy) || !Double.isFinite(vz))
            return new double[]{0,0,0};
        if (distance<1e-7) return new double[]{0,0,0};
        double speed=Math.min(SPEED,Math.sqrt(.10*distance));
        double x=vx+(dx/distance*speed-vx)*.28;
        double y=vy+(dy/distance*speed-vy)*.28;
        double z=vz+(dz/distance*speed-vz)*.28;
        double length=Math.sqrt(x*x+y*y+z*z), scale=Math.min(1,Math.min(SPEED,distance)/Math.max(length,1e-9));
        return new double[]{x*scale,y*scale,z*scale};
    }
}
