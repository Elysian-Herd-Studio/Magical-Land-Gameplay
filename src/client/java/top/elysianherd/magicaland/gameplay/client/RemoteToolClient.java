package top.elysianherd.magicaland.gameplay.client;

import top.elysianherd.magicaland.gameplay.client.sense.EarthSenseClient;
import top.elysianherd.magicaland.gameplay.client.levitation.UnicornLevitationClient;
import top.elysianherd.magicaland.gameplay.client.telekinesis.TelekinesisClient;

import java.util.HashMap;
import java.util.HashSet;
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
import net.minecraft.client.gui.screen.Screen;
import net.minecraft.client.option.KeyBinding;
import net.minecraft.client.option.Perspective;
import net.minecraft.client.util.InputUtil;
import net.minecraft.entity.Entity;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.item.ItemStack;
import net.minecraft.text.Text;
import org.lwjgl.glfw.GLFW;
import top.elysianherd.magicaland.api.ApiVersion;
import top.elysianherd.magicaland.api.client.AppearanceOverrides;
import top.elysianherd.magicaland.api.client.Registration;
import top.elysianherd.magicaland.gameplay.remote.RemoteAction;
import top.elysianherd.magicaland.gameplay.config.GameplayClientConfig;
import top.elysianherd.magicaland.gameplay.client.race.RaceClient;
import top.elysianherd.magicaland.gameplay.remote.RemoteToolEntity;
import top.elysianherd.magicaland.gameplay.remote.RemoteToolMath;
import top.elysianherd.magicaland.gameplay.remote.RemoteCapabilities;
import top.elysianherd.magicaland.gameplay.remote.RemoteToolServer;

public final class RemoteToolClient implements ClientModInitializer {
    private static final RemoteSessionState SESSION = new RemoteSessionState();
    private static final RemoteInputGate INPUT = new RemoteInputGate();
    private static final Map<UUID, float[]> FACING = new HashMap<>();
    private static final Map<UUID, RemoteToolEntity> TOOLS = new HashMap<>();
    private static final HashSet<UUID> RELEASED = new HashSet<>();
    private static final String APPEARANCE_OWNER = "magicaland_gameplay:remote_tool";
    private static KeyBinding wheel, activate;
    private static Registration gazeOverride, magicOverride;
    private static RemoteToolEntity camera;
    private static Perspective previousPerspective;
    private static ItemStack[] cargo = {ItemStack.EMPTY};
    private static int waiting, selectedSlot, selectionAck = -1, lastKeys;
    private static boolean attackQueued, useQueued, returned;
    private static double scrollRemainder, returnStarted, returnSwitched;
    private static float stateOcclusion;

    public static boolean active() { return SESSION.active(); }
    public static boolean controlling() { return SESSION.phase() == RemoteSessionState.Phase.CONTROLLING && camera != null; }
    public static boolean returning() { return SESSION.phase() == RemoteSessionState.Phase.RETURNING; }
    public static boolean waiting() { return SESSION.phase() == RemoteSessionState.Phase.REQUESTING || SESSION.phase() == RemoteSessionState.Phase.CONNECTING; }
    public static boolean blockBodyInput() { return active() || MinecraftClient.getInstance().currentScreen instanceof AbilityWheelScreen; }
    public static boolean blocksInventory() { return active() && (!returning() || camera!=null); }
    private static boolean acceptsInput() {
        var client = MinecraftClient.getInstance();
        return controlling() && client.currentScreen == null && client.isWindowFocused() && INPUT.accepting();
    }
    public static int capacity() { return cargo.length; }
    public static int selectedSlot() { return selectedSlot; }
    public static Text activationKey() { return activate.getBoundKeyLocalizedText(); }
    public static ItemStack stack(int slot) { return slot >= 0 && slot < cargo.length ? cargo[slot] : ItemStack.EMPTY; }
    public static RemoteToolEntity camera() { return camera; }
    public static boolean ownsCamera(RemoteToolEntity tool) { return camera == tool && active(); }
    public static ItemStack visualStack(RemoteToolEntity tool) { return ownsCamera(tool) && !tool.returning() ? stack(selectedSlot) : tool.stack(); }
    public static int visualSlot(RemoteToolEntity tool) { return ownsCamera(tool) && !tool.returning() ? selectedSlot : tool.selectedSlot(); }
    public static float occlusion() { return returned ? 0 : camera == null ? stateOcclusion : camera.occlusion(); }
    public static double seconds() { return System.nanoTime() / 1_000_000_000.0; }

    public static float returnOpacity() {
        if (!returning()) return 0;
        double now = seconds();
        return returned ? 1 - RemoteVisualMath.smooth((float)((now - returnSwitched) / .18))
                : RemoteVisualMath.smooth((float)((now - returnStarted) / .10));
    }

    @Override public void onInitializeClient() {
        ApiVersion.requireCompatible(1,6);
        RaceClient.init();
        top.elysianherd.magicaland.gameplay.client.sense.EarthSenseClient.init();
        UnicornLevitationClient.init();
        top.elysianherd.magicaland.gameplay.client.telekinesis.TelekinesisClient.init();
        top.elysianherd.magicaland.gameplay.client.pegasus.PegasusFlightClient.init();
        top.elysianherd.magicaland.gameplay.client.pegasus.PegasusFlightEffects.init();
        wheel = KeyBindingHelper.registerKeyBinding(new KeyBinding("key.magicaland_gameplay.wheel", InputUtil.Type.KEYSYM, GLFW.GLFW_KEY_R, "category.magicaland_gameplay"));
        activate = KeyBindingHelper.registerKeyBinding(new KeyBinding("key.magicaland_gameplay.activate", InputUtil.Type.KEYSYM, GLFW.GLFW_KEY_V, "category.magicaland_gameplay"));
        EntityRendererRegistry.register(RemoteToolServer.TYPE, RemoteToolRenderer::new);
        RemoteBodyRenderer.register();
        RemoteToolHud.init();
        RemoteMagicAudio.init();
        ClientPlayConnectionEvents.JOIN.register((handler,sender,client) -> {
            reset(client); clearVisuals(); closeAppearanceOverride();
            gazeOverride = AppearanceOverrides.registerGaze(APPEARANCE_OWNER,0,RemoteToolClient::gazeTarget);
            magicOverride = AppearanceOverrides.registerMagicActivity(APPEARANCE_OWNER,0,RemoteToolClient::magicActive);
        });
        ClientPlayConnectionEvents.DISCONNECT.register((handler,client) -> {
            reset(client); clearVisuals(); AbilityClient.reset(); closeAppearanceOverride();
        });
        ClientPlayNetworking.registerGlobalReceiver(RemoteToolServer.STATE,(client,handler,buf,sender) -> {
            try {
                if (buf.readableBytes() > 262144) return;
                long request = buf.readLong(), session = buf.readLong();
                int entity = buf.readInt(), sequence = buf.readInt(), ack = buf.readInt();
                int reason = buf.readUnsignedByte(), capacity = buf.readUnsignedByte(), slot = buf.readUnsignedByte();
                float occlusion = buf.readFloat();
                if (capacity < 1 || capacity > 9 || slot >= capacity || reason > 5 || ack < -1
                        || !Float.isFinite(occlusion) || occlusion < 0 || occlusion > 1) return;
                ItemStack[] stacks = new ItemStack[capacity];
                for (int i=0; i<capacity; i++) stacks[i] = buf.readItemStack();
                if (buf.isReadable()) return;
                client.execute(() -> {
                    if (client.getNetworkHandler() != handler) return;
                    receive(client,request,session,entity,sequence,ack,slot,occlusion,stacks);
                });
            } catch (RuntimeException ignored) {}
        });
        ClientTickEvents.END_CLIENT_TICK.register(RemoteToolClient::tick);
        HudRenderCallback.EVENT.register(RemoteToolHud::renderHud);
    }

    private static void receive(MinecraftClient client, long request, long session, int entity,
            int sequence, int ack, int slot, float occlusion, ItemStack[] stacks) {
        RemoteSessionState.Update result = SESSION.accept(request,session,entity,sequence);
        if (result == RemoteSessionState.Update.CANCEL_LATE_START) {
            sendStop(request,session,entity); return;
        }
        if (result == RemoteSessionState.Update.IGNORE) {
            if (!active() && request == SESSION.request() && entity >= 0) sendStop(request,session,entity);
            return;
        }
        cargo = stacks; stateOcclusion = occlusion;
        if (selectionAck < 0 || ack >= selectionAck) { selectedSlot = slot; selectionAck = -1; }
        selectedSlot = Math.min(selectedSlot,cargo.length-1);
        if (result == RemoteSessionState.Update.ENDED) beginReturn(client);
    }

    private static void closeAppearanceOverride() {
        if (gazeOverride != null) gazeOverride.close();
        if (magicOverride != null) magicOverride.close();
        gazeOverride = magicOverride = null;
    }

    private static boolean magicActive(UUID owner) {
        var tool=TOOLS.get(owner);
        var world=MinecraftClient.getInstance().world;
        return world!=null && tool!=null && !tool.isRemoved() && tool.getWorld()==world && owner.equals(tool.owner());
    }

    private static RemoteToolEntity gazeTarget(UUID owner) {
        var tool = TOOLS.get(owner);
        return magicActive(owner) && !tool.returning() && !RELEASED.contains(tool.getUuid()) ? tool : null;
    }

    private static boolean returnPending(MinecraftClient client) {
        if (client.world == null || client.player == null) return false;
        for (Entity entity : client.world.getEntities()) {
            if (entity instanceof RemoteToolEntity tool && !tool.isRemoved() && client.player.getUuid().equals(tool.owner())
                    && !tool.autonomous()
                    && (tool.returning() || RELEASED.contains(tool.getUuid()))) return true;
        }
        return false;
    }

    public static boolean held(KeyBinding binding) {
        var key = KeyBindingHelper.getBoundKeyOf(binding);
        long window = MinecraftClient.getInstance().getWindow().getHandle();
        if (key.getCode() < 0) return false;
        return key.getCategory() == InputUtil.Type.MOUSE ? GLFW.glfwGetMouseButton(window,key.getCode()) == GLFW.GLFW_PRESS
                : InputUtil.isKeyPressed(window,key.getCode());
    }
    public static boolean wheelHeld() { return held(wheel); }
    public static void select() { AbilityClient.select(0); }

    public static void startAbility() {
        var client = MinecraftClient.getInstance();
        if (client.player == null || active()) return;
        if (!RaceClient.canUseUnicornAbility()) { client.player.sendMessage(RaceClient.abilityUnavailable(), true); return; }
        if (returnPending(client)) {
            client.player.sendMessage(Text.translatable("text.magicaland_gameplay.remote.return_flying"), true); return;
        }
        if (!ClientPlayNetworking.canSend(RemoteToolServer.CONTROL)) {
            client.player.sendMessage(Text.translatable("text.magicaland_gameplay.remote.server"), true); return;
        }
        UnicornLevitationClient.pauseForRemote();
        INPUT.suspend(); attackQueued=useQueued=false;
        waiting=40; returned=false; selectionAck=-1; lastKeys=0;
        selectedSlot=0; cargo=new ItemStack[] {ItemStack.EMPTY};
        var request=PacketByteBufs.create(); request.writeByte(0).writeLong(SESSION.begin());
        ClientPlayNetworking.send(RemoteToolServer.CONTROL,request);
    }

    private static void tick(MinecraftClient client) {
        if (client.world == null || client.player == null) { TelekinesisClient.cancelInput(); reset(client); clearVisuals(); return; }
        TOOLS.clear();
        var present = new HashSet<UUID>();
        var controllingOwners = new HashSet<UUID>();
        for (Entity entity : client.world.getEntities()) if (entity instanceof RemoteToolEntity tool && !tool.isRemoved() && tool.owner()!=null) {
            present.add(tool.getUuid());
            if (tool.autonomous()) continue;
            TOOLS.put(tool.owner(),tool);
            if (tool.returning() || RELEASED.contains(tool.getUuid())) continue;
            PlayerEntity player = client.world.getPlayerByUuid(tool.owner());
            if (player == null) continue;
            controllingOwners.add(player.getUuid());
            float[] old = FACING.computeIfAbsent(player.getUuid(),id -> new float[] {player.bodyYaw,player.headYaw,player.getPitch()});
            var target = tool.getEyePos().subtract(player.getEyePos());
            float[] next = RemoteToolMath.facing(target.x,target.y,target.z,old[0],old[1],old[2]);
            player.prevBodyYaw=old[0]; player.prevHeadYaw=old[1]; player.prevPitch=old[2];
            player.bodyYaw=next[0]; player.headYaw=next[1]; player.setYaw(next[1]); player.setPitch(next[2]);
            FACING.put(player.getUuid(),next);
        }
        FACING.keySet().retainAll(controllingOwners); RELEASED.retainAll(present); RemoteHeldAnimation.retain(present);
        var options=client.options;
        boolean inputAvailable=controlling() && client.currentScreen==null && client.isWindowFocused() && !client.isPaused();
        int heldActions=inputAvailable ? (held(options.attackKey)?RemoteInputGate.ATTACK:0)
                |(held(options.useKey)?RemoteInputGate.USE:0)|(held(options.dropKey)?RemoteInputGate.DROP:0) : 0;
        INPUT.tick(inputAvailable,heldActions);
        while (wheel.wasPressed()) if (client.currentScreen == null && client.isWindowFocused()) {
            suspendInput();
            client.setScreen(new AbilityWheelScreen());
        }
        boolean telekinesisPressed = false;
        while (activate.wasPressed()) {
            if (AbilityClient.selected() == 3) telekinesisPressed = true;
            else if (client.currentScreen == null && client.isWindowFocused() && (!active() || INPUT.accepting()))
                AbilityClient.activate(client, wheel.getBoundKeyLocalizedText());
        }
        TelekinesisClient.activationInput(telekinesisPressed, held(activate));
        if (!active()) return;
        if (!client.player.isAlive()) {
            sendStop(SESSION.request(),SESSION.session(),SESSION.entity()); reset(client); return;
        }
        if (camera!=null && camera.getWorld()!=client.world) {
            sendStop(SESSION.request(),SESSION.session(),SESSION.entity()); reset(client); return;
        }
        if (returning()) {
            if (!returned && seconds()-returnStarted >= .10) { restoreCamera(client); returned=true; returnSwitched=seconds(); }
            if (returned && seconds()-returnSwitched >= .18) reset(client);
            consumeBodyActions(); return;
        }
        if (camera == null && SESSION.entity() >= 0 && client.world.getEntityById(SESSION.entity()) instanceof RemoteToolEntity tool
                && client.player.getUuid().equals(tool.owner())) {
            camera=tool; camera.localSteering=true; previousPerspective=client.options.getPerspective();
            if (GameplayClientConfig.automaticAbilityThirdPerson())
                client.options.setPerspective(Perspective.THIRD_PERSON_BACK);
            client.setCameraEntity(camera);
            SESSION.connected(); waiting=0;
        }
        if (camera == null) { if (!client.isPaused() && --waiting<=0) stop(); consumeBodyActions(); return; }
        if (camera.isRemoved()) { stop(); return; }
        if (camera.returning()) { beginReturn(client); return; }
        int keys=0;
        if (acceptsInput()) keys=(held(options.forwardKey)?1:0)|(held(options.backKey)?2:0)|(held(options.leftKey)?4:0)
                |(held(options.rightKey)?8:0)|(held(options.jumpKey)?16:0)|(held(options.sneakKey)?32:0)
                |INPUT.filter((held(options.attackKey)||attackQueued?64:0)|(held(options.useKey)||useQueued?128:0));
        if (RemoteCapabilities.SPIRITUAL_ECHO || camera.occlusion()<.999f) {
            if ((keys&64)!=0 && ((lastKeys&64)==0 || attackQueued)) RemoteHeldAnimation.predict(camera,RemoteAction.SWING);
            if ((keys&128)!=0 && ((lastKeys&128)==0 || useQueued)) RemoteHeldAnimation.predict(camera,RemoteAction.USE);
        }
        sendInput(keys);
        consumeBodyActions();
    }

    private static void sendInput(int keys) {
        int sequence=SESSION.nextInput();
        if (selectionAck==Integer.MAX_VALUE) selectionAck=sequence;
        var input=PacketByteBufs.create();
        input.writeByte(2).writeLong(SESSION.session()).writeInt(SESSION.entity()).writeInt(sequence)
                .writeFloat(camera.getYaw()).writeFloat(camera.getPitch()).writeByte(keys).writeByte(selectedSlot);
        ClientPlayNetworking.send(RemoteToolServer.CONTROL,input);
        lastKeys=keys; attackQueued=useQueued=false;
    }

    public static void suspendInput() {
        TelekinesisClient.cancelInput();
        INPUT.suspend(); attackQueued=useQueued=false; scrollRemainder=0;
        var client=MinecraftClient.getInstance();
        if (controlling() && client.player!=null && client.player.isAlive() && camera.getWorld()==client.world
                && ClientPlayNetworking.canSend(RemoteToolServer.CONTROL)) sendInput(0);
    }

    public static void consumeBodyActions() {
        if (!active()) return;
        var options=MinecraftClient.getInstance().options;
        boolean accept=acceptsInput();
        if (!accept) { attackQueued=useQueued=false; scrollRemainder=0; }
        for (int i=0; i<options.hotbarKeys.length; i++) {
            var key=options.hotbarKeys[i]; key.setPressed(false);
            while (key.wasPressed()) if (accept) selectSlot(i);
        }
        for (KeyBinding key : new KeyBinding[] {options.swapHandsKey,options.dropKey,options.attackKey,options.useKey,options.inventoryKey}) {
            if (key==options.inventoryKey && !blocksInventory()) continue;
            key.setPressed(false);
            while (key.wasPressed()) {
                if (!accept) continue;
                if (key==options.attackKey && INPUT.accepts(RemoteInputGate.ATTACK)) attackQueued=true;
                if (key==options.useKey && INPUT.accepts(RemoteInputGate.USE)) useQueued=true;
                if (key==options.dropKey && INPUT.accepts(RemoteInputGate.DROP)) dropSelected(Screen.hasControlDown());
            }
        }
    }

    private static void dropSelected(boolean wholeStack) {
        var client=MinecraftClient.getInstance();
        if (!acceptsInput() || !INPUT.accepts(RemoteInputGate.DROP)
                || camera.isRemoved() || camera.getWorld()!=client.world || (!RemoteCapabilities.SPIRITUAL_ECHO && camera.occlusion()>=1)
                || stack(selectedSlot).isEmpty() || !ClientPlayNetworking.canSend(RemoteToolServer.CONTROL)) return;
        int sequence=SESSION.nextInput();
        if (selectionAck==Integer.MAX_VALUE) selectionAck=sequence;
        var drop=PacketByteBufs.create();
        drop.writeByte(3).writeLong(SESSION.session()).writeInt(SESSION.entity()).writeInt(sequence)
                .writeByte(selectedSlot).writeByte(wholeStack?1:0);
        ClientPlayNetworking.send(RemoteToolServer.CONTROL,drop);
    }

    public static void scroll(double amount) {
        if (!acceptsInput() || !Double.isFinite(amount)) return;
        scrollRemainder+=Math.max(-100,Math.min(100,amount));
        int steps=(int)scrollRemainder; scrollRemainder-=steps;
        if (steps!=0) selectSlot(RemoteVisualMath.slot(selectedSlot,-steps,cargo.length));
    }
    public static void selectSlot(int slot) {
        if (!acceptsInput() || slot < 0 || slot >= cargo.length || slot==selectedSlot) return;
        selectedSlot=slot; selectionAck=Integer.MAX_VALUE;
        attackQueued=useQueued=false;
    }
    public static boolean look(double x,double y) {
        if (!active()) return false;
        if (acceptsInput()) {
            camera.changeLookDirection(x,y);
            camera.setPitch(Math.max(-89,Math.min(89,camera.getPitch())));
        }
        return true;
    }
    public static void stop() {
        if (!active() || returning()) return;
        sendStop(SESSION.request(),SESSION.session(),SESSION.entity());
        SESSION.returning(); beginReturn(MinecraftClient.getInstance());
    }
    private static void sendStop(long requestToken,long sessionToken,int entity) {
        if (!ClientPlayNetworking.canSend(RemoteToolServer.CONTROL)) return;
        var request=PacketByteBufs.create();
        request.writeByte(1).writeLong(requestToken).writeLong(sessionToken).writeInt(entity);
        ClientPlayNetworking.send(RemoteToolServer.CONTROL,request);
    }
    private static void beginReturn(MinecraftClient client) {
        SESSION.returning(); returnStarted=seconds(); returned=false;
        INPUT.suspend();
        attackQueued=useQueued=false;
        if (camera==null || client.world==null || client.player==null) { reset(client); return; }
        RELEASED.add(camera.getUuid());
        camera.localSteering=false;
    }
    private static void restoreCamera(MinecraftClient client) {
        if (camera!=null) {
            RELEASED.add(camera.getUuid());
            camera.localSteering=false;
            if (client.getCameraEntity()==camera) client.setCameraEntity(client.player);
        }
        if (previousPerspective!=null) client.options.setPerspective(previousPerspective);
        camera=null; previousPerspective=null;
    }
    private static void reset(MinecraftClient client) {
        restoreCamera(client); SESSION.clear(); RemoteToolHud.reset();
        INPUT.suspend();
        waiting=lastKeys=selectedSlot=0; selectionAck=-1; stateOcclusion=0; scrollRemainder=0;
        returned=attackQueued=useQueued=false; cargo=new ItemStack[] {ItemStack.EMPTY};
    }

    private static void clearVisuals() {
        TOOLS.clear(); FACING.clear(); RELEASED.clear(); RemoteHeldAnimation.clear();
    }
}
