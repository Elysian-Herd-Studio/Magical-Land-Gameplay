package top.elysianherd.magicaland.gameplay.client.telekinesis;

final class TelekinesisGesture {
    enum Release { NONE, SHORT, DIRECT }
    static final long AIM_NANOS = 250_000_000L;
    private boolean pressed, token, blocked;
    private long started;

    void begin(long now, boolean token) {
        if (pressed || blocked) return;
        pressed = true; started = now; this.token = token;
    }

    boolean pressed() { return pressed; }
    boolean accept(boolean held) {
        if (!blocked) return true;
        if (!held) blocked = false;
        return false;
    }
    boolean aiming(long now) { return pressed && !token && now - started >= AIM_NANOS; }

    Release release(long now) {
        if (!pressed) return Release.NONE;
        boolean longPress = now - started >= AIM_NANOS;
        pressed = false;
        return longPress ? token ? Release.NONE : Release.DIRECT : Release.SHORT;
    }

    void cancel() { pressed = false; blocked = true; }
}
