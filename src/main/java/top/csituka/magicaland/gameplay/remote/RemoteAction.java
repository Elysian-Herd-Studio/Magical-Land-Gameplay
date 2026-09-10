package top.csituka.magicaland.gameplay.remote;

public enum RemoteAction {
    NONE(0), SWING(6), HIT(6), MINING(6), USE(6), BREAK(6), TOOL_BREAK(8);

    private final int duration;
    RemoteAction(int duration) { this.duration=duration; }
    public int durationTicks() { return duration; }
    public static RemoteAction fromId(int id) {
        return id>=0 && id<values().length?values()[id]:NONE;
    }
}
