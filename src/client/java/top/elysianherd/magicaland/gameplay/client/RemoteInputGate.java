package top.elysianherd.magicaland.gameplay.client;

final class RemoteInputGate {
    static final int ATTACK = 64, USE = 128, DROP = 256;
    private static final int ACTIONS = ATTACK | USE | DROP;
    private boolean suspended = true, accepting;
    private int blocked = ACTIONS;

    void suspend() {
        suspended = true;
        accepting = false;
        blocked = ACTIONS;
    }

    void tick(boolean available, int heldActions) {
        if (!available) { suspend(); return; }
        blocked &= heldActions;
        accepting = !suspended;
        suspended = false;
    }

    boolean accepting() { return accepting; }
    boolean accepts(int action) { return accepting && (blocked & action) == 0; }
    int filter(int actions) { return accepting ? actions & ~blocked : 0; }
}
