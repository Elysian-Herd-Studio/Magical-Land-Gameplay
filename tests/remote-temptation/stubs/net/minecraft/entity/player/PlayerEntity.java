package net.minecraft.entity.player;
import java.util.UUID;
import net.minecraft.item.ItemStack;
public final class PlayerEntity {
    public final UUID uuid;
    public boolean spectator;
    public float yaw, pitch;
    public double distance = 3;
    public ItemStack held = new ItemStack("air");
    public PlayerEntity(UUID uuid) { this.uuid = uuid; }
    public boolean isSpectator() { return spectator; }
    public float getYaw() { return yaw; }
    public float getPitch() { return pitch; }
}
