package top.csituka.magicaland.gameplay.client;

import net.minecraft.client.MinecraftClient;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.render.RenderLayer;
import net.minecraft.client.render.VertexConsumer;
import net.minecraft.text.Text;
import net.minecraft.util.Identifier;
import org.joml.Matrix4f;
import top.csituka.magicaland.api.client.Appearances;
import top.csituka.magicaland.gameplay.client.echo.SpiritualEchoRenderer;
import top.csituka.magicaland.gameplay.remote.RemoteCapabilities;

public final class RemoteToolHud {
    private static final Identifier WIDGETS = new Identifier("minecraft","textures/gui/widgets.png");
    private static float visualOcclusion;
    private static double lastFrame;
    private RemoteToolHud() {}

    public static void init() { RemoteAim.init(); SpiritualEchoRenderer.init(); }
    public static void reset() { visualOcclusion=0; lastFrame=0; RemoteAim.clear(); SpiritualEchoRenderer.reset(); }

    public static void renderWorldOverlay(DrawContext context,float delta) {
        var client=MinecraftClient.getInstance();
        if (!RemoteToolClient.active() || client.player==null || client.world==null) return;
        double now=RemoteToolClient.seconds();
        double elapsed=lastFrame==0 ? 0 : now-lastFrame;
        lastFrame=now;
        if (RemoteToolClient.returning() && RemoteToolClient.camera()==null) visualOcclusion=0;
        else if (!RemoteToolClient.returning())
            visualOcclusion=RemoteVisualMath.approachOcclusion(visualOcclusion,RemoteToolClient.occlusion(),elapsed);
        float recall=RemoteToolClient.returnOpacity();
        boolean first=client.options.getPerspective().isFirstPerson() && RemoteToolClient.camera()!=null;
        int color=Appearances.magicColor(client.player.getUuid());
        int width=context.getScaledWindowWidth(), height=context.getScaledWindowHeight();
        context.draw();
        var vertices=context.getVertexConsumers().getBuffer(RenderLayer.getGuiOverlay());
        var matrix=context.getMatrices().peek().getPositionMatrix();
        int columns=40, rows=24;
        for (int row=0; row<rows; row++) for (int col=0; col<columns; col++) {
            float x0=width*col/(float)columns, x1=width*(col+1)/(float)columns;
            float y0=height*row/(float)rows, y1=height*(row+1)/(float)rows;
            vertex(vertices,matrix,x0,y0,width,height,color,first,recall);
            vertex(vertices,matrix,x0,y1,width,height,color,first,recall);
            vertex(vertices,matrix,x1,y1,width,height,color,first,recall);
            vertex(vertices,matrix,x1,y0,width,height,color,first,recall);
        }
        context.draw();
        if (recall==0) SpiritualEchoRenderer.render(visualOcclusion);
        if (first && RemoteToolClient.stack(RemoteToolClient.selectedSlot()).isEmpty() && visualOcclusion<.95f && recall==0)
            renderEmptyFlow(context,color,now,delta);
    }

    private static void vertex(VertexConsumer vertices,Matrix4f matrix,float x,float y,int width,int height,
            int color,boolean first,float recall) {
        float nx=2*x/width-1, ny=2*y/height-1;
        float black=Math.max(recall,RemoteVisualMath.vignette(nx,ny,visualOcclusion));
        float radius=(float)Math.sqrt(nx*nx+ny*ny);
        float tint=first ? .035f*RemoteVisualMath.smooth((radius-.82f)/.50f) : 0;
        float alpha=black+tint*(1-black);
        float glow=alpha==0 ? 0 : tint*(1-black)/alpha;
        vertices.vertex(matrix,x,y,0).color((int)((color>>16&255)*glow),(int)((color>>8&255)*glow),
                (int)((color&255)*glow),Math.round(alpha*255)).next();
    }

    private static void renderEmptyFlow(DrawContext context,int color,double now,float delta) {
        int width=context.getScaledWindowWidth(), height=context.getScaledWindowHeight();
        var tool=RemoteToolClient.camera();
        var frame=RemoteHeldAnimation.sample(tool,RemoteToolClient.visualStack(tool),RemoteToolClient.visualSlot(tool),delta);
        float pulse=frame.acting() ? (float)Math.sin(frame.swing()*Math.PI) : 0;
        for (int side : new int[] {-1,1}) for (int i=0; i<3; i++) {
            double phase=(now*.42+i/3.0)%1;
            int x=(int)(width*(.5+side*.24)+Math.sin(phase*Math.PI)*side*7);
            int y=(int)(height*(.86-phase*.14));
            int alpha=(int)((24+40*pulse)*Math.sin(phase*Math.PI)*(1-visualOcclusion));
            context.fill(RenderLayer.getGuiOverlay(),x,y,x+2+(int)(pulse*2),y+5+(int)(pulse*5),(alpha<<24)|color);
        }
    }

    public static void renderHud(DrawContext context,float delta) {
        var client=MinecraftClient.getInstance();
        if (!RemoteToolClient.active() || client.player==null || client.options.hudHidden) return;
        int width=context.getScaledWindowWidth(), height=context.getScaledWindowHeight();
        int capacity=RemoteToolClient.capacity(), hotbarWidth=RemoteVisualMath.hotbarWidth(capacity);
        int x=(width-hotbarWidth)/2, y=height-22;
        context.drawTexture(WIDGETS,x,y,0,0,1,22);
        context.drawTexture(WIDGETS,x+1,y,1,0,capacity*20,22);
        context.drawTexture(WIDGETS,x+hotbarWidth-1,y,181,0,1,22);
        context.drawTexture(WIDGETS,x-1+RemoteToolClient.selectedSlot()*20,y-1,0,22,24,22);
        for (int slot=0; slot<capacity; slot++) {
            var stack=RemoteToolClient.stack(slot);
            context.drawItem(stack,x+3+slot*20,y+3);
            context.drawItemInSlot(client.textRenderer,stack,x+3+slot*20,y+3);
        }
        int color=Appearances.magicColor(client.player.getUuid());
        context.drawTextWithShadow(client.textRenderer,Text.translatable("text.magicaland_gameplay.remote.marker"),
                x-4-client.textRenderer.getWidth(Text.translatable("text.magicaland_gameplay.remote.marker")),y+7,color);
        if (RemoteToolClient.waiting()) {
            context.drawCenteredTextWithShadow(client.textRenderer,Text.translatable("text.magicaland_gameplay.remote.wait",RemoteToolClient.returnKey()),width/2,y-23,0xcceeff);
            return;
        }
        var selected=RemoteToolClient.stack(RemoteToolClient.selectedSlot());
        if (!selected.isEmpty()) context.drawCenteredTextWithShadow(client.textRenderer,selected.getName(),width/2,y-13,0xffffff);
        String hint=RemoteToolClient.returning() ? "text.magicaland_gameplay.remote.returning"
                : visualOcclusion>=.999f ? (SpiritualEchoRenderer.unavailable()
                    ? "text.magicaland_gameplay.remote.echo_unavailable" : "text.magicaland_gameplay.remote.blind")
                : "text.magicaland_gameplay.remote.controls";
        Text hintText=hint.endsWith(".controls")
                ? Text.translatable(hint,RemoteToolClient.dropKey(),RemoteToolClient.returnKey())
                : Text.translatable(hint,RemoteToolClient.returnKey());
        if (hint.endsWith(".controls") && client.textRenderer.getWidth(hintText)>width-16)
            hintText=Text.translatable("text.magicaland_gameplay.remote.controls_short",RemoteToolClient.dropKey(),RemoteToolClient.returnKey());
        int hintY=y-37;
        for (var line : client.textRenderer.wrapLines(hintText,width-16)) {
            context.drawCenteredTextWithShadow(client.textRenderer,line,width/2,hintY,0xcceeff);
            hintY+=10;
        }
        var camera=RemoteToolClient.camera();
        if (camera!=null && camera.getPos().distanceTo(client.player.getEyePos())>RemoteCapabilities.MAX_RANGE*.85)
            context.drawCenteredTextWithShadow(client.textRenderer,Text.translatable("text.magicaland_gameplay.remote.edge",(int)RemoteCapabilities.MAX_RANGE),width/2,12,0xffcc66);
        if (RemoteToolClient.controlling()) {
            var aim=client.options.getPerspective().isFirstPerson() ? new RemoteAimMath.Point(.5f,.5f) : RemoteAim.point();
            if (aim==null) return;
            float visibility=RemoteCapabilities.SPIRITUAL_ECHO ? 1 : 1-RemoteVisualMath.vignette(aim.x()*2-1,aim.y()*2-1,visualOcclusion);
            if (visibility<.05f) return;
            int cx=Math.round(aim.x()*width), cy=Math.round(aim.y()*height);
            int reticle=(Math.round(221*visibility)<<24)|0xffffff;
            context.fill(cx,cy-3,cx+1,cy-2,reticle);
            context.fill(cx-3,cy,cx-2,cy+1,reticle);
            context.fill(cx+3,cy,cx+4,cy+1,reticle);
            context.fill(cx,cy+3,cx+1,cy+4,reticle);
            float cooldown=camera==null ? 1 : camera.attackCooldown();
            if (!selected.isEmpty() && cooldown<1) {
                context.fill(cx-4,cy+8,cx+4,cy+9,(Math.round(102*visibility)<<24)|0x333333);
                context.fill(cx-4,cy+8,cx-4+(int)(8*cooldown),cy+9,(Math.round(255*visibility)<<24)|0xdddddd);
            }
        }
    }
}
