package top.csituka.magicaland.gameplay.client.levitation;

import java.util.Random;
import java.util.UUID;
import top.csituka.magicaland.gameplay.levitation.UnicornLevitationMath;
import top.csituka.magicaland.gameplay.levitation.UnicornLevitationMath.Mode;
import top.csituka.magicaland.gameplay.levitation.UnicornLevitationProtocol.State;

public final class UnicornLevitationSessionTest {
    private static int checks;
    private static final UUID PLAYER = UUID.fromString("00000000-0000-0000-0000-000000000007");
    private static final String WORLD = "minecraft:overworld";
    private static final UnicornLevitationInput UP = input(true, false, false, false, false, false, 0);
    public static void main(String[] args) {
        inputs(); lifecycle(); releaseAndAck(); passive(); randomized();
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
