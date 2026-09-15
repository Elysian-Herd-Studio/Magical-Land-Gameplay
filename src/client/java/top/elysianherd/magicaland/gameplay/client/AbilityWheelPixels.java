package top.elysianherd.magicaland.gameplay.client;

import java.util.ArrayList;
import java.util.List;

record AbilityWheelPixels(int step, List<Span> spans) {
    static AbilityWheelPixels create(AbilityWheelLayout layout) {
        int step = layout.radius() >= 60 ? 2 : 1;
        if (layout.count() == 0) return new AbilityWheelPixels(step, List.of());
        int left = (int) Math.floor(layout.x() - layout.radius());
        int top = (int) Math.floor(layout.y() - layout.radius());
        int columns = (int) Math.ceil((layout.x() + layout.radius() - left) / step);
        int rows = (int) Math.ceil((layout.y() + layout.radius() - top) / step);
        int[][] slots = new int[rows][columns];
        for (int y = 0; y < rows; y++) for (int x = 0; x < columns; x++)
            slots[y][x] = layout.slotAt(left + (x + .5) * step, top + (y + .5) * step);

        var spans = new ArrayList<Span>();
        for (int y = 0; y < rows; y++) for (int x = 0; x < columns;) {
            int slot = slots[y][x];
            if (slot < 0) { x++; continue; }
            int shade = shade(slots, x, y, slot), end = x + 1;
            while (end < columns && slots[y][end] == slot && shade(slots, end, y, slot) == shade) end++;
            spans.add(new Span(left + x * step, top + y * step, (end - x) * step, step, slot, shade));
            x = end;
        }
        return new AbilityWheelPixels(step, List.copyOf(spans));
    }

    private static int shade(int[][] slots, int x, int y, int slot) {
        if (at(slots, x - 1, y) != slot || at(slots, x + 1, y) != slot
                || at(slots, x, y - 1) != slot || at(slots, x, y + 1) != slot) return 0;
        if (at(slots, x - 2, y) != slot || at(slots, x, y - 2) != slot) return 1;
        if (at(slots, x + 2, y) != slot || at(slots, x, y + 2) != slot) return 2;
        return 3;
    }

    private static int at(int[][] slots, int x, int y) {
        return y < 0 || y >= slots.length || x < 0 || x >= slots[0].length ? -1 : slots[y][x];
    }

    static int color(int shade, boolean hovered, boolean selected) {
        return switch (shade) {
            case 0 -> selected ? 0xFFF0F0F0 : 0xFF191919;
            case 1 -> hovered ? 0xFFD2D2D2 : 0xFFB0B0B0;
            case 2 -> hovered ? 0xFF555555 : 0xFF393939;
            default -> hovered ? 0xFF8B8B8B : 0xFF686868;
        };
    }

    record Span(int x, int y, int width, int height, int slot, int shade) {}
}
