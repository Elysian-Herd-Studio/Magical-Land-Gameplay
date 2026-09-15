package top.elysianherd.magicaland.gameplay.client.race;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.gui.screen.Screen;
import net.minecraft.client.gui.tooltip.Tooltip;
import net.minecraft.client.gui.widget.ButtonWidget;
import net.minecraft.text.Text;
import top.elysianherd.magicaland.gameplay.race.RaceDefinitions;
import top.elysianherd.magicaland.gameplay.race.RaceRules;

public final class RaceRulesScreen extends Screen {
    private final Screen parent;
    private final RaceRulesDraft draft = new RaceRulesDraft();
    private final List<ButtonWidget> raceButtons = new ArrayList<>();
    private ButtonWidget mode, appearance, save, reload;
    private RaceRules requested;
    private Text result;
    private long receivedVersion = -1, requestId, deadline;

    public RaceRulesScreen(Screen parent) {
        super(RaceClient.text("rules.title"));
        this.parent = parent;
        if (RaceClient.ready()) draft.reset(RaceClient.view().rules());
        RaceClient.request();
    }

    @Override protected void init() {
        raceButtons.clear();
        int buttonWidth = Math.min(360, width - 32);
        int x = (width - buttonWidth) / 2;
        mode = addDrawableChild(ButtonWidget.builder(Text.empty(), button -> {
            draft.cycleMode(); result = null; refresh();
        }).dimensions(x, 52, buttonWidth, 20).build());
        appearance = addDrawableChild(ButtonWidget.builder(Text.empty(), button -> {
            draft.toggleAppearance(); result = null; refresh();
        }).dimensions(x, 76, buttonWidth, 20).build());
        appearance.setTooltip(Tooltip.of(RaceClient.text("rules.appearance.hint")));
        int count = RaceDefinitions.all().size();
        int smallWidth = (buttonWidth - (count - 1) * 4) / count;
        for (int i = 0; i < count; i++) {
            String id = RaceDefinitions.all().get(i).id();
            raceButtons.add(addDrawableChild(ButtonWidget.builder(Text.empty(), button -> {
                result = draft.toggleRace(id) ? null : RaceClient.text("rules.keep_one");
                refresh();
            }).dimensions(x + i * (smallWidth + 4), 113, smallWidth, 20).build()));
        }
        int halfWidth = (buttonWidth - 8) / 2;
        save = addDrawableChild(ButtonWidget.builder(RaceClient.text("rules.save"), button -> save())
                .dimensions(x, height - 52, halfWidth, 20).build());
        reload = addDrawableChild(ButtonWidget.builder(RaceClient.text("rules.reload"), button -> {
            if (RaceClient.ready()) draft.reset(RaceClient.view().rules());
            result = null; RaceClient.request(); refresh();
        }).dimensions(x + halfWidth + 8, height - 52, halfWidth, 20).build());
        reload.setTooltip(Tooltip.of(RaceClient.text("rules.reload.hint")));
        addDrawableChild(ButtonWidget.builder(Text.translatable("gui.done"), button -> close())
                .dimensions(x, height - 28, buttonWidth, 20).build());
        refresh();
    }

    private void save() {
        if (draft.value() == null || !draft.dirty()) return;
        RaceRules rules = draft.value();
        if (RaceClient.saveRules(rules)) {
            requested = rules;
            requestId = RaceClient.lastRequestId();
            deadline = System.nanoTime() + 10_000_000_000L;
            result = null;
        } else result = RaceClient.ready() ? RaceClient.text("not_applied") : RaceClient.unavailable();
        refresh();
    }

    @Override public void tick() {
        if (requested != null) {
            var reply = RaceClient.result(requestId);
            if (!RaceClient.ready()) {
                requested = null;
                result = RaceClient.unavailable();
            } else if (reply != null && !reply.denial().isEmpty()) {
                requested = null;
                result = RaceClient.text("error." + reply.denial());
            } else if (RaceRulesDraft.sameValues(requested, RaceClient.view().rules())) {
                draft.reset(RaceClient.view().rules());
                result = RaceClient.text("rules_saved");
                requested = null;
            } else if (System.nanoTime() >= deadline) {
                requested = null;
                result = RaceClient.text("timeout");
                RaceClient.request();
            }
        }
        if (receivedVersion != RaceClient.version()) {
            receivedVersion = RaceClient.version();
            if (RaceClient.ready() && requested == null) draft.receive(RaceClient.view().rules());
        }
        refresh();
    }

    private void refresh() {
        RaceRules rules = draft.value();
        boolean editable = RaceClient.canManage() && rules != null && requested == null;
        mode.active = appearance.active = editable;
        save.active = editable && draft.dirty() && !draft.stale(RaceClient.view().rules());
        reload.active = RaceClient.ready() && requested == null;
        save.setMessage(RaceClient.text(requested == null ? "rules.save" : "waiting"));
        mode.setMessage(RaceClient.text("rules.mode", rules == null ? RaceClient.text("loading")
                : RaceClient.text("mode." + rules.mode().name().toLowerCase(Locale.ROOT))));
        if (rules != null) mode.setTooltip(Tooltip.of(RaceClient.text("mode." + rules.mode().name().toLowerCase(Locale.ROOT) + ".hint")));
        appearance.setMessage(RaceClient.text("rules.appearance", onOff(rules != null && rules.freeAppearance())));
        for (int i = 0; i < raceButtons.size(); i++) {
            String id = RaceDefinitions.all().get(i).id();
            ButtonWidget button = raceButtons.get(i);
            boolean enabled = rules != null && rules.enabledRaces().contains(id);
            button.active = editable;
            button.setMessage(RaceClient.text("rules.race", RaceClient.name(id), onOff(enabled)));
            button.setTooltip(Tooltip.of(RaceClient.text("rules.race.hint")));
        }
    }

    private static Text onOff(boolean enabled) { return Text.translatable(enabled ? "options.on" : "options.off"); }
    private Text status() {
        if (!RaceClient.ready()) return RaceClient.unavailable();
        if (requested != null) return RaceClient.text("waiting");
        if (result != null) return result;
        if (draft.stale(RaceClient.view().rules())) return RaceClient.text("rules.stale");
        return RaceClient.text(RaceClient.canManage() ? "rules.edit_hint" : "rules.read_only");
    }

    @Override public void render(DrawContext context, int mouseX, int mouseY, float delta) {
        renderBackground(context);
        context.drawCenteredTextWithShadow(textRenderer, title, width / 2, 14, 0xFFFFFF);
        context.drawCenteredTextWithShadow(textRenderer, RaceClient.text(RaceClient.canManage() ? "rules.admin" : "rules.read_only"),
                width / 2, 34, 0xBBBBBB);
        int textWidth = Math.min(360, width - 32);
        int x = (width - textWidth) / 2;
        context.drawTextWithShadow(textRenderer, RaceClient.text("rules.enabled"), x, 101, 0xBBBBBB);
        context.drawTextWrapped(textRenderer, RaceClient.text("rules.appearance.hint"), x, 140, textWidth, 0xBBBBBB);
        context.drawTextWrapped(textRenderer, status(), x, height - 78, textWidth, 0xFFCC88);
        super.render(context, mouseX, mouseY, delta);
    }

    @Override public boolean shouldPause() { return false; }
    @Override public void close() { if (client != null) client.setScreen(parent); }
}
