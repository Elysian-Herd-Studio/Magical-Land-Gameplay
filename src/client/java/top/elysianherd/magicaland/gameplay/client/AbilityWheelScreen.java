package top.elysianherd.magicaland.gameplay.client;

import com.mojang.blaze3d.systems.RenderSystem;
import java.util.Arrays;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.gui.screen.Screen;
import net.minecraft.client.gui.widget.ButtonWidget;
import net.minecraft.client.render.BufferBuilder;
import net.minecraft.client.render.BufferRenderer;
import net.minecraft.client.render.GameRenderer;
import net.minecraft.client.render.Tessellator;
import net.minecraft.client.render.VertexFormat;
import net.minecraft.client.render.VertexFormats;
import net.minecraft.text.Text;
import org.joml.Matrix4f;
import org.lwjgl.glfw.GLFW;

public final class AbilityWheelScreen extends Screen {
    private AbilityWheelLayout layout;
    private AbilityWheelPixels pixels;
    private int[] abilities = new int[0];
    private int hovered = -1;
    private long raceRevision = AbilityClient.revision();
    private boolean keyboardSelection, selectionChanged;
    private double lastMouseX = Double.NaN, lastMouseY = Double.NaN;

    public AbilityWheelScreen() { super(Text.translatable("text.magicaland_gameplay.wheel")); }

    @Override protected void init() {
        abilities = visibleAbilities();
        layout = AbilityWheelLayout.fit(width, height, abilities.length);
        pixels = AbilityWheelPixels.create(layout);
        hovered = -1; keyboardSelection = false;
        addDrawableChild(ButtonWidget.builder(Text.translatable("text.magicaland_gameplay.wheel.settings_short"),
                button -> client.setScreen(new GameplaySettingsScreen(null))).dimensions(8, 8, 58, 20).build());
    }

    private static int[] visibleAbilities() {
        return java.util.stream.IntStream.range(0, AbilityClient.count())
                .filter(id -> AbilityClient.available(id) || AbilityClient.enabled(id)).toArray();
    }

    private int ability(int slot) { return slot >= 0 && slot < abilities.length ? abilities[slot] : -1; }

    private void refreshAbilities() {
        if (raceRevision == AbilityClient.revision() && Arrays.equals(abilities, visibleAbilities())) return;
        raceRevision = AbilityClient.revision(); selectionChanged = true;
        clearChildren(); init();
    }

    @Override public boolean shouldPause() { return false; }

    @Override public void tick() {
        refreshAbilities();
        if (client == null || client.player == null || !client.player.isAlive() || !client.isWindowFocused()) {
            close(); return;
        }
        if (!RemoteToolClient.wheelHeld()) choose();
    }

    private void choose() {
        if (!selectionChanged && client != null && client.isWindowFocused()) AbilityClient.select(ability(hovered));
        close();
    }

    private void trackMouse(double x, double y) {
        if (x == lastMouseX && y == lastMouseY) return;
        lastMouseX = x; lastMouseY = y; keyboardSelection = false; selectionChanged = false;
        hovered = layout == null ? -1 : layout.slotAt(x, y);
    }

    @Override public void mouseMoved(double x, double y) { trackMouse(x, y); super.mouseMoved(x, y); }

    @Override public void render(DrawContext draw, int mouseX, int mouseY, float delta) {
        refreshAbilities(); trackMouse(mouseX, mouseY); renderBackground(draw);
        if (abilities.length == 0) {
            draw.drawCenteredTextWithShadow(textRenderer, AbilityClient.wheelEmptyMessage(), width / 2, height / 2, 0xDFDFDF);
            super.render(draw, mouseX, mouseY, delta); return;
        }
        draw.draw();
        RenderSystem.enableBlend(); RenderSystem.defaultBlendFunc(); RenderSystem.disableCull();
        RenderSystem.setShader(GameRenderer::getPositionColorProgram);
        var buffer = Tessellator.getInstance().getBuffer();
        buffer.begin(VertexFormat.DrawMode.QUADS, VertexFormats.POSITION_COLOR);
        var matrix = draw.getMatrices().peek().getPositionMatrix();
        for (var span : pixels.spans()) rectangle(buffer, matrix, span, pixels.step(), 0x70000000);
        for (var span : pixels.spans()) rectangle(buffer, matrix, span, 0,
                AbilityWheelPixels.color(span.shade(), span.slot() == hovered, ability(span.slot()) == AbilityClient.selected()));
        BufferRenderer.drawWithGlobalProgram(buffer.end());
        RenderSystem.enableCull(); RenderSystem.disableBlend();
        for (int slot = 0; slot < abilities.length; slot++) {
            int id = ability(slot), x = layout.iconX(slot), y = layout.iconY(slot);
            AbilityIcons.ability(draw, id, x, y - 8, AbilityClient.available(id));
            String label = textRenderer.trimToWidth(AbilityClient.name(id).getString(), (int) Math.min(100, layout.radius() * .94));
            draw.drawCenteredTextWithShadow(textRenderer, label, x + 8, y + 13, 0xFFFFFF);
            if (AbilityClient.enabled(id)) {
                draw.fill(x + 17, y - 8, x + 23, y - 2, 0xFF191919);
                draw.fill(x + 18, y - 7, x + 22, y - 3, 0xFF79BF65);
                draw.fill(x + 18, y - 7, x + 21, y - 6, 0xFFC1E9AB);
            }
        }
        int selected = ability(hovered) >= 0 ? ability(hovered) : AbilityClient.selected();
        if (selected >= 0) {
            AbilityIcons.ability(draw, selected, (int) layout.x() - 8, (int) layout.y() - 12, AbilityClient.available(selected));
            if (AbilityClient.enabled(selected)) draw.drawCenteredTextWithShadow(textRenderer,
                    Text.translatable("text.magicaland_gameplay.wheel.active"), width / 2, (int) layout.y() + 10, 0xA9EBBB);
        }
        super.render(draw, mouseX, mouseY, delta);
        if (ability(hovered) >= 0 && AbilityClient.enabled(ability(hovered))) draw.drawTooltip(textRenderer,
                Text.translatable("text.magicaland_gameplay.wheel.stop"), mouseX, mouseY);
    }

    private static void rectangle(BufferBuilder buffer, Matrix4f matrix, AbilityWheelPixels.Span span, int offset, int color) {
        int x = span.x() + offset, y = span.y() + offset;
        buffer.vertex(matrix, x, y + span.height(), 0).color(color).next();
        buffer.vertex(matrix, x + span.width(), y + span.height(), 0).color(color).next();
        buffer.vertex(matrix, x + span.width(), y, 0).color(color).next();
        buffer.vertex(matrix, x, y, 0).color(color).next();
    }

    @Override public boolean mouseClicked(double x, double y, int button) {
        refreshAbilities(); trackMouse(x, y);
        if (super.mouseClicked(x, y, button)) return true;
        int id = ability(layout.slotAt(x, y));
        if (button == GLFW.GLFW_MOUSE_BUTTON_RIGHT) {
            if (AbilityClient.enabled(id)) AbilityClient.stop(id);
            close(); return true;
        }
        if (button == GLFW.GLFW_MOUSE_BUTTON_LEFT) {
            choose();
            return true;
        }
        return false;
    }

    @Override public boolean keyPressed(int key, int scanCode, int modifiers) {
        if (key >= GLFW.GLFW_KEY_1 && key < GLFW.GLFW_KEY_1 + abilities.length) hovered = key - GLFW.GLFW_KEY_1;
        else if (abilities.length > 0 && (key == GLFW.GLFW_KEY_LEFT || key == GLFW.GLFW_KEY_UP
                || key == GLFW.GLFW_KEY_RIGHT || key == GLFW.GLFW_KEY_DOWN)) {
            int direction = key == GLFW.GLFW_KEY_LEFT || key == GLFW.GLFW_KEY_UP ? -1 : 1;
            hovered = hovered < 0 ? 0 : Math.floorMod(hovered + direction, abilities.length);
        } else if ((key == GLFW.GLFW_KEY_ENTER || key == GLFW.GLFW_KEY_KP_ENTER) && keyboardSelection) {
            choose();
            return true;
        } else {
            if (key == GLFW.GLFW_KEY_TAB) { hovered = -1; keyboardSelection = false; }
            return super.keyPressed(key, scanCode, modifiers);
        }
        keyboardSelection = true; selectionChanged = false; setFocused(null);
        if (client != null) client.getNarratorManager().narrate(AbilityClient.name(ability(hovered)));
        return true;
    }
}
