package top.csituka.magicaland.gameplay.race;

import java.util.Objects;
import java.util.Set;
import java.util.stream.Collectors;

public record RaceRules(ChangeMode mode, boolean freeAppearance, Set<String> enabledRaces, long revision) {
    public enum ChangeMode { POTION, FREE, LOCKED }

    public RaceRules {
        Objects.requireNonNull(mode);
        enabledRaces = Set.copyOf(enabledRaces);
        if (enabledRaces.isEmpty() || enabledRaces.size() > 64 || revision < 0
                || enabledRaces.stream().anyMatch(id -> !RaceDefinition.validId(id)))
            throw new IllegalArgumentException("Invalid race rules");
    }

    public static RaceRules defaults() {
        return new RaceRules(ChangeMode.POTION, false,
                RaceDefinitions.all().stream().map(RaceDefinition::id).collect(Collectors.toSet()), 0);
    }

    public RaceRules nextRevision() {
        return new RaceRules(mode, freeAppearance, enabledRaces, Math.addExact(revision, 1));
    }
}
