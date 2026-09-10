import top.csituka.magicaland.gameplay.remote.RemoteToolMath;

public final class RemoteToolMathTest {
    private static int checks;
    private static void check(boolean value) { checks++; if (!value) throw new AssertionError("check "+checks); }
    public static void main(String[] args) {
        for (int yaw=-540;yaw<=540;yaw+=15) for (int pitch=-89;pitch<=89;pitch+=10) for (int keys=0;keys<256;keys++) {
            double[] d=RemoteToolMath.movement(yaw,pitch,keys);
            double length=Math.sqrt(d[0]*d[0]+d[1]*d[1]+d[2]*d[2]);
            check(Double.isFinite(length) && length<=RemoteToolMath.SPEED+1e-10);
            double[] same=RemoteToolMath.movement(yaw,pitch,keys&63);
            check(java.util.Arrays.equals(d,same));
        }
        check(RemoteToolMath.movement(0,0,1)[2]>0);
        check(RemoteToolMath.movement(0,0,4)[0]>0);
        check(RemoteToolMath.movement(90,0,1)[0]<0);
        check(RemoteToolMath.movement(0,-45,1)[1]>0);
        check(RemoteToolMath.approach(179,-179,6)==181);
        float[] pose={0,0,0};
        for (int i=0;i<100;i++) {
            float[] next=RemoteToolMath.facing(0,2,-8,pose[0],pose[1],pose[2]);
            check(Math.abs(RemoteToolMath.wrap(next[0]-pose[0]))<=6.001);
            check(Math.abs(RemoteToolMath.wrap(next[1]-next[0]))<=75.001);
            check(Math.abs(next[2])<=60.001); pose=next;
        }
        check(Math.abs(RemoteToolMath.wrap(pose[1]-180))<=14.01);
        float[] still=RemoteToolMath.facing(-Math.sin(Math.toRadians(12)),0,Math.cos(Math.toRadians(12)),0,0,0);
        check(still[0]==0 && still[1]==0 && still[2]==0);
        check(RemoteToolMath.facing(-1,0,1,0,0,0)[1]>0);
        check(RemoteToolMath.facing(0,.1,1,0,0,0)[2]==0);
        check(RemoteToolMath.facing(0,1,1,0,0,0)[2]<0);
        System.out.println("PASS RemoteToolMathTest: "+checks+" checks");
    }
}
