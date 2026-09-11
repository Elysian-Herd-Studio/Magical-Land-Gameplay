package top.csituka.magicaland.gameplay.client.levitation;

import net.minecraft.text.Text;
import top.csituka.magicaland.gameplay.client.race.RaceClient;

public final class UnicornLevitationClient {
    public static boolean ready=true,armed;
    public static int startRequests,stops;
    public static boolean available(){return RaceClient.canUseUnicornAbility()&&ready;}
    public static boolean armed(){return armed;}
    public static void toggle(){startRequests++;}
    public static void stop(){stops++;armed=false;}
    public static Text unavailable(){return Text.translatable("test.levitation.unavailable");}
    public static void reset(){ready=true;armed=false;startRequests=stops=0;}
}
