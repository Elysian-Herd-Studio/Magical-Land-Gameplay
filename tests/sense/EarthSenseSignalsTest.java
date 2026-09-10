package top.csituka.magicaland.gameplay.sense;

import java.util.*;

public final class EarthSenseSignalsTest {
    private static int checks;
    public static void main(String[] args) {
        var state=new EarthSenseSignals();var observations=new ArrayList<EarthSenseSignals.Observation>();
        for(int i=63;i>=0;i--)observations.add(observe(i,(i+1)*.2,0));
        var signals=state.update(observations,0);check(signals.size()==32,"nearest 32 from bounded 64 candidates");
        for(int i=0;i<32;i++){var signal=signals.get(i);check(signal.id()==i+1,"short IDs allocated independently of entities");check(signal.strength()==EarthSenseRules.strength((i+1)*.2),"nearest ordering uses real distance");}
        check(signals.stream().map(s->s.id()).distinct().count()==32,"same position and kind never merged");
        var reordered=new ArrayList<>(observations);Collections.shuffle(reordered,new Random(4));check(state.update(reordered,1).equals(signals),"input order has no effect on stable IDs or rhythm");
        for(var signal:signals) {
            check(quarter(signal.x())&&quarter(signal.y())&&quarter(signal.z())&&quarter(signal.width())&&quarter(signal.height()),"position and coarse body dimensions quantized");
            check(signal.activity()==.12f&&signal.width()==.5f&&signal.height()==2,"still remains weak without false distant classification");
        }
        var first=state.update(List.of(observe(0,1,0)),2).get(0);check(first.id()==1,"existing individual ID preserved");
        state.update(List.of(),3);var reentry=state.update(List.of(observe(0,1,0)),4).get(0);check(reentry.id()>32,"departed mapping removed; reentry not recycled");
        var activity=new EarthSenseSignals();var idle=activity.update(List.of(observe(0,1,0)),0).get(0);
        var running=activity.update(List.of(observe(0,1,2)),5).get(0);check(running.id()==idle.id()&&running.pulse()!=idle.pulse()&&running.activity()>.8,"faster movement triggers without stale idle delay");
        check(running.strength()==idle.strength()&&idle.strength()==4,"distance independent of activity");
        check(activity.update(List.of(observe(0,1,2)),6).get(0).pulse()==running.pulse(),"unchanged pulse between footsteps");
        var next=activity.update(List.of(observe(0,1,2)),10).get(0);check(next.pulse()==((running.pulse()+1)&255),"sprint rhythm");
        var landing=activity.update(List.of(observe(0,1,3)),11).get(0);check(landing.pulse()==((next.pulse()+1)&255)&&landing.activity()==1,"landing immediate pulse");
        for(int i=0;i<300;i++){var s=activity.update(List.of(observe(0,1,2)),20L+i*5).get(0);check(s.pulse()>=0&&s.pulse()<=255,"bounded pulse wrap");}
        var unusual=new EarthSenseSignals.Observation(new UUID(5,1),2,-.126,30000000,-.124,100,.001f,2,1);
        var clamped=new EarthSenseSignals().update(List.of(unusual),0).get(0);check(clamped.x()==-.25&&clamped.z()==0&&clamped.width()==8&&clamped.height()==.25,"signed position quantization and coarse size caps");
        check(new EarthSenseSignals().update(List.of(observe(0,14.01,0)),0).isEmpty(),"out of range skipped");
        var bad=new EarthSenseSignals.Observation(new UUID(5,2),2,Double.NaN,0,0,1,1,2,0);check(new EarthSenseSignals().update(List.of(bad),0).isEmpty(),"nonfinite target skipped");
        var viewer=new EarthSenseSignals();
        var all=List.of(observe(10,2,EarthSenseRules.RUN),observe(11,5,EarthSenseRules.STILL),observe(12,10,EarthSenseRules.WALK));
        var full=viewer.update(all,0,14);
        var crouching=viewer.update(all,1,6);
        check(crouching.size()==2,"viewer motion excludes targets beyond effective range");
        check(crouching.get(0).id()==full.get(0).id()&&crouching.get(1).id()==full.get(1).id(),"remaining targets keep stable interpolation IDs");
        check(crouching.get(0).strength()==3&&crouching.get(1).strength()==1,"same distance weaker inside reduced range");
        check(crouching.get(0).activity()==full.get(0).activity()&&crouching.get(1).activity()==full.get(1).activity(),"viewer speed never replaces target activity");
        check(crouching.get(0).pulse()==full.get(0).pulse(),"viewer dimming creates no false target footstep");
        var recovered=viewer.update(all,2,14);
        check(recovered.size()==3&&recovered.get(0).id()==full.get(0).id()&&recovered.get(2).id()>full.get(2).id(),"stop recovery restores distant targets with fresh departed IDs");
        check(viewer.update(all,3,Double.NaN).isEmpty(),"bad effective range emits no malformed strength");
        var exhausted=new EarthSenseSignals(65535);check(exhausted.update(List.of(observe(0,1,0)),0).get(0).id()==65535,"last short ID usable");exhausted.update(List.of(),1);
        try{exhausted.update(List.of(observe(1,1,0)),2);throw new AssertionError("ID wrapped");}catch(IllegalStateException expected){checks++;}
        System.out.println("PASS EarthSenseSignalsTest: "+checks+" independent targets, nearest cap, stable short IDs, quantization, size/rhythm and exhaustion checks");
    }
    private static EarthSenseSignals.Observation observe(int id,double distance,int activity){return new EarthSenseSignals.Observation(new UUID(0,id),0,3.13,64.88,-2.88,.61f,1.93f,distance,activity);}
    private static boolean quarter(double value){return value*4==Math.rint(value*4);}
    private static void check(boolean ok,String label){checks++;if(!ok)throw new AssertionError(label);}
}
