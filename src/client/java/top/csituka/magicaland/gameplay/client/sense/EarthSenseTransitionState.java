package top.csituka.magicaland.gameplay.client.sense;

/** epoch 是请求编号；正常退出也会递增，断线由 reset 单独处理。 */
public final class EarthSenseTransitionState {
    public enum Action { NONE, ENTER, EXIT }
    private boolean active;
    private long epoch;

    public Action tick(boolean confirmedActive, long currentEpoch) {
        Action action = confirmedActive
                ? !active || epoch != currentEpoch ? Action.ENTER : Action.NONE
                : active ? Action.EXIT : Action.NONE;
        active = confirmedActive;
        epoch = currentEpoch;
        return action;
    }

    public void reset() {
        active = false;
        epoch = 0;
    }
}
