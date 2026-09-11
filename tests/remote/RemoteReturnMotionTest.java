import top.csituka.magicaland.gameplay.remote.RemoteReturnMotion;

public final class RemoteReturnMotionTest {
    private static int checks;
    public static void main(String[] args) {
        for (double start : new double[]{.01,.1,.7,1,5,128}) {
            double x=start,vx=0;
            for (int tick=0;tick<2000 && Math.abs(x)>1e-6;tick++) {
                var next=RemoteReturnMotion.approach(vx,0,0,-x,0,0);
                check(Math.abs(next[0])<=Math.min(.45,Math.abs(x))+1e-10,"bounded speed and near-waypoint displacement");
                vx=next[0]; x+=vx;
                check(x>=-1e-9,"return slows down without crossing the waypoint");
            }
            check(Math.abs(x)<1e-6,"returns from both near and far distances");
        }
        var initial=RemoteReturnMotion.approach(0,0,0,100,0,0);
        check(initial[0]>0 && initial[0]<RemoteReturnMotion.SPEED,"smooth launch");
        var reversing=RemoteReturnMotion.approach(.45,0,0,-20,0,0);
        check(reversing[0]>.0 && reversing[0]<.45,"direction reversal retains inertia before braking");
        double[] motion={.2,-.1,.3};
        for (int tick=0;tick<200;tick++) {
            motion=RemoteReturnMotion.approach(motion[0],motion[1],motion[2],Math.sin(tick)*10,Math.cos(tick*.7)*10,10);
            check(Math.sqrt(motion[0]*motion[0]+motion[1]*motion[1]+motion[2]*motion[2])<=.45000001,"3D speed cap");
        }
        for (double value : new double[]{Double.NaN,Double.POSITIVE_INFINITY}) {
            var invalid=RemoteReturnMotion.approach(value,0,0,1,2,3);
            check(invalid[0]==0 && invalid[1]==0 && invalid[2]==0,"bad velocity cannot poison return entity");
        }
        System.out.println("PASS RemoteReturnMotionTest: "+checks);
    }
    private static void check(boolean value,String message) { checks++; if (!value) throw new AssertionError(message); }
}
