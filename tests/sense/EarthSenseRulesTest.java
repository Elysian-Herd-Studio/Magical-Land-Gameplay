package top.csituka.magicaland.gameplay.sense;

public final class EarthSenseRulesTest {
    private static int checks;
    public static void main(String[] args) {
        check(EarthSenseRules.RANGE==14 && EarthSenseRules.MAX_ACTIVE==8,"initial range and active cap");
        check(EarthSenseRules.MAX_CANDIDATES==64 && EarthSenseRules.MAX_NODES==2048 && EarthSenseRules.MAX_PROBES==4096,"bounded server work");
        for(int activity=0;activity<=4;activity++) {
            check(EarthSenseRules.activityLevel(activity)>=0&&EarthSenseRules.activityLevel(activity)<=1,"finite activity scale");
            int last=5;
            for(int millimeters=0;millimeters<=14000;millimeters++) {
                double distance=millimeters/1000d;int strength=EarthSenseRules.strength(distance);
                check(strength>=1&&strength<=last&&strength<=4,"distance never stronger farther away");last=strength;
            }
        }
        check(EarthSenseRules.activityLevel(0)<EarthSenseRules.activityLevel(4)&&EarthSenseRules.activityLevel(4)<EarthSenseRules.activityLevel(1)&&EarthSenseRules.activityLevel(1)<EarthSenseRules.activityLevel(2)&&EarthSenseRules.activityLevel(2)<EarthSenseRules.activityLevel(3),"activity separate from distance; idle weakest landing strongest");
        for(double invalid:new double[]{-.001,14.001,Double.NaN,Double.POSITIVE_INFINITY,Double.NEGATIVE_INFINITY})check(EarthSenseRules.strength(invalid)==0,"invalid/outside distance hidden");
        for(double range:new double[]{4,6,10,14}) {
            int last=5;
            for(int step=0;step<=10000;step++) {
                double distance=range*step/10000;
                int strength=EarthSenseRules.strength(distance,range);
                check(strength>=1&&strength<=last&&strength<=EarthSenseRules.strength(distance),"reduced range never improves distance strength");
                last=strength;
            }
            check(EarthSenseRules.strength(range,range)==1&&EarthSenseRules.strength(range+.00001,range)==0,"effective radius has explicit inclusive boundary");
        }
        for(double invalidRange:new double[]{0,-1,14.001,Double.NaN,Double.POSITIVE_INFINITY})check(EarthSenseRules.strength(1,invalidRange)==0,"invalid effective range hidden");
        check(EarthSenseRules.interval(2)<EarthSenseRules.interval(1)&&EarthSenseRules.interval(1)<EarthSenseRules.interval(4)&&EarthSenseRules.interval(4)<EarthSenseRules.interval(0),"run walk sneak idle cadence");
        check(EarthSenseRules.activity(0,false,false,true)==0,"stationary sprint key no walking");
        check(EarthSenseRules.activity(.1,false,false,false)==1&&EarthSenseRules.activity(.3,false,false,false)==2,"actual speed activity");
        check(EarthSenseRules.activity(.3,false,true,true)==4&&EarthSenseRules.activity(0,true,true,false)==3,"sneak and landing precedence");
        for(boolean target:new boolean[]{false,true})for(boolean neutral:new boolean[]{false,true})for(boolean monster:new boolean[]{false,true})for(boolean friendly:new boolean[]{false,true}) {
            check(EarthSenseRules.kind(true,target,neutral,monster,friendly)==1,"players never become hostile-category IDs");
            if(target)check(EarthSenseRules.kind(false,true,neutral,monster,friendly)==0,"targeting observer hostile");
            if(!target&&neutral)check(EarthSenseRules.kind(false,false,true,monster,friendly)==2,"unprovoked neutral not blanket hostile");
        }
        System.out.println("PASS EarthSenseRulesTest: "+checks+" independent range/strength/activity, rhythm, classification and budget checks");
    }
    private static void check(boolean ok,String label){checks++;if(!ok)throw new AssertionError(label);}
}
