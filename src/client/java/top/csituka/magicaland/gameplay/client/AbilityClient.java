package top.csituka.magicaland.gameplay.client;

import java.util.Objects;
import net.minecraft.client.MinecraftClient;
import net.minecraft.text.Text;
import top.csituka.magicaland.gameplay.client.race.RaceClient;
import top.csituka.magicaland.gameplay.client.sense.EarthSenseClient;
import top.csituka.magicaland.gameplay.client.levitation.UnicornLevitationClient;
import top.csituka.magicaland.gameplay.client.telekinesis.TelekinesisClient;

public final class AbilityClient {
    private static int selected = -1;
    private static long revision;
    private AbilityClient() {}
    public static int count() { return 4; }
    public static int selected() { return selected; }
    public static boolean available(int slot) {
        return switch (slot) {
            case 0 -> RaceClient.canUseUnicornAbility();
            case 1 -> EarthSenseClient.available();
            case 2 -> UnicornLevitationClient.available();
            case 3 -> TelekinesisClient.available();
            default -> false;
        };
    }
    public static boolean enabled(int slot) {
        return switch (slot) {
            case 0 -> RemoteToolClient.active();
            case 1 -> EarthSenseClient.active() || EarthSenseClient.pending();
            case 2 -> UnicornLevitationClient.armed();
            case 3 -> TelekinesisClient.enabled() || TelekinesisClient.pending();
            default -> false;
        };
    }
    public static int wheelCount() {
        int visible = 0;
        for (int ability = 0; ability < count(); ability++) if (available(ability)) visible++;
        return visible;
    }
    public static Text wheelEmptyMessage() {
        if (!RaceClient.ready()) return RaceClient.unavailable();
        if (!RaceClient.hasRace()) return RaceClient.text("choose_first");
        return Text.translatable("text.magicaland_gameplay.wheel.empty");
    }
    public static Text name(int slot) {
        return Text.translatable(switch (slot) {
            case 0 -> "text.magicaland_gameplay.remote.name";
            case 1 -> "text.magicaland_gameplay.sense.name";
            case 2 -> "text.magicaland_gameplay.levitation.name";
            case 3 -> "text.magicaland_gameplay.telekinesis.name";
            default -> "text.magicaland_gameplay.wheel.locked";
        });
    }
    public static Text unavailable(int slot) { return slot == 0 ? RaceClient.abilityUnavailable()
            : slot == 1 ? EarthSenseClient.unavailable() : slot == 2 ? UnicornLevitationClient.unavailable()
            : RaceClient.canUseUnicornAbility() ? Text.translatable("text.magicaland_gameplay.telekinesis.unavailable")
            : RaceClient.abilityUnavailable(); }
    public static void select(int slot) {
        if (available(slot) && selected != slot) {
            TelekinesisClient.cancelInput();
            selected = slot;
        }
    }
    public static long revision() { return revision; }
    public static void raceChanged(String previous, String current) {
        if (!Objects.equals(previous, current)) reset();
    }
    public static void reset() { TelekinesisClient.cancelInput(); selected = -1; revision++; }
    public static void stop(int slot) {
        switch (slot) {
            case 0 -> RemoteToolClient.stop();
            case 1 -> EarthSenseClient.stop();
            case 2 -> UnicornLevitationClient.stop();
            case 3 -> TelekinesisClient.stop();
            default -> { }
        }
    }
    public static void activate(MinecraftClient client, Text wheelKey) {
        if (client.player == null) return;
        if (selected < 0 || !available(selected)) {
            selected = -1;
            client.player.sendMessage(wheelCount() == 0 ? wheelEmptyMessage()
                    : Text.translatable("text.magicaland_gameplay.ability.select", wheelKey), true);
            return;
        }
        switch (selected) {
            case 0 -> { if (RemoteToolClient.active()) RemoteToolClient.stop(); else RemoteToolClient.startAbility(); }
            case 1 -> EarthSenseClient.toggle();
            case 2 -> UnicornLevitationClient.toggle();
            default -> { }
        }
    }
}
