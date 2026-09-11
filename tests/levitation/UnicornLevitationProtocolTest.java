package top.csituka.magicaland.gameplay.levitation;

import io.netty.buffer.Unpooled;
import java.util.UUID;
import net.minecraft.network.PacketByteBuf;

public final class UnicornLevitationProtocolTest {
    private static int checks;
    public static void main(String[] args) {
        check(UnicornLevitationProtocol.CONTROL.getPath().endsWith("_v3") && UnicornLevitationProtocol.STATE.getPath().endsWith("_v3"), "new physical semantics do not negotiate older physical protocols");
        for (int flags = 0; flags < 256; flags++) {
            boolean enabled = (flags & 1) != 0; if (!enabled && (flags & 6) != 0) continue;
            var input = new UnicornLevitationProtocol.Control(Long.MAX_VALUE, Integer.MAX_VALUE, enabled,
                    (flags & 2) != 0, (flags & 4) != 0, (flags & 8) != 0, (flags & 16) != 0,
                    (flags & 32) != 0, (flags & 64) != 0, (flags & 128) != 0, -360);
            var buf = buffer(); try { UnicornLevitationProtocol.writeControl(buf, input); check(buf.readableBytes() <= 18, "bounded control"); check(input.equals(UnicornLevitationProtocol.readControl(buf)), "flags roundtrip"); } finally { buf.release(); }
        }
        for (var mode : UnicornLevitationMath.Mode.values()) for (int mana = 0; mana <= 100; mana++) {
            var state = new UnicornLevitationProtocol.State(new UUID(7, 9), 1, Long.MAX_VALUE, 0, "minecraft:overworld", true, true, mode, mana, .18, -128, .03, "");
            var buf = buffer(); try { UnicornLevitationProtocol.writeState(buf, state); check(state.equals(UnicornLevitationProtocol.readState(buf)), "state roundtrip"); } finally { buf.release(); }
        }
        bad(() -> new UnicornLevitationProtocol.Control(0, 0, true, false, false, false, false, false, false, false, 0));
        bad(() -> new UnicornLevitationProtocol.Control(1, -1, true, false, false, false, false, false, false, false, 0));
        bad(() -> new UnicornLevitationProtocol.Control(1, 0, true, false, false, false, false, false, false, false, Float.NaN));
        bad(() -> new UnicornLevitationProtocol.Control(1, 0, false, true, true, false, false, false, false, false, 0));
        for (float mana : new float[]{Float.NaN, -1, 101}) bad(() -> state(mana, 0, "minecraft:overworld", false, UnicornLevitationMath.Mode.OFF));
        bad(() -> state(50, Double.POSITIVE_INFINITY, "minecraft:overworld", false, UnicornLevitationMath.Mode.OFF));
        bad(() -> state(50, 0, "invalid", false, UnicornLevitationMath.Mode.OFF));
        bad(() -> state(50, 0, "minecraft:overworld", false, UnicornLevitationMath.Mode.ASCEND));
        var input = new UnicornLevitationProtocol.Control(1, 0, true, false, false, false, false, false, false, false, 0);
        var controlBuf = buffer(); try { UnicornLevitationProtocol.writeControl(controlBuf, input); controlBuf.writeByte(0); bad(() -> UnicornLevitationProtocol.readControl(controlBuf)); } finally { controlBuf.release(); }
        var stateBuf = buffer(); try { UnicornLevitationProtocol.writeState(stateBuf, state(50, 0, "minecraft:overworld", false, UnicornLevitationMath.Mode.OFF)); stateBuf.writeByte(0); bad(() -> UnicornLevitationProtocol.readState(stateBuf)); } finally { stateBuf.release(); }
        for (int length = 0; length < 14; length++) { var truncated = buffer(); truncated.writeZero(length); try { bad(() -> UnicornLevitationProtocol.readControl(truncated)); } finally { truncated.release(); } }
        check(UnicornLevitationMath.Mode.HOVER.ordinal() == 4, "HOVER appended without renumbering existing modes");
        System.out.println("PASS UnicornLevitationProtocolTest: " + checks);
    }
    private static UnicornLevitationProtocol.State state(float mana, double y, String dimension, boolean allowed, UnicornLevitationMath.Mode mode) {
        return new UnicornLevitationProtocol.State(new UUID(0, 1), 1, 1, 0, dimension, allowed, false, mode, mana, 0, y, 0, "");
    }
    private static PacketByteBuf buffer() { return new PacketByteBuf(Unpooled.buffer()); }
    private static void bad(Runnable action) { checks++; try { action.run(); } catch (RuntimeException expected) { return; } throw new AssertionError("invalid input accepted"); }
    private static void check(boolean value, String label) { checks++; if (!value) throw new AssertionError(label); }
}
