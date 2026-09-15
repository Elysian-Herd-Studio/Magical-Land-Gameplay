package top.elysianherd.magicaland.gameplay.client.levitation;

import top.elysianherd.magicaland.gameplay.levitation.UnicornLevitationProtocol;
import top.elysianherd.magicaland.gameplay.levitation.UnicornLevitationMath.Mode;
import top.elysianherd.magicaland.gameplay.levitation.UnicornLevitationMath;

public final class UnicornLevitationSession {
    private long token, received = -1;
    private int sent = -1, ack = -1, landedInput = -1, ascendFence, age;
    private boolean enabled, armed, allowed, confirmedArmed, airborneCast, remoteRelease, damageRelease;
    private Mode confirmedMode = Mode.OFF;
    private UnicornLevitationInput input = UnicornLevitationInput.NONE;

    public void begin(boolean prepare) {
        token = Math.addExact(token, 1); sent = ack = landedInput = -1; ascendFence = age = 0;
        enabled = true; armed = prepare; allowed = confirmedArmed = false; confirmedMode = Mode.OFF;
        airborneCast = remoteRelease = damageRelease = false;
        input = UnicornLevitationInput.NONE;
    }
    public void disarm() { armed = confirmedArmed = airborneCast = false; confirmedMode = Mode.OFF; }
    public void disable() { disarm(); enabled = allowed = remoteRelease = damageRelease = false; }
    public void interruptForDamage() {
        if (!enabled) return;
        damageRelease = true; airborneCast = false; confirmedMode = Mode.OFF;
    }
    public void resumeAfterDamage(boolean inputAvailable, boolean jumpHeld) {
        if (inputAvailable && !jumpHeld) damageRelease = false;
    }
    public boolean jumpSuspended() { return enabled && (remoteRelease || damageRelease); }
    public UnicornLevitationInput filterInput(UnicornLevitationInput next) {
        if (!enabled || remoteRelease) return UnicornLevitationInput.NONE;
        return damageRelease && next.space() ? new UnicornLevitationInput(false, next.sneak(), next.forward(),
                next.backward(), next.left(), next.right(), next.yaw()) : next;
    }
    public void pauseForRemote() {
        if (!enabled) return;
        remoteRelease = true; airborneCast = false; confirmedMode = Mode.OFF;
    }
    public void resumeAfterRemote(boolean inputAvailable, boolean jumpHeld) {
        if (inputAvailable && !jumpHeld) remoteRelease = false;
    }
    public boolean controlsSuspended() { return enabled && remoteRelease; }
    public void clear() {
        disable(); token = Math.addExact(token, 1); sent = ack = landedInput = -1; received = -1;
        age = 0; input = UnicornLevitationInput.NONE;
    }
    public UnicornLevitationProtocol.Control packet(UnicornLevitationInput next) {
        if (token <= 0 || sent == Integer.MAX_VALUE) throw new IllegalStateException("Levitation session exhausted");
        int sequence = ++sent;
        next = filterInput(next);
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
        if (!allowed || !confirmedArmed) airborneCast = false;
        return true;
    }
    public void tick() { age = Math.min(1000, age + 1); if (expired()) airborneCast = false; }
    public Mode mode(UnicornLevitationInput current) {
        if (!enabled || !allowed || !armed || !confirmedArmed || expired() || remoteRelease) return Mode.OFF;
        if (confirmedMode == Mode.ASCEND || confirmedMode == Mode.HOVER || confirmedMode == Mode.RECOVER) {
            if (damageRelease || !current.space() || ack < ascendFence || ack <= landedInput) return Mode.OFF;
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
        if (remoteRelease) return Mode.OFF;
        boolean eligible = allowed() && armed && confirmedArmed;
        if (!eligible) airborneCast = false;
        if (grounded && airborneCast) {
            airborneCast = false;
            landedInput = sent;
        }
        Mode requested = mode(current);
        boolean confirmedCast = requested == Mode.ASCEND || requested == Mode.HOVER;
        if (!grounded && confirmedCast) airborneCast = true;
        // 同次空中施法可立即续按；落地后必须重新得到服务端确认。
        boolean held = !damageRelease && current.space() && (confirmedCast || !grounded && airborneCast);
        return UnicornLevitationMath.chooseMode(eligible, held,
                current.sneak(), grounded, previous, velocityY, clearance, fluidSurface, fallDistance);
    }
    public boolean enabled() { return enabled; }
    public boolean armed() { return enabled && armed; }
    public boolean allowed() { return enabled && allowed && !expired(); }
    public boolean expired() { return age > 20; }
    public long token() { return token; }
    public UnicornLevitationInput input() { return input; }
}
