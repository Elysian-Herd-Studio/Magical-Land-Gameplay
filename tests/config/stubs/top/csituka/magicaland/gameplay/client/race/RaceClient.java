package top.csituka.magicaland.gameplay.client.race;
import net.minecraft.text.Text;
import top.csituka.magicaland.gameplay.race.RaceDefinitions;
import top.csituka.magicaland.gameplay.race.RaceRules;
public final class RaceClient {
    public record View(RaceRules rules,String ownRace,boolean canManage){}
    public record Result(long requestId,String denial){}
    public static final java.util.Map<Long,Result> results=new java.util.HashMap<>();
    public static boolean connected;
    public static View state;
    public static long version,sentRevision,requestSequence;
    public static String chosen;
    public static RaceRules saved;
    public static boolean connected(){return connected;}
    public static boolean ready(){return connected&&state!=null;}
    public static boolean hasRace(){return ready()&&!state.ownRace().isEmpty();}
    public static boolean canManage(){return ready()&&state.canManage();}
    public static View view(){return state;}
    public static void openedSelection(){}
    public static long version(){return version;}
    public static long lastRequestId(){return requestSequence;}
    public static Result result(long requestId){return results.get(requestId);}
    public static void reply(long requestId,String denial){results.put(requestId,new Result(requestId,denial));}
    public static Text text(String suffix,Object... args){return Text.translatable("text.magicaland_gameplay.race."+suffix,args);}
    public static Text name(String id){return id==null||id.isEmpty()?text("unselected"):Text.translatable("race."+id.replace(':','.'));}
    public static Text unavailable(){return text(connected?"loading":"no_server");}
    public static void request(){}
    public static boolean canChoose(String id){return ready()&&state.rules().enabledRaces().contains(id)&&RaceDefinitions.find(id)!=null
        &&(!hasRace()||state.rules().mode()==RaceRules.ChangeMode.FREE)&&!id.equals(state.ownRace());}
    public static boolean choose(String id,long revision){if(!canChoose(id))return false;chosen=id;sentRevision=revision;requestSequence++;return true;}
    public static boolean saveRules(RaceRules rules){if(!canManage())return false;saved=rules;requestSequence++;return true;}
}
