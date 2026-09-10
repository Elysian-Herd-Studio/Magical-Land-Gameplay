package top.csituka.magicaland.gameplay.config;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.JsonObject;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.AtomicMoveNotSupportedException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import net.fabricmc.loader.api.FabricLoader;

public final class GameplayClientConfig {
    private static final Gson GSON = new GsonBuilder().setPrettyPrinting().create();
    private static final System.Logger LOGGER = System.getLogger("magicaland_gameplay/config");
    private static final String VIEW = "automaticAbilityThirdPerson";
    private static JsonObject values;

    private GameplayClientConfig() {}

    public static void load() {
        Path file = file();
        values = new JsonObject();
        if (Files.exists(file)) {
            try { values = read(file); }
            catch (IOException | RuntimeException error) {
                LOGGER.log(System.Logger.Level.WARNING, "Could not read gameplay client settings", error);
            }
            return;
        }
        // Only the retired experimental preference is imported; appearance files stay untouched.
        Path legacy = FabricLoader.getInstance().getConfigDir().resolve("magicaland/config.json");
        if (Files.isRegularFile(legacy)) {
            try { values.addProperty(VIEW, view(read(legacy))); }
            catch (IOException | RuntimeException error) {
                LOGGER.log(System.Logger.Level.WARNING, "Could not import the previous ability view setting", error);
            }
        }
        values.addProperty(VIEW, view(values));
        store(values);
    }

    public static boolean automaticAbilityThirdPerson() {
        if (values == null) load();
        return view(values);
    }

    public static boolean setAutomaticAbilityThirdPerson(boolean enabled) {
        if (values == null) load();
        JsonObject next = values.deepCopy();
        next.addProperty(VIEW, enabled);
        if (!store(next)) return false;
        values = next;
        return true;
    }

    private static boolean view(JsonObject object) {
        var value = object.get(VIEW);
        return value == null || !value.isJsonPrimitive() || !value.getAsJsonPrimitive().isBoolean()
                || value.getAsBoolean();
    }

    private static JsonObject read(Path path) throws IOException {
        try (var reader = Files.newBufferedReader(path, StandardCharsets.UTF_8)) {
            JsonObject object = GSON.fromJson(reader, JsonObject.class);
            if (object == null) throw new IOException("Settings must be a JSON object");
            return object;
        }
    }

    private static Path file() {
        return FabricLoader.getInstance().getConfigDir().resolve("magicaland-gameplay/client.json");
    }

    private static boolean store(JsonObject next) {
        Path file = file(), temporary = file.resolveSibling("client.json.tmp");
        try {
            Files.createDirectories(file.getParent());
            Files.writeString(temporary, GSON.toJson(next), StandardCharsets.UTF_8);
            try { Files.move(temporary, file, StandardCopyOption.REPLACE_EXISTING, StandardCopyOption.ATOMIC_MOVE); }
            catch (AtomicMoveNotSupportedException ignored) {
                Files.move(temporary, file, StandardCopyOption.REPLACE_EXISTING);
            }
            return true;
        } catch (IOException | RuntimeException error) {
            LOGGER.log(System.Logger.Level.WARNING, "Could not save gameplay client settings", error);
            return false;
        } finally {
            try { Files.deleteIfExists(temporary); }
            catch (IOException | RuntimeException ignored) {}
        }
    }
}
