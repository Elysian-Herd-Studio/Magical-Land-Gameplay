package top.elysianherd.magicaland.gameplay.remote;

import java.util.Objects;
import java.util.UUID;
import net.minecraft.block.BlockState;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.item.Item;
import net.minecraft.item.ItemStack;
import net.minecraft.item.ItemUsageContext;
import net.minecraft.nbt.NbtElement;
import net.minecraft.registry.Registries;
import net.minecraft.registry.Registry;
import net.minecraft.screen.ScreenHandler;
import net.minecraft.screen.slot.SlotActionType;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.util.ActionResult;
import net.minecraft.util.Hand;
import net.minecraft.util.Identifier;
import net.minecraft.util.TypedActionResult;
import net.minecraft.util.math.BlockPos;
import net.minecraft.world.World;

/** 原槽只保存任务引用；工具的唯一真栈保存在持久货仓。 */
public final class TelekinesisToken extends Item {
    private static final Item ITEM = new TelekinesisToken();
    private static boolean registered;
    private TelekinesisToken() { super(new Item.Settings().maxCount(1)); }

    public static void register() {
        if (!registered) {
            Registry.register(Registries.ITEM, new Identifier("magicaland_gameplay", "telekinesis_token"), ITEM);
            registered = true;
        }
    }

    public static boolean isToken(ItemStack stack) { return stack != null && !stack.isEmpty() && stack.getItem() == ITEM; }
    public static long taskId(ItemStack stack) {
        return isToken(stack) && stack.hasNbt() ? Math.max(0, stack.getNbt().getLong("Task")) : 0;
    }
    public static UUID owner(ItemStack stack) {
        return isToken(stack) && stack.hasNbt() && stack.getNbt().containsUuid("Owner") ? stack.getNbt().getUuid("Owner") : null;
    }
    public static int sourceSlot(ItemStack stack) {
        return isToken(stack) && stack.hasNbt() && stack.getNbt().contains("Slot", NbtElement.INT_TYPE) ? stack.getNbt().getInt("Slot") : -1;
    }
    static ItemStack create(UUID owner, long id, int slot) {
        if (owner == null || id <= 0 || slot < 0 || slot > 8) throw new IllegalArgumentException("Invalid tool reservation");
        var stack = new ItemStack(ITEM);
        var tag = stack.getOrCreateNbt();
        tag.putUuid("Owner", owner); tag.putLong("Task", id); tag.putInt("Slot", slot);
        return stack;
    }

    public static boolean reserve(ServerPlayerEntity player, long id, int slot) {
        if (slot < 0 || slot > 8 || !player.getInventory().getStack(slot).isEmpty()) return false;
        var cargo = TelekinesisCargoState.get(player.getServer()).cargo(id);
        if (cargo == null || !player.getUuid().equals(cargo.owner()) || cargo.sourceSlot() != slot) return false;
        player.getInventory().setStack(slot, create(player.getUuid(), id, slot));
        player.getInventory().markDirty();
        return true;
    }

    public static boolean matches(ServerPlayerEntity player, long id, int slot) {
        return slot >= 0 && slot < 9 && matches(player.getInventory().getStack(slot), player.getUuid(), id, slot);
    }
    static boolean matches(ItemStack stack, UUID owner, long id, int slot) {
        return id > 0 && isToken(stack) && stack.getCount() == 1 && taskId(stack) == id
                && Objects.equals(owner(stack), owner) && sourceSlot(stack) == slot;
    }

    public static void clear(ServerPlayerEntity player, long id) {
        boolean changed = false;
        for (int slot = 0; slot < player.getInventory().size(); slot++) {
            var stack = player.getInventory().getStack(slot);
            if (isToken(stack) && taskId(stack) == id && player.getUuid().equals(owner(stack))) {
                player.getInventory().setStack(slot, ItemStack.EMPTY); changed = true;
            }
        }
        var cursor = player.currentScreenHandler.getCursorStack();
        if (isToken(cursor) && taskId(cursor) == id && player.getUuid().equals(owner(cursor))) {
            player.currentScreenHandler.setCursorStack(ItemStack.EMPTY); changed = true;
        }
        if (changed) player.getInventory().markDirty();
    }

    public static void reconcile(ServerPlayerEntity player) {
        if (player.getServer() == null) return;
        var state = TelekinesisCargoState.get(player.getServer());
        boolean changed = false;
        for (int slot = 0; slot < player.getInventory().size(); slot++) {
            var stack = player.getInventory().getStack(slot);
            if (!isToken(stack)) continue;
            var cargo = state.cargo(taskId(stack));
            if (cargo == null || slot > 8 || cargo.sourceSlot() != slot || !cargo.owner().equals(player.getUuid())
                    || !matches(stack, player.getUuid(), taskId(stack), slot)) {
                player.getInventory().setStack(slot, ItemStack.EMPTY); changed = true;
            }
        }
        if (isToken(player.currentScreenHandler.getCursorStack())) {
            player.currentScreenHandler.setCursorStack(ItemStack.EMPTY); changed = true;
        }
        if (changed) { player.getInventory().markDirty(); player.currentScreenHandler.syncState(); }
    }

    public static boolean blockedClick(ScreenHandler handler, int slot, int button, SlotActionType action, PlayerEntity player) {
        if (isToken(handler.getCursorStack())) return true;
        if (slot >= 0 && slot < handler.slots.size() && isToken(handler.getSlot(slot).getStack())) return true;
        return action == SlotActionType.SWAP && (button >= 0 && button < 9 || button == 40)
                && isToken(player.getInventory().getStack(button));
    }

    public static void resync(PlayerEntity player) {
        if (player instanceof ServerPlayerEntity server) server.currentScreenHandler.syncState();
    }
    @Override public boolean canMine(BlockState state, World world, BlockPos pos, PlayerEntity miner) { return false; }
    @Override public ActionResult useOnBlock(ItemUsageContext context) { return ActionResult.FAIL; }
    @Override public TypedActionResult<ItemStack> use(World world, PlayerEntity player, Hand hand) {
        return TypedActionResult.fail(player.getStackInHand(hand));
    }
}
