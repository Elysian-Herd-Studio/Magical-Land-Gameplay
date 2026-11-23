package top.elysianherd.magicaland.gameplay.client;

import java.util.function.DoublePredicate;
import java.util.function.DoubleSupplier;
import net.minecraft.client.gui.screen.Screen;
import net.minecraft.client.gui.tooltip.Tooltip;
import net.minecraft.client.gui.widget.ButtonWidget;
import net.minecraft.text.Text;
import top.elysianherd.magicaland.gameplay.config.GameplayClientConfig;

public final class EarthSenseSettingsScreen extends GameplayConfigScreen {
    public EarthSenseSettingsScreen(Screen parent) {
        super(parent, Text.translatable("text.magicaland_gameplay.sense.settings"));
    }

    @Override protected void initOptions(ConfigList list) {
        option(list, "filter", .8f, GameplayClientConfig::earthSenseFilterStrength,
                value -> GameplayClientConfig.setEarthSenseFilterStrength((float) value));
        option(list, "blur", .35f, GameplayClientConfig::earthSenseBlurStrength,
                value -> GameplayClientConfig.setEarthSenseBlurStrength((float) value));
        option(list, "audio", .6f, GameplayClientConfig::earthSenseAudioStrength,
                value -> GameplayClientConfig.setEarthSenseAudioStrength((float) value));
    }

    private void option(ConfigList list, String key, float normal, DoubleSupplier getter, DoublePredicate setter) {
        var button = ButtonWidget.builder(label(key, getter.getAsDouble(), normal), clicked -> {
            double current = getter.getAsDouble();
            double next = current < normal * .25 ? normal * .5 : current < normal * .75 ? normal : 0;
            setSaveFailed(!setter.test(next));
            clicked.setMessage(label(key, getter.getAsDouble(), normal));
        }).width(optionWidth()).build();
        button.setTooltip(Tooltip.of(Text.translatable("text.magicaland_gameplay.sense." + key + ".hint")));
        list.addOption(button);
    }

    private static Text label(String key, double value, float normal) {
        String level = value < normal * .25 ? "off" : value < normal * .75 ? "reduced" : "normal";
        return Text.translatable("text.magicaland_gameplay.sense." + key,
                Text.translatable("text.magicaland_gameplay.sense.filter." + level));
    }
}
