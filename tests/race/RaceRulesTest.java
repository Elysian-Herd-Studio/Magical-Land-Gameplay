package top.csituka.magicaland.gameplay.race;

import java.util.*;
import com.mojang.brigadier.StringReader;
import net.minecraft.command.argument.IdentifierArgumentType;

public final class RaceRulesTest {
    private static int checks;
    private static final List<String> IDS=List.of(RaceDefinitions.UNICORN_ID,RaceDefinitions.PEGASUS_ID,RaceDefinitions.EARTH_PONY_ID);
    private static final String FUTURE="addon:future_race";
    public static void main(String[] args) throws Exception {
        check(RaceDefinitions.all().size()==3,"three built-ins");
        for(int i=0;i<3;i++) {var r=RaceDefinitions.find(IDS.get(i));check(r!=null&&r.hasHorn()==(i==0)&&r.hasWings()==(i==1),"anatomy mapping");check(r.translationKey().equals("race."+r.id().replace(':','.')),"translation key");}
        for(String id:IDS) {
            var reader=new StringReader(id);
            check(IdentifierArgumentType.identifier().parse(reader).toString().equals(id),"real command parser preserves namespace");
            check(!reader.canRead(),"real command parser consumes complete race ID");
        }
        check(RaceDefinitions.find(FUTURE)==null,"unknown has no fallback");invalid(()->RaceDefinitions.all().clear(),"immutable registry snapshot");
        for(String bad:new String[]{"",":unicorn","unicorn","a:","UPPER:race","a:bad space","a:evil\n","a:"+"x".repeat(127)}) {check(!RaceDefinition.validId(bad),"bad ID");invalid(()->new RaceDefinition(bad,false,false),"constructor bad ID");}
        check(!RaceDefinition.validId(null),"null ID");check(RaceDefinition.validId("a:"+"x".repeat(126)),"128 boundary");check(RaceDefinition.validId("my.mod-1:folder/race_name"),"addon ID");
        var d=RaceRules.defaults();check(d.mode()==RaceRules.ChangeMode.POTION&&!d.freeAppearance()&&d.revision()==0&&d.enabledRaces().equals(Set.copyOf(IDS)),"defaults");
        Set<String> source=new HashSet<>(IDS);var isolated=new RaceRules(RaceRules.ChangeMode.FREE,true,source,7);source.clear();check(isolated.enabledRaces().size()==3,"input deep copy");invalid(()->isolated.enabledRaces().clear(),"immutable enabled");check(isolated.nextRevision().revision()==8&&isolated.revision()==7,"revision immutable increment");
        invalid(()->new RaceRules(RaceRules.ChangeMode.FREE,false,Set.of(),0),"empty enabled");invalid(()->new RaceRules(null,false,Set.copyOf(IDS),0),"null mode");invalid(()->new RaceRules(RaceRules.ChangeMode.FREE,false,Set.copyOf(IDS),-1),"negative revision");invalid(()->new RaceRules(RaceRules.ChangeMode.FREE,false,Set.of("bad"),0),"bad rules ID");
        var excess=new HashSet<String>();for(int i=0;i<65;i++)excess.add("test:r"+i);invalid(()->new RaceRules(RaceRules.ChangeMode.FREE,false,excess,0),"65 rules");
        var max=new RaceRules(RaceRules.ChangeMode.FREE,false,Set.copyOf(IDS),Long.MAX_VALUE);invalid(max::nextRevision,"overflow");
        for(int mask=1;mask<8;mask++)for(var mode:RaceRules.ChangeMode.values())for(boolean free:new boolean[]{false,true}) {
            var enabled=new HashSet<String>();for(int i=0;i<3;i++)if((mask&(1<<i))!=0)enabled.add(IDS.get(i));var rules=new RaceRules(mode,free,enabled,19);
            for(String current:new String[]{null,"",IDS.get(0),IDS.get(1),IDS.get(2),FUTURE})for(String target:new String[]{IDS.get(0),IDS.get(1),IDS.get(2),FUTURE,"bad"})for(var cause:RacePolicy.ChangeCause.values())for(boolean safe:new boolean[]{false,true})check(Objects.equals(expected(current,target,rules,cause,safe),RacePolicy.denial(current,target,rules,cause,safe)),"policy matrix "+mode+"/"+current+"/"+target+"/"+cause+"/"+safe);
            check("permission".equals(RacePolicy.rulesDenial(false,rules,rules)),"nonadmin");check(RacePolicy.rulesDenial(true,rules,rules)==null,"admin current revision");for(long revision:new long[]{0,18,20,Long.MAX_VALUE})check("stale".equals(RacePolicy.rulesDenial(true,rules,new RaceRules(mode,free,enabled,revision))),"revision conflict");
        }
        var unknown=new RaceRules(RaceRules.ChangeMode.LOCKED,false,Set.of(FUTURE),9);check("invalid_rules".equals(RacePolicy.rulesDenial(true,unknown,unknown)),"unregistered enabled rejected");check("stale".equals(RacePolicy.rulesDenial(true,max,max)),"admin overflow guard");
        var before=RaceDefinitions.all();RaceDefinitions.register(new RaceDefinition(FUTURE,true,true));check(before.size()==3&&RaceDefinitions.all().size()==4,"snapshot stable");check(RacePolicy.rulesDenial(true,unknown,unknown)==null,"restored addon accepted");invalid(()->RaceDefinitions.register(new RaceDefinition(FUTURE,false,false)),"duplicate cannot override");invalid(()->RaceDefinitions.register(null),"null register");for(int i=4;i<64;i++)RaceDefinitions.register(new RaceDefinition("test:r"+i,false,false));check(RaceDefinitions.all().size()==64,"capacity boundary");invalid(()->RaceDefinitions.register(new RaceDefinition("test:overflow",false,false)),"capacity exceeded");
        System.out.println("PASS RaceRulesTest: "+checks+" definitions, modes, first selection, enabled races, admin/revision checks");
    }
    private static String expected(String current,String target,RaceRules rules,RacePolicy.ChangeCause cause,boolean safe) {
        if(!IDS.contains(target))return "unknown";if(target.equals(current))return "same";
        if(cause!=RacePolicy.ChangeCause.ADMIN){if(!rules.enabledRaces().contains(target))return "disabled";if(current!=null&&!current.isEmpty()){if(!IDS.contains(current)||rules.mode()==RaceRules.ChangeMode.LOCKED)return "locked";if(cause==RacePolicy.ChangeCause.SELECT&&rules.mode()==RaceRules.ChangeMode.POTION)return "potion_required";}}
        return safe?null:"stance";
    }
    private static void invalid(Runnable run,String label){try{run.run();throw new AssertionError(label);}catch(RuntimeException expected){checks++;}}
    private static void check(boolean ok,String label){checks++;if(!ok)throw new AssertionError(label);}
}
