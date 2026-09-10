package top.csituka.magicaland.gameplay.sense;

public final class EarthSenseViewerMotionTest {
    private static int checks;
    public static void main(String[] args) {
        close(range(0),14,"stationary radius unchanged");
        close(range(.015),14,"minor contact tolerance");
        check(range(.02)>13.5,"gentle push barely dims");
        close(range(.05),6,"slow crouch radius");
        close(range(.1),4,"faster external movement floor");
        close(range(10),4,"large displacement dims, never creates an unbounded range");
        double previous=14;
        for(int i=0;i<=20000;i++) {
            double current=range(i/100000d);
            check(Double.isFinite(current)&&current>=4&&current<=14&&current<=previous+1e-12,"continuous speed response bounded and monotone");
            check(Math.abs(current-previous)<.01,"no quality step at piecewise boundaries");
            previous=current;
        }
        for(double invalid:new double[]{Double.NaN,Double.NEGATIVE_INFINITY,Double.POSITIVE_INFINITY,-1}) {
            close(range(invalid),4,"invalid speed cannot improve sensing");
            check(Double.isFinite(EarthSenseViewerMotion.range(invalid)),"invalid quality produces a finite radius");
        }
        close(EarthSenseViewerMotion.range(0),4,"range input clamped low");
        close(EarthSenseViewerMotion.range(2),14,"range input clamped high");

        var moving=new EarthSenseViewerMotion(0,0,100);
        double first=moving.observe(.1,0,101);
        check(first<1&&first>EarthSenseViewerMotion.MIN_QUALITY,"first displacement dims smoothly");
        close(moving.observe(.2,0,102),EarthSenseViewerMotion.MIN_QUALITY,"two tick dim reaches floor");
        for(int tick=1;tick<=10;tick++) {
            double recovered=moving.observe(.2,0,102+tick);
            close(recovered,EarthSenseViewerMotion.MIN_QUALITY+(1-EarthSenseViewerMotion.MIN_QUALITY)*tick/10,"half second linear recovery");
        }
        close(moving.quality(),1,"fully recovered without toggling or restarting");

        var axis=new EarthSenseViewerMotion(10,-10,0);
        var diagonal=new EarthSenseViewerMotion(10,-10,0);
        for(int tick=1;tick<100;tick++) {
            close(axis.observe(10+.05*tick,-10,tick),diagonal.observe(10+.05*tick/Math.sqrt(2),-10-.05*tick/Math.sqrt(2),tick),"direction-independent actual distance");
        }
        close(axis.quality(),EarthSenseViewerMotion.viewerQuality(.05),"continued slow crouch holds attenuated quality");
        close(new EarthSenseViewerMotion(0,0,5,.05).quality(),EarthSenseViewerMotion.viewerQuality(.05),"moving entry can initialize at current quality");

        var once=new EarthSenseViewerMotion(0,0,0);
        double sampled=once.observe(.05,0,1);
        for(int i=0;i<50;i++)close(once.observe(.05,0,1),sampled,"same tick cannot repeat smoothing");
        close(once.observe(.1,0,2),EarthSenseViewerMotion.viewerQuality(.05),"same tick observations did not consume displacement");

        var gap=new EarthSenseViewerMotion(0,0,0);
        close(gap.observe(.25,0,5),EarthSenseViewerMotion.viewerQuality(.05),"tick gap uses speed, not raw displacement");
        close(gap.observe(.25,0,15),1,"ten tick gap restores fully");
        close(gap.observe(.25,0,14),EarthSenseViewerMotion.MIN_QUALITY,"clock rewind bounded conservatively");
        close(gap.observe(Double.NaN,0,15),EarthSenseViewerMotion.MIN_QUALITY,"bad coordinate cannot leak NaN");
        check(Double.isFinite(gap.observe(0,0,16)),"valid observations recover after invalid input");

        var still=new EarthSenseViewerMotion(0,0,0);
        for(int tick=1;tick<=200;tick++)close(still.observe(.01*tick,0,tick),1,"sustained harmless drift never accumulates into cancellation or dimming");
        System.out.println("PASS EarthSenseViewerMotionTest: "+checks+" slow-crouch range, gentle pushes, real displacement, direction independence, bounded smoothing and recovery checks");
    }
    private static double range(double speed){return EarthSenseViewerMotion.range(EarthSenseViewerMotion.viewerQuality(speed));}
    private static void close(double actual,double expected,String label){check(Math.abs(actual-expected)<1e-10,label+": "+actual+" vs "+expected);}
    private static void check(boolean ok,String label){checks++;if(!ok)throw new AssertionError(label);}
}
