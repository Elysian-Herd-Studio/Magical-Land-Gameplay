package top.csituka.magicaland.gameplay.remote;
import java.util.UUID;
import net.minecraft.item.ItemStack;
import net.minecraft.world.World;
public final class RemoteToolEntity {
    private final UUID uuid = UUID.randomUUID();
    private final World world;
    public ItemStack stack = ItemStack.EMPTY;
    public RemoteAction action = RemoteAction.NONE;
    public boolean returning;
    public int sequence, slot;
    public long started;
    public RemoteToolEntity(World world) { this.world = world; }
    public UUID getUuid() { return uuid; }
    public World getWorld() { return world; }
    public ItemStack stack() { return stack; }
    public int selectedSlot() { return slot; }
    public RemoteAction action() { return action; }
    public int actionSequence() { return sequence; }
    public long actionStartedTick() { return started; }
    public boolean returning() { return returning; }
}
