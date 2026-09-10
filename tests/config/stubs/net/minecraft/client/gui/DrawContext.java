package net.minecraft.client.gui;
import java.util.ArrayList;
import java.util.List;
import net.minecraft.client.font.TextRenderer;
import net.minecraft.text.Text;
public final class DrawContext {
    public record DrawCall(Text text,int x,int y,int width,boolean centered,boolean wrapped){}
    public final List<Text> drawn=new ArrayList<>();
    public final List<DrawCall> calls=new ArrayList<>();
    public void drawCenteredTextWithShadow(TextRenderer renderer,Text text,int x,int y,int color){drawn.add(text);calls.add(new DrawCall(text,x,y,-1,true,false));}
    public void drawTextWrapped(TextRenderer renderer,Text text,int x,int y,int width,int color){drawn.add(text);calls.add(new DrawCall(text,x,y,width,false,true));}
    public void drawTextWithShadow(TextRenderer renderer,Text text,int x,int y,int color){drawn.add(text);calls.add(new DrawCall(text,x,y,-1,false,false));}
    public void fill(int x1,int y1,int x2,int y2,int color){}
    public void drawItem(net.minecraft.item.ItemStack stack,int x,int y){}
}
