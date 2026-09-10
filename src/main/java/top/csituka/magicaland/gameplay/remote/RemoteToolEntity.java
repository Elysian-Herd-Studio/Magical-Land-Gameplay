package top.csituka.magicaland.gameplay.remote;

import java.util.Optional;
import java.util.UUID;
import net.minecraft.entity.Entity;
import net.minecraft.entity.EntityType;
import net.minecraft.entity.data.DataTracker;
import net.minecraft.entity.data.TrackedData;
import net.minecraft.entity.data.TrackedDataHandlerRegistry;
import net.minecraft.item.ItemStack;
import net.minecraft.nbt.NbtCompound;
import net.minecraft.network.packet.Packet;
import net.minecraft.network.packet.s2c.play.EntitySpawnS2CPacket;
import net.minecraft.network.listener.ClientPlayPacketListener;
import net.minecraft.world.World;

public final class RemoteToolEntity extends Entity {
    private static final TrackedData<Optional<UUID>> OWNER = DataTracker.registerData(RemoteToolEntity.class, TrackedDataHandlerRegistry.OPTIONAL_UUID);
    private static final TrackedData<ItemStack> STACK = DataTracker.registerData(RemoteToolEntity.class, TrackedDataHandlerRegistry.ITEM_STACK);
    private static final TrackedData<Boolean> ORIGINAL = DataTracker.registerData(RemoteToolEntity.class, TrackedDataHandlerRegistry.BOOLEAN);
    private double targetX, targetY, targetZ;
    private int lerpTicks;
    public boolean localSteering;
    public RemoteToolEntity(EntityType<? extends RemoteToolEntity> type, World world) {
        super(type, world);
        setNoGravity(true);
    }
    @Override protected void initDataTracker() {
        dataTracker.startTracking(OWNER, Optional.empty());
        dataTracker.startTracking(STACK, ItemStack.EMPTY);
        dataTracker.startTracking(ORIGINAL, false);
    }
    public void setup(UUID owner, ItemStack stack) {
        dataTracker.set(OWNER, Optional.of(owner));
        updateStack(stack);
        dataTracker.set(ORIGINAL, !stack.isEmpty());
    }
    public UUID owner() { return dataTracker.get(OWNER).orElse(null); }
    public ItemStack stack() { return dataTracker.get(STACK); }
    public boolean carriesOriginal() { return dataTracker.get(ORIGINAL); }
    public void updateStack(ItemStack stack) { dataTracker.set(STACK, RemoteCargoInventory.carriedView(stack)); }
    @Override public void tick() {
        super.tick();
        if (!getWorld().isClient && !RemoteToolServer.owns(this)) { discard(); return; }
        if (getWorld().isClient && lerpTicks > 0) {
            setPosition(getX() + (targetX-getX())/lerpTicks, getY() + (targetY-getY())/lerpTicks,
                    getZ() + (targetZ-getZ())/lerpTicks);
            lerpTicks--;
        }
    }
    @Override public void updateTrackedPositionAndAngles(double x, double y, double z, float yaw, float pitch, int steps, boolean interpolate) {
        targetX=x; targetY=y; targetZ=z; lerpTicks=2;
        if (!localSteering) { setYaw(yaw); setPitch(pitch); }
    }
    @Override protected void readCustomDataFromNbt(NbtCompound nbt) {}
    @Override protected void writeCustomDataToNbt(NbtCompound nbt) {}
    @Override public Packet<ClientPlayPacketListener> createSpawnPacket() { return new EntitySpawnS2CPacket(this); }
}
