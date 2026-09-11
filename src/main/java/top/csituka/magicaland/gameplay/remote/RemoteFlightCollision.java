package top.csituka.magicaland.gameplay.remote;

import java.util.function.Predicate;
import net.minecraft.entity.Entity;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Box;
import net.minecraft.util.math.Vec3d;
import net.minecraft.world.World;

public final class RemoteFlightCollision {
    private RemoteFlightCollision() {}

    public static boolean clear(Entity tool, RemoteReturnNavigator.Point from, RemoteReturnNavigator.Point to) {
        World world=tool.getWorld();
        return sweep(from,to,tool.getWidth(),tool.getHeight(),box -> {
            if (box.minY<world.getBottomY()+.01 || box.maxY>world.getTopY()-.01
                    || !world.getWorldBorder().contains(box)
                    || !world.isRegionLoaded(BlockPos.ofFloored(box.minX-1,box.minY,box.minZ-1),
                            BlockPos.ofFloored(box.maxX+1,box.maxY,box.maxZ+1))) return false;
            return !world.getBlockCollisions(tool,box).iterator().hasNext();
        });
    }

    public static boolean sweep(RemoteReturnNavigator.Point from,RemoteReturnNavigator.Point to,
                                double width,double height,Predicate<Box> free) {
        if (!Double.isFinite(width) || !Double.isFinite(height) || width<=0 || height<=0) return false;
        double dx=to.x()-from.x(),dy=to.y()-from.y(),dz=to.z()-from.z();
        double span=Math.max(Math.abs(dx),Math.max(Math.abs(dy),Math.abs(dz)));
        if (!Double.isFinite(span) || span>256) return false;
        int steps=Math.max(1,(int)Math.ceil(span/.5));
        Vec3d previous=new Vec3d(from.x(),from.y(),from.z());
        double half=width/2;
        for (int i=1;i<=steps;i++) {
            Vec3d next=new Vec3d(from.x()+dx*i/steps,from.y()+dy*i/steps,from.z()+dz*i/steps);
            Box segment=new Box(previous.x-half,previous.y,previous.z-half,
                    previous.x+half,previous.y+height,previous.z+half).stretch(next.subtract(previous));
            if (!free.test(segment)) return false;
            previous=next;
        }
        return true;
    }
}
