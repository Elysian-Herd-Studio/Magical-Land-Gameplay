package top.csituka.magicaland.gameplay.client.race;
import net.minecraft.text.Text;
public final class RaceClient {
    public record View(String ownRace){}
    public static boolean ready;
    public static String ownRace="";
    public static boolean ready(){return ready;}
    public static boolean hasRace(){return ready&&!ownRace.isEmpty();}
    public static View view(){return new View(ownRace);}
    public static Text unavailable(){return Text.translatable("test.race.unavailable");}
    public static Text text(String suffix,Object... args){return Text.translatable("text.magicaland_gameplay.race."+suffix,args);}
    public static boolean canUseUnicornAbility(){return ready&&ownRace.equals("magicaland_gameplay:unicorn");}
    public static Text abilityUnavailable(){return Text.translatable("test.unicorn.unavailable");}
}
