package top.csituka.magicaland.gameplay.client.race;

import java.util.HashSet;
import top.csituka.magicaland.gameplay.race.RaceRules;

public final class RaceRulesDraft {
    private RaceRules base, draft;

    public void reset(RaceRules rules) { base = rules; draft = rules; }
    public RaceRules value() { return draft; }
    public boolean dirty() { return draft != null && !sameValues(base, draft); }
    public boolean stale(RaceRules current) { return base != null && base.revision() != current.revision(); }
    public void receive(RaceRules current) { if (draft == null || !dirty()) reset(current); }
    public void cycleMode() {
        var modes = RaceRules.ChangeMode.values();
        draft = new RaceRules(modes[(draft.mode().ordinal() + 1) % modes.length], draft.freeAppearance(),
                draft.enabledRaces(), draft.revision());
    }
    public void toggleAppearance() {
        draft = new RaceRules(draft.mode(), !draft.freeAppearance(), draft.enabledRaces(), draft.revision());
    }
    public boolean toggleRace(String id) {
        var enabled = new HashSet<>(draft.enabledRaces());
        if (enabled.contains(id) && enabled.size() == 1) return false;
        if (!enabled.remove(id)) enabled.add(id);
        draft = new RaceRules(draft.mode(), draft.freeAppearance(), enabled, draft.revision());
        return true;
    }
    public static boolean sameValues(RaceRules a, RaceRules b) {
        return a != null && b != null && a.mode() == b.mode() && a.freeAppearance() == b.freeAppearance()
                && a.enabledRaces().equals(b.enabledRaces());
    }
}
