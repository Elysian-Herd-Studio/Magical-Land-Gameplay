package top.csituka.magicaland.gameplay.client.sense;

public final class EarthSenseViewMathTest {
    private static int checks;
    public static void main(String[] args) {
        for(boolean world:new boolean[]{false,true})for(boolean player:new boolean[]{false,true})
            for(boolean screen:new boolean[]{false,true})for(boolean remote:new boolean[]{false,true})
                check(EarthSenseViewMath.applies(world,player,screen,remote)==(world&&player&&!screen&&!remote),"world/player only scope");
        for(double fov:new double[]{30,50,70,90,110,150}) {
            near(fov,EarthSenseViewMath.fov(fov,0),"zero opacity untouched");near(fov+6,EarthSenseViewMath.fov(fov,1),"six degrees at full focus");
            for(int i=0;i<=1000;i++)near(fov+6*i/1000d,EarthSenseViewMath.fov(fov,i/1000f),"smooth opacity FOV");
        }
        for(double delta:new double[]{-1000,-5,-.01,-0d,0,.01,5,1000})for(int i=0;i<=1000;i++) {
            double result=EarthSenseViewMath.look(delta,i/1000f);
            near(delta*(1-.15*(i/1000f)),result,"relative final vanilla delta");
            check(Math.abs(result)<=Math.abs(delta)+1e-9&&Math.abs(result)>=Math.abs(delta)*.85-1e-9,"sensitivity bounded and never inverted");
        }
        for(float invalid:new float[]{Float.NaN,Float.POSITIVE_INFINITY,Float.NEGATIVE_INFINITY,-1}) {
            near(70,EarthSenseViewMath.fov(70,invalid),"invalid opacity preserves FOV");near(-5,EarthSenseViewMath.look(-5,invalid),"invalid opacity preserves mouse");
        }
        near(175,EarthSenseViewMath.fov(174,1),"do not cross extreme perspective bound");
        near(180,EarthSenseViewMath.fov(180,1),"do not clamp another camera mod's existing FOV");
        near(-1,EarthSenseViewMath.fov(-1,1),"invalid base left unchanged");
        near(76,EarthSenseViewMath.fov(70,2),"opacity clamped high");
        for(double finalVanilla:new double[]{.001,.0125,.24,3})near(finalVanilla*.85,EarthSenseViewMath.look(finalVanilla,1),"smooth-camera/spyglass final output scaled, not raw input");
        for(int fps:new int[]{30,60,144})for(int frame=0;frame<=fps;frame++) {
            float opacity=(float)Math.min(1,frame*20d/fps/10);
            near(70d+6d*opacity,EarthSenseViewMath.fov(70,opacity),"render rate does not accumulate offsets");
            near(EarthSenseViewMath.fov(70,opacity),EarthSenseViewMath.fov(70,opacity),"multiple render passes idempotent");
        }
        near(70,EarthSenseViewMath.fov(70,0),"fade exit restores supplied live setting");
        near(93,EarthSenseViewMath.fov(93,0),"changed user FOV never overwritten by saved old value");
        System.out.println("PASS EarthSenseViewMathTest: "+checks+" FOV +6, final mouse x0.85, smooth fade, scope, finite bounds and nonpersistent composition");
    }
    private static void near(double expected,double actual,String label){check(Double.isFinite(actual)&&Math.abs(expected-actual)<2e-6,label);}
    private static void check(boolean ok,String label){checks++;if(!ok)throw new AssertionError(label);}
}
