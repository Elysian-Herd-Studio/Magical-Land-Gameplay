package top.csituka.magicaland.gameplay.client;

import net.minecraft.client.MinecraftClient;
import net.minecraft.text.Text;
import top.csituka.magicaland.gameplay.client.race.RaceClient;
import top.csituka.magicaland.gameplay.client.sense.EarthSenseClient;

public final class AbilityClientTest {
    private static int checks;
    private static final Text WHEEL=Text.translatable("test.wheel.key");
    public static void main(String[] args) {
        var client=new MinecraftClient();reset();
        check(AbilityClient.count()==2,"stable registry retains remote presence ID 0 and tremor sense ID 1");
        for(String race:new String[]{"","magicaland_gameplay:unicorn","magicaland_gameplay:pegasus","magicaland_gameplay:earth_pony","addon:unknown","addon:unicorn","addon:earth_pony"})for(boolean ready:new boolean[]{false,true}) {
            RaceClient.ready=ready;RaceClient.ownRace=race;
            boolean unicorn=ready&&race.equals("magicaland_gameplay:unicorn"),earth=ready&&race.equals("magicaland_gameplay:earth_pony");
            check(AbilityClient.available(0)==unicorn,"stable remote ID follows confirmed race availability");
            check(AbilityClient.available(1)==earth,"stable sense ID follows confirmed earth pony availability");
            check(!AbilityClient.available(-1)&&!AbilityClient.available(2)&&!AbilityClient.available(Integer.MAX_VALUE),"out-of-range wheel slots are never available");
            check(AbilityClient.wheelCount()==(unicorn||earth?1:0),"wheel exposes only abilities owned by the confirmed current race");
            check(AbilityClient.wheelAbility(0)==(unicorn?0:earth?1:-1),"visible slot zero maps to correct stable ability ID");
            for(int slot:new int[]{-1,1,2,5,Integer.MAX_VALUE})check(AbilityClient.wheelAbility(slot)==-1,"missing visible slot never aliases another ability");
        }
        RaceClient.ready=false;RaceClient.ownRace="magicaland_gameplay:unicorn";String unsynchronized=AbilityClient.wheelEmptyMessage().key();
        RaceClient.ready=true;RaceClient.ownRace="";String unselected=AbilityClient.wheelEmptyMessage().key();
        RaceClient.ownRace="magicaland_gameplay:pegasus";String noAbility=AbilityClient.wheelEmptyMessage().key();
        check(!unsynchronized.isEmpty()&&!unselected.isEmpty()&&!noAbility.isEmpty(),"empty wheel states provide translated explanatory text");
        check(!unsynchronized.equals(unselected)&&!unsynchronized.equals(noAbility)&&!unselected.equals(noAbility),"unsynchronized, unselected and no-ability states have distinct messages");
        RaceClient.ownRace="addon:unknown";check(AbilityClient.wheelEmptyMessage().key().equals(noAbility),"unknown saved race exposes no owned ability");
        for(String race:new String[]{"","magicaland_gameplay:pegasus","addon:unknown"}) {
            reset();RaceClient.ready=true;RaceClient.ownRace=race;AbilityClient.activate(client,WHEEL);
            check(noStarts()&&lastMessage(client).key().equals(AbilityClient.wheelEmptyMessage().key()),"activation with empty wheel explains current race state without offering hidden abilities");
        }
        reset();AbilityClient.activate(client,WHEEL);
        check(noStarts()&&lastMessage(client).key().equals(unsynchronized),"activation before synchronization displays unavailable state");
        check(AbilityClient.name(0).key().equals("text.magicaland_gameplay.remote.name")&&AbilityClient.name(1).key().equals("text.magicaland_gameplay.sense.name"),"ability names remain translated per slot");
        check(AbilityClient.unavailable(0).key().equals("test.unicorn.unavailable")&&AbilityClient.unavailable(1).key().equals("test.sense.unavailable"),"ability denial explanations route to owning client");

        reset();race("unicorn");AbilityClient.activate(client,WHEEL);
        check(noStarts()&&lastMessage(client).key().equals("text.magicaland_gameplay.ability.select"),"activation without selection requests explicit wheel selection");
        check(lastMessage(client).arguments()[0]==WHEEL&&client.player.messages.get(client.player.messages.size()-1).actionBar(),"selection prompt preserves key hint in action bar");
        AbilityClient.select(0);check(noStarts()&&!RemoteToolClient.active(),"selection never sends start or claims server authorization");
        AbilityClient.activate(client,WHEEL);check(RemoteToolClient.startRequests==1&&EarthSenseClient.startRequests==0,"selected unicorn ability dispatches remote request");
        check(!RemoteToolClient.active(),"remote request leaves activation to server acknowledgement");
        AbilityClient.select(1);AbilityClient.select(5);AbilityClient.activate(client,WHEEL);
        check(RemoteToolClient.startRequests==2&&EarthSenseClient.startRequests==0,"unavailable selection does not replace existing valid ability");

        race("earth_pony");AbilityClient.activate(client,WHEEL);
        check(RemoteToolClient.startRequests==2&&EarthSenseClient.startRequests==0,"race change invalidates selected remote ability before activation");
        race("unicorn");AbilityClient.activate(client,WHEEL);
        check(RemoteToolClient.startRequests==2,"invalidated selection does not reappear when switching back");
        race("earth_pony");AbilityClient.select(AbilityClient.wheelAbility(0));check(EarthSenseClient.startRequests==0&&!EarthSenseClient.active(),"earth pony visible slot zero selects sense but waits for activation");
        AbilityClient.activate(client,WHEEL);
        check(EarthSenseClient.startRequests==1&&!EarthSenseClient.active()&&!EarthSenseClient.pending(),"sense dispatch does not claim activation or write pending state itself");

        for(String first:new String[]{"unicorn","earth_pony"}) {
            reset();race(first);AbilityClient.select(AbilityClient.wheelAbility(0));long before=AbilityClient.revision();
            race(first.equals("unicorn")?"earth_pony":"unicorn");
            check(AbilityClient.revision()==before+1,"own race change invalidates selection revision immediately");
            race(first);check(AbilityClient.revision()==before+2,"returning to prior race is a second invalidation");
            AbilityClient.activate(client,WHEEL);
            check(noStarts()&&lastMessage(client).key().equals("text.magicaland_gameplay.ability.select"),"rapid A to B to A without activation never resurrects old selection");
            AbilityClient.select(AbilityClient.wheelAbility(0));AbilityClient.activate(client,WHEEL);
            check(first.equals("unicorn")?RemoteToolClient.startRequests==1:EarthSenseClient.startRequests==1,"new wheel selection restores only current race ability");
        }
        reset();race("earth_pony");AbilityClient.select(AbilityClient.wheelAbility(0));long sameRaceRevision=AbilityClient.revision();
        AbilityClient.raceChanged(RaceClient.ownRace,RaceClient.ownRace);
        check(AbilityClient.revision()==sameRaceRevision,"same-race rules or player-map sync preserves selection revision");
        AbilityClient.activate(client,WHEEL);check(EarthSenseClient.startRequests==1,"same-race sync preserves previously selected sense ability");
        AbilityClient.raceChanged(new String(RaceClient.ownRace),new String(RaceClient.ownRace));
        check(AbilityClient.revision()==sameRaceRevision,"same-race comparison uses identity value rather than String object reference");
        long beforeReset=AbilityClient.revision();AbilityClient.reset();
        check(AbilityClient.revision()==beforeReset+1,"explicit connection cleanup always advances selection revision");
        AbilityClient.reset();check(AbilityClient.revision()==beforeReset+2,"repeated cleanup still invalidates any cached hover");
        RaceClient.ready=false;RaceClient.ownRace="";
        check(AbilityClient.wheelCount()==0&&AbilityClient.wheelAbility(0)==-1,"disconnect clears visible ability mapping");
        race("earth_pony");AbilityClient.activate(client,WHEEL);
        check(EarthSenseClient.startRequests==1,"rejoining same race does not restore previous session selection");

        RemoteToolClient.active=true;EarthSenseClient.active=true;EarthSenseClient.pending=true;
        int remoteStarts=RemoteToolClient.startRequests,senseStarts=EarthSenseClient.startRequests;
        RaceClient.ready=false;RaceClient.ownRace="";AbilityClient.activate(client,WHEEL);
        check(RemoteToolClient.stops==1&&EarthSenseClient.stops==0,"active remote stop has priority even after permission loss");
        check(RemoteToolClient.startRequests==remoteStarts&&EarthSenseClient.startRequests==senseStarts,"stop never starts the newly selected ability in same press");
        AbilityClient.activate(client,WHEEL);
        check(EarthSenseClient.stops==1&&!EarthSenseClient.active()&&!EarthSenseClient.pending(),"active or pending sense is stopped before authorization checks");
        EarthSenseClient.pending=true;AbilityClient.activate(client,WHEEL);
        check(EarthSenseClient.stops==2&&!EarthSenseClient.pending(),"pending sense can always be cancelled");

        reset();race("earth_pony");AbilityClient.select(1);EarthSenseClient.active=true;
        race("unicorn");AbilityClient.select(0);AbilityClient.activate(client,WHEEL);
        check(EarthSenseClient.stops==1&&noStarts(),"switching selection while sensing ends sense before starting remote");
        AbilityClient.activate(client,WHEEL);check(RemoteToolClient.startRequests==1,"next press starts newly selected remote ability");
        RemoteToolClient.active=true;race("earth_pony");AbilityClient.select(1);AbilityClient.activate(client,WHEEL);
        check(RemoteToolClient.stops==1&&EarthSenseClient.startRequests==0,"switching from remote to sense first stops remote");
        AbilityClient.activate(client,WHEEL);check(EarthSenseClient.startRequests==1,"next press starts selected sense ability");
        AbilityClient.reset();int before=EarthSenseClient.startRequests;AbilityClient.activate(client,WHEEL);
        check(EarthSenseClient.startRequests==before&&lastMessage(client).key().equals("text.magicaland_gameplay.ability.select"),"connection reset clears prior selected ability");
        System.out.println("PASS AbilityClientTest: "+checks+" visible-slot mapping, race transitions, revision, stop-priority and authority-boundary checks (stub clients)");
    }
    private static void reset(){AbilityClient.reset();RemoteToolClient.reset();EarthSenseClient.reset();RaceClient.ready=false;RaceClient.ownRace="";}
    private static void race(String value){String previous=RaceClient.ownRace;RaceClient.ready=true;RaceClient.ownRace="magicaland_gameplay:"+value;AbilityClient.raceChanged(previous,RaceClient.ownRace);}
    private static Text lastMessage(MinecraftClient client){return client.player.messages.get(client.player.messages.size()-1).text();}
    private static boolean noStarts(){return RemoteToolClient.startRequests==0&&EarthSenseClient.startRequests==0;}
    private static void check(boolean value,String message){checks++;if(!value)throw new AssertionError(message);}
}
