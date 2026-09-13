package top.csituka.magicaland.gameplay.client.pegasus;

import net.minecraft.client.option.Perspective;

final class PegasusFlightPerspective {
    private Perspective original;

    Perspective begin(Perspective current, boolean enabled) {
        if (!enabled || original != null || current == Perspective.THIRD_PERSON_BACK) return current;
        original = current;
        return Perspective.THIRD_PERSON_BACK;
    }
    void manualChange() { original = null; }
    void observe(Perspective current) {
        if (current != Perspective.THIRD_PERSON_BACK) manualChange();
    }
    Perspective finish(Perspective current, boolean otherCamera) {
        Perspective restore = original;
        original = null;
        return !otherCamera && restore != null && current == Perspective.THIRD_PERSON_BACK ? restore : current;
    }
}
