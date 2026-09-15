package top.elysianherd.magicaland.gameplay.client;

import java.util.function.DoublePredicate;
import java.util.function.DoubleSupplier;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.gui.screen.Screen;
import net.minecraft.client.gui.tooltip.Tooltip;
import net.minecraft.client.gui.widget.ButtonWidget;
import net.minecraft.text.Text;
import top.elysianherd.magicaland.gameplay.config.GameplayClientConfig;

public final class EarthSenseSettingsScreen extends Screen {
    private final Screen parent;
    private boolean saveFailed;

    public EarthSenseSettingsScreen(Screen parent) {
        super(Text.translatable("text.magicaland_gameplay.sense.settings"));
        this.parent = parent;
    }

    @Override protected void init() {
        option("filter", 60, .8f, GameplayClientConfig::earthSenseFilterStrength,
                value -> GameplayClientConfig.setEarthSenseFilterStrength((float) value));
        option("blur", 88, .35f, GameplayClientConfig::earthSenseBlurStrength,
                value -> GameplayClientConfig.setEarthSenseBlurStrength((float) value));
        option("audio", 116, .6f, GameplayClientConfig::earthSenseAudioStrength,
                value -> GameplayClientConfig.setEarthSenseAudioStrength((float) value));
        int buttonWidth = Math.min(200, width - 32);
        addDrawableChild(ButtonWidget.builder(Text.translatable("gui.done"), button -> close())
                .dimensions((width - buttonWidth) / 2, height - 28, buttonWidth, 20).build());
    }

    private void option(String key, int y, float normal, DoubleSupplier getter, DoublePredicate setter) {
        int buttonWidth = Math.min(310, width - 32);
        var button = addDrawableChild(ButtonWidget.builder(label(key, getter.getAsDouble(), normal), clicked -> {
            double current = getter.getAsDouble();
            double next = current < normal * .25 ? normal * .5 : current < normal * .75 ? normal : 0;
            saveFailed = !setter.test(next);
            clicked.setMessage(label(key, getter.getAsDouble(), normal));
        }).dimensions((width - buttonWidth) / 2, y, buttonWidth, 20).build());
        button.setTooltip(Tooltip.of(Text.translatable("text.magicaland_gameplay.sense." + key + ".hint")));
    }

    private static Text label(String key, double value, float normal) {
        String level = value < normal * .25 ? "off" : value < normal * .75 ? "reduced" : "normal";
        return Text.translatable("text.magicaland_gameplay.sense." + key,
                Text.translatable("text.magicaland_gameplay.sense.filter." + level));
    }

    @Override public void render(DrawContext context, int mouseX, int mouseY, float delta) {
        renderBackground(context);
        context.drawCenteredTextWithShadow(textRenderer, title, width / 2, 28, 0xFFFFFF);
        int textWidth = Math.min(310, width - 32);
        context.drawTextWrapped(textRenderer, Text.translatable(saveFailed
                        ? "text.magicaland_gameplay.config.save_failed" : "text.magicaland_gameplay.sense.settings.hint"),
                (width - textWidth) / 2, 152, textWidth, saveFailed ? 0xFF7777 : 0xBBBBBB);
        super.render(context, mouseX, mouseY, delta);
    }

    @Override public void close() { if (client != null) client.setScreen(parent); }
}
