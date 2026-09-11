import java.util.ArrayList;
import net.minecraft.util.math.Box;
import top.csituka.magicaland.gameplay.remote.RemoteFlightCollision;
import top.csituka.magicaland.gameplay.remote.RemoteReturnNavigator.Point;

public final class RemoteFlightCollisionTest {
    private static int checks;
    public static void main(String[] args) {
        var start=new Point(0,0,0);
        for (var end : new Point[]{new Point(128,0,0),new Point(-128,64,-64),new Point(.05,.1,.03),start}) {
            var boxes=new ArrayList<Box>();
            check(RemoteFlightCollision.sweep(start,end,.3,.3,b -> { boxes.add(b); return true; }),"clear path accepted");
            check(boxes.size()<=256,"long diagonal is bounded subdivisions, not cubic-volume scanning");
            for (var box : boxes) check(box.getXLength()<=.80000001 && box.getYLength()<=.80000001
                    && box.getZLength()<=.80000001,"sweep keeps a small collision volume");
        }
        var wall=new Box(1,-5,-5,1.1,5,5);
        check(!RemoteFlightCollision.sweep(start,new Point(2,0,0),.3,.3,b -> !b.intersects(wall)),"thin wall cannot be tunneled through");
        var lowRoof=new Box(-1,.29,-1,1,1,1);
        check(!RemoteFlightCollision.sweep(start,start,.3,.3,b -> !b.intersects(lowRoof)),"stationary occupancy is checked");
        var nearEdge=new Box(.14,0,-.5,.2,1,.5);
        check(!RemoteFlightCollision.sweep(start,new Point(0,0,1),.3,.3,b -> !b.intersects(nearEdge)),"ball width participates, not only center ray");
        check(RemoteFlightCollision.sweep(start,new Point(0,0,4),.3,.3,
                b -> b.minX>=-.5 && b.maxX<=.5 && b.minY>=0 && b.maxY<=1),"one-block corridor remains traversable");
        check(!RemoteFlightCollision.sweep(start,new Point(257,0,0),.3,.3,b -> {throw new AssertionError("budget failure must be early");}),"unbounded sweep rejected");
        check(!RemoteFlightCollision.sweep(start,start,Double.NaN,.3,b -> true),"invalid collision dimensions rejected");
        System.out.println("PASS RemoteFlightCollisionTest: "+checks);
    }
    private static void check(boolean value,String message) { checks++; if (!value) throw new AssertionError(message); }
}
