package top.csituka.magicaland.gameplay.client;

import java.util.HashMap;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import net.minecraft.item.ItemStack;
import top.csituka.magicaland.gameplay.remote.RemoteAction;
import top.csituka.magicaland.gameplay.remote.RemoteToolEntity;

public final class RemoteHeldAnimation {
    public record Frame(ItemStack stack, float swing, float equip, boolean acting) {}
    private static final Map<UUID, State> STATES = new HashMap<>();
    private RemoteHeldAnimation() {}

    public static void clear() { STATES.clear(); }
    public static void retain(Set<UUID> present) { STATES.keySet().retainAll(present); }
    public static void predict(RemoteToolEntity tool, RemoteAction action) {
        if (tool.returning()) return;
        State state = state(tool);
        double time = tool.getWorld().getTime();
        if (state.action == RemoteAction.MINING || state.action != RemoteAction.NONE && time - state.started < 3) return;
        state.action = action; state.started = time; state.predicted = true;
    }

    public static Frame sample(RemoteToolEntity tool, ItemStack stack, int slot, float delta) {
        State state = state(tool);
        double time = tool.getWorld().getTime() + delta;
        if (tool.returning()) {
            state.sequence = tool.actionSequence(); state.action = RemoteAction.NONE; state.predicted = false;
            state.previous = ItemStack.EMPTY; state.shown = stack.copy(); state.slot = slot;
            state.equipped = Double.NEGATIVE_INFINITY;
            return new Frame(state.shown, 0, 0, false);
        }
        if (state.sequence != tool.actionSequence()) {
            RemoteAction action = tool.action();
            boolean keepPrediction = state.predicted && state.action == RemoteAction.SWING
                    && (action == RemoteAction.SWING || action == RemoteAction.HIT)
                    && time - state.started >= 0 && time - state.started < action.durationTicks();
            if (!keepPrediction) state.started = tool.actionStartedTick();
            state.action = action; state.sequence = tool.actionSequence(); state.predicted = false;
        }
        if (state.slot != slot || state.shown.isEmpty() != stack.isEmpty()
                || (!stack.isEmpty() && state.shown.getItem() != stack.getItem())) {
            state.previous = state.shown.copy(); state.shown = stack.copy();
            state.equipped = time; state.slot = slot;
        } else state.shown = stack.copy();
        double equipTime = time - state.equipped;
        float equip = equipTime >= 5 ? 0 : equipTime < 2
                ? RemoteVisualMath.smooth((float)(equipTime / 2))
                : 1 - RemoteVisualMath.smooth((float)((equipTime - 2) / 3));
        ItemStack displayed = equipTime < 2 ? state.previous : state.shown;
        double elapsed = time - state.started;
        boolean acting = state.action != RemoteAction.NONE && elapsed >= 0
                && (state.action == RemoteAction.MINING || elapsed < state.action.durationTicks());
        float swing = acting && equip < .5f ? RemoteVisualMath.cycle(elapsed,
                state.action.durationTicks(), state.action == RemoteAction.MINING) : 0;
        return new Frame(displayed, swing, equip, acting);
    }

    private static State state(RemoteToolEntity tool) {
        if (STATES.size() > 512) STATES.clear();
        return STATES.computeIfAbsent(tool.getUuid(), id -> new State(tool));
    }
    private static final class State {
        int sequence, slot;
        RemoteAction action;
        double started, equipped = Double.NEGATIVE_INFINITY;
        boolean predicted;
        ItemStack previous = ItemStack.EMPTY, shown;
        State(RemoteToolEntity tool) {
            sequence = tool.actionSequence(); action = tool.action(); started = tool.actionStartedTick();
            slot = tool.selectedSlot(); shown = tool.stack().copy();
        }
    }
}
