package net.minecraft.client.gui;
import java.util.ArrayList;
import java.util.List;
import net.minecraft.client.font.TextRenderer;
import net.minecraft.text.Text;
public final class DrawContext {
    public final List<Text> drawn=new ArrayList<>();
    public void drawCenteredTextWithShadow(TextRenderer renderer,Text text,int x,int y,int color){drawn.add(text);}
    public void drawTextWrapped(TextRenderer renderer,Text text,int x,int y,int width,int color){drawn.add(text);}
}
