package top.csituka.magicaland.gameplay.client.sense;

import io.netty.buffer.Unpooled;
import java.util.ArrayList;
import java.util.List;
import net.minecraft.network.PacketByteBuf;
import top.csituka.magicaland.gameplay.sense.EarthSenseProtocol;

public final class EarthSenseSessionTest {
    private static int checks;
    private static final String OVERWORLD="minecraft:overworld",NETHER="minecraft:the_nether";
    private static final List<EarthSenseProtocol.Signal> FIRST=List.of(new EarthSenseProtocol.Signal(2,0,1,2,3,1,2,.5f,3,17),new EarthSenseProtocol.Signal(9,1,2,3,4,1,2,.25f,1,255));
    private static final List<EarthSenseProtocol.Signal> SECOND=List.of(new EarthSenseProtocol.Signal(14,3,3,4,5,2,3,1,4,4));

    public static void main(String[] args) throws Exception {
        var session=new EarthSenseSession();
        check(!session.wanted()&&!session.active()&&!session.grounded()&&session.signals().isEmpty(),"fresh session has no requested or rendered state");
        check(!session.accept(state(1,0,OVERWORLD,true,true,"",FIRST),OVERWORLD),"unsolicited start cannot activate a fresh session");
        long first=session.begin(true);
        check(first>0&&session.wanted()&&!session.active(),"begin allocates positive token without claiming server activation");
        check(session.signals().isEmpty()&&session.reason().isEmpty(),"new request clears old visual data and error");
        check(!session.accept(state(first+1,0,OVERWORLD,true,true,"",FIRST),OVERWORLD),"future token cannot activate pending request");
        check(!session.accept(state(first,99,NETHER,true,true,"",FIRST),OVERWORLD),"wrong dimension cannot activate pending request");
        check(!session.active()&&session.signals().isEmpty(),"rejected packets leave pending state untouched");
        check(session.accept(state(first,0,OVERWORLD,true,true,"",FIRST),OVERWORLD),"matching server start is accepted");
        check(session.active()&&session.wanted()&&session.grounded()&&session.signals().equals(FIRST),"accepted grounded snapshot establishes active signals");
        expectUnsupported(()->session.signals().clear(),"renderers cannot mutate session signal list");

        check(!session.accept(state(first,0,OVERWORLD,true,true,"",SECOND),OVERWORLD),"duplicate sequence cannot replace signals");
        check(session.accept(state(first,2,OVERWORLD,true,true,"",SECOND),OVERWORLD),"newer sequence replaces grounded signals");
        check(!session.accept(state(first,1,OVERWORLD,true,true,"",FIRST),OVERWORLD),"older sequence cannot overwrite newer signals");
        check(session.signals().equals(SECOND),"reordered and duplicate snapshots preserve newest signal content");
        check(!session.accept(state(first,500,NETHER,false,false,"dimension",List.of()),OVERWORLD),"foreign dimension close cannot end local session");
        check(session.accept(state(first,3,OVERWORLD,true,false,"airborne",List.of()),OVERWORLD),"temporary ground loss is accepted");
        check(session.active()&&session.wanted()&&!session.grounded(),"airborne grace remains active but loses ground contact");
        check(session.signals().equals(SECOND)&&session.reason().equals("airborne"),"airborne frame retains last ground signals for partial fade");
        check(session.accept(state(first,4,OVERWORLD,true,true,"",FIRST),OVERWORLD),"landing accepts fresh grounded sample");
        check(session.grounded()&&session.reason().isEmpty()&&session.signals().equals(FIRST),"landing restores signals and clears airborne reason");

        check(session.accept(state(first,5,OVERWORLD,false,false,"airborne",List.of()),OVERWORLD),"server terminal state closes active session");
        check(!session.active()&&!session.wanted()&&!session.grounded(),"server close clears active/requested/contact state");
        check(session.signals().equals(FIRST)&&session.reason().equals("airborne"),"server close retains signals and reason for final fade");
        check(!session.accept(state(first,6,OVERWORLD,true,true,"",SECOND),OVERWORLD),"late active snapshot cannot resurrect a terminal session");
        for(int i=0;i<100;i++)check(!session.tickExpired(),"closed session does not report heartbeat timeout");

        long restarted=session.begin(true);
        check(restarted>first&&session.signals().isEmpty(),"restart uses a new token and clears stale fade signals");
        check(!session.accept(state(first,1000,OVERWORLD,true,true,"",FIRST),OVERWORLD),"old token cannot overwrite a new request even with high sequence");
        long cancelled=session.begin(false);
        check(cancelled>restarted&&!session.wanted()&&!session.active(),"local cancellation invalidates pending request");
        check(!session.accept(state(restarted,0,OVERWORLD,true,true,"",FIRST),OVERWORLD),"late start for cancelled request is ignored");
        check(!session.accept(state(cancelled,0,OVERWORLD,true,true,"",FIRST),OVERWORLD),"active state using cancellation token is also ignored");
        check(session.accept(state(cancelled,0,OVERWORLD,false,false,"manual",List.of()),OVERWORLD),"matching stop acknowledgement is accepted");

        long local=session.begin(true);session.accept(state(local,0,OVERWORLD,true,true,"",SECOND),OVERWORLD);
        session.begin(false);
        check(!session.active()&&!session.grounded()&&session.signals().equals(SECOND),"manual stop keeps previous signals available for shutdown fade");
        long preClear=session.token();session.clear();
        check(session.token()>preClear&&!session.wanted()&&!session.active()&&session.signals().isEmpty()&&session.reason().isEmpty(),"disconnect/world reset invalidates token and removes every retained visual");
        check(!session.accept(state(local,999,OVERWORLD,true,true,"",FIRST),NETHER),"old-world packet cannot restore cleared session in new world");

        var heartbeat=new EarthSenseSession();long heartbeatToken=heartbeat.begin(true);
        for(int i=1;i<=60;i++)check(!heartbeat.tickExpired(),"pending request remains within heartbeat allowance at tick "+i);
        check(heartbeat.tickExpired(),"pending request expires on first tick beyond lease");
        heartbeat.begin(false);check(!heartbeat.tickExpired(),"cancellation consumes expiry and prevents repeated expiry reports");
        heartbeatToken=heartbeat.begin(true);
        check(heartbeat.accept(state(heartbeatToken,0,OVERWORLD,true,true,"",FIRST),OVERWORLD),"active heartbeat session begins with server sample");
        for(int i=0;i<55;i++)heartbeat.tickExpired();
        check(heartbeat.accept(state(heartbeatToken,1,OVERWORLD,true,false,"airborne",List.of()),OVERWORLD),"valid ground-loss heartbeat renews lease");
        for(int i=1;i<=60;i++)check(!heartbeat.tickExpired(),"accepted heartbeat resets expiry countdown");
        check(!heartbeat.accept(state(heartbeatToken,1,OVERWORLD,true,true,"",SECOND),OVERWORLD),"duplicate heartbeat is rejected");
        check(!heartbeat.accept(state(heartbeatToken,99,NETHER,true,true,"",SECOND),OVERWORLD),"foreign dimension heartbeat is rejected");
        check(!heartbeat.accept(state(heartbeatToken+1,99,OVERWORLD,true,true,"",SECOND),OVERWORLD),"wrong-token heartbeat is rejected");
        check(heartbeat.tickExpired(),"invalid or replayed packets do not renew the lease");
        check(heartbeat.signals().equals(FIRST),"expired session still retains visual data until caller performs fade/reset");

        var mutable=new ArrayList<>(FIRST);var immutable=state(session.begin(true),0,OVERWORLD,true,true,"",mutable);mutable.clear();
        check(session.accept(immutable,OVERWORLD)&&session.signals().equals(FIRST),"wire snapshot is independent from mutable source collection");
        var field=EarthSenseSession.class.getDeclaredField("token");field.setAccessible(true);field.setLong(session,Long.MAX_VALUE);
        try{session.begin(true);throw new AssertionError("token overflow must not wrap into reusable token");}catch(ArithmeticException expected){checks++;}
        check(session.token()==Long.MAX_VALUE,"overflow retains previous token instead of reuse");
        System.out.println("PASS EarthSenseSessionTest: "+checks+" real-protocol session, replay, dimension, heartbeat and fade-data checks");
    }
    private static EarthSenseProtocol.State state(long token,int sequence,String dimension,boolean active,boolean grounded,String reason,List<EarthSenseProtocol.Signal> signals){
        var original=new EarthSenseProtocol.State(token,sequence,dimension,active,grounded,reason,signals);
        var buffer=new PacketByteBuf(Unpooled.buffer());
        try{EarthSenseProtocol.writeState(buffer,original);var decoded=EarthSenseProtocol.readState(buffer);check(decoded.equals(original),"real PacketByteBuf state roundtrip");return decoded;}finally{buffer.release();}
    }
    private static void expectUnsupported(Runnable action,String reason){try{action.run();throw new AssertionError(reason);}catch(UnsupportedOperationException expected){checks++;}}
    private static void check(boolean value,String reason){checks++;if(!value)throw new AssertionError(reason);}
}
