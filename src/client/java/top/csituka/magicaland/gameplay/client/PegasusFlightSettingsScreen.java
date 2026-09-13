package top.csituka.magicaland.gameplay.client;

import java.util.function.BooleanSupplier;
import java.util.function.DoublePredicate;
import java.util.function.DoubleSupplier;
import java.util.function.Predicate;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.gui.screen.Screen;
import net.minecraft.client.gui.widget.ButtonWidget;
import net.minecraft.text.Text;
import top.csituka.magicaland.gameplay.config.GameplayClientConfig;

public final class PegasusFlightSettingsScreen extends Screen {
    private final Screen parent;
    private boolean saveFailed;
    public PegasusFlightSettingsScreen(Screen parent) {
        super(Text.translatable("text.magicaland_gameplay.flight.settings")); this.parent = parent;
    }
    @Override protected void init() {
        int width = Math.min(310, this.width - 32);
        addDrawableChild(ButtonWidget.builder(viewLabel(), button -> {
            saveFailed = !GameplayClientConfig.setAutomaticFlightThirdPerson(
                    !GameplayClientConfig.automaticFlightThirdPerson());
            button.setMessage(viewLabel());
        }).dimensions((this.width - width) / 2, 48, width, 20).build());
        addDrawableChild(ButtonWidget.builder(modeLabel(), button -> {
            saveFailed = !GameplayClientConfig.setFlightAerobatics(!GameplayClientConfig.flightAerobatics());
            button.setMessage(modeLabel());
        }).dimensions((this.width - width) / 2, 72, width, 20).build());
        protection("water_protection", 96, GameplayClientConfig::pegasusWaterProtection,
                GameplayClientConfig::setPegasusWaterProtection);
        protection("ground_protection", 120, GameplayClientConfig::pegasusGroundProtection,
                GameplayClientConfig::setPegasusGroundProtection);
        option("shake", 144, 1, GameplayClientConfig::flightShakeStrength,
                value -> GameplayClientConfig.setFlightShakeStrength((float) value));
        option("wind", 168, .6f, GameplayClientConfig::flightWindVolume,
                value -> GameplayClientConfig.setFlightWindVolume((float) value));
        int size = Math.min(200, this.width - 32);
        addDrawableChild(ButtonWidget.builder(Text.translatable("gui.done"), button -> close())
                .dimensions((this.width - size) / 2, height - 28, size, 20).build());
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
    private void protection(String key, int y, BooleanSupplier getter, Predicate<Boolean> setter) {
        int size = Math.min(310, width - 32);
        addDrawableChild(ButtonWidget.builder(protectionLabel(key, getter.getAsBoolean()), button -> {
            saveFailed = !setter.test(!getter.getAsBoolean());
            button.setMessage(protectionLabel(key, getter.getAsBoolean()));
        }).dimensions((width - size) / 2, y, size, 20).build());
    }
    private static Text protectionLabel(String key, boolean enabled) {
        return Text.translatable("text.magicaland_gameplay.flight." + key,
                Text.translatable(enabled ? "options.on" : "options.off"));
    }
    private void option(String key, int y, float normal, DoubleSupplier getter, DoublePredicate setter) {
        int size = Math.min(310, width - 32);
        addDrawableChild(ButtonWidget.builder(label(key, getter.getAsDouble(), normal), button -> {
            double current = getter.getAsDouble();
            saveFailed = !setter.test(current < normal * .25 ? normal * .5 : current < normal * .75 ? normal : 0);
            button.setMessage(label(key, getter.getAsDouble(), normal));
        }).dimensions((width - size) / 2, y, size, 20).build());
    }
    private static Text label(String key, double value, float normal) {
        String level = value < normal * .25 ? "off" : value < normal * .75 ? "reduced" : "normal";
        return Text.translatable("text.magicaland_gameplay.flight." + key,
                Text.translatable("text.magicaland_gameplay.sense.filter." + level));
    }
    @Override public void render(DrawContext context, int mouseX, int mouseY, float delta) {
        renderBackground(context);
        context.drawCenteredTextWithShadow(textRenderer, title, width / 2, 28, 0xFFFFFF);
        if (saveFailed) context.drawCenteredTextWithShadow(textRenderer,
                Text.translatable("text.magicaland_gameplay.config.save_failed"), width / 2, height - 42, 0xFF7777);
        super.render(context, mouseX, mouseY, delta);
    }
    @Override public void close() { if (client != null) client.setScreen(parent); }
}
