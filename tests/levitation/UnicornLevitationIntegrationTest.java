package top.csituka.magicaland.gameplay.levitation;

import io.netty.buffer.Unpooled;
import java.lang.invoke.MethodHandles;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Set;
import net.minecraft.network.PacketByteBuf;
import net.minecraft.network.packet.Packet;
import net.minecraft.network.packet.s2c.play.PlayerPositionLookS2CPacket;
import net.minecraft.network.packet.s2c.play.PositionFlag;
import net.minecraft.util.math.Vec3d;
import org.objectweb.asm.ClassReader;
import org.objectweb.asm.ClassWriter;
import org.objectweb.asm.Opcodes;
import org.objectweb.asm.Type;
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
        check(server.contains("player.getYaw(), player.getPitch(), PositionFlag.ROT") && server.contains("session.movementGuard.observe"),
                "correction supplies current absolute server angles and counts sustained errors");
        teleportRotation(handler);
        check(server.matches("(?s).*if \\(!input\\.space\\(\\) && \\(session\\.mode == Mode\\.ASCEND.*?\\)\\) \\{\\s*session\\.mode = Mode\\.OFF;\\s*session\\.budget = null;\\s*\\}.*"),
                "space release clears previous movement credit before the next packet");
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
    private static void teleportRotation(ClassNode handler) throws Exception {
        var teleport = replayTeleport(handler);
        for (float yaw : new float[]{-450.5f, -179.25f, 0, 83.125f, 540.5f}) {
            for (float pitch : new float[]{-89, -37.25f, 0, 14.5f, 89}) {
                teleport.player.yaw = yaw; teleport.player.pitch = pitch;
                for (int correction = 0; correction < 4; correction++) {
                    float serverYaw = teleport.player.getYaw(), serverPitch = teleport.player.getPitch();
                    teleport.requestTeleport(12.5, 65, -8.75, serverYaw, serverPitch, PositionFlag.ROT);
                    check(teleport.player.getYaw() == serverYaw && teleport.player.getPitch() == serverPitch,
                            "actual vanilla correction keeps absolute server yaw/pitch");
                    var packet = roundTrip(teleport.packet);
                    check(packet.getYaw() == 0 && packet.getPitch() == 0 && packet.getFlags().equals(PositionFlag.ROT),
                            "actual vanilla ROT correction sends zero relative rotation");
                    check(packet.getX() == 12.5 && packet.getY() == 65 && packet.getZ() == -8.75,
                            "ROT does not make correction coordinates relative");
                    check(packet.getTeleportId() == teleport.requestedTeleportId,
                            "vanilla teleport confirmation id survives serialization");
                    float inFlightYaw = serverYaw + 17.25f, inFlightPitch = serverPitch * .5f;
                    check(inFlightYaw + packet.getYaw() == inFlightYaw && inFlightPitch + packet.getPitch() == inFlightPitch,
                            "relative packet preserves client turns made while correction is in flight");
                    teleport.player.yaw = inFlightYaw; teleport.player.pitch = inFlightPitch;
                }
            }
        }
        teleport.player.yaw = 123.5f; teleport.player.pitch = -27.25f;
        teleport.requestTeleport(0, 0, 0, 0, 0, PositionFlag.ROT);
        var broken = roundTrip(teleport.packet);
        check(teleport.player.getYaw() == 0 && teleport.player.getPitch() == 0
                        && broken.getYaw() == -123.5f && broken.getPitch() == 27.25f,
                "regression witness: zero arguments reset server angles and send negative previous angles");
        teleport.requestTeleport(0, 0, 0, -78.5f, 31.25f, Set.of());
        var absolute = roundTrip(teleport.packet);
        check(teleport.player.getYaw() == -78.5f && teleport.player.getPitch() == 31.25f
                        && absolute.getYaw() == -78.5f && absolute.getPitch() == 31.25f && absolute.getFlags().isEmpty(),
                "actual vanilla parameters remain absolute with or without relative flags");
    }
    private static PlayerPositionLookS2CPacket roundTrip(Packet<?> outgoing) {
        check(outgoing instanceof PlayerPositionLookS2CPacket, "actual vanilla method constructs teleport packet");
        var bytes = new PacketByteBuf(Unpooled.buffer());
        try {
            outgoing.write(bytes);
            var decoded = new PlayerPositionLookS2CPacket(bytes);
            check(!bytes.isReadable(), "actual teleport packet consumes complete wire payload");
            return decoded;
        } finally { bytes.release(); }
    }
    private static TeleportReplay replayTeleport(ClassNode handler) throws Exception {
        var original = handler.methods.stream().filter(m -> m.name.equals("requestTeleport")
                && m.desc.equals("(DDDFFLjava/util/Set;)V")).findFirst().orElseThrow();
        var replay = new MethodNode(Opcodes.ACC_PUBLIC, original.name, original.desc, null, null);
        original.accept(replay);
        // Execute vanilla instructions; only the world-owning handler/player types are replaced.
        for (var insn : replay.instructions) {
            if (insn instanceof FieldInsnNode field) {
                field.owner = replayType(field.owner); field.desc = replayDescriptor(field.desc);
            } else if (insn instanceof MethodInsnNode method) {
                method.owner = replayType(method.owner); method.desc = replayDescriptor(method.desc);
            }
        }
        String parent = Type.getInternalName(TeleportReplay.class), generated = parent + "Vanilla";
        var writer = new ClassWriter(ClassWriter.COMPUTE_FRAMES | ClassWriter.COMPUTE_MAXS);
        writer.visit(Opcodes.V17, Opcodes.ACC_PUBLIC, generated, null, parent, null);
        var constructor = writer.visitMethod(Opcodes.ACC_PUBLIC, "<init>", "()V", null, null);
        constructor.visitCode(); constructor.visitVarInsn(Opcodes.ALOAD, 0);
        constructor.visitMethodInsn(Opcodes.INVOKESPECIAL, parent, "<init>", "()V", false);
        constructor.visitInsn(Opcodes.RETURN); constructor.visitMaxs(0, 0); constructor.visitEnd();
        replay.accept(writer); writer.visitEnd();
        return (TeleportReplay) MethodHandles.lookup().defineClass(writer.toByteArray()).getConstructor().newInstance();
    }
    private static String replayType(String type) {
        return switch (type) {
            case "net/minecraft/server/network/ServerPlayNetworkHandler" -> Type.getInternalName(TeleportReplay.class);
            case "net/minecraft/server/network/ServerPlayerEntity" -> Type.getInternalName(TeleportPlayer.class);
            default -> type;
        };
    }
    private static String replayDescriptor(String descriptor) {
        for (String type : new String[]{"net/minecraft/server/network/ServerPlayNetworkHandler", "net/minecraft/server/network/ServerPlayerEntity"})
            descriptor = descriptor.replace("L" + type + ";", "L" + replayType(type) + ";");
        return descriptor;
    }
    public abstract static class TeleportReplay {
        public final TeleportPlayer player = new TeleportPlayer(this);
        public Vec3d requestedTeleportPos;
        public int requestedTeleportId, ticks, teleportRequestTick;
        public Packet<?> packet;
        public abstract void requestTeleport(double x, double y, double z, float yaw, float pitch, Set<PositionFlag> flags);
        public void sendPacket(Packet<?> packet) { this.packet = packet; }
    }
    public static final class TeleportPlayer {
        public final TeleportReplay networkHandler;
        private double x, y, z;
        private float yaw, pitch;
        public TeleportPlayer(TeleportReplay networkHandler) { this.networkHandler = networkHandler; }
        public double getX() { return x; }
        public double getY() { return y; }
        public double getZ() { return z; }
        public float getYaw() { return yaw; }
        public float getPitch() { return pitch; }
        public void updatePositionAndAngles(double x, double y, double z, float yaw, float pitch) {
            this.x = x; this.y = y; this.z = z; this.yaw = yaw; this.pitch = pitch;
        }
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
