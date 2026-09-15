package top.elysianherd.magicaland.gameplay.race;

public final class RacePolicy {
    public enum ChangeCause { SELECT, POTION, ADMIN }
    private RacePolicy() {}

    public static String denial(String current, String target, RaceRules rules, ChangeCause cause, boolean safe) {
        if (RaceDefinitions.find(target) == null) return "unknown";
        if (target.equals(current)) return "same";
        if (cause != ChangeCause.ADMIN && !rules.enabledRaces().contains(target)) return "disabled";
        if (cause != ChangeCause.ADMIN && current != null && !current.isEmpty()) {
            if (RaceDefinitions.find(current) == null) return "locked";
            if (rules.mode() == RaceRules.ChangeMode.LOCKED) return "locked";
            if (cause == ChangeCause.SELECT && rules.mode() != RaceRules.ChangeMode.FREE) return "potion_required";
        }
        if (!safe) return "stance";
        return null;
    }

    public static String rulesDenial(boolean canManage, RaceRules current, RaceRules requested) {
        if (!canManage) return "permission";
        if (requested.revision() != current.revision() || current.revision() == Long.MAX_VALUE) return "stale";
        if (requested.enabledRaces().stream().anyMatch(id -> RaceDefinitions.find(id) == null)) return "invalid_rules";
        return null;
    }
}
