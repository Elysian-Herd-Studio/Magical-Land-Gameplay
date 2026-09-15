package top.elysianherd.magicaland.gameplay.client.telekinesis;

import net.minecraft.client.MinecraftClient;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.item.ItemStack;
import top.elysianherd.magicaland.api.client.Appearances;
import top.elysianherd.magicaland.gameplay.remote.TelekinesisToken;

public final class TelekinesisSlotRenderer {
    private TelekinesisSlotRenderer() {}

    public static ItemStack replacement(ItemStack stack) {
        if (!TelekinesisToken.isToken(stack)) return stack;
        var client = MinecraftClient.getInstance();
        if (client.player == null || client.world == null || !client.player.isAlive()
                || !client.player.getUuid().equals(TelekinesisToken.owner(stack))) return stack;
        long task = TelekinesisToken.taskId(stack);
        int sourceSlot = TelekinesisToken.sourceSlot(stack);
        if (task <= 0 || sourceSlot < 0 || sourceSlot >= 9) return stack;
        for (var view : TelekinesisClient.views()) {
            if (view.id() != task || view.sourceSlot() != sourceSlot) continue;
            var original = view.renderStack();
            return original.isEmpty() || TelekinesisToken.isToken(original) ? stack : original;
        }
        return stack;
    }

    public static void glow(DrawContext draw, int x, int y) {
        var player = MinecraftClient.getInstance().player;
        if (player == null) return;
        int color = Appearances.magicColor(player.getUuid()) & 0xFFFFFF;
        for (int pad = 2; pad >= 0; pad--) {
            int alpha = pad == 0 ? 125 : pad == 1 ? 52 : 20;
            int tint = alpha << 24 | color;
            int left = x-pad, top = y-pad, right = x+16+pad, bottom = y+16+pad;
            draw.fill(left,top,right,top+1,tint);
            draw.fill(left,bottom-1,right,bottom,tint);
            draw.fill(left,top+1,left+1,bottom-1,tint);
            draw.fill(right-1,top+1,right,bottom-1,tint);
        }
        draw.fill(x+1,y+1,x+15,y+15,0x10000000 | color);
    }
}
