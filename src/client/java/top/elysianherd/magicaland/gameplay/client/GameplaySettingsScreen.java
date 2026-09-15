package top.elysianherd.magicaland.gameplay.client;

import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.gui.screen.Screen;
import net.minecraft.client.gui.tooltip.Tooltip;
import net.minecraft.client.gui.widget.ButtonWidget;
import net.minecraft.text.Text;
import top.elysianherd.magicaland.gameplay.config.GameplayClientConfig;
import top.elysianherd.magicaland.gameplay.client.race.RaceClient;
import top.elysianherd.magicaland.gameplay.client.race.RaceSelectionScreen;
import top.elysianherd.magicaland.gameplay.client.race.RaceRulesScreen;

public final class GameplaySettingsScreen extends Screen {
    private final Screen parent;
    private boolean saveFailed;
    private ButtonWidget race, rules;

    public GameplaySettingsScreen(Screen parent) {
        super(Text.translatable("text.magicaland_gameplay.config.title"));
        this.parent = parent;
    }

    @Override protected void init() {
        int buttonWidth = Math.min(310, width - 32);
        var view = ButtonWidget.builder(viewText(), button -> {
            saveFailed = !GameplayClientConfig.setAutomaticAbilityThirdPerson(
                    !GameplayClientConfig.automaticAbilityThirdPerson());
            button.setMessage(viewText());
        }).dimensions((width - buttonWidth) / 2, 60, buttonWidth, 20).build();
        view.setTooltip(Tooltip.of(Text.translatable("text.magicaland_gameplay.config.ability_view.hint")));
        addDrawableChild(view);
        race = addDrawableChild(ButtonWidget.builder(RaceClient.text("title"), button -> {
            if (client != null) client.setScreen(new RaceSelectionScreen(this));
        }).dimensions((width - buttonWidth) / 2, 116, buttonWidth, 20).build());
        rules = addDrawableChild(ButtonWidget.builder(RaceClient.text("rules.title"), button -> {
            if (client != null) client.setScreen(new RaceRulesScreen(this));
        }).dimensions((width - buttonWidth) / 2, 140, buttonWidth, 20).build());
        var filter = addDrawableChild(ButtonWidget.builder(Text.translatable("text.magicaland_gameplay.sense.settings"), button -> {
            if (client != null) client.setScreen(new EarthSenseSettingsScreen(this));
        }).dimensions((width - buttonWidth) / 2, 164, buttonWidth, 20).build());
        filter.setTooltip(Tooltip.of(Text.translatable("text.magicaland_gameplay.sense.settings.hint")));
        addDrawableChild(ButtonWidget.builder(Text.translatable("text.magicaland_gameplay.flight.settings"), button -> {
            if (client != null) client.setScreen(new PegasusFlightSettingsScreen(this));
        }).dimensions((width - buttonWidth) / 2, 188, buttonWidth, 20).build());
        updateRaceButtons();
        addDrawableChild(ButtonWidget.builder(Text.translatable("gui.done"), button -> close())
                .dimensions((width - Math.min(200, buttonWidth)) / 2, height - 28, Math.min(200, buttonWidth), 20).build());
    }

    private void updateRaceButtons() {
        race.active = rules.active = RaceClient.connected();
        if (!RaceClient.connected()) {
            race.setTooltip(Tooltip.of(RaceClient.unavailable()));
            rules.setTooltip(Tooltip.of(RaceClient.unavailable()));
        } else {
            race.setTooltip(Tooltip.of(RaceClient.text("entry_hint")));
            rules.setTooltip(Tooltip.of(RaceClient.text("rules.entry_hint")));
        }
    }

    @Override public void tick() { updateRaceButtons(); }

    private static Text viewText() {
        String mode = GameplayClientConfig.automaticAbilityThirdPerson() ? "third_person" : "body";
        return Text.translatable("text.magicaland_gameplay.config.ability_view.name",
                Text.translatable("text.magicaland_gameplay.config.ability_view." + mode));
    }

    @Override public void render(DrawContext context, int mouseX, int mouseY, float delta) {
        renderBackground(context);
        context.drawCenteredTextWithShadow(textRenderer, title, width / 2, 28, 0xFFFFFF);
        int textWidth = Math.min(310, width - 32);
        context.drawTextWrapped(textRenderer, Text.translatable(saveFailed
                        ? "text.magicaland_gameplay.config.save_failed" : "text.magicaland_gameplay.config.ability_view.hint"),
                (width - textWidth) / 2, 92, textWidth, saveFailed ? 0xFF7777 : 0xBBBBBB);
        super.render(context, mouseX, mouseY, delta);
    }

    @Override public void close() {
        if (client != null) client.setScreen(parent);
    }
}
