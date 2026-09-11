package top.csituka.magicaland.gameplay.remote;
import java.util.UUID;
import net.minecraft.item.ItemStack;
import net.minecraft.world.World;
import net.minecraft.util.math.Vec3d;
public final class RemoteToolEntity {
    public World world;
    public ItemStack stack = new ItemStack("wheat");
    public boolean active = true, returning, visible = true;
    public double distance = 2;
    public Vec3d position = new Vec3d(0, 0, 0);
    public float yaw, pitch;
    private final UUID owner;
    public RemoteToolEntity(World world, UUID owner) { this.world = world; this.owner = owner; }
    public UUID owner() { return owner; }
    public Vec3d getPos() { return position; }
    public float getYaw() { return yaw; }
    public float getPitch() { return pitch; }
}
