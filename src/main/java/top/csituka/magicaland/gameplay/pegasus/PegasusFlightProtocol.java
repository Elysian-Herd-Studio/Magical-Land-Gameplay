package top.csituka.magicaland.gameplay.pegasus;

import java.util.UUID;
import net.minecraft.network.PacketByteBuf;
import net.minecraft.util.Identifier;
import static top.csituka.magicaland.gameplay.pegasus.PegasusFlightMath.*;

public final class PegasusFlightProtocol {
    public static final Identifier CONTROL = new Identifier("magicaland_gameplay", "pegasus_control_v2");
    public static final Identifier STATE = new Identifier("magicaland_gameplay", "pegasus_state_v2");
    public static final Identifier IMPACT = new Identifier("magicaland_gameplay", "pegasus_impact_v1");
    public record Control(long token, int sequence, boolean flying, boolean gliding, boolean unlocked,
                          boolean forward, boolean backward, boolean left, boolean right, boolean ascend,
                          boolean descend, Attitude attitude) {
        public Control {
            if (token <= 0 || sequence < 0 || attitude == null || gliding && !flying || unlocked && !gliding)
                throw new IllegalArgumentException("Invalid flight input");
        }
        public Control neutral() { return new Control(token, sequence, flying, gliding, unlocked, false, false, false, false, false, false, attitude); }
    }
    public record State(UUID actor, long token, long sequence, int ackInputSequence, String dimension,
                        boolean allowed, Mode mode, boolean unlocked, float stamina, boolean exhausted, boolean boosting, Dynamics dynamics,
                        double x, double y, double z, double velocityX, double velocityY, double velocityZ,
                        int reboundTicks, String reason) {
        public State {
            if (actor == null || token <= 0 || sequence <= 0 || ackInputSequence < 0 || !validDimension(dimension)
                    || mode == null || dynamics == null || !Float.isFinite(stamina) || stamina < 0 || stamina > MAX_STAMINA
                    || !coordinate(x) || !coordinate(y) || !coordinate(z) || !velocity(velocityX) || !velocity(velocityY) || !velocity(velocityZ)
                    || reboundTicks < 0 || reboundTicks > REBOUND_TICKS || reason == null || !reason.matches("[a-z_]{0,32}")
                    || !allowed && mode != Mode.OFF) throw new IllegalArgumentException("Invalid flight state");
        }
        public Attitude attitude() { return dynamics.body(); }
    }
    public record Impact(String dimension, double x, double y, double z, float strength, float radius) {
        public Impact {
            if (!validDimension(dimension) || !coordinate(x) || !coordinate(y) || !coordinate(z) || !Float.isFinite(strength)
                    || strength < 0 || strength > 1 || !Float.isFinite(radius) || radius <= 0 || radius > 64)
                throw new IllegalArgumentException("Invalid flight impact");
        }
    }
    private PegasusFlightProtocol() {}
    private static boolean validDimension(String value) { return value != null && value.length() <= 128 && value.matches("[a-z0-9_.-]+:[a-z0-9_./-]+"); }
    private static boolean coordinate(double n) { return Double.isFinite(n) && Math.abs(n) <= 30_000_000; }
    private static boolean velocity(double n) { return Double.isFinite(n) && Math.abs(n) <= 128; }
    private static void attitude(PacketByteBuf b, Attitude q) { b.writeFloat(q.x()); b.writeFloat(q.y()); b.writeFloat(q.z()); b.writeFloat(q.w()); }
    private static Attitude attitude(PacketByteBuf b) { return new Attitude(b.readFloat(), b.readFloat(), b.readFloat(), b.readFloat()); }
    private static void dynamics(PacketByteBuf b, Dynamics d) {
        attitude(b, d.body()); b.writeFloat((float)d.angularVelocity().x()); b.writeFloat((float)d.angularVelocity().y());
        b.writeFloat((float)d.angularVelocity().z()); b.writeFloat(d.thrust());
    }
    private static Dynamics dynamics(PacketByteBuf b) {
        return new Dynamics(attitude(b), new Motion(b.readFloat(), b.readFloat(), b.readFloat()), b.readFloat());
    }
    public static void writeControl(PacketByteBuf b, Control c) {
        b.writeLong(c.token()); b.writeVarInt(c.sequence());
        b.writeShort((c.flying() ? 1 : 0) | (c.gliding() ? 2 : 0) | (c.unlocked() ? 4 : 0) | (c.forward() ? 8 : 0)
                | (c.backward() ? 16 : 0) | (c.left() ? 32 : 0) | (c.right() ? 64 : 0) | (c.ascend() ? 128 : 0) | (c.descend() ? 256 : 0));
        attitude(b, c.attitude());
    }
    public static Control readControl(PacketByteBuf b) {
        if (b.readableBytes() > 31) throw new IllegalArgumentException("Oversized flight input");
        long token = b.readLong(); int seq = b.readVarInt(), f = b.readUnsignedShort();
        if (f > 511) throw new IllegalArgumentException("Flight input flags");
        var c = new Control(token, seq, (f & 1) != 0, (f & 2) != 0, (f & 4) != 0, (f & 8) != 0,
                (f & 16) != 0, (f & 32) != 0, (f & 64) != 0, (f & 128) != 0, (f & 256) != 0, attitude(b));
        end(b); return c;
    }
    public static void writeState(PacketByteBuf b, State s) {
        b.writeUuid(s.actor()); b.writeLong(s.token()); b.writeLong(s.sequence()); b.writeVarInt(s.ackInputSequence()); b.writeString(s.dimension(), 128);
        b.writeBoolean(s.allowed()); b.writeByte(s.mode().ordinal()); b.writeBoolean(s.unlocked()); b.writeFloat(s.stamina()); b.writeBoolean(s.exhausted()); b.writeBoolean(s.boosting());
        dynamics(b, s.dynamics()); b.writeDouble(s.x()); b.writeDouble(s.y()); b.writeDouble(s.z());
        b.writeDouble(s.velocityX()); b.writeDouble(s.velocityY()); b.writeDouble(s.velocityZ()); b.writeVarInt(s.reboundTicks()); b.writeString(s.reason(), 32);
    }
    public static State readState(PacketByteBuf b) {
        if (b.readableBytes() > 768) throw new IllegalArgumentException("Oversized flight state");
        UUID actor = b.readUuid(); long token = b.readLong(), seq = b.readLong(); int ack = b.readVarInt(); String dim = b.readString(128);
        boolean allowed = b.readBoolean(); int mode = b.readUnsignedByte();
        if (mode >= Mode.values().length) throw new IllegalArgumentException("Flight mode");
        var s = new State(actor, token, seq, ack, dim, allowed, Mode.values()[mode], b.readBoolean(), b.readFloat(), b.readBoolean(), b.readBoolean(), dynamics(b),
                b.readDouble(), b.readDouble(), b.readDouble(), b.readDouble(), b.readDouble(), b.readDouble(), b.readVarInt(), b.readString(32));
        end(b); return s;
    }
    public static void writeImpact(PacketByteBuf b, Impact i) { b.writeString(i.dimension(), 128); b.writeDouble(i.x()); b.writeDouble(i.y()); b.writeDouble(i.z()); b.writeFloat(i.strength()); b.writeFloat(i.radius()); }
    public static Impact readImpact(PacketByteBuf b) {
        if (b.readableBytes() > 550) throw new IllegalArgumentException("Oversized flight impact");
        var i = new Impact(b.readString(128), b.readDouble(), b.readDouble(), b.readDouble(), b.readFloat(), b.readFloat()); end(b); return i;
    }
    private static void end(PacketByteBuf b) { if (b.isReadable()) throw new IllegalArgumentException("Trailing flight data"); }
}
