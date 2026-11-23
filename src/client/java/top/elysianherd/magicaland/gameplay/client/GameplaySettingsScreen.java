package top.elysianherd.magicaland.gameplay.client;

import net.minecraft.client.gui.screen.Screen;
import net.minecraft.client.gui.tooltip.Tooltip;
import net.minecraft.client.gui.widget.ButtonWidget;
import net.minecraft.text.Text;
import top.elysianherd.magicaland.gameplay.config.GameplayClientConfig;
import top.elysianherd.magicaland.gameplay.client.race.RaceClient;
import top.elysianherd.magicaland.gameplay.client.race.RaceSelectionScreen;
import top.elysianherd.magicaland.gameplay.client.race.RaceRulesScreen;

public final class GameplaySettingsScreen extends GameplayConfigScreen {
    private ButtonWidget race, rules;

    public GameplaySettingsScreen(Screen parent) {
        super(parent, Text.translatable("text.magicaland_gameplay.config.title"));
    }

    @Override protected void initOptions(ConfigList list) {
        var view = ButtonWidget.builder(viewText(), button -> {
            setSaveFailed(!GameplayClientConfig.setAutomaticAbilityThirdPerson(
                    !GameplayClientConfig.automaticAbilityThirdPerson()));
            button.setMessage(viewText());
        }).width(optionWidth()).build();
        view.setTooltip(Tooltip.of(Text.translatable("text.magicaland_gameplay.config.ability_view.hint")));
        list.addOption(view);
        race = ButtonWidget.builder(RaceClient.text("title"), button -> {
            if (client != null) client.setScreen(new RaceSelectionScreen(this));
        }).width(optionWidth()).build();
        list.addOption(race);
        rules = ButtonWidget.builder(RaceClient.text("rules.title"), button -> {
            if (client != null) client.setScreen(new RaceRulesScreen(this));
        }).width(optionWidth()).build();
        list.addOption(rules);
        var sense = ButtonWidget.builder(Text.translatable("text.magicaland_gameplay.sense.settings"), button -> {
            if (client != null) client.setScreen(new EarthSenseSettingsScreen(this));
        }).width(optionWidth()).build();
        sense.setTooltip(Tooltip.of(Text.translatable("text.magicaland_gameplay.sense.settings.hint")));
        list.addOption(sense);
        list.addOption(ButtonWidget.builder(Text.translatable("text.magicaland_gameplay.flight.settings"), button -> {
            if (client != null) client.setScreen(new PegasusFlightSettingsScreen(this));
        }).width(optionWidth()).build());
        updateRaceButtons();
    }

    private void updateRaceButtons() {
        boolean connected = RaceClient.connected();
        race.active = rules.active = connected;
        if (!connected) {
            race.setTooltip(Tooltip.of(RaceClient.unavailable()));
            rules.setTooltip(Tooltip.of(RaceClient.unavailable()));
        } else {
            race.setTooltip(Tooltip.of(RaceClient.text("entry_hint")));
            rules.setTooltip(Tooltip.of(RaceClient.text("rules.entry_hint")));
        }
    }

    @Override public void tick() {
        updateRaceButtons();
    }

    private static Text viewText() {
        String mode = GameplayClientConfig.automaticAbilityThirdPerson() ? "third_person" : "body";
        return Text.translatable("text.magicaland_gameplay.config.ability_view.name",
                Text.translatable("text.magicaland_gameplay.config.ability_view." + mode));
    }
}
