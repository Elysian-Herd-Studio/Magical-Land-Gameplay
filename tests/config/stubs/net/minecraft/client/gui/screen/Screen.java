package net.minecraft.client.gui.screen;
import java.util.ArrayList;
import java.util.List;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.font.TextRenderer;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.gui.widget.ButtonWidget;
import net.minecraft.text.Text;
public class Screen {
    protected MinecraftClient client;
    protected TextRenderer textRenderer=new TextRenderer();
    protected final Text title;
    public int width,height,additions;
    public final List<ButtonWidget> widgets=new ArrayList<>();
    public Screen(Text title){this.title=title;}
    protected void init(){}
    public void testInit(MinecraftClient client,int width,int height){
        this.client=client;this.width=width;this.height=height;widgets.clear();init();
    }
    protected <T extends ButtonWidget> T addDrawableChild(T button){widgets.add(button);additions++;return button;}
    public void renderBackground(DrawContext context){}
    public void render(DrawContext context,int mouseX,int mouseY,float delta){}
    public void close(){if(client!=null)client.setScreen(null);}
    public void tick(){}
    public boolean shouldPause(){return true;}
}
