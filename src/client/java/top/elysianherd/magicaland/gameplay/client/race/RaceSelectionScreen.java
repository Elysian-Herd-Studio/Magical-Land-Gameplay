package top.elysianherd.magicaland.gameplay.client.race;

import java.util.ArrayList;
import java.util.List;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.gui.screen.ConfirmScreen;
import net.minecraft.client.gui.screen.Screen;
import net.minecraft.client.gui.tooltip.Tooltip;
import net.minecraft.client.gui.widget.ButtonWidget;
import net.minecraft.item.ItemStack;
import net.minecraft.item.Items;
import net.minecraft.text.Text;
import top.elysianherd.magicaland.gameplay.race.RaceDefinition;
import top.elysianherd.magicaland.gameplay.race.RaceDefinitions;

public final class RaceSelectionScreen extends Screen {
    private final Screen parent;
    private final List<ButtonWidget> cards = new ArrayList<>();
    private String selected, requested;
    private ButtonWidget choose;
    private Text result;
    private long requestId, deadline;
    private int cardWidth, left;

    public RaceSelectionScreen(Screen parent) {
        super(RaceClient.text("title"));
        this.parent = parent;
        RaceClient.openedSelection();
        RaceClient.request();
    }

    @Override protected void init() {
        cards.clear();
        List<RaceDefinition> definitions = RaceDefinitions.all();
        cardWidth = Math.min(144, (width - 32 - (definitions.size() - 1) * 8) / definitions.size());
        left = (width - (cardWidth * definitions.size() + (definitions.size() - 1) * 8)) / 2;
        for (int i = 0; i < definitions.size(); i++) {
            String id = definitions.get(i).id();
            var card = ButtonWidget.builder(RaceClient.name(id), button -> {
                selected = id;
                result = null;
                refresh();
            }).dimensions(left + i * (cardWidth + 8), 96, cardWidth, 20).build();
            card.setTooltip(Tooltip.of(details(id)));
            cards.add(addDrawableChild(card));
        }
        int buttonWidth = Math.min(288, width - 32);
        choose = addDrawableChild(ButtonWidget.builder(RaceClient.text("review"), button -> confirm())
                .dimensions((width - buttonWidth) / 2, height - 52, buttonWidth, 20).build());
        addDrawableChild(ButtonWidget.builder(RaceClient.text("later"), button -> close())
                .dimensions((width - buttonWidth) / 2, height - 28, buttonWidth, 20).build());
        refresh();
    }

    private void refresh() {
        if (selected == null && RaceClient.ready()) {
            selected = RaceDefinitions.find(RaceClient.view().ownRace()) != null ? RaceClient.view().ownRace()
                    : RaceDefinitions.all().stream().map(RaceDefinition::id)
                            .filter(id -> RaceClient.view().rules().enabledRaces().contains(id)).findFirst().orElse(null);
        }
        choose.active = requested == null && selected != null && RaceClient.canChoose(selected);
        choose.setMessage(RaceClient.text(requested != null ? "waiting" : "review"));
        for (var card : cards) card.active = requested == null;
    }

    private void confirm() {
        if (client == null || !RaceClient.canChoose(selected)) return;
        String id = selected;
        long revision = RaceClient.view().rules().revision();
        Text policy = RaceClient.text("mode." + RaceClient.view().rules().mode().name().toLowerCase(java.util.Locale.ROOT) + ".hint");
        client.setScreen(new ConfirmScreen(accepted -> {
            client.setScreen(this);
            if (!accepted) return;
            if (RaceClient.choose(id, revision)) {
                requested = id;
                requestId = RaceClient.lastRequestId();
                deadline = System.nanoTime() + 10_000_000_000L;
                result = null;
            } else result = RaceClient.ready() ? RaceClient.text("not_applied") : RaceClient.unavailable();
            refresh();
        }, RaceClient.text("confirm.title", RaceClient.name(id)), RaceClient.text("confirm.body", policy),
                RaceClient.text("confirm.accept"), Text.translatable("gui.cancel")) {
            @Override public boolean shouldPause() { return false; }
        });
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
            } else if (requested.equals(RaceClient.view().ownRace())) {
                result = RaceClient.text("changed", RaceClient.name(requested));
                requested = null;
            } else if (System.nanoTime() >= deadline) {
                requested = null;
                result = RaceClient.text("timeout");
                RaceClient.request();
            }
        }
        refresh();
    }

    private static String suffix(String id) { return id.substring(id.indexOf(':') + 1); }
    private static Text details(String id) { return RaceClient.text("details." + suffix(id)); }
    private static ItemStack icon(String id) {
        if (RaceDefinitions.UNICORN_ID.equals(id)) return new ItemStack(Items.AMETHYST_SHARD);
        if (RaceDefinitions.PEGASUS_ID.equals(id)) return new ItemStack(Items.FEATHER);
        return new ItemStack(Items.WHEAT);
    }

    private Text status() {
        if (!RaceClient.ready()) return RaceClient.unavailable();
        if (requested != null) return RaceClient.text("waiting");
        if (result != null) return result;
        if (selected != null && !RaceClient.view().rules().enabledRaces().contains(selected)) return RaceClient.text("disabled");
        if (RaceClient.hasRace() && RaceDefinitions.find(RaceClient.view().ownRace()) == null) return RaceClient.text("unknown_hint");
        return RaceClient.text("mode." + RaceClient.view().rules().mode().name().toLowerCase(java.util.Locale.ROOT) + ".hint");
    }

    @Override public void render(DrawContext context, int mouseX, int mouseY, float delta) {
        renderBackground(context);
        context.drawCenteredTextWithShadow(textRenderer, title, width / 2, 14, 0xFFFFFF);
        context.drawCenteredTextWithShadow(textRenderer, RaceClient.text("current",
                RaceClient.name(RaceClient.view() == null ? null : RaceClient.view().ownRace())), width / 2, 34, 0xCCCCCC);
        List<RaceDefinition> definitions = RaceDefinitions.all();
        for (int i = 0; i < definitions.size(); i++) {
            String id = definitions.get(i).id();
            int x = left + i * (cardWidth + 8);
            context.fill(x, 54, x + cardWidth, 95, id.equals(selected) ? 0xCC395775 : 0xAA202630);
            context.drawItem(icon(id), x + (cardWidth - 16) / 2, 59);
            String label = RaceClient.ready() && id.equals(RaceClient.view().ownRace()) ? "card.current"
                    : RaceClient.ready() && !RaceClient.view().rules().enabledRaces().contains(id) ? "disabled"
                    : RaceDefinitions.UNICORN_ID.equals(id) || RaceDefinitions.EARTH_PONY_ID.equals(id)
                            ? "card.available" : "card.planned";
            context.drawCenteredTextWithShadow(textRenderer, RaceClient.text(label), x + cardWidth / 2, 81, 0xCCCCCC);
        }
        int textWidth = Math.min(448, width - 32);
        int textX = (width - textWidth) / 2;
        if (selected != null) context.drawTextWrapped(textRenderer, details(selected), textX, 126, textWidth, 0xDDDDDD);
        context.drawTextWrapped(textRenderer, status(), textX, height - 78, textWidth, result != null ? 0xFFCC88 : 0xBBBBBB);
        super.render(context, mouseX, mouseY, delta);
    }

    @Override public boolean shouldPause() { return false; }
    @Override public void close() { if (client != null) client.setScreen(parent); }
}
