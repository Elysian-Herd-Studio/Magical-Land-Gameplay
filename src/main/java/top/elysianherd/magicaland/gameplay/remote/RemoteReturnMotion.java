package top.elysianherd.magicaland.gameplay.remote;

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

    static RemoteReturnNavigator.Point safeStep(RemoteReturnNavigator.Point from, RemoteReturnNavigator.Point waypoint,
                                               RemoteReturnNavigator.Point velocity, RemoteReturnNavigator.Collision collision) {
        double dx=waypoint.x()-from.x(), dy=waypoint.y()-from.y(), dz=waypoint.z()-from.z();
        double[] motion=approach(velocity.x(),velocity.y(),velocity.z(),dx,dy,dz);
        var next=new RemoteReturnNavigator.Point(from.x()+motion[0],from.y()+motion[1],from.z()+motion[2]);
        if (collision.clear(from,next)) return next;
        // 转角处先沿已验证的路径收住惯性，避免反复撞墙。
        double distance=from.distanceTo(waypoint), scale=Math.min(.12,distance)/Math.max(distance,1e-9);
        next=new RemoteReturnNavigator.Point(from.x()+dx*scale,from.y()+dy*scale,from.z()+dz*scale);
        return collision.clear(from,next)?next:from;
    }
}
