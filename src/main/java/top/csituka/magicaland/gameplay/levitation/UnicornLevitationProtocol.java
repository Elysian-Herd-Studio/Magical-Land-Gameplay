package top.csituka.magicaland.gameplay.levitation;

import java.util.UUID;
import net.minecraft.network.PacketByteBuf;
import net.minecraft.util.Identifier;

public final class UnicornLevitationProtocol {
    public static final String MOVEMENT_CORRECTION = "movement_correction";
    public static final String HURT_INTERRUPT = "hurt_interrupt";
    public static final Identifier CONTROL = new Identifier("magicaland_gameplay", "unicorn_levitation_control_v3");
    public static final Identifier STATE = new Identifier("magicaland_gameplay", "unicorn_levitation_state_v3");
    // sneak 表示定高；v3 增加下坠回托阶段，关闭技能同时关闭被动保护。
    public record Control(long token, int sequence, boolean enabled, boolean armed, boolean space,
                          boolean sneak, boolean forward, boolean backward, boolean left, boolean right, float yaw) {
        public Control {
            if (token <= 0 || sequence < 0 || !Float.isFinite(yaw) || Math.abs(yaw) > 10_000_000)
                throw new IllegalArgumentException("Invalid levitation input");
            if (!enabled && (armed || space)) throw new IllegalArgumentException("Disabled levitation input");
        }
    }
    public record State(UUID actor, long token, long sequence, int ackInputSequence, String dimension,
                        boolean allowed, boolean armed, UnicornLevitationMath.Mode mode, float mana,
                        double velocityX, double velocityY, double velocityZ, String reason) {
        public State {
            if (actor == null || token <= 0 || sequence <= 0 || ackInputSequence < 0 || mode == null
                    || dimension == null || dimension.length() > 128 || !dimension.matches("[a-z0-9_.-]+:[a-z0-9_./-]+")
                    || !Float.isFinite(mana) || mana < 0 || mana > 100 || !velocity(velocityX)
                    || !velocity(velocityY) || !velocity(velocityZ) || reason == null
                    || reason.length() > 32 || !reason.matches("[a-z_]*")
                    || !allowed && (armed || mode != UnicornLevitationMath.Mode.OFF))
                throw new IllegalArgumentException("Invalid levitation state");
        }
    }
    private UnicornLevitationProtocol() {}
    private static boolean velocity(double value) { return Double.isFinite(value) && Math.abs(value) <= 128; }
    public static void writeControl(PacketByteBuf buf, Control input) {
        buf.writeLong(input.token()); buf.writeVarInt(input.sequence());
        buf.writeByte((input.enabled() ? 1 : 0) | (input.armed() ? 2 : 0) | (input.space() ? 4 : 0)
                | (input.sneak() ? 8 : 0) | (input.forward() ? 16 : 0) | (input.backward() ? 32 : 0)
                | (input.left() ? 64 : 0) | (input.right() ? 128 : 0));
        buf.writeFloat(input.yaw());
    }
    public static Control readControl(PacketByteBuf buf) {
        if (buf.readableBytes() > 18) throw new IllegalArgumentException("Oversized levitation input");
        long token = buf.readLong(); int sequence = buf.readVarInt(); int flags = buf.readUnsignedByte();
        var value = new Control(token, sequence, (flags & 1) != 0, (flags & 2) != 0, (flags & 4) != 0,
                (flags & 8) != 0, (flags & 16) != 0, (flags & 32) != 0, (flags & 64) != 0, (flags & 128) != 0, buf.readFloat());
        end(buf); return value;
    }
    public static void writeState(PacketByteBuf buf, State state) {
        buf.writeUuid(state.actor()); buf.writeLong(state.token()); buf.writeLong(state.sequence());
        buf.writeVarInt(state.ackInputSequence()); buf.writeString(state.dimension(), 128);
        buf.writeByte((state.allowed() ? 1 : 0) | (state.armed() ? 2 : 0)); buf.writeByte(state.mode().ordinal());
        buf.writeFloat(state.mana()); buf.writeDouble(state.velocityX()); buf.writeDouble(state.velocityY());
        buf.writeDouble(state.velocityZ()); buf.writeString(state.reason(), 32);
    }
    public static State readState(PacketByteBuf buf) {
        if (buf.readableBytes() > 640) throw new IllegalArgumentException("Oversized levitation state");
        UUID actor = buf.readUuid(); long token = buf.readLong(), sequence = buf.readLong();
        int ack = buf.readVarInt(); String dimension = buf.readString(128); int flags = buf.readUnsignedByte();
        int mode = buf.readUnsignedByte();
        if (flags > 3 || mode >= UnicornLevitationMath.Mode.values().length) throw new IllegalArgumentException("Invalid levitation flags");
        var value = new State(actor, token, sequence, ack, dimension, (flags & 1) != 0, (flags & 2) != 0,
                UnicornLevitationMath.Mode.values()[mode], buf.readFloat(), buf.readDouble(), buf.readDouble(), buf.readDouble(), buf.readString(32));
        end(buf); return value;
    }
    private static void end(PacketByteBuf buf) { if (buf.isReadable()) throw new IllegalArgumentException("Trailing levitation data"); }
}
