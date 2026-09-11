package top.csituka.magicaland.gameplay.levitation;

import java.nio.file.Files;
import java.nio.file.Path;
import org.objectweb.asm.ClassReader;
import org.objectweb.asm.Opcodes;
import org.objectweb.asm.tree.*;

public final class UnicornLevitationIntegrationTest {
    private static int checks;
    public static void main(String[] args) throws Exception {
        ClassNode handler = load("net/minecraft/server/network/ServerPlayNetworkHandler"); var move = method(handler, "onPlayerMove");
        int threading = call(move, "forceMainThread"), collision = call(move, "move"), fall = call(move, "handleFall");
        check(threading >= 0 && threading < collision && collision < fall, "actual thread/collision/fall order");
        check(call(move, "isPlayerNotCollidingWithBlocks") >= 0 && call(move, "requestTeleport") >= 0, "vanilla collision correction");
        check(handler.fields.stream().anyMatch(f -> f.name.equals("floating") && f.desc.equals("Z")), "actual floating field");
        check(handler.fields.stream().anyMatch(f -> f.name.equals("requestedTeleportPos") && f.desc.equals("Lnet/minecraft/util/math/Vec3d;")), "actual teleport field");
        var player = load("net/minecraft/server/network/ServerPlayerEntity");
        check(call(method(player, "handleFall"), "fall") >= 0, "vanilla fall damage delegated");
        check(method(player, "fall").instructions.getFirst().getOpcode() == Opcodes.RETURN, "move does not separately deal fall damage");
        Path root = Path.of(args[1]).resolve("src/main");
        String server = Files.readString(root.resolve("java/top/csituka/magicaland/gameplay/levitation/UnicornLevitationServer.java"));
        String network = Files.readString(root.resolve("java/top/csituka/magicaland/gameplay/mixin/UnicornLevitationNetworkMixin.java"));
        String fallMixin = Files.readString(root.resolve("java/top/csituka/magicaland/gameplay/mixin/UnicornLevitationFallMixin.java"));
        check(network.contains("NetworkThreadUtils;forceMainThread") && network.contains("shift = At.Shift.AFTER"), "main-thread injection");
        check(network.contains("requestedTeleportPos == null") && network.contains("@At(\"RETURN\")"), "teleport/final floating guards");
        check(!server.contains(".move(") && !server.contains("setPosition(") && !server.contains("setNoGravity("), "no double movement or gravity flag");
        check(!server.matches("(?s).*\\.(allowFlying|flying|invulnerable)\\s*=.*"), "no permission writes");
        check(!server.contains("rules.spend") && !server.contains("rules.regenerate") && !server.contains("RaceState") && !server.contains("NbtElement"), "no mana cost, recovery or NBT writes");
        check(server.contains("session.rules.protectsFall(session.input.armed(), now(player))"), "server requires explicit armed lease for fall protection");
        check(fallMixin.contains("@Mixin(PlayerEntity.class)") && fallMixin.contains("handleFallDamage") && fallMixin.contains("instanceof ServerPlayerEntity"), "only server player fall damage guarded");
        check(fallMixin.contains("cir.setReturnValue(false)") && !fallMixin.contains("damage("), "fall-specific gate does not grant general invulnerability");
        check(call(method(load("net/minecraft/entity/player/PlayerEntity"), "handleFallDamage"), "handleFallDamage") >= 0, "actual player fall-damage entry delegates ordinary fall");
        check(!server.contains("freshMovement") && !server.contains("session.landing"), "old timing-dependent proof removed");
        check(server.indexOf("session.health = player.getHealth();") < server.indexOf("private static void tick"), "restart captures current injury baseline");
        check(server.contains("session.grounded && tick - session.observedTick <= 2") && server.contains("player.isOnGround() || session.spaceGrounded"), "queued jump keeps grounded-origin charge");
        check(server.contains("UnicornLevitationGround.find(player.getWorld(), player, controlMotion)") && !server.contains("player.isOnGround() ? null"), "grounded shore can probe ahead");
        check(server.contains("PENDING.compute") && server.contains("MAX_SESSIONS") && server.contains("rules.expired"), "bounded queue/sessions/lease");
        check(!server.contains(".progress("), "ability cannot create or change race progress");
        check(server.contains("session.budget.advance") && server.contains("session.budget.validate"), "shared-math finite packet credit");
        check(server.contains("verdict == UnicornLevitationBudget.Verdict.REJECT) close(player, \"movement\")"),
                "only severe or sustained anomalies close the session");
        check(server.contains("session.budget.reanchor(player.getX(), player.getY(), player.getZ())")
                && server.contains("session.motion = session.budget.expected()"), "correction uses confirmed position and trusted inertia");
        check(server.indexOf("player.networkHandler.requestTeleport") < server.indexOf("send(session, true, UnicornLevitationProtocol.MOVEMENT_CORRECTION, true)"),
                "correction state restores inertial velocity after the vanilla teleport packet");
        check(server.contains("0, 0, PositionFlag.ROT") && server.contains("session.movementGuard.observe"),
                "correction preserves client view rotation and counts sustained errors");
        check(network.contains("physicsActive(player)) floating = false;"), "early corrective cancellation cannot leave vanilla floating kick flag set");
        String client = Files.readString(Path.of(args[1]).resolve("src/client/java/top/csituka/magicaland/gameplay/client/levitation/UnicornLevitationClient.java"));
        check(client.contains("MOVEMENT_CORRECTION.equals(state.reason())") && client.contains("SESSION.allowed() && SESSION.armed() && scope(client)")
                && client.contains("client.player.setVelocity(state.velocityX(), state.velocityY(), state.velocityZ())"),
                "only an acknowledged valid owner correction restores velocity without rearming");
        check(server.indexOf("PENDING.remove(player.getUuid())") < server.indexOf("if (!physicsActive(player)) return true"), "queued controls handled before move validation");
        check(!server.contains("player.isCreative()") && !server.contains("getAbilities().allowFlying") && server.contains("getAbilities().flying"), "creative allowed without native flight overlap");
        String main = Files.readString(root.resolve("java/top/csituka/magicaland/gameplay/MagicalLandGameplay.java"));
        String race = Files.readString(root.resolve("java/top/csituka/magicaland/gameplay/race/RaceServer.java"));
        String remote = Files.readString(root.resolve("java/top/csituka/magicaland/gameplay/remote/RemoteToolServer.java"));
        String mixins = Files.readString(root.resolve("resources/magicaland.gameplay.mixins.json"));
        check(main.contains("UnicornLevitationServer.register()") && race.contains("UnicornLevitationServer.stop(player)"), "initializer/race cleanup");
        check(remote.contains("UnicornLevitationServer.active(player)") && remote.contains("UnicornLevitationServer.stop(player)"), "remote mutual exclusion");
        check(mixins.contains("UnicornLevitationNetworkMixin") && mixins.contains("UnicornLevitationFallMixin"), "hooks registered");
        System.out.println("PASS UnicornLevitationIntegrationTest: " + checks + " actual MC bytecode/source checks; not a live game test");
    }
    private static ClassNode load(String name) throws Exception {
        var type = new ClassNode(); try (var stream = UnicornLevitationIntegrationTest.class.getClassLoader().getResourceAsStream(name + ".class")) {
            if (stream == null) throw new AssertionError(name); new ClassReader(stream).accept(type, ClassReader.SKIP_DEBUG | ClassReader.SKIP_FRAMES);
        } return type;
    }
    private static MethodNode method(ClassNode owner, String name) { return owner.methods.stream().filter(m -> m.name.equals(name)).findFirst().orElseThrow(); }
    private static int call(MethodNode method, String name) { int index = 0; for (var insn : method.instructions) { if (insn instanceof MethodInsnNode m && m.name.equals(name)) return index; index++; } return -1; }
    private static void check(boolean value, String label) { checks++; if (!value) throw new AssertionError(label); }
}
