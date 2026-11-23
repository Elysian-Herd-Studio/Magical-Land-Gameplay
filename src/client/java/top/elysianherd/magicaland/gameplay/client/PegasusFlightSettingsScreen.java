package top.elysianherd.magicaland.gameplay.client;

import java.util.function.BooleanSupplier;
import java.util.function.DoublePredicate;
import java.util.function.DoubleSupplier;
import java.util.function.Predicate;
import net.minecraft.client.gui.screen.Screen;
import net.minecraft.client.gui.widget.ButtonWidget;
import net.minecraft.text.Text;
import top.elysianherd.magicaland.gameplay.config.GameplayClientConfig;

public final class PegasusFlightSettingsScreen extends GameplayConfigScreen {
    public PegasusFlightSettingsScreen(Screen parent) {
        super(parent, Text.translatable("text.magicaland_gameplay.flight.settings"));
    }

    @Override protected void initOptions(ConfigList list) {
        list.addOption(ButtonWidget.builder(viewLabel(), button -> {
            setSaveFailed(!GameplayClientConfig.setAutomaticFlightThirdPerson(
                    !GameplayClientConfig.automaticFlightThirdPerson()));
            button.setMessage(viewLabel());
        }).width(optionWidth()).build());
        list.addOption(ButtonWidget.builder(modeLabel(), button -> {
            setSaveFailed(!GameplayClientConfig.setFlightAerobatics(!GameplayClientConfig.flightAerobatics()));
            button.setMessage(modeLabel());
        }).width(optionWidth()).build());
        protection(list, "water_protection", GameplayClientConfig::pegasusWaterProtection,
                GameplayClientConfig::setPegasusWaterProtection);
        protection(list, "ground_protection", GameplayClientConfig::pegasusGroundProtection,
                GameplayClientConfig::setPegasusGroundProtection);
        option(list, "shake", 1, GameplayClientConfig::flightShakeStrength,
                value -> GameplayClientConfig.setFlightShakeStrength((float) value));
        option(list, "wind", .6f, GameplayClientConfig::flightWindVolume,
                value -> GameplayClientConfig.setFlightWindVolume((float) value));
    }

    private static Text viewLabel() {
        return Text.translatable("text.magicaland_gameplay.flight.view",
                Text.translatable("text.magicaland_gameplay.flight.view."
                        + (GameplayClientConfig.automaticFlightThirdPerson() ? "back" : "keep")));
    }

    private static Text modeLabel() {
        return Text.translatable("text.magicaland_gameplay.flight.control",
                Text.translatable("text.magicaland_gameplay.flight.control."
                        + (GameplayClientConfig.flightAerobatics() ? "aerobatic" : "normal")));
    }

    private void protection(ConfigList list, String key, BooleanSupplier getter, Predicate<Boolean> setter) {
        var button = ButtonWidget.builder(protectionLabel(key, getter.getAsBoolean()), clicked -> {
            setSaveFailed(!setter.test(!getter.getAsBoolean()));
            clicked.setMessage(protectionLabel(key, getter.getAsBoolean()));
        }).width(optionWidth()).build();
        list.addOption(button);
    }

    private static Text protectionLabel(String key, boolean enabled) {
        return Text.translatable("text.magicaland_gameplay.flight." + key,
                Text.translatable(enabled ? "options.on" : "options.off"));
    }

    private void option(ConfigList list, String key, float normal, DoubleSupplier getter, DoublePredicate setter) {
        var button = ButtonWidget.builder(label(key, getter.getAsDouble(), normal), clicked -> {
            double current = getter.getAsDouble();
            setSaveFailed(!setter.test(current < normal * .25 ? normal * .5 : current < normal * .75 ? normal : 0));
            clicked.setMessage(label(key, getter.getAsDouble(), normal));
        }).width(optionWidth()).build();
        list.addOption(button);
    }

    private static Text label(String key, double value, float normal) {
        String level = value < normal * .25 ? "off" : value < normal * .75 ? "reduced" : "normal";
        return Text.translatable("text.magicaland_gameplay.flight." + key,
                Text.translatable("text.magicaland_gameplay.sense.filter." + level));
    }
}
