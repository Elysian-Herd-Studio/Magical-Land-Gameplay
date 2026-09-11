package top.csituka.magicaland.gameplay.client.levitation;

import java.util.Random;
import java.util.UUID;
import java.util.ArrayDeque;
import top.csituka.magicaland.gameplay.levitation.UnicornLevitationBudget;
import top.csituka.magicaland.gameplay.levitation.UnicornLevitationMath;
import top.csituka.magicaland.gameplay.levitation.UnicornLevitationMath.Mode;
import top.csituka.magicaland.gameplay.levitation.UnicornLevitationMath.Motion;
import top.csituka.magicaland.gameplay.levitation.UnicornLevitationProtocol.State;
import top.csituka.magicaland.gameplay.levitation.UnicornLevitationRules;

public final class UnicornLevitationSessionTest {
    private static int checks;
    private static final UUID PLAYER = UUID.fromString("00000000-0000-0000-0000-000000000007");
    private static final String WORLD = "minecraft:overworld";
    private static final UnicornLevitationInput UP = input(true, false, false, false, false, false, 0);
    public static void main(String[] args) {
        inputs(); lifecycle(); releaseAndAck(); airborneResume(); airborneRevocation(); landingFence();
        repeatedReleasePhysics(); passive(); randomized();
        System.out.println("UnicornLevitationSessionTest: " + checks + " checks PASS");
    }
    private static void inputs() {
        for (int bits = 0; bits < 64; bits++) for (boolean use : new boolean[]{false, true}) {
            var value = input((bits&1)!=0,(bits&2)!=0,(bits&4)!=0,(bits&8)!=0,(bits&16)!=0,(bits&32)!=0, 725);
            double factor = use ? .2 : 1;
            near(value.forwardAxis(use), ((value.forward()?1:0)-(value.backward()?1:0))*factor, "forward scale once");
            near(value.sideAxis(use), ((value.left()?1:0)-(value.right()?1:0))*factor, "side scale once");
            near(value.yaw(), 5, "wrapped yaw");
            yes(value.sameKeys(input(value.space(),value.sneak(),value.forward(),value.backward(),value.left(),value.right(),-60)), "key identity excludes view");
        }
        near(input(false,false,false,false,false,false,Float.NaN).yaw(),0,"nonfinite yaw");
        yes(!input(false,false,false,false,false,false,359).needsUpdate(UnicornLevitationInput.NONE),"359/0 is one degree not a full turn");
        yes(!input(false,false,false,false,false,false,0).needsUpdate(input(false,false,false,false,false,false,359)),"wrapped turn symmetric");
        yes(input(false,false,false,false,false,false,3).needsUpdate(UnicornLevitationInput.NONE),"meaningful turn updates before heartbeat");
        yes(!input(false,false,false,false,false,false,2).needsUpdate(UnicornLevitationInput.NONE),"small mouse motion bounded");
        yes(UP.needsUpdate(UnicornLevitationInput.NONE),"key edge always sent");
        for(int yaw=-720;yaw<=720;yaw++) {
            var first=input(false,false,false,false,false,false,yaw);
            yes(!first.needsUpdate(input(false,false,false,false,false,false,yaw+360)),"same heading across wrap");
            yes(first.needsUpdate(input(false,false,false,false,false,false,yaw+5)),"five-degree turn sent");
        }
    }
    private static void lifecycle() {
        var session = new UnicornLevitationSession();
        session.begin(false); var packet = session.packet(UnicornLevitationInput.NONE);
        yes(packet.enabled() && !packet.armed() && !packet.space(), "passive initial baseline");
        yes(!session.allowed() && session.mode(UP)==Mode.OFF,"nothing before confirmation");
        yes(!session.accept(state(packet.token(),1,1,true,false,Mode.OFF,100),WORLD),"unsent ack rejected");
        yes(!session.accept(state(packet.token(),1,0,true,false,Mode.OFF,100),"minecraft:the_nether"),"dimension mismatch");
        yes(session.accept(state(packet.token(),1,0,true,false,Mode.OFF,100),WORLD),"baseline accepted");
        yes(session.allowed(),"allowed but not armed");
        for (int i=0;i<20;i++) session.tick();
        yes(session.allowed(),"lease at boundary"); session.tick(); yes(!session.allowed(),"lease expired");
        session.disable(); var closed=session.packet(UP);
        yes(!closed.enabled()&&!closed.armed()&&!closed.space(),"disabled packet clears action keys");
        yes(!session.accept(state(packet.token(),2,closed.sequence(),true,true,Mode.ASCEND,100),WORLD),"late start cannot reopen");
        yes(session.accept(state(packet.token(),3,closed.sequence(),false,false,Mode.OFF,.5f),WORLD),"closed mana feedback accepted");
        yes(!session.allowed()&&!session.enabled(),"feedback does not grant permission");
        yes(session.accept(state(packet.token(),4,closed.sequence(),false,false,Mode.OFF,6),WORLD),"regeneration feedback");
        session.begin(false); var fresh=session.packet(UnicornLevitationInput.NONE);
        yes(fresh.token()>packet.token(),"new token required");
        yes(!session.accept(state(packet.token(),5,0,true,true,Mode.ASCEND,100),WORLD),"old token ignored");
        yes(session.accept(state(fresh.token(),6,0,true,false,Mode.OFF,6),WORLD),"fresh baseline after mana recovery");
        session.clear(); yes(!session.allowed()&&!session.armed(),"disconnect reset");
    }
    private static void releaseAndAck() {
        var session=new UnicornLevitationSession(); session.begin(true); var press=session.packet(UP);
        yes(session.mode(UP)==Mode.OFF,"held key not authority");
        yes(session.accept(state(press.token(),1,0,true,true,Mode.OFF,100),WORLD),"charging confirmation");
        yes(session.mode(UP)==Mode.OFF,"normal short jump remains off");
        session.accept(state(press.token(),2,0,true,true,Mode.ASCEND,99),WORLD);
        yes(session.mode(UP)==Mode.ASCEND,"acknowledged ascent");
        var boost=input(true,true,true,false,false,false,0);
        yes(session.mode(boost)==Mode.HOVER,"Shift instantly changes confirmed cast into hover");
        yes(session.mode(UP)==Mode.ASCEND,"release Shift resumes ascent without another handshake");
        session.accept(state(press.token(),3,0,true,true,Mode.HOVER,0),WORLD);
        yes(session.mode(UP)==Mode.ASCEND,"confirmed hover permits held Space ascent even with legacy zero mana");
        yes(session.mode(UnicornLevitationInput.NONE)==Mode.OFF,"release before any network roundtrip");
        var release=session.packet(UnicornLevitationInput.NONE);
        yes(session.accept(state(press.token(),4,0,true,true,Mode.ASCEND,99),WORLD),"old in-flight state may arrive");
        yes(session.mode(UnicornLevitationInput.NONE)==Mode.OFF,"late ascend does not undo release");
        var repress=session.packet(UP);
        yes(session.mode(UP)==Mode.OFF,"new press requires new acknowledgement");
        session.accept(state(press.token(),5,release.sequence(),true,true,Mode.ASCEND,99),WORLD);
        yes(session.mode(UP)==Mode.OFF,"release acknowledgement not enough for repress");
        session.accept(state(press.token(),6,repress.sequence(),true,true,Mode.ASCEND,99),WORLD);
        yes(session.mode(UP)==Mode.ASCEND,"new held press confirmed");
        yes(!session.accept(state(press.token(),6,repress.sequence(),true,true,Mode.ASCEND,99),WORLD),"duplicate state");
        yes(!session.accept(state(press.token(),7,release.sequence(),true,true,Mode.ASCEND,99),WORLD),"ack cannot move backwards");
        session.disarm(); yes(session.mode(UP)==Mode.OFF,"V disarms immediately");
        session.accept(state(press.token(),8,repress.sequence(),true,true,Mode.ASCEND,99),WORLD);
        yes(session.mode(UP)==Mode.OFF,"late state cannot undo V");
    }
    private static void passive() {
        var session=new UnicornLevitationSession(); session.begin(false); var packet=session.packet(UnicornLevitationInput.NONE);
        session.accept(state(packet.token(),1,0,true,false,Mode.OFF,100),WORLD);
        yes(session.predict(UP,false,Mode.OFF,-.8,.2,false)==Mode.OFF,"unarmed has no automatic braking");
        yes(session.predict(UP,true,Mode.OFF,0,.1,true)==Mode.OFF,"unarmed has no water hover");
        yes(session.predict(UnicornLevitationInput.NONE,false,Mode.OFF,-.5,1.5,true,0)==Mode.OFF,"unarmed has no early fluid buffer");
        session.begin(true); packet=session.packet(UnicornLevitationInput.NONE);
        session.accept(state(packet.token(),2,0,true,true,Mode.OFF,100),WORLD);
        yes(session.predict(UnicornLevitationInput.NONE,false,Mode.OFF,-.5,1,false,4)==Mode.LANDING,"armed release retains braking");
        yes(session.predict(UnicornLevitationInput.NONE,true,Mode.OFF,0,.1,true)==Mode.SURFACE,"armed water hover");
        yes(session.predict(UnicornLevitationInput.NONE,false,Mode.OFF,-.5,1.5,true,0)==Mode.SURFACE,"armed early fluid braking needs no held key or dangerous fall distance");
        session.disarm();
        yes(session.predict(UnicornLevitationInput.NONE,false,Mode.LANDING,-.5,.1,false,4)==Mode.OFF,"V immediately removes passive braking");
        yes(session.predict(UnicornLevitationInput.NONE,true,Mode.SURFACE,0,.1,true)==Mode.OFF,"V immediately removes liquid support");
        yes(session.predict(UnicornLevitationInput.NONE,false,Mode.SURFACE,-.5,1.5,true,0)==Mode.OFF,"V immediately removes early fluid braking");
        session.accept(state(packet.token(),3,0,true,true,Mode.SURFACE,100),WORLD);
        yes(session.predict(UnicornLevitationInput.NONE,true,Mode.SURFACE,0,.1,true)==Mode.OFF,"late armed state cannot restore passive");
        session.begin(true); packet=session.packet(UP);
        session.accept(state(packet.token(),4,0,true,true,Mode.RECOVER,0),WORLD);
        var keys=input(true,true,true,false,false,false,0);
        yes(session.predict(keys,false,Mode.OFF,-1,Double.NaN,false)==Mode.RECOVER,"Shift first still recovers falling cast");
        yes(session.predict(keys,false,Mode.RECOVER,.08,Double.NaN,false)==Mode.RECOVER,"recovery lifts before holding height");
        yes(session.predict(keys,false,Mode.RECOVER,.16,Double.NaN,false)==Mode.HOVER,"recovered cast eases into hover");
        session.disable();
        yes(session.predict(keys,false,Mode.RECOVER,-.5,.1,false,4)==Mode.OFF,"disabled has no recovery");
    }
    private static UnicornLevitationSession flyingSession() {
        var session=new UnicornLevitationSession(); session.begin(true); var press=session.packet(UP);
        session.accept(state(press.token(),1,press.sequence(),true,true,Mode.ASCEND,100),WORLD);
        yes(session.predict(UP,false,Mode.OFF,.16,Double.NaN,false)==Mode.ASCEND,"initial airborne cast is confirmed");
        return session;
    }
    private static void airborneResume() {
        var session=flyingSession();
        var release=session.packet(UnicornLevitationInput.NONE);
        yes(session.predict(UnicornLevitationInput.NONE,false,Mode.ASCEND,.16,Double.NaN,false)==Mode.OFF,"release stops lift immediately");
        var repress=session.packet(UP);
        yes(session.mode(UP)==Mode.OFF,"wire acknowledgement still fences strict mode");
        yes(session.predict(UP,false,Mode.OFF,-.08,Double.NaN,false)==Mode.ASCEND,"same airborne cast resumes before repress acknowledgement");
        session.accept(state(session.token(),2,release.sequence(),true,true,Mode.OFF,100),WORLD);
        yes(session.predict(UP,false,Mode.OFF,-.08,Double.NaN,false)==Mode.ASCEND,"late release confirmation cannot interrupt airborne repress");
        session.accept(state(session.token(),3,repress.sequence(),true,true,Mode.OFF,100),WORLD);
        var hover=input(true,true,false,false,false,false,0);
        yes(session.predict(hover,false,Mode.OFF,-.2,Double.NaN,false)==Mode.RECOVER,"airborne Space+Shift first brakes a fall");
        yes(session.predict(hover,false,Mode.RECOVER,.16,Double.NaN,false)==Mode.HOVER,"airborne recast retains hover transition");
        yes(session.predict(UnicornLevitationInput.NONE,false,Mode.HOVER,.16,Double.NaN,false)==Mode.OFF,"cached authority never forces held lift after release");
    }
    private static void airborneRevocation() {
        for(int reset=0;reset<7;reset++) {
            var session=flyingSession(); session.packet(UnicornLevitationInput.NONE); session.packet(UP);
            switch(reset) {
                case 0 -> session.disarm();
                case 1 -> session.disable();
                case 2 -> { for(int tick=0;tick<21;tick++)session.tick(); }
                case 3 -> session.accept(state(session.token(),2,2,false,false,Mode.OFF,100),WORLD);
                case 4 -> session.accept(state(session.token(),2,2,true,false,Mode.OFF,100),WORLD);
                case 5 -> session.begin(true);
                default -> session.clear();
            }
            yes(session.predict(UP,false,Mode.OFF,.16,Double.NaN,false)==Mode.OFF,"airborne authority revoked by lifecycle condition "+reset);
            if(reset==2) {
                session.accept(state(session.token(),2,2,true,true,Mode.OFF,100),WORLD);
                yes(session.predict(UP,false,Mode.OFF,.16,Double.NaN,false)==Mode.OFF,"lease refresh alone cannot restore expired airborne authority");
            }
        }
        var session=new UnicornLevitationSession();session.begin(true);var press=session.packet(UP);
        yes(session.predict(UP,false,Mode.ASCEND,.16,Double.NaN,false)==Mode.OFF,"previous mode cannot invent first-cast authority");
        session.accept(state(press.token(),1,press.sequence(),true,true,Mode.OFF,100),WORLD);
        yes(session.predict(UP,false,Mode.ASCEND,.16,Double.NaN,false)==Mode.OFF,"armed baseline is not confirmed airborne casting");
    }
    private static void landingFence() {
        var session=flyingSession(); var release=session.packet(UnicornLevitationInput.NONE);
        yes(session.predict(UnicornLevitationInput.NONE,true,Mode.OFF,0,0,false)==Mode.OFF,"landing ends continuous airborne casting");
        session.accept(state(session.token(),2,release.sequence(),true,true,Mode.ASCEND,100),WORLD);
        yes(session.predict(UP,false,Mode.OFF,.42,Double.NaN,false)==Mode.OFF,"late pre-landing ascent cannot authorize the next jump");
        var press=session.packet(UP);
        yes(session.predict(UP,false,Mode.OFF,.42,Double.NaN,false)==Mode.OFF,"new ground jump still needs its confirmation");
        var rules=new UnicornLevitationRules();rules.input(press.token(),press.sequence(),true,0);
        for(int tick=1;tick<=UnicornLevitationRules.CHARGE_TICKS;tick++) {
            boolean ready=rules.ready(true,true,tick==1);
            session.accept(state(session.token(),tick+2,press.sequence(),true,true,ready?Mode.ASCEND:Mode.OFF,100),WORLD);
            Mode mode=session.predict(UP,tick==1,Mode.OFF,.2,Double.NaN,false);
            yes(mode==(tick<UnicornLevitationRules.CHARGE_TICKS?Mode.OFF:Mode.ASCEND),"fresh grounded press retains seven-tick server charge "+tick);
        }
    }
    private static void repeatedReleasePhysics() {
        for(int delay=0;delay<=3;delay++) for(int releasedTicks=1;releasedTicks<=8;releasedTicks++)
                for(int heldTicks:new int[]{1,3,20}) {
            var session=flyingSession();
            var pending=new ArrayDeque<DelayedState>();
            var budget=new UnicornLevitationBudget(0,64,0,new Motion(0,.16,0));
            budget.advance(0,0,0,0,Mode.ASCEND,64,Double.NaN);
            Motion velocity=new Motion(0,.16,0);
            Mode previous=Mode.ASCEND,serverMode=Mode.ASCEND;
            int inputSequence=0;
            long stateSequence=1;
            double y=64;
            for(int tick=1;tick<=400;tick++) {
                session.tick();
                while(!pending.isEmpty()&&pending.peekFirst().tick()<=tick)
                    yes(session.accept(pending.removeFirst().state(),WORLD),"ordered delayed acknowledgement accepted");
                boolean held=(tick-1)%(releasedTicks+heldTicks)>=releasedTicks;
                var keys=held?UP:UnicornLevitationInput.NONE;
                if(keys.needsUpdate(session.input()))inputSequence=session.packet(keys).sequence();
                if(!held) {serverMode=Mode.OFF;budget=null;}
                Mode mode=session.predict(keys,false,previous,velocity.y(),Double.NaN,false);
                yes(mode==(held?Mode.ASCEND:Mode.OFF),"continuous airborne input has no extra ACK gravity tick delay "+delay+" release "+releasedTicks+" hold "+heldTicks+" tick "+tick);
                Motion movement=mode==Mode.OFF?velocity:UnicornLevitationMath.step(velocity,0,0,0,mode,y,Double.NaN);
                y+=movement.y();
                if(serverMode!=Mode.OFF)
                    yes(budget.accept(tick,0,y,0),"Session/physics/budget recast trajectory delay "+delay+" release "+releasedTicks+" hold "+heldTicks+" tick "+tick);
                velocity=mode==Mode.OFF?new Motion(0,(movement.y()-.08)*.98,0):movement;
                previous=mode;
                serverMode=held?Mode.ASCEND:Mode.OFF;
                if(serverMode!=Mode.OFF) {
                    if(budget==null)budget=new UnicornLevitationBudget(0,y,0,movement);
                    budget.advance(tick,0,0,0,serverMode,y,Double.NaN);
                }
                var confirmation=state(session.token(),++stateSequence,inputSequence,true,true,serverMode,100);
                if(delay==0)session.accept(confirmation,WORLD);
                else pending.addLast(new DelayedState(tick+delay,confirmation));
            }
        }
    }
    private record DelayedState(int tick,State state) {}
    private static void randomized() {
        var random=new Random(4185);
        for(int n=0;n<128;n++) {
            var session=new UnicornLevitationSession(); session.begin(random.nextBoolean());
            long serverSequence=0; int ack=0;
            for(int frame=0;frame<20;frame++) {
                boolean held=random.nextBoolean(); var keys=held?UP:UnicornLevitationInput.NONE;
                var packet=session.packet(keys); ack=packet.sequence();
                if(random.nextBoolean()) session.accept(state(packet.token(),++serverSequence,ack,true,session.armed(),Mode.ASCEND,100),WORLD);
                if(!held || !session.armed()) yes(session.mode(keys)==Mode.OFF,"random local cancellation cannot resurrect");
                if(random.nextInt(5)==0) session.disarm();
                if(!session.armed()) yes(session.mode(UP)==Mode.OFF,"random V cancellation");
            }
            session.disable();
            yes(!session.accept(state(session.token(),++serverSequence,ack,true,true,Mode.ASCEND,100),WORLD),"random disabled fence");
            for(int t=0;t<100;t++) session.tick();
            yes(session.predict(UP,false,Mode.LANDING,-.5,.1,false)==Mode.OFF,"random stale no motion");
        }
    }
    private static UnicornLevitationInput input(boolean space,boolean sneak,boolean f,boolean b,boolean l,boolean r,float yaw) {
        return new UnicornLevitationInput(space,sneak,f,b,l,r,yaw);
    }
    private static State state(long token,long sequence,int ack,boolean allowed,boolean armed,Mode mode,float mana) {
        return new State(PLAYER,token,sequence,ack,WORLD,allowed,armed,mode,mana,0,0,0,allowed?"":"mana");
    }
    private static void near(double actual,double expected,String name) { yes(Math.abs(actual-expected)<1e-6,name); }
    private static void yes(boolean value,String name) { checks++; if(!value) throw new AssertionError(name); }
}
