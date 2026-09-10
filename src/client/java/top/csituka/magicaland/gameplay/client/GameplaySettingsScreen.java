package top.csituka.magicaland.gameplay.client;

import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.gui.screen.Screen;
import net.minecraft.client.gui.tooltip.Tooltip;
import net.minecraft.client.gui.widget.ButtonWidget;
import net.minecraft.text.Text;
import top.csituka.magicaland.gameplay.config.GameplayClientConfig;

public final class GameplaySettingsScreen extends Screen {
    private final Screen parent;
    private boolean saveFailed;

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
        addDrawableChild(ButtonWidget.builder(Text.translatable("gui.done"), button -> close())
                .dimensions((width - Math.min(200, buttonWidth)) / 2, height - 28, Math.min(200, buttonWidth), 20).build());
    }

    private static Text viewText() {
        String mode = GameplayClientConfig.automaticAbilityThirdPerson() ? "third_person" : "body";
        return Text.translatable("text.magicaland_gameplay.config.ability_view.name",
                Text.translatable("text.magicaland_gameplay.config.ability_view." + mode));
    }

    @Override public void render(DrawContext context, int mouseX, int mouseY, float delta) {
        renderBackground(context);
        context.drawCenteredTextWithShadow(textRenderer, title, width / 2, 28, 0xFFFFFF);
        int textWidth = Math.min(310, width - 32);
        context.drawTextWrapped(textRenderer, Text.translatable("text.magicaland_gameplay.config.ability_view.hint"),
                (width - textWidth) / 2, 92, textWidth, 0xBBBBBB);
        if (saveFailed) context.drawCenteredTextWithShadow(textRenderer,
                Text.translatable("text.magicaland_gameplay.config.save_failed"), width / 2, height - 48, 0xFF7777);
        super.render(context, mouseX, mouseY, delta);
    }

    @Override public void close() {
        if (client != null) client.setScreen(parent);
    }
}
