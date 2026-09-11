package top.csituka.magicaland.gameplay.levitation;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.UUID;
import net.minecraft.nbt.NbtCompound;
import net.minecraft.nbt.NbtIo;
import top.csituka.magicaland.gameplay.race.RaceState;

public final class UnicornLevitationPersistenceTest {
    private static int checks;
    public static void main(String[] args) throws Exception {
        UUID owner = new UUID(0, 1), other = new UUID(0, 2);
        var state = new RaceState(); state.race(owner, "magicaland_gameplay:unicorn");
        var progress = state.progress(owner); progress.putInt("FutureGrowth", 37);
        var levitation = new NbtCompound(); levitation.putDouble("Mana", 62.5); levitation.putString("FutureMode", "preserved");
        progress.put("UnicornLevitation", levitation); state.progress(owner, progress);
        progress.putInt("FutureGrowth", -1); levitation.putDouble("Mana", 0);
        check(state.progress(owner).getInt("FutureGrowth") == 37, "setter copy");
        check(state.progress(owner).getCompound("UnicornLevitation").getDouble("Mana") == 62.5, "nested setter copy");
        state.progress(owner).getCompound("UnicornLevitation").putDouble("Mana", 0);
        check(state.progress(owner).getCompound("UnicornLevitation").getDouble("Mana") == 62.5, "getter copy");
        check(state.progress(other).isEmpty(), "other player isolated");
        state.race(owner, "magicaland_gameplay:earth_pony");
        check(state.progress(owner).getCompound("UnicornLevitation").getDouble("Mana") == 62.5, "race change no refill");
        Path path = Files.createTempDirectory(Path.of(args[0]), "mana-").resolve("state.nbt");
        NbtIo.write(state.writeNbt(new NbtCompound()), path.toFile()); var restored = RaceState.read(NbtIo.read(path.toFile()));
        check(restored.progress(owner).equals(state.progress(owner)), "real NBT file roundtrip");
        var changed = restored.progress(owner); var saved = changed.getCompound("UnicornLevitation").copy();
        saved.putDouble("Mana", 61.9); changed.put("UnicornLevitation", saved); restored.progress(owner, changed);
        check(restored.progress(owner).getInt("FutureGrowth") == 37, "other growth retained");
        check(restored.progress(owner).getCompound("UnicornLevitation").getString("FutureMode").equals("preserved"), "future mode retained");
        System.out.println("PASS UnicornLevitationPersistenceTest: " + checks);
    }
    private static void check(boolean value, String label) { checks++; if (!value) throw new AssertionError(label); }
}
