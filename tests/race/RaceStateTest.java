package top.csituka.magicaland.gameplay.race;

import java.nio.file.*;
import java.util.*;
import net.minecraft.nbt.*;

public final class RaceStateTest {
    private static int checks;
    private static final UUID OWNER=new UUID(0,1),OTHER=new UUID(0,2);
    private static final String FUTURE="addon:future_race";
    public static void main(String[] args)throws Exception {
        var blank=new RaceState();check(blank.race(OWNER).isEmpty()&&!blank.isDirty(),"blank state");blank.race(OWNER,RaceDefinitions.UNICORN_ID);check(blank.isDirty(),"assignment dirty");
        try{blank.race(OWNER,"bad");throw new AssertionError("invalid ID accepted");}catch(IllegalArgumentException expected){checks++;}check(blank.race(OWNER).equals(RaceDefinitions.UNICORN_ID),"invalid assignment retains old race");
        var original=new NbtCompound();original.putInt("Version",7);original.putString("FutureRoot","keep-root");
        var player=new NbtCompound();player.putUuid("Owner",OWNER);player.putString("Race",FUTURE);player.putString("UnknownField","keep-player");
        var progress=new NbtCompound();progress.putInt("Level",23);progress.putString("FutureAbility","custom-state");player.put("Progress",progress);
        var players=new NbtList();players.add(player);original.put("Players",players);
        var rules=new NbtCompound();rules.putString("Mode","POTION");rules.putBoolean("FreeAppearance",true);rules.putLong("Revision",91);rules.putString("FuturePolicy","keep-rules");
        var enabled=new NbtList();enabled.add(NbtString.of(RaceDefinitions.UNICORN_ID));enabled.add(NbtString.of(FUTURE));rules.put("Enabled",enabled);original.put("Rules",rules);
        var loaded=RaceState.read(original);original.putString("FutureRoot","mutated");player.putString("Race",RaceDefinitions.EARTH_PONY_ID);progress.putInt("Level",0);
        check(loaded.race(OWNER).equals(FUTURE)&&!loaded.isDirty(),"unknown race preserved without dirtying");check(loaded.rules().revision()==91&&loaded.rules().enabledRaces().contains(FUTURE)&&loaded.rules().freeAppearance(),"unknown enabled rules retained");
        check("locked".equals(RacePolicy.denial(FUTURE,RaceDefinitions.UNICORN_ID,loaded.rules(),RacePolicy.ChangeCause.POTION,true)),"unknown race protected from ordinary replacement");
        var written=loaded.writeNbt(new NbtCompound());check(written.getInt("Version")==7&&written.getString("FutureRoot").equals("keep-root"),"future root schema preserved, input isolated");check(written.getCompound("Rules").getString("FuturePolicy").equals("keep-rules"),"future policy metadata");
        var saved=find(written,OWNER);check(saved.getString("Race").equals(FUTURE)&&saved.getString("UnknownField").equals("keep-player"),"unknown player fields");check(saved.getCompound("Progress").getInt("Level")==23,"progress input isolated");saved.getCompound("Progress").putInt("Level",99);check(find(loaded.writeNbt(new NbtCompound()),OWNER).getCompound("Progress").getInt("Level")==23,"output isolated");
        loaded.race(OWNER,RaceDefinitions.PEGASUS_ID);var changed=find(loaded.writeNbt(new NbtCompound()),OWNER);check(changed.getString("UnknownField").equals("keep-player")&&changed.getCompound("Progress").getInt("Level")==23,"change preserves future fields and progress");
        loaded.race(OTHER,RaceDefinitions.EARTH_PONY_ID);check(find(loaded.writeNbt(new NbtCompound()),OTHER).contains("Progress",NbtElement.COMPOUND_TYPE),"progress reserved for new owner");
        var next=new RaceRules(RaceRules.ChangeMode.LOCKED,false,Set.of(RaceDefinitions.PEGASUS_ID),92);loaded.rules(next);check(loaded.isDirty()&&loaded.rules().equals(next),"rules dirty and revision");
        var beforeDisk=loaded.writeNbt(new NbtCompound());Path data=Files.createTempDirectory(Path.of(args[0]),"race-state-").resolve("races.nbt");NbtIo.write(beforeDisk,data.toFile());var restored=RaceState.read(NbtIo.read(data.toFile()));check(restored.race(OWNER).equals(RaceDefinitions.PEGASUS_ID)&&restored.race(OTHER).equals(RaceDefinitions.EARTH_PONY_ID),"real isolated NBT file roundtrip");check(restored.rules().equals(next),"rules file roundtrip");check(beforeDisk.equals(restored.writeNbt(new NbtCompound())),"repeated save/load stable with all unknown metadata");
        for(int badCase=0;badCase<5;badCase++) {
            var bad=written.copy();var r=bad.getCompound("Rules");
            if(badCase==0)r.putString("Mode","FUTURE_MODE");if(badCase==1)r.putLong("Revision",-1);if(badCase==2)r.put("Enabled",new NbtList());
            if(badCase==3){var ids=new NbtList();ids.add(NbtString.of("bad"));r.put("Enabled",ids);}if(badCase==4){var ids=new NbtList();for(int i=0;i<65;i++)ids.add(NbtString.of("addon:r"+i));r.put("Enabled",ids);}
            var fallback=RaceState.read(bad);check(fallback.rules().mode()==RaceRules.ChangeMode.LOCKED&&!fallback.rules().freeAppearance(),"invalid rules fail closed");check(fallback.race(OWNER).equals(FUTURE),"bad rules do not erase race");check(fallback.writeNbt(new NbtCompound()).getCompound("Rules").getString("FuturePolicy").equals("keep-rules"),"fallback retains metadata");
        }
        var corrupt=written.copy();find(corrupt,OWNER).putString("Race","INVALID malformed race");
        var protectedState=RaceState.read(corrupt);String exposed=protectedState.race(OWNER);
        check(exposed.isEmpty()||RaceDefinition.validId(exposed),"malformed stored race cannot poison STATE view for every online player");
        check("locked".equals(RacePolicy.denial(exposed,RaceDefinitions.UNICORN_ID,protectedState.rules(),RacePolicy.ChangeCause.SELECT,true)),"malformed saved race requires administrator recovery, never grants another first choice");
        check(find(protectedState.writeNbt(new NbtCompound()),OWNER).getString("Race").equals("INVALID malformed race"),"corrupt raw race preserved for recovery");
        new RaceProtocol.View(protectedState.rules(),exposed,false,exposed.isEmpty()?Map.of():Map.of(OWNER,exposed));
        checks++;
        System.out.println("PASS RaceStateTest: "+checks+" persistence, unknown IDs/fields, progress, deep-copy and fail-closed checks");
    }
    private static NbtCompound find(NbtCompound root,UUID id){var list=root.getList("Players",NbtElement.COMPOUND_TYPE);for(int i=0;i<list.size();i++)if(list.getCompound(i).getUuid("Owner").equals(id))return list.getCompound(i);throw new AssertionError("Missing player");}
    private static void check(boolean ok,String label){checks++;if(!ok)throw new AssertionError(label);}
}
