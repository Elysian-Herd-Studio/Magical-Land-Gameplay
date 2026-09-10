package top.csituka.magicaland.gameplay.sense;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import net.minecraft.network.PacketByteBuf;
import net.minecraft.util.Identifier;

public final class EarthSenseProtocol {
    public static final int VERSION = 2, MAX_SIGNALS = 32, MAX_STATE_BYTES = 2048, MAX_SIGNAL_ID = 65535;
    public static final double MAX_POSITION = 32_000_000;
    public static final float MIN_SIZE = .25f, MAX_WIDTH = 8, MAX_HEIGHT = 12;
    public static final Identifier CONTROL = new Identifier("magicaland_gameplay", "earth_sense_control_v2");
    public static final Identifier STATE = new Identifier("magicaland_gameplay", "earth_sense_state_v2");

    public record Control(long token, boolean enabled) {
        public Control { if (token <= 0) throw new IllegalArgumentException("Invalid sense token"); }
    }
    public record Signal(int id, int kind, double x, double y, double z, float width, float height,
                         float activity, int strength, int pulse) {
        public Signal {
            if (id < 1 || id > MAX_SIGNAL_ID || kind < 0 || kind > 3 || strength < 1 || strength > 4
                    || !position(x) || !position(y) || !position(z) || !Float.isFinite(width) || !Float.isFinite(height)
                    || width < MIN_SIZE || width > MAX_WIDTH || height < MIN_SIZE || height > MAX_HEIGHT
                    || !Float.isFinite(activity) || activity < 0 || activity > 1
                    || pulse < 0 || pulse > 255) throw new IllegalArgumentException("Invalid sense signal");
        }
    }
    public record State(long token, int sequence, String dimension, boolean active, boolean grounded,
                        String reason, List<Signal> signals) {
        public State {
            if (token <= 0 || sequence < 0 || dimension == null || dimension.length() > 128
                    || !dimension.matches("[a-z0-9_.-]+:[a-z0-9/._-]+") || reason == null
                    || reason.length() > 32 || !reason.matches("[a-z_]*"))
                throw new IllegalArgumentException("Invalid sense state");
            signals = List.copyOf(signals);
            if (signals.size() > MAX_SIGNALS || !active && grounded || (!active || !grounded) && !signals.isEmpty())
                throw new IllegalArgumentException("Invalid sense phase");
            var occupied = new HashSet<Integer>();
            for (var signal : signals) if (!occupied.add(signal.id()))
                throw new IllegalArgumentException("Duplicate sense signal ID");
        }
    }
    private EarthSenseProtocol() {}
    private static boolean position(double value) { return Double.isFinite(value) && Math.abs(value) <= MAX_POSITION; }

    public static void writeControl(PacketByteBuf buf, Control control) {
        buf.writeByte(VERSION).writeLong(control.token()).writeBoolean(control.enabled());
    }
    public static Control readControl(PacketByteBuf buf) {
        if (buf.readableBytes() != 10 || buf.readUnsignedByte() != VERSION)
            throw new IllegalArgumentException("Invalid sense control length/version");
        return new Control(buf.readLong(), bool(buf));
    }
    public static void writeState(PacketByteBuf buf, State state) {
        buf.writeByte(VERSION).writeLong(state.token());
        buf.writeVarInt(state.sequence()).writeString(state.dimension(), 128)
                .writeBoolean(state.active()).writeBoolean(state.grounded());
        buf.writeString(state.reason(), 32).writeVarInt(state.signals().size());
        for (var signal : state.signals()) {
            buf.writeVarInt(signal.id()).writeByte(signal.kind()).writeDouble(signal.x()).writeDouble(signal.y())
                    .writeDouble(signal.z()).writeFloat(signal.width()).writeFloat(signal.height())
                    .writeFloat(signal.activity()).writeByte(signal.strength()).writeByte(signal.pulse());
        }
    }
    public static State readState(PacketByteBuf buf) {
        if (buf.readableBytes() > MAX_STATE_BYTES || buf.readUnsignedByte() != VERSION)
            throw new IllegalArgumentException("Invalid sense state length/version");
        long token = buf.readLong();
        int sequence = buf.readVarInt();
        String dimension = buf.readString(128);
        boolean active = bool(buf), grounded = bool(buf);
        String reason = buf.readString(32);
        int size = buf.readVarInt();
        if (size < 0 || size > MAX_SIGNALS) throw new IllegalArgumentException("Invalid sense count");
        var signals = new ArrayList<Signal>(size);
        for (int i = 0; i < size; i++) signals.add(new Signal(buf.readVarInt(), buf.readUnsignedByte(),
                buf.readDouble(), buf.readDouble(), buf.readDouble(), buf.readFloat(), buf.readFloat(),
                buf.readFloat(), buf.readUnsignedByte(), buf.readUnsignedByte()));
        if (buf.isReadable()) throw new IllegalArgumentException("Trailing sense state");
        return new State(token, sequence, dimension, active, grounded, reason, signals);
    }
    private static boolean bool(PacketByteBuf buf) {
        int value = buf.readUnsignedByte();
        if (value > 1) throw new IllegalArgumentException("Invalid sense boolean");
        return value == 1;
    }
}
