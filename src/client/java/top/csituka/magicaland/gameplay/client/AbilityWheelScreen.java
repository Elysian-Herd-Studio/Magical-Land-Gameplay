package top.csituka.magicaland.gameplay.client;

import com.mojang.blaze3d.systems.RenderSystem;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.gui.screen.Screen;
import net.minecraft.client.gui.widget.ButtonWidget;
import net.minecraft.client.render.*;
import net.minecraft.text.Text;

public final class AbilityWheelScreen extends Screen {
    private int hovered=-1;
    private int hoveredAbility=-1;
    private long raceRevision=AbilityClient.revision();
    public AbilityWheelScreen() { super(Text.translatable("text.magicaland_gameplay.wheel")); }
    @Override protected void init() {
        addDrawableChild(ButtonWidget.builder(Text.translatable("text.magicaland_gameplay.wheel.settings"), button -> {
            if (client != null) client.setScreen(new GameplaySettingsScreen(null));
        }).dimensions(10, 10, Math.min(170, width - 20), 20).build());
    }
    @Override public boolean shouldPause() { return false; }
    private void refreshRace() {
        if (raceRevision != AbilityClient.revision()) {
            raceRevision = AbilityClient.revision();
            hovered = hoveredAbility = -1;
        }
    }
    @Override public void tick() {
        refreshRace();
        if (!RemoteToolClient.wheelHeld()) {
            if (hoveredAbility>=0) AbilityClient.select(hoveredAbility);
            close();
        }
    }
    @Override public void render(DrawContext context,int mouseX,int mouseY,float delta) {
        refreshRace();
        renderBackground(context);
        double x=mouseX-width/2.0,y=mouseY-height/2.0,r=Math.hypot(x,y);
        hovered=r<30 || r>112 ? -1 : Math.floorMod((int)Math.floor((Math.atan2(y,x)+Math.PI/2+Math.PI/6)/(Math.PI/3)),6);
        hoveredAbility=AbilityClient.wheelAbility(hovered);
        int visibleCount=AbilityClient.wheelCount();
        context.draw();
        RenderSystem.enableBlend(); RenderSystem.defaultBlendFunc(); RenderSystem.disableCull();
        RenderSystem.setShader(GameRenderer::getPositionColorProgram);
        var buffer=Tessellator.getInstance().getBuffer();
        buffer.begin(VertexFormat.DrawMode.QUADS,VertexFormats.POSITION_COLOR);
        var matrix=context.getMatrices().peek().getPositionMatrix();
        for (int sector=0;sector<6;sector++) for (int step=0;step<12;step++) {
            double a=-Math.PI/2-Math.PI/6+sector*Math.PI/3+.025+step*(Math.PI/3-.05)/12;
            double b=a+(Math.PI/3-.05)/12;
            boolean available=AbilityClient.wheelAbility(sector)>=0;
            int red=available?65:35,green=available?130:40,blue=available?175:50;
            if (sector==hovered) { red+=30;green+=30;blue+=30; }
            for (double[] point : new double[][] {{a,32},{a,108},{b,108},{b,32}})
                buffer.vertex(matrix,(float)(width/2.0+Math.cos(point[0])*point[1]),
                        (float)(height/2.0+Math.sin(point[0])*point[1]),0).color(red,green,blue,225).next();
        }
        BufferRenderer.drawWithGlobalProgram(buffer.end()); RenderSystem.enableCull(); RenderSystem.disableBlend();
        for (int slot=0;slot<visibleCount;slot++) {
            double angle=-Math.PI/2+slot*Math.PI/3;
            context.drawCenteredTextWithShadow(textRenderer,AbilityClient.name(AbilityClient.wheelAbility(slot)),
                    width/2+(int)(Math.cos(angle)*72),height/2+(int)(Math.sin(angle)*72)-4,0xffffff);
        }
        context.drawCenteredTextWithShadow(textRenderer,Text.translatable("text.magicaland_gameplay.wheel.cancel"),width/2,height/2-4,0xffffff);
        context.drawCenteredTextWithShadow(textRenderer,visibleCount==0 ? AbilityClient.wheelEmptyMessage()
                : Text.translatable("text.magicaland_gameplay.wheel.hint"),width/2,Math.min(height-30,height/2+126),0xffffff);
        if (visibleCount>0 && hovered>=visibleCount) context.drawCenteredTextWithShadow(textRenderer,Text.translatable("text.magicaland_gameplay.wheel.locked"),width/2,Math.min(height-14,height/2+144),0xaaaaaa);
        super.render(context,mouseX,mouseY,delta);
    }
}
