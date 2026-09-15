package top.elysianherd.magicaland.gameplay.client;

import net.minecraft.client.gui.DrawContext;
import net.minecraft.item.ItemStack;
import net.minecraft.item.Items;

final class AbilityIcons {
    private static final ItemStack[] ABILITIES = {new ItemStack(Items.ENDER_EYE),
            new ItemStack(Items.SCULK_SENSOR), new ItemStack(Items.FEATHER), new ItemStack(Items.AMETHYST_SHARD)};

    private AbilityIcons() { }

    static void ability(DrawContext draw, int ability, int x, int y, boolean available) {
        if (ability < 0 || ability >= ABILITIES.length) return;
        draw.drawItem(ABILITIES[ability], x, y);
        if (!available) draw.fill(x, y, x + 16, y + 16, 0xA0202430);
    }
}
