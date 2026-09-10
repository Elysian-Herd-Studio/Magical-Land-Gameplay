import top.csituka.magicaland.gameplay.client.RemoteSessionState;
import top.csituka.magicaland.gameplay.client.RemoteVisualMath;
import static top.csituka.magicaland.gameplay.client.RemoteSessionState.Update.*;

public final class RemotePresentationTest {
    private static int checks;
    private static void check(boolean value,String reason) {
        checks++;
        if (!value) throw new AssertionError(reason);
    }
    public static void main(String[] args) {
        var session=new RemoteSessionState();
        long first=session.begin();
        check(session.accept(first+1,20,5,0)==IGNORE,"another request cannot take the camera");
        check(session.accept(first,0,5,0)==IGNORE,"active state needs a server session");
        session.returning();
        check(session.accept(first,20,5,0)==CANCEL_LATE_START,"cancelled starts are explicitly stopped");
        session.clear();
        long second=session.begin();
        check(second>first,"request tokens survive cleanup");
        check(session.accept(first,20,5,0)==IGNORE,"late old start cannot replace a newer request");
        check(session.accept(second,21,6,0)==ACCEPT,"new session accepted");
        session.connected();
        check(session.phase()==RemoteSessionState.Phase.CONTROLLING,"only entity acquisition starts control");
        check(session.accept(second,21,7,5)==IGNORE,"entity cannot change within a session");
        check(session.accept(second,21,6,1)==ACCEPT,"invalid entity packet does not poison ordering");
        check(session.accept(second,21,6,1)==IGNORE,"duplicate snapshot ignored");
        check(session.accept(second,21,6,0)==IGNORE,"old snapshot ignored");
        check(session.accept(second,20,-1,9)==IGNORE,"old session cannot terminate this camera");
        check(session.nextInput()==1 && session.nextInput()==2,"input sequence increases");
        check(session.accept(second,21,-1,2)==ENDED,"matching server close enters return");
        check(session.accept(second,21,6,3)==IGNORE,"late active packet cannot revive a closed session");
        session.clear();
        check(!session.active(),"return cleanup releases control");
        long third=session.begin();
        check(session.accept(third,0,-1,0)==ENDED,"pending request can be rejected without a session");
        for (int capacity=1; capacity<=9; capacity++) {
            check(RemoteVisualMath.hotbarWidth(capacity)==capacity*20+2,"vanilla slot spacing");
            check(RemoteVisualMath.slot(0,-1,capacity)==capacity-1,"wheel wraps backwards");
            check(RemoteVisualMath.slot(capacity-1,1,capacity)==0,"wheel wraps forwards");
        }
        for (float x : new float[] {-1,0,1}) for (float y : new float[] {-1,0,1}) {
            check(RemoteVisualMath.vignette(x,y,0)==0,"clear connection has no black mask");
            check(RemoteVisualMath.vignette(x,y,1)==1,"full occlusion covers the entire scene");
        }
        check(RemoteVisualMath.vignette(0,0,.5f)<RemoteVisualMath.vignette(1,0,.5f),"occlusion closes from edges");
        check(RemoteVisualMath.vignette(0,0,.999f)>.999f,"the centre reaches black continuously before the full-black threshold");
        float previous=0;
        for (int step=0; step<=100; step++) {
            float centre=RemoteVisualMath.vignette(0,0,step/100f);
            check(centre>=previous,"increasing occlusion cannot expose the centre");
            check(RemoteVisualMath.vignette(1,1,step/100f)>=centre,"corners never become clearer than the centre");
            previous=centre;
        }
        float opacity=0;
        for (int frame=0; frame<30; frame++) opacity=RemoteVisualMath.approachOcclusion(opacity,1,1.0/60);
        check(opacity==1,"full black is reached, not only approached asymptotically");
        for (int fps : new int[] {15,20,24,30,60,90,120,144,240}) {
            float blackout=0;
            double elapsed=0;
            while (blackout<1 && elapsed<1) {
                blackout=RemoteVisualMath.approachOcclusion(blackout,1,1.0/fps);
                elapsed+=1.0/fps;
            }
            check(blackout==1 && elapsed<=.08,"full black precedes recall across frame rates: "+fps);
        }
        check(RemoteVisualMath.approachOcclusion(0,1,.2)==1,"a delayed frame completes blackout without further waiting");
        float partial=RemoteVisualMath.approachOcclusion(0,.5f,1.0/60);
        check(partial>0 && partial<.5f,"partial coverage remains smoothly filtered");
        check(RemoteVisualMath.approachOcclusion(0,1,.079)==1,"full black has a finite sub-80ms limit");
        for (int frame=0; frame<30; frame++) opacity=RemoteVisualMath.approachOcclusion(opacity,0,1.0/60);
        check(opacity==0,"backtracking restores an entirely clear scene");
        check(RemoteVisualMath.cycle(15,6,true)==.5f,"mining repeats its swing");
        check(RemoteVisualMath.cycle(15,6,false)==0,"a completed action does not repeat");
        check(RemoteVisualMath.cycle(-1,6,false)==0,"future action timestamp does not create a swing");
        System.out.println("PASS RemotePresentationTest: "+checks+" checks");
    }
}
