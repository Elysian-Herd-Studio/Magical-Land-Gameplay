package top.csituka.magicaland.gameplay.sense;

import java.util.Random;
import static top.csituka.magicaland.gameplay.sense.EarthSenseLease.Action.*;

public final class EarthSenseLeaseTest {
    private static int checks;
    public static void main(String[] args) {
        var lease=new EarthSenseLease();
        check(lease.control(1,true,0)==START&&!lease.closed(),"start");
        check(!lease.expired(59)&&lease.expired(60),"exact lease deadline");
        check(lease.control(1,true,20)==RENEW&&!lease.expired(79)&&lease.expired(80),"same token heartbeat");
        lease.close();check(lease.control(1,true,100)==IGNORE&&lease.closed(),"late heartbeat never restarts timeout");
        check(lease.control(2,true,101)==START,"new token restarts");
        check(lease.control(1,false,102)==IGNORE&&!lease.closed(),"late old off cannot cancel new session");
        check(lease.control(3,false,103)==STOP&&lease.closed(),"new false supersedes true");
        check(lease.control(3,true,104)==IGNORE,"same token closed tombstone");
        check(lease.control(4,true,105)==START&&lease.control(4,false,106)==STOP,"same token cancellation also final");
        check(lease.control(Long.MAX_VALUE,true,107)==START,"maximum positive token");
        lease.close();check(lease.control(Long.MAX_VALUE,true,108)==IGNORE,"maximum token never wraps");
        var random=new Random(4096);lease=new EarthSenseLease();long modelToken=0,heartbeat=0;boolean closed=true;
        for(int tick=0;tick<20000;tick++) {
            if(random.nextInt(9)==0){lease.close();closed=true;}
            long token=Math.max(-1,modelToken+random.nextInt(5)-2);boolean enabled=random.nextBoolean();
            EarthSenseLease.Action expected;
            if(token<=0||token<modelToken)expected=IGNORE;
            else if(token==modelToken){if(closed)expected=IGNORE;else if(!enabled){closed=true;expected=STOP;}else{heartbeat=tick;expected=RENEW;}}
            else{modelToken=token;heartbeat=tick;closed=!enabled;expected=enabled?START:STOP;}
            check(lease.control(token,enabled,tick)==expected,"random ordering action");
            check(lease.token()==modelToken&&lease.closed()==closed,"random ordering state");
            check(lease.expired(tick+60)==(!closed&&tick+60-heartbeat>=60),"random timeout");
        }
        System.out.println("PASS EarthSenseLeaseTest: "+checks+" delayed/reordered controls, renewals, closure tombstones and timeout checks");
    }
    private static void check(boolean ok,String label){checks++;if(!ok)throw new AssertionError(label);}
}
