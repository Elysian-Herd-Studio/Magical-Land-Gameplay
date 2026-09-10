package top.csituka.magicaland.gameplay.client;

import com.mojang.blaze3d.systems.RenderSystem;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.gui.screen.Screen;
import net.minecraft.client.render.*;
import net.minecraft.text.Text;

public final class AbilityWheelScreen extends Screen {
    private int hovered=-1;
    public AbilityWheelScreen() { super(Text.translatable("text.magicaland_gameplay.wheel")); }
    @Override public boolean shouldPause() { return false; }
    @Override public void tick() {
        if (!RemoteToolClient.wheelHeld()) {
            if (hovered==0) RemoteToolClient.select();
            close();
        }
    }
    @Override public void render(DrawContext context,int mouseX,int mouseY,float delta) {
        renderBackground(context);
        double x=mouseX-width/2.0,y=mouseY-height/2.0,r=Math.hypot(x,y);
        hovered=r<30 || r>112 ? -1 : Math.floorMod((int)Math.floor((Math.atan2(y,x)+Math.PI/2+Math.PI/6)/(Math.PI/3)),6);
        context.draw();
        RenderSystem.enableBlend(); RenderSystem.defaultBlendFunc(); RenderSystem.disableCull();
        RenderSystem.setShader(GameRenderer::getPositionColorProgram);
        var buffer=Tessellator.getInstance().getBuffer();
        buffer.begin(VertexFormat.DrawMode.QUADS,VertexFormats.POSITION_COLOR);
        var matrix=context.getMatrices().peek().getPositionMatrix();
        for (int sector=0;sector<6;sector++) for (int step=0;step<12;step++) {
            double a=-Math.PI/2-Math.PI/6+sector*Math.PI/3+.025+step*(Math.PI/3-.05)/12;
            double b=a+(Math.PI/3-.05)/12;
            int red=sector==0?65:35,green=sector==0?130:40,blue=sector==0?175:50;
            if (sector==hovered) { red+=30;green+=30;blue+=30; }
            for (double[] point : new double[][] {{a,32},{a,108},{b,108},{b,32}})
                buffer.vertex(matrix,(float)(width/2.0+Math.cos(point[0])*point[1]),
                        (float)(height/2.0+Math.sin(point[0])*point[1]),0).color(red,green,blue,225).next();
        }
        BufferRenderer.drawWithGlobalProgram(buffer.end()); RenderSystem.enableCull(); RenderSystem.disableBlend();
        context.drawCenteredTextWithShadow(textRenderer,Text.translatable("text.magicaland_gameplay.remote.name"),width/2,height/2-76,0xffffff);
        context.drawCenteredTextWithShadow(textRenderer,Text.translatable("text.magicaland_gameplay.wheel.cancel"),width/2,height/2-4,0xffffff);
        context.drawCenteredTextWithShadow(textRenderer,Text.translatable("text.magicaland_gameplay.wheel.hint"),width/2,height/2+126,0xffffff);
        if (hovered>0) context.drawCenteredTextWithShadow(textRenderer,Text.translatable("text.magicaland_gameplay.wheel.locked"),width/2,height/2+144,0xaaaaaa);
    }
}
