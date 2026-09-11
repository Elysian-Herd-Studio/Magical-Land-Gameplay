package top.csituka.magicaland.gameplay.client;

import java.nio.file.Files;
import java.nio.file.Path;

public final class RemoteInputGateTest {
    private static int checks;
    private static final int[] ACTIONS = {RemoteInputGate.ATTACK, RemoteInputGate.USE, RemoteInputGate.DROP};

    public static void main(String[] args) throws Exception {
        for (int action : ACTIONS) releaseGuard(action);
        independentButtons(); longPause(); lifecycle(); boundaries(Path.of(args[0]));
        System.out.println("PASS RemoteInputGateTest: " + checks + " input-state/source-boundary checks");
    }

    private static void releaseGuard(int action) {
        var gate = new RemoteInputGate();
        gate.tick(true, 0); gate.tick(true, 0);
        check(gate.accepts(action) && gate.filter(action) == action, "fresh action accepted");
        gate.suspend();
        check(!gate.accepting() && gate.filter(action) == 0, "screen/focus callback suppresses input immediately");
        for (int i = 0; i < 100; i++) gate.tick(false, action);
        gate.tick(true, action);
        check(!gate.accepting() && gate.filter(action) == 0, "first resume tick drains stale clicks");
        for (int i = 0; i < 100; i++) {
            gate.tick(true, action);
            check(gate.accepting() && !gate.accepts(action) && gate.filter(action) == 0,
                    "held attack/use/drop stays locked across resume");
        }
        gate.tick(true, 0);
        check(gate.accepts(action), "release unlocks only a fresh press");
        gate.tick(true, action);
        check(gate.filter(action) == action, "next deliberate action works normally");
    }

    private static void independentButtons() {
        var gate = new RemoteInputGate();
        int all = ACTIONS[0] | ACTIONS[1] | ACTIONS[2];
        gate.tick(true, all); gate.tick(true, all);
        for (int action : ACTIONS) check(!gate.accepts(action), "start-held buttons must first release");
        for (int action : ACTIONS) {
            all &= ~action;
            gate.tick(true, all);
            check(gate.accepts(action), "each released button is unlocked independently");
            check(gate.filter(all) == 0, "other buttons cannot leak through release");
        }
    }

    private static void longPause() {
        var gate = new RemoteInputGate();
        for (int i = 0; i < 10000; i++) {
            gate.tick(false, (i % 8) << 6);
            check(!gate.accepting() && gate.filter(511) == 0, "arbitrarily long menu/focus suspension stays neutral");
        }
        gate.tick(true, 0);
        check(!gate.accepting(), "all paused click queues are discarded on resume");
        gate.tick(true, 0);
        check(gate.accepting() && gate.filter(255) == 255, "normal movement and actions restored after clean resume");
    }

    private static void lifecycle() {
        var session = new RemoteSessionState();
        var gate = new RemoteInputGate();
        long request = session.begin();
        session.accept(request, 9, 3, 0); session.connected();
        for (int i = 0; i < 200; i++) {
            gate.tick(false, 0);
            check(session.nextInput() == i + 1 && session.phase() == RemoteSessionState.Phase.CONTROLLING,
                    "zero-input heartbeat sequences can continue without returning the camera");
        }
        session.returning(); gate.suspend();
        check(!gate.accepting() && session.phase() == RemoteSessionState.Phase.RETURNING,
                "explicit return still stops actions");
        session.clear(); gate.suspend();
        check(!session.active() && !gate.accepting(), "disconnect/reset remains terminal");
    }

    private static void boundaries(Path repo) throws Exception {
        String base = "src/client/java/top/csituka/magicaland/gameplay/";
        String client = Files.readString(repo.resolve(base + "client/RemoteToolClient.java"));
        String mixin = Files.readString(repo.resolve(base + "mixin/client/RemoteInteractionMixin.java"));
        String scroll = Files.readString(repo.resolve(base + "mixin/client/RemoteScrollMixin.java"));
        String tick = section(client, "private static void tick(", "private static void sendInput(");
        String pause = section(client, "public static void suspendInput(", "public static void consumeBodyActions(");
        String consume = section(client, "public static void consumeBodyActions(", "private static void dropSelected(");
        check(tick.contains("if (!client.player.isAlive())") && tick.contains("camera.getWorld()!=client.world"),
                "death and world change retain explicit cancellation");
        check(!tick.contains("|| client.currentScreen != null || !client.isWindowFocused()"),
                "menus and focus loss are not cancellation reasons");
        check(tick.contains("int keys=0;") && tick.contains("if (acceptsInput()) keys=") && tick.contains("sendInput(keys);"),
                "suspended remote still sends neutral heartbeat every client tick");
        check(tick.contains("!client.isPaused() && --waiting<=0"), "local single-player pause freezes connection timeout");
        check(pause.contains("sendInput(0)") && pause.contains("attackQueued=useQueued=false") && !pause.contains("sendStop"),
                "screen/focus transition clears transient actions and immediately neutralizes movement");
        check(consume.contains("options.inventoryKey") && consume.contains("key.setPressed(false)") && consume.contains("while (key.wasPressed())"),
                "standard inventory key is fully consumed before vanilla input processing");
        check(consume.contains("if (!accept) continue") && consume.contains("INPUT.accepts(RemoteInputGate.DROP)"),
                "suspended Q/clicks are drained instead of deferred");
        check(mixin.contains("method=\"setScreen\"") && mixin.contains("screen instanceof InventoryScreen")
                && mixin.contains("screen instanceof CreativeInventoryScreen") && mixin.contains("RemoteToolClient.blocksInventory() &&"),
                "both direct survival and creative backpack openings are blocked only during the remote session");
        check(client.contains("active() && (!returning() || camera!=null)")
                && consume.contains("key==options.inventoryKey && !blocksInventory()"),
                "backpack use resumes as soon as the camera returns, not after the orb delivery");
        check(mixin.contains("method=\"onWindowFocusChanged\"") && mixin.contains("if (!focused) RemoteToolClient.suspendInput()"),
                "focus callback protects presses before the next tick");
        check(!mixin.contains("client.setScreen") && !mixin.contains("screen!=null) ci.cancel"),
                "Esc, settings and unrelated screens remain available");
        check(scroll.contains("currentScreen==null"), "remote scroll capture leaves open menu scrolling intact");
        String server = Files.readString(repo.resolve("src/main/java/top/csituka/magicaland/gameplay/remote/RemoteToolServer.java"));
        check(server.contains("now-s.inputTick>40") && server.contains("player.currentScreenHandler==player.playerScreenHandler"),
                "server timeout and genuine container ownership safeguards stay intact");
    }

    private static String section(String source, String start, String end) {
        int from = source.indexOf(start), to = source.indexOf(end, from + 1);
        if (from < 0 || to < 0) throw new AssertionError("Missing source boundary: " + start);
        return source.substring(from, to);
    }
    private static void check(boolean condition, String name) {
        if (!condition) throw new AssertionError(name);
        checks++;
    }
}
