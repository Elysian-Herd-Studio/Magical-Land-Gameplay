package top.elysianherd.magicaland.gameplay.client.levitation;

import net.minecraft.client.MinecraftClient;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.render.RenderLayer;
import top.elysianherd.magicaland.api.client.Appearances;

/** 待命微光与托举星点；实际飞行软边仍由外观包绘制。 */
public final class LevitationReadinessCue {
    private static final Envelope ENVELOPE = new Envelope();
    private static Object world, player;

    private LevitationReadinessCue() { }

    static void reset() { world = player = null; ENVELOPE.reset(); }

    static void render(DrawContext context, float delta) {
        var client = MinecraftClient.getInstance();
        var self = client.player;
        if (self == null || client.world == null || self.getWorld() != client.world
                || !self.isAlive() || self.isRemoved() || self.isSpectator()) { reset(); return; }
        if (world != client.world || player != self) { reset(); world = client.world; player = self; }
        double ticks = self.age + clamp(delta);
        boolean visible = !client.isPaused() && client.currentScreen == null && client.getOverlay() == null
                && client.isWindowFocused() && !client.options.hudHidden
                && client.options.getPerspective().isFirstPerson() && client.getCameraEntity() == self
                && client.gameRenderer.getCamera().getFocusedEntity() == self;
        ENVELOPE.observe(ticks, visible, UnicornLevitationClient.armed(), UnicornLevitationClient.flightCueActive());
        if (!visible || ENVELOPE.amount() <= .001f) return;
        var buffer = context.getVertexConsumers().getBuffer(RenderLayer.getGuiOverlay());
        var matrix = context.getMatrices().peek().getPositionMatrix();
        emit(context.getScaledWindowWidth(), context.getScaledWindowHeight(), ENVELOPE.amount(), ENVELOPE.rim(),
                Appearances.magicColor(self.getUuid()), ENVELOPE.clock(), (x, y, red, green, blue, alpha) ->
                        buffer.vertex(matrix, x, y, 0).color(red, green, blue, alpha).next());
    }

    @FunctionalInterface
    interface VertexSink { void vertex(float x, float y, float red, float green, float blue, float alpha); }

    static void emit(int width, int height, float amount, float rim, int color, double ticks, VertexSink sink) {
        if (width < 1 || height < 1 || sink == null || !Double.isFinite(ticks)) return;
        amount = clamp(amount); rim = clamp(rim);
        float red = .5f + .5f * (color >>> 16 & 255) / 255f;
        float green = .5f + .5f * (color >>> 8 & 255) / 255f;
        float blue = .5f + .5f * (color & 255) / 255f;
        float depth = Math.min(width, height) * .045f;
        for (int band = 0; band < 5 && rim > .001f; band++) {
            float outer = depth * band / 5, inner = depth * (band + 1) / 5;
            float a = rim * .09f * (1 - band / 5f) * (1 - band / 5f);
            float b = rim * .09f * (1 - (band + 1) / 5f) * (1 - (band + 1) / 5f);
            for (int side = 0; side < 4; side++) {
                corner(sink, width, height, side, outer, red, green, blue, a);
                corner(sink, width, height, side, inner, red, green, blue, b);
                corner(sink, width, height, (side + 1) % 4, inner, red, green, blue, b);
                corner(sink, width, height, (side + 1) % 4, outer, red, green, blue, a);
            }
        }
        if (amount <= .001f) return;
        for (int slot = 0; slot < 4; slot++) {
            double phase = (ticks + slot * 60) % 240;
            if (phase < 2 || phase > 26) continue;
            float life = (float) ((phase - 2) / 24), opacity = (float) Math.sin(life * Math.PI);
            float margin = Math.min(width, height) * .027f;
            float x = slot % 2 == 0 ? margin : width - margin;
            float y = height * (slot < 2 ? .27f : .73f) - life * 4;
            float radius = Math.max(1.6f, Math.min(4, Math.min(width, height) * .012f));
            star(sink, x, y, radius, red, green, blue, amount * opacity * .38f);
        }
    }

    private static void corner(VertexSink sink, int width, int height, int corner, float inset,
                               float red, float green, float blue, float alpha) {
        sink.vertex(corner == 0 || corner == 3 ? inset : width - inset,
                corner < 2 ? inset : height - inset, red, green, blue, alpha);
    }

    private static void star(VertexSink sink, float x, float y, float radius,
                             float red, float green, float blue, float alpha) {
        for (int band = 0; band < 3; band++) for (int segment = 0; segment < 32; segment++) {
            float inner = band / 3f, outer = (band + 1) / 3f;
            point(sink, x, y, radius * inner, segment, red, green, blue, alpha * (1 - inner));
            point(sink, x, y, radius * inner, segment + 1, red, green, blue, alpha * (1 - inner));
            point(sink, x, y, radius * outer, segment + 1, red, green, blue, alpha * (1 - outer));
            point(sink, x, y, radius * outer, segment, red, green, blue, alpha * (1 - outer));
        }
    }

    private static void point(VertexSink sink, float x, float y, float radius, int segment,
                              float red, float green, float blue, float alpha) {
        double angle = segment * Math.PI / 16, dx = Math.cos(angle), dy = Math.sin(angle);
        sink.vertex(x + radius * (float) Math.copySign(Math.pow(Math.abs(dx), 2 / .65), dx),
                y + radius * (float) Math.copySign(Math.pow(Math.abs(dy), 2 / .65), dy), red, green, blue, alpha);
    }

    private static float clamp(float value) { return Float.isFinite(value) ? Math.max(0, Math.min(1, value)) : 0; }

    static final class Envelope {
        private double previous = Double.NaN, clock;
        private float amount, rim;
        private boolean enabled;
        void reset() { previous = Double.NaN; clock = amount = rim = 0; enabled = false; }
        void observe(double ticks, boolean visible, boolean armed, boolean active) {
            if (!Double.isFinite(ticks)) { reset(); return; }
            double elapsed = Double.isFinite(previous) ? Math.max(0, Math.min(2, ticks - previous)) : 0;
            previous = ticks;
            if (!visible) return;
            boolean next = armed || active;
            if (next && !enabled) clock = 0;
            else clock += elapsed;
            enabled = next;
            float change = (float) (1 - Math.exp(-elapsed / 3));
            amount += ((next ? 1 : 0) - amount) * change;
            rim = active ? 0 : amount;
        }
        float amount() { return amount; }
        float rim() { return rim; }
        double clock() { return clock; }
    }
}
