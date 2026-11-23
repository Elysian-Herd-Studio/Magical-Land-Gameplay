package top.elysianherd.magicaland.gameplay.client;

import net.fabricmc.loader.api.FabricLoader;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.gui.screen.Screen;
import net.minecraft.client.gui.widget.ButtonWidget;
import net.minecraft.text.Text;

public abstract class GameplayConfigScreen extends Screen {
    private static final int SIDEBAR_WIDTH = 100;
    private static final int PADDING = 10;
    private static final int LIST_TOP = 50;
    private static final int LIST_BOTTOM_MARGIN = 40;
    private static final int ITEM_HEIGHT = 28;
    private final Screen parent;
    private final Text version;
    private ConfigList list;
    private boolean saveFailed;

    protected GameplayConfigScreen(Screen parent, Text title) {
        super(title);
        this.parent = parent;
        this.version = Text.literal(FabricLoader.getInstance().getModContainer("magicaland_gameplay")
                .map(container -> container.getMetadata().getName() + " v"
                        + container.getMetadata().getVersion().getFriendlyString() + " · Fabric")
                .orElse("Magical Land: Gameplay · Fabric"));
    }

    @Override protected void init() {
        list = new ConfigList(MinecraftClient.getInstance(), width - SIDEBAR_WIDTH - PADDING * 2,
                height, LIST_TOP, height - LIST_BOTTOM_MARGIN, ITEM_HEIGHT);
        list.setLeftPos(SIDEBAR_WIDTH + PADDING);
        initOptions(list);
        addDrawableChild(list);
        addDrawableChild(ButtonWidget.builder(Text.translatable("gui.done"), button -> close())
                .dimensions(10, height - 30, 80, 20).build());
    }

    protected abstract void initOptions(ConfigList list);

    protected int optionWidth() {
        return Math.min(250, width - SIDEBAR_WIDTH - PADDING * 2 - 20);
    }

    protected void setSaveFailed(boolean failed) {
        saveFailed = failed;
    }

    @Override public void render(DrawContext context, int mouseX, int mouseY, float delta) {
        renderBackground(context);
        context.getMatrices().push();
        context.getMatrices().translate(20.0f, 20.0f, 0.0f);
        context.getMatrices().scale(0.5f, 0.5f, 1.0f);
        context.drawTextWithShadow(textRenderer, version, 0, 0, 0xAAAAAA);
        context.getMatrices().pop();
        context.drawTextWithShadow(textRenderer, title, 20, 28, 0xFFFFFF);
        super.render(context, mouseX, mouseY, delta);
        if (saveFailed) {
            context.drawTextWrapped(textRenderer, Text.translatable("text.magicaland_gameplay.config.save_failed"),
                    10, 46, SIDEBAR_WIDTH - 20, 0xFF7777);
        }
    }

    @Override public boolean mouseScrolled(double mouseX, double mouseY, double amount) {
        if (super.mouseScrolled(mouseX, mouseY, amount)) return true;
        return list != null && list.isMouseOver(mouseX, mouseY) && list.mouseScrolled(mouseX, mouseY, amount);
    }

    @Override public void close() {
        if (client != null) client.setScreen(parent);
    }
}
