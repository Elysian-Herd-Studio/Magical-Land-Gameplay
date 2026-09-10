package top.csituka.magicaland.gameplay.client;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;
import net.fabricmc.api.ClientModInitializer;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.fabricmc.fabric.api.client.keybinding.v1.KeyBindingHelper;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayConnectionEvents;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking;
import net.fabricmc.fabric.api.client.rendering.v1.EntityRendererRegistry;
import net.fabricmc.fabric.api.client.rendering.v1.HudRenderCallback;
import net.fabricmc.fabric.api.networking.v1.PacketByteBufs;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.option.KeyBinding;
import net.minecraft.client.option.Perspective;
import net.minecraft.client.util.InputUtil;
import net.minecraft.entity.Entity;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.text.Text;
import net.minecraft.screen.slot.Slot;
import net.minecraft.util.Identifier;
import org.lwjgl.glfw.GLFW;
import top.csituka.magicaland.client.api.HeldItemVisibility;
import top.csituka.magicaland.gameplay.remote.RemoteToolEntity;
import top.csituka.magicaland.gameplay.remote.RemoteToolMath;
import top.csituka.magicaland.gameplay.remote.RemoteToolServer;
import top.csituka.magicaland.gameplay.remote.RemoteCargoInventory;

public final class RemoteToolClient implements ClientModInitializer {
    private static KeyBinding wheel, activate;
    private static int entityId=-1, waiting;
    private static boolean selected, stopping;
    private static boolean attackQueued, useQueued;
    private static RemoteToolEntity camera;
    private static Perspective previousPerspective;
    private static final Map<UUID, float[]> FACING=new HashMap<>();
    private static final Map<UUID, RemoteToolEntity> TOOLS=new HashMap<>();
    private static final RemoteCargoInventory CARGO=new RemoteCargoInventory();
    private static final Slot CARGO_SLOT=new Slot(CARGO,0,0,0);
    private static final Identifier SLOT_TEXTURE=new Identifier("minecraft","textures/gui/container/generic_54.png");
    public static boolean active() { return entityId >= 0 || waiting > 0; }
    public static boolean controlling() { return camera != null && !camera.isRemoved() && !stopping; }
    public static boolean blockBodyInput() {
        return active() || MinecraftClient.getInstance().currentScreen instanceof AbilityWheelScreen;
    }
    @Override public void onInitializeClient() {
        wheel=KeyBindingHelper.registerKeyBinding(new KeyBinding("key.magicaland_gameplay.wheel",InputUtil.Type.KEYSYM,GLFW.GLFW_KEY_R,"category.magicaland_gameplay"));
        activate=KeyBindingHelper.registerKeyBinding(new KeyBinding("key.magicaland_gameplay.activate",InputUtil.Type.KEYSYM,GLFW.GLFW_KEY_V,"category.magicaland_gameplay"));
        EntityRendererRegistry.register(RemoteToolServer.TYPE,RemoteToolRenderer::new);
        RemoteBodyRenderer.register();
        top.csituka.magicaland.client.api.ExternalGaze.setProvider(TOOLS::get);
        HeldItemVisibility.setExternalMainHand(uuid -> {
            RemoteToolEntity tool=TOOLS.get(uuid); return tool != null && !tool.isRemoved() && tool.carriesOriginal();
        });
        ClientPlayNetworking.registerGlobalReceiver(RemoteToolServer.STATE,(client,handler,buf,sender) -> {
            int id=buf.readInt();
            var cargo=buf.readItemStack();
            client.execute(() -> {
                CARGO.setStack(0,cargo);
                if (id < 0) { restore(client); return; }
                if (stopping) { sendStop(id); return; }
                if (entityId != id) waiting=40;
                entityId=id;
            });
        });
        ClientPlayConnectionEvents.DISCONNECT.register((handler,client) -> { restore(client); TOOLS.clear(); FACING.clear(); selected=false; });
        ClientTickEvents.END_CLIENT_TICK.register(RemoteToolClient::tick);
        HudRenderCallback.EVENT.register((context,delta) -> {
            var client=MinecraftClient.getInstance();
            if (!active() || client.player == null || client.options.hudHidden) return;
            String key=camera == null ? "text.magicaland_gameplay.remote.wait" : "text.magicaland_gameplay.remote.controls";
            context.drawCenteredTextWithShadow(client.textRenderer,Text.translatable(key,activate.getBoundKeyLocalizedText()),
                    context.getScaledWindowWidth()/2,context.getScaledWindowHeight()-65,0xcceeff);
            if (camera != null) {
                double distance=camera.getPos().distanceTo(client.player.getEyePos());
                if (distance > 13) context.drawCenteredTextWithShadow(client.textRenderer,
                        Text.translatable("text.magicaland_gameplay.remote.edge"),context.getScaledWindowWidth()/2,35,0xffcc66);
                int x=context.getScaledWindowWidth()/2-9, y=context.getScaledWindowHeight()-105;
                context.drawTexture(SLOT_TEXTURE,x,y,7,17,18,18);
                var stack=CARGO_SLOT.getStack();
                context.drawItem(stack,x+1,y+1);
                context.drawItemInSlot(client.textRenderer,stack,x+1,y+1);
                int capacity=stack.isEmpty()?RemoteCargoInventory.CAPACITY:Math.min(RemoteCargoInventory.CAPACITY,stack.getMaxCount());
                context.drawCenteredTextWithShadow(client.textRenderer,
                        Text.translatable("text.magicaland_gameplay.remote.cargo",stack.getCount(),capacity),x+9,y+22,0xcceeff);
            }
        });
    }
    public static boolean held(KeyBinding binding) {
        var key=KeyBindingHelper.getBoundKeyOf(binding);
        long window=MinecraftClient.getInstance().getWindow().getHandle();
        if (key.getCode() < 0) return false;
        return key.getCategory()==InputUtil.Type.MOUSE ? GLFW.glfwGetMouseButton(window,key.getCode())==GLFW.GLFW_PRESS
                : InputUtil.isKeyPressed(window,key.getCode());
    }
    public static boolean wheelHeld() { return held(wheel); }
    public static void select() { selected=true; }
    private static void tick(MinecraftClient client) {
        if (client.world == null || client.player == null) { restore(client); TOOLS.clear(); FACING.clear(); return; }
        TOOLS.clear();
        for (Entity entity : client.world.getEntities()) if (entity instanceof RemoteToolEntity tool && !tool.isRemoved() && tool.owner()!=null) {
            TOOLS.put(tool.owner(),tool);
            PlayerEntity player=client.world.getPlayerByUuid(tool.owner());
            if (player == null) continue;
            float[] old=FACING.computeIfAbsent(player.getUuid(),id -> new float[] {player.bodyYaw,player.headYaw,player.getPitch()});
            var target=tool.getEyePos().subtract(player.getEyePos());
            float[] next=RemoteToolMath.facing(target.x,target.y,target.z,old[0],old[1],old[2]);
            player.prevBodyYaw=old[0]; player.prevHeadYaw=old[1]; player.prevPitch=old[2];
            player.bodyYaw=next[0]; player.headYaw=next[1]; player.setYaw(next[1]); player.setPitch(next[2]);
            FACING.put(player.getUuid(),next);
        }
        FACING.keySet().retainAll(TOOLS.keySet());
        while (wheel.wasPressed()) if (client.currentScreen==null && !active()) client.setScreen(new AbilityWheelScreen());
        while (activate.wasPressed()) if (client.currentScreen==null) {
            if (active()) stop();
            else if (!selected) client.player.sendMessage(Text.translatable("text.magicaland_gameplay.remote.select",wheel.getBoundKeyLocalizedText()),true);
            else if (ClientPlayNetworking.canSend(RemoteToolServer.CONTROL)) {
                waiting=40; stopping=false;
                var request=PacketByteBufs.create(); request.writeByte(0);
                ClientPlayNetworking.send(RemoteToolServer.CONTROL,request);
            } else client.player.sendMessage(Text.translatable("text.magicaland_gameplay.remote.server"),true);
        }
        if (!active()) return;
        if (client.currentScreen!=null || !client.player.isAlive() || !client.isWindowFocused()) { stop(); return; }
        if (camera == null && entityId >= 0 && client.world.getEntityById(entityId) instanceof RemoteToolEntity tool
                && client.player.getUuid().equals(tool.owner())) {
            camera=tool; camera.localSteering=true; previousPerspective=client.options.getPerspective();
            client.options.setPerspective(Perspective.FIRST_PERSON); client.setCameraEntity(camera); waiting=0;
        }
        if (camera == null) { if (--waiting<=0) stop(); return; }
        if (camera.isRemoved() || camera.getWorld()!=client.world) { stop(); return; }
        if (!stopping) {
            var options=client.options;
            int keys=(held(options.forwardKey)?1:0)|(held(options.backKey)?2:0)|(held(options.leftKey)?4:0)
                    |(held(options.rightKey)?8:0)|(held(options.jumpKey)?16:0)|(held(options.sneakKey)?32:0)
                    |(held(options.attackKey)||attackQueued?64:0)|(held(options.useKey)||useQueued?128:0);
            var input=PacketByteBufs.create();
            input.writeByte(2).writeInt(entityId).writeFloat(camera.getYaw()).writeFloat(camera.getPitch()).writeByte(keys);
            ClientPlayNetworking.send(RemoteToolServer.CONTROL,input);
            attackQueued=useQueued=false;
            consumeBodyActions();
        }
    }
    public static void consumeBodyActions() {
        if (!active()) return;
        var options=MinecraftClient.getInstance().options;
        for (KeyBinding key : new KeyBinding[] {options.swapHandsKey,options.dropKey,options.attackKey,options.useKey}) {
            key.setPressed(false);
            while(key.wasPressed()) {
                if (key==options.attackKey) attackQueued=true;
                if (key==options.useKey) useQueued=true;
            }
        }
        for (KeyBinding key : options.hotbarKeys) { key.setPressed(false); while(key.wasPressed()) {} }
    }
    public static boolean look(double x,double y) {
        if (!active()) return false;
        if (controlling()) camera.changeLookDirection(x,y);
        return true;
    }
    public static void stop() {
        var client=MinecraftClient.getInstance();
        int id=entityId;
        restore(client);
        stopping=true;
        if (id>=0) sendStop(id);
    }
    private static void sendStop(int id) {
        if (ClientPlayNetworking.canSend(RemoteToolServer.CONTROL)) {
            var request=PacketByteBufs.create(); request.writeByte(1).writeInt(id);
            ClientPlayNetworking.send(RemoteToolServer.CONTROL,request);
        }
    }
    private static void restore(MinecraftClient client) {
        if (camera!=null) {
            camera.localSteering=false;
            if (client.getCameraEntity()==camera) client.setCameraEntity(client.player);
        }
        if (previousPerspective!=null) client.options.setPerspective(previousPerspective);
        camera=null; entityId=-1; waiting=0; stopping=false; previousPerspective=null;
        attackQueued=useQueued=false;
    }
}
