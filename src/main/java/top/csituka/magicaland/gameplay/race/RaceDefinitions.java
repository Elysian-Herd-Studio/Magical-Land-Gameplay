package top.csituka.magicaland.gameplay.race;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

public final class RaceDefinitions {
    public static final String UNICORN_ID = "magicaland_gameplay:unicorn";
    public static final String PEGASUS_ID = "magicaland_gameplay:pegasus";
    public static final String EARTH_PONY_ID = "magicaland_gameplay:earth_pony";
    private static final Map<String, RaceDefinition> DEFINITIONS = new LinkedHashMap<>();

    static {
        register(new RaceDefinition(UNICORN_ID, true, false));
        register(new RaceDefinition(PEGASUS_ID, false, true));
        register(new RaceDefinition(EARTH_PONY_ID, false, false));
    }

    private RaceDefinitions() {}
    public static synchronized void register(RaceDefinition race) {
        if (race == null || DEFINITIONS.containsKey(race.id()) || DEFINITIONS.size() >= 64)
            throw new IllegalArgumentException("Duplicate race or registry capacity exceeded");
        DEFINITIONS.put(race.id(), race);
    }
    public static synchronized List<RaceDefinition> all() { return List.copyOf(DEFINITIONS.values()); }
    public static synchronized RaceDefinition find(String id) { return DEFINITIONS.get(id); }
}
