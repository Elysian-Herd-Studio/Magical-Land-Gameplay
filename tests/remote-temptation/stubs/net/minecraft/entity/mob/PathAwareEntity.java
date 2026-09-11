package net.minecraft.entity.mob;
import net.minecraft.entity.ai.brain.Brain;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.world.World;
import top.csituka.magicaland.gameplay.remote.RemoteToolEntity;
public final class PathAwareEntity {
    public World world;
    public PlayerEntity passenger;
    public boolean valid = true;
    private final Brain brain = new Brain();
    public PathAwareEntity(World world) { this.world = world; }
    public Brain getBrain() { return brain; }
    public World getWorld() { return world; }
    public boolean hasPassenger(PlayerEntity player) { return passenger == player; }
    public double squaredDistanceTo(RemoteToolEntity tool) { return tool.distance * tool.distance; }
    public int getMaxHeadRotation() { return 20; }
    public int getMaxLookPitchChange() { return 20; }
    public LookControl getLookControl() { return new LookControl(); }
    public Navigation getNavigation() { return new Navigation(); }
    public static final class LookControl {
        public void lookAt(RemoteToolEntity entity, float yaw, float pitch) {}
    }
    public static final class Navigation {
        public void stop() {}
        public boolean startMovingTo(RemoteToolEntity entity, double speed) { return true; }
    }
}
