package top.csituka.magicaland.gameplay.race;

public record RaceDefinition(String id, boolean hasHorn, boolean hasWings) {
    public RaceDefinition {
        if (!validId(id)) throw new IllegalArgumentException("Invalid race identifier");
    }

    public String translationKey() { return "race." + id.replace(':', '.'); }

    public static boolean validId(String id) {
        return id != null && id.length() <= 128 && id.matches("[a-z0-9_.-]+:[a-z0-9/._-]+");
    }
}
