package top.csituka.magicaland.gameplay.client.sense;
import net.minecraft.text.Text;
import top.csituka.magicaland.gameplay.client.race.RaceClient;
public final class EarthSenseClient {
    public static boolean active,pending;
    public static int stops,startRequests;
    public static boolean available(){return RaceClient.ready&&RaceClient.ownRace.equals("magicaland_gameplay:earth_pony");}
    public static boolean active(){return active;}
    public static boolean pending(){return pending;}
    public static void stop(){stops++;active=pending=false;}
    public static void toggle(){startRequests++;}
    public static Text unavailable(){return Text.translatable("test.sense.unavailable");}
    public static void reset(){active=pending=false;stops=startRequests=0;}
}
