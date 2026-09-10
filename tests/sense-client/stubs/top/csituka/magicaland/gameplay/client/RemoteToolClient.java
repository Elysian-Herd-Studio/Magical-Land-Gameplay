package top.csituka.magicaland.gameplay.client;
public final class RemoteToolClient {
    public static boolean active;
    public static int stops,startRequests;
    public static boolean active(){return active;}
    public static void stop(){stops++;active=false;}
    public static void startAbility(){startRequests++;}
    public static void reset(){active=false;stops=startRequests=0;}
}
