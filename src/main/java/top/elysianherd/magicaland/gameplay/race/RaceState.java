package top.elysianherd.magicaland.gameplay.race;

import java.util.HashMap;
import java.util.LinkedHashSet;
import java.util.Map;
import java.util.UUID;
import net.minecraft.nbt.NbtCompound;
import net.minecraft.nbt.NbtElement;
import net.minecraft.nbt.NbtList;
import net.minecraft.nbt.NbtString;
import net.minecraft.server.MinecraftServer;
import net.minecraft.world.PersistentState;
import org.slf4j.LoggerFactory;

public final class RaceState extends PersistentState {
    private final Map<UUID, NbtCompound> players = new HashMap<>();
    private NbtCompound preserved = new NbtCompound();
    private RaceRules rules = RaceRules.defaults();

    public static RaceState get(MinecraftServer server) {
        return server.getOverworld().getPersistentStateManager().getOrCreate(
                RaceState::read, RaceState::new, "magicaland_gameplay_races");
    }

    public RaceRules rules() { return rules; }
    public void rules(RaceRules value) { rules = value; markDirty(); }
    public NbtCompound progress(UUID player) {
        var entry = players.get(player);
        return entry == null ? new NbtCompound() : entry.getCompound("Progress").copy();
    }
    public void progress(UUID player, NbtCompound value) {
        var entry = players.computeIfAbsent(player, key -> new NbtCompound());
        entry.putUuid("Owner", player);
        entry.put("Progress", value.copy());
        markDirty();
    }
    public String race(UUID player) {
        NbtCompound entry = players.get(player);
        if (entry == null) return "";
        String id = entry.getString("Race");
        return RaceDefinition.validId(id) ? id : "magicaland_gameplay:unrecognized_saved_race";
    }
    public void race(UUID player, String id) {
        if (!RaceDefinition.validId(id)) throw new IllegalArgumentException("Invalid race identifier");
        var entry = players.computeIfAbsent(player, key -> new NbtCompound());
        entry.putUuid("Owner", player);
        entry.putString("Race", id);
        if (!entry.contains("Progress", NbtElement.COMPOUND_TYPE)) entry.put("Progress", new NbtCompound());
        markDirty();
    }

    public static RaceState read(NbtCompound nbt) {
        var state = new RaceState();
        state.preserved = nbt.copy();
        NbtList entries = nbt.getList("Players", NbtElement.COMPOUND_TYPE);
        for (int i = 0; i < entries.size(); i++) {
            var entry = entries.getCompound(i);
            if (entry.containsUuid("Owner")) state.players.put(entry.getUuid("Owner"), entry.copy());
        }
        if (nbt.contains("Rules", NbtElement.COMPOUND_TYPE)) {
            var saved = nbt.getCompound("Rules");
            try {
                var enabled = new LinkedHashSet<String>();
                var ids = saved.getList("Enabled", NbtElement.STRING_TYPE);
                for (int i = 0; i < ids.size(); i++) enabled.add(ids.getString(i));
                state.rules = new RaceRules(RaceRules.ChangeMode.valueOf(saved.getString("Mode")),
                        saved.getBoolean("FreeAppearance"), enabled, saved.getLong("Revision"));
            } catch (IllegalArgumentException exception) {
                state.rules = new RaceRules(RaceRules.ChangeMode.LOCKED, false, RaceRules.defaults().enabledRaces(), 0);
                LoggerFactory.getLogger("magicaland_gameplay").warn("Invalid saved race rules; changes locked until an administrator updates them");
            }
        }
        return state;
    }

    @Override public NbtCompound writeNbt(NbtCompound nbt) {
        for (String key : preserved.getKeys()) nbt.put(key, preserved.get(key).copy());
        var entries = new NbtList();
        players.entrySet().stream().sorted(Map.Entry.comparingByKey()).forEach(entry -> entries.add(entry.getValue().copy()));
        nbt.put("Players", entries);
        var saved = preserved.getCompound("Rules").copy();
        saved.putString("Mode", rules.mode().name());
        saved.putBoolean("FreeAppearance", rules.freeAppearance());
        saved.putLong("Revision", rules.revision());
        var ids = new NbtList();
        rules.enabledRaces().stream().sorted().forEach(id -> ids.add(NbtString.of(id)));
        saved.put("Enabled", ids);
        nbt.put("Rules", saved);
        nbt.putInt("Version", Math.max(1, preserved.getInt("Version")));
        return nbt;
    }
}
