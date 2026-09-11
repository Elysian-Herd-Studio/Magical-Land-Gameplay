package top.csituka.magicaland.gameplay.client;

import java.lang.reflect.Field;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import net.minecraft.item.Item;
import net.minecraft.item.ItemStack;
import net.minecraft.world.World;
import top.csituka.magicaland.gameplay.remote.RemoteAction;
import top.csituka.magicaland.gameplay.remote.RemoteToolEntity;

public final class RemoteReturnPresentationTest {
    private static int checks;
    private static final Item TOOL = new Item(), CARGO = new Item();
    public static void main(String[] args) throws Exception {
        matureReturn(); otherCargo(); retainLifecycle(); normalEquip(); sourceBoundaries(Path.of(args[0]));
        System.out.println("PASS RemoteReturnPresentationTest: " + checks + " real-animation/stub-entity and source-boundary checks");
    }

    private static void matureReturn() {
        RemoteHeldAnimation.clear();
        World world = new World(); world.time = 100;
        RemoteToolEntity tool = new RemoteToolEntity(world); tool.stack = new ItemStack(TOOL, 1);
        RemoteHeldAnimation.Frame first = RemoteHeldAnimation.sample(tool, tool.stack, 0, .5f);
        check(first.stack().getItem() == TOOL && first.equip() == 0, "already visible tool starts fully equipped");
        RemoteHeldAnimation.predict(tool, RemoteAction.SWING); world.time++;
        check(RemoteHeldAnimation.sample(tool, tool.stack, 0, .5f).acting(), "outbound actions still animate");
        tool.returning = true; tool.action = RemoteAction.NONE; tool.sequence++;
        for (int tick = 0; tick < 400; tick++) {
            world.time++;
            RemoteHeldAnimation.predict(tool, RemoteAction.USE);
            for (float delta : new float[] {0, .2f, .7f, 1}) {
                var frame = RemoteHeldAnimation.sample(tool, tool.stack, 0, delta);
                check(frame.stack().getItem() == TOOL && frame.stack().getCount() == 1, "return keeps actual carried model");
                check(frame.equip() == 0 && frame.swing() == 0 && !frame.acting(), "return never replays equip, swing or mining");
                check(frame.stack() != tool.stack, "frame keeps a copy of tracked cargo");
            }
        }
        tool.returning = false;
        check(!RemoteHeldAnimation.sample(tool, tool.stack, 0, 0).acting(), "return predictions never poison action state");
    }

    private static void otherCargo() {
        RemoteHeldAnimation.clear();
        World world = new World(); world.time = 20;
        RemoteToolEntity tool = new RemoteToolEntity(world);
        check(RemoteHeldAnimation.sample(tool, ItemStack.EMPTY, 0, 0).stack().isEmpty(), "selected empty cargo starts as flame");
        tool.returning = true; tool.slot = 4; tool.stack = new ItemStack(CARGO, 37);
        var frame = RemoteHeldAnimation.sample(tool, tool.stack, tool.slot, .2f);
        check(frame.stack().getItem() == CARGO && frame.stack().getCount() == 37 && frame.equip() == 0,
                "server-selected nonempty cargo appears immediately on return");
        tool.stack.setCount(36);
        check(frame.stack().getCount() == 37, "tracked update cannot alter an already sampled return frame");
        check(RemoteHeldAnimation.sample(tool, tool.stack, tool.slot, .3f).stack().getCount() == 36,
                "next frame follows authority without re-equipping");
    }

    @SuppressWarnings("unchecked")
    private static void retainLifecycle() throws Exception {
        RemoteHeldAnimation.clear();
        World world = new World();
        RemoteToolEntity returning = new RemoteToolEntity(world), other = new RemoteToolEntity(world);
        returning.stack = new ItemStack(TOOL, 1); returning.returning = true;
        other.stack = new ItemStack(CARGO, 6);
        RemoteHeldAnimation.sample(returning, returning.stack, 0, 0);
        RemoteHeldAnimation.sample(other, other.stack, 0, 0);
        Field field = RemoteHeldAnimation.class.getDeclaredField("STATES"); field.setAccessible(true);
        Map<UUID, ?> states = (Map<UUID, ?>) field.get(null);
        Object oldReturn = states.get(returning.getUuid()), oldOther = states.get(other.getUuid());
        for (int tick = 0; tick < 300; tick++) {
            RemoteHeldAnimation.retain(Set.of(returning.getUuid(), other.getUuid()));
            check(states.get(returning.getUuid()) == oldReturn && states.get(other.getUuid()) == oldOther,
                    "camera session exit retains returning and other players' interpolation state");
        }
        RemoteHeldAnimation.retain(Set.of(other.getUuid()));
        check(states.size() == 1 && states.get(other.getUuid()) == oldOther, "arrival/discard retires only the removed entity");
        RemoteHeldAnimation.retain(Set.of());
        check(states.isEmpty(), "untracked entities leave no visual state");
    }

    private static void normalEquip() {
        RemoteHeldAnimation.clear();
        World world = new World(); world.time = 40;
        RemoteToolEntity tool = new RemoteToolEntity(world); tool.stack = new ItemStack(TOOL, 1);
        RemoteHeldAnimation.sample(tool, tool.stack, 0, 0);
        ItemStack selected = new ItemStack(CARGO, 10);
        var start = RemoteHeldAnimation.sample(tool, selected, 1, 0);
        check(start.stack().getItem() == TOOL && start.equip() == 0, "normal outbound slot change keeps old start");
        world.time = 42;
        var middle = RemoteHeldAnimation.sample(tool, selected, 1, 0);
        check(middle.stack().getItem() == CARGO && middle.equip() == 1, "normal outbound slot change still lowers item");
        world.time = 45;
        check(RemoteHeldAnimation.sample(tool, selected, 1, 0).equip() == 0, "normal outbound slot change settles");
    }

    private static void sourceBoundaries(Path repo) throws Exception {
        Path client = repo.resolve("src/client/java/top/csituka/magicaland/gameplay/client");
        String source = Files.readString(client.resolve("RemoteToolClient.java"));
        String renderer = Files.readString(client.resolve("RemoteToolRenderer.java"));
        String animation = Files.readString(client.resolve("RemoteHeldAnimation.java"));
        String reset = source.substring(source.indexOf("private static void reset("), source.indexOf("private static void clearVisuals("));
        check(!reset.contains("RemoteHeldAnimation.clear"), "session cleanup is independent from visual lifetime");
        check(source.contains("RemoteHeldAnimation.retain(present)") && source.contains("RELEASED.retainAll(present)"),
                "tracked entity lifetime owns cache cleanup");
        check(source.contains("reset(client); clearVisuals();"), "disconnect and absent-world paths clear every visual cache");
        check(source.contains("RemoteToolClient::gazeTarget") && source.contains("!tool.returning() && !RELEASED.contains(tool.getUuid())"),
                "returning or locally released entity cannot own gaze");
        check(source.contains("if (tool.returning() || RELEASED.contains(tool.getUuid())) continue;")
                && source.contains("FACING.keySet().retainAll(controllingOwners)"), "return does not force body/head direction");
        String magic = source.substring(source.indexOf("private static boolean magicActive"), source.indexOf("private static RemoteToolEntity gazeTarget"));
        check(!magic.contains("returning()") && !magic.contains("SESSION"), "horn stays lit until tracked entity actually disappears");
        check(source.contains("ownsCamera(tool) && !tool.returning() ? stack(selectedSlot) : tool.stack()"),
                "server return cargo does not use emptied local HUD inventory");
        check(source.contains("if (returnPending(client))") && source.contains("text.magicaland_gameplay.remote.return_flying"),
                "new starts wait for visible in-flight return without a long tooltip");
        check(source.contains("if (camera.returning()) { beginReturn(client); return; }"), "tracked return can release camera before state snapshot arrives");
        check(source.contains("RemoteCapabilities.SPIRITUAL_ECHO || camera.occlusion()<.999f")
                && source.contains("!RemoteCapabilities.SPIRITUAL_ECHO && camera.occlusion()>=1"), "previous echo interactions preserved");
        check(renderer.contains("if (entity.returning())") && renderer.contains("matrices.scale(.55f,.55f,.55f)")
                && renderer.contains("renderFlame(matrices,entity,color,delta)"), "small return flame accompanies cargo");
        check(renderer.contains("RemoteItemPose.tool") && renderer.contains("RemoteItemPose.billboard")
                && renderer.contains("renderLevitatingItem") && renderer.contains("new ItemVisualContext(entity,"),
                "tool orientation, pixel billboard and same-source appearance inertia preserved");
        check(!renderer.matches("(?s).*entity\\.(?:setPosition|setPos|teleport|move)\\s*\\(.*"),
                "renderer never changes return physics");
        check(animation.contains("if (tool.returning()) return;"), "return cannot predict new interactions");
    }

    private static void check(boolean value, String message) { checks++; if (!value) throw new AssertionError(message); }
}
