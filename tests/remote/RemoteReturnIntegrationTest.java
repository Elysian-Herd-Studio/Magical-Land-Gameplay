import java.nio.file.Files;
import java.nio.file.Path;

public final class RemoteReturnIntegrationTest {
    private static int checks;
    public static void main(String[] args) throws Exception {
        Path repo=Path.of(args[0]);
        String common="src/main/java/top/csituka/magicaland/gameplay/remote/";
        String server=Files.readString(repo.resolve(common+"RemoteToolServer.java"));
        String entity=Files.readString(repo.resolve(common+"RemoteToolEntity.java"));
        String client=Files.readString(repo.resolve("src/client/java/top/csituka/magicaland/gameplay/client/RemoteToolClient.java"));
        String hud=Files.readString(repo.resolve("src/client/java/top/csituka/magicaland/gameplay/client/RemoteToolHud.java"));
        String stop=section(server,"private static void stop(","private static boolean canReturn(");
        check(stop.contains("s.rules.close()") && stop.contains("ACTIVE.remove"),"control is closed once before return starts");
        check(stop.contains("if (s.cargo.isEmpty()) s.tool.discard()") && stop.indexOf("s.cargo.isEmpty()")<stop.indexOf("RETURNS.put"),"empty whole cargo skips physical return");
        check(!stop.contains("returnTo(") && !stop.contains("deliver("),"manual stop never hands cargo back before arrival");
        check(!stop.contains("RETURNS.remove"),"ordinary body actions cannot cancel an existing flight");
        check(server.contains("ACTIVE.size()+RETURNS.size()>=64") && server.contains("PENDING.containsKey(player.getUuid())"),"occupied cargo cannot start a second control session");
        check(server.contains("flight!=null && flight.session.tool==tool"),"return entity retains server ownership");
        String tick=section(server,"private static int returnTick(","private static void clearMining(");
        check(!tick.contains("interact(") && !tick.contains("RemoteActionContext.open") && !tick.contains("RemotePickup.collect"),"returning orb does not work or pick up more goods");
        check(tick.contains("now<flight.departAt") && server.contains("getTicks()+6"),"body view returns before cargo begins moving");
        check(tick.contains("RemoteReturnNavigator.Status.ARRIVED") && tick.contains("squaredDistanceTo(home)<=.7*.7")
                && tick.contains("RemoteFlightCollision.clear(tool,from,goal)"),"delivery needs both proximity and unblocked final segment");
        check(tick.contains("RETURNS.remove(p.getUuid(),flight)"),"late arrival cannot settle a different flight");
        check(tick.contains("return_blocked") && !tick.contains("teleport"),"blocked route waits instead of teleporting cargo");
        check(server.contains("navigationBudget=256") && server.contains("returnCursor"),"search budget is shared fairly between flights");
        String delivery=section(server,"private static void deliver(","private static int returnTick(");
        check(delivery.contains("PENDING.remove") && delivery.contains("finally") && delivery.contains("PENDING.put"),"settlement removes ownership before callbacks and retains failed remainder");
        check(delivery.indexOf("returnTo(")<delivery.indexOf("dropRemainder("),"inventory accepts what it can before feet drops");
        check(delivery.contains("player.getY()+.1") && delivery.contains("setPickupDelay(10)") && delivery.contains("spawnEntity(item)"),"overflow drops at current feet with a pickup delay");
        check(server.contains("ServerPlayConnectionEvents.JOIN") && server.contains("ServerPlayConnectionEvents.DISCONNECT")
                && server.contains("SERVER_STOPPING") && server.contains("cargo.takeAll()") && server.contains("hasVanishingCurse"),"recovery and ordinary death rules remain wired");
        check(entity.contains("TrackedData<Boolean> RETURNING") && entity.contains("returning()?inventory.displayStack()"),"returned display stack is independent of an empty selected slot");
        check(client.contains("!tool.returning()") && client.contains("RELEASED.contains") && client.contains("FACING.keySet().retainAll(controllingOwners)"),"body gaze and facing stop while return glow remains");
        check(!section(client,"private static void reset(","private static void clearVisuals(").contains("RemoteHeldAnimation.clear()"),"camera reset keeps return interpolation");
        check(hud.contains("else if (!RemoteToolClient.returning())"),"beginReturn clearing orbital coverage cannot flash the black world");
        System.out.println("PASS RemoteReturnIntegrationTest: "+checks+" source lifecycle boundaries (not an in-world test)");
    }
    private static String section(String text,String from,String to) { return text.substring(text.indexOf(from),text.indexOf(to,text.indexOf(from))); }
    private static void check(boolean value,String message) { checks++; if (!value) throw new AssertionError(message); }
}
