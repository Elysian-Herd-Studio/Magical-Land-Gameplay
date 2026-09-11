package top.csituka.magicaland.gameplay.client.levitation;

import top.csituka.magicaland.gameplay.levitation.UnicornLevitationProtocol;
import top.csituka.magicaland.gameplay.levitation.UnicornLevitationMath.Mode;
import top.csituka.magicaland.gameplay.levitation.UnicornLevitationMath;

public final class UnicornLevitationSession {
    private long token, received = -1;
    private int sent = -1, ack = -1, ascendFence, age;
    private boolean enabled, armed, allowed, confirmedArmed;
    private Mode confirmedMode = Mode.OFF;
    private UnicornLevitationInput input = UnicornLevitationInput.NONE;

    public void begin(boolean prepare) {
        token = Math.addExact(token, 1); sent = ack = -1; ascendFence = age = 0;
        enabled = true; armed = prepare; allowed = confirmedArmed = false; confirmedMode = Mode.OFF;
        input = UnicornLevitationInput.NONE;
    }
    public void disarm() { armed = confirmedArmed = false; confirmedMode = Mode.OFF; }
    public void disable() { disarm(); enabled = allowed = false; }
    public void clear() {
        disable(); token = Math.addExact(token, 1); sent = ack = -1; received = -1;
        age = 0; input = UnicornLevitationInput.NONE;
    }
    public UnicornLevitationProtocol.Control packet(UnicornLevitationInput next) {
        if (token <= 0 || sent == Integer.MAX_VALUE) throw new IllegalStateException("Levitation session exhausted");
        int sequence = ++sent;
        next = enabled ? next : UnicornLevitationInput.NONE;
        if (input.space() != next.space() || !armed || !enabled) ascendFence = sequence;
        input = next;
        return new UnicornLevitationProtocol.Control(token, sequence, enabled, armed, next.space(), next.sneak(),
                next.forward(), next.backward(), next.left(), next.right(), next.yaw());
    }
    public boolean accept(UnicornLevitationProtocol.State state, String dimension) {
        if ((!enabled && state.allowed()) || state.token() != token || state.sequence() <= received || state.ackInputSequence() < ack
                || state.ackInputSequence() > sent || !state.dimension().equals(dimension)) return false;
        received = state.sequence(); ack = state.ackInputSequence(); age = 0;
        allowed = state.allowed(); confirmedArmed = state.armed(); confirmedMode = state.mode();
        return true;
    }
    public void tick() { age = Math.min(1000, age + 1); }
    public Mode mode(UnicornLevitationInput current) {
        if (!enabled || !allowed || !armed || !confirmedArmed || expired()) return Mode.OFF;
        if (confirmedMode == Mode.ASCEND || confirmedMode == Mode.HOVER || confirmedMode == Mode.RECOVER) {
            if (!armed || !confirmedArmed || !current.space() || ack < ascendFence) return Mode.OFF;
            return current.sneak() ? Mode.HOVER : Mode.ASCEND;
        }
        return confirmedMode;
    }
    public Mode predict(UnicornLevitationInput current, boolean grounded, Mode previous, double velocityY,
                        double clearance, boolean fluidSurface) {
        return predict(current, grounded, previous, velocityY, clearance, fluidSurface, 0);
    }
    public Mode predict(UnicornLevitationInput current, boolean grounded, Mode previous, double velocityY,
                        double clearance, boolean fluidSurface, double fallDistance) {
        Mode requested = mode(current);
        return UnicornLevitationMath.chooseMode(allowed() && armed && confirmedArmed, requested == Mode.ASCEND || requested == Mode.HOVER,
                current.sneak(), grounded, previous, velocityY, clearance, fluidSurface, fallDistance);
    }
    public boolean enabled() { return enabled; }
    public boolean armed() { return enabled && armed; }
    public boolean allowed() { return enabled && allowed && !expired(); }
    public boolean expired() { return age > 20; }
    public long token() { return token; }
    public UnicornLevitationInput input() { return input; }
}
