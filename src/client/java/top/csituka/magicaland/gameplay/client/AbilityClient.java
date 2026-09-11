package top.csituka.magicaland.gameplay.client;

import java.util.Objects;
import net.minecraft.client.MinecraftClient;
import net.minecraft.text.Text;
import top.csituka.magicaland.gameplay.client.race.RaceClient;
import top.csituka.magicaland.gameplay.client.sense.EarthSenseClient;
import top.csituka.magicaland.gameplay.client.levitation.UnicornLevitationClient;

public final class AbilityClient {
    private static int selected = -1;
    private static long revision;
    private AbilityClient() {}
    public static int count() { return 3; }
    public static boolean available(int slot) {
        return slot == 0 ? RaceClient.canUseUnicornAbility() : slot == 1 ? EarthSenseClient.available()
                : slot == 2 && UnicornLevitationClient.available();
    }
    public static int wheelCount() {
        int visible = 0;
        for (int ability = 0; ability < count(); ability++) if (available(ability)) visible++;
        return visible;
    }
    public static int wheelAbility(int slot) {
        if (slot < 0) return -1;
        for (int ability = 0; ability < count(); ability++) {
            if (available(ability) && slot-- == 0) return ability;
        }
        return -1;
    }
    public static Text wheelEmptyMessage() {
        if (!RaceClient.ready()) return RaceClient.unavailable();
        if (!RaceClient.hasRace()) return RaceClient.text("choose_first");
        return Text.translatable("text.magicaland_gameplay.wheel.empty");
    }
    public static Text name(int slot) {
        return Text.translatable(slot == 0 ? "text.magicaland_gameplay.remote.name" : slot == 1
                ? "text.magicaland_gameplay.sense.name" : "text.magicaland_gameplay.levitation.name");
    }
    public static Text unavailable(int slot) { return slot == 0 ? RaceClient.abilityUnavailable()
            : slot == 1 ? EarthSenseClient.unavailable() : UnicornLevitationClient.unavailable(); }
    public static void select(int slot) { if (available(slot)) selected = slot; }
    public static long revision() { return revision; }
    public static void raceChanged(String previous, String current) {
        if (!Objects.equals(previous, current)) reset();
    }
    public static void reset() { selected = -1; revision++; }
    public static void activate(MinecraftClient client, Text wheelKey) {
        if (RemoteToolClient.active()) { RemoteToolClient.stop(); return; }
        if (EarthSenseClient.active() || EarthSenseClient.pending()) { EarthSenseClient.stop(); return; }
        if (UnicornLevitationClient.armed()) { UnicornLevitationClient.stop(); return; }
        if (selected < 0 || !available(selected)) {
            selected = -1;
            client.player.sendMessage(wheelCount() == 0 ? wheelEmptyMessage()
                    : Text.translatable("text.magicaland_gameplay.ability.select", wheelKey), true);
            return;
        }
        if (selected == 0) RemoteToolClient.startAbility();
        else if (selected == 1) EarthSenseClient.toggle();
        else UnicornLevitationClient.toggle();
    }
}
