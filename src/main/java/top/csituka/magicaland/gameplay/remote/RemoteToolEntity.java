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
import net.minecraft.util.math.MathHelper;

public final class RemoteToolEntity extends Entity {
    private static final TrackedData<Optional<UUID>> OWNER = DataTracker.registerData(RemoteToolEntity.class, TrackedDataHandlerRegistry.OPTIONAL_UUID);
    private static final TrackedData<ItemStack> STACK = DataTracker.registerData(RemoteToolEntity.class, TrackedDataHandlerRegistry.ITEM_STACK);
    private static final TrackedData<Integer> ACTION = DataTracker.registerData(RemoteToolEntity.class, TrackedDataHandlerRegistry.INTEGER);
    private static final TrackedData<Integer> ACTION_SEQUENCE = DataTracker.registerData(RemoteToolEntity.class, TrackedDataHandlerRegistry.INTEGER);
    private static final TrackedData<Long> ACTION_TICK = DataTracker.registerData(RemoteToolEntity.class, TrackedDataHandlerRegistry.LONG);
    private static final TrackedData<Float> OCCLUSION = DataTracker.registerData(RemoteToolEntity.class, TrackedDataHandlerRegistry.FLOAT);
    private static final TrackedData<Integer> SELECTED = DataTracker.registerData(RemoteToolEntity.class, TrackedDataHandlerRegistry.INTEGER);
    private static final TrackedData<Integer> CAPACITY = DataTracker.registerData(RemoteToolEntity.class, TrackedDataHandlerRegistry.INTEGER);
    private static final TrackedData<Float> ATTACK_COOLDOWN = DataTracker.registerData(RemoteToolEntity.class, TrackedDataHandlerRegistry.FLOAT);
    private static final TrackedData<Boolean> RETURNING = DataTracker.registerData(RemoteToolEntity.class, TrackedDataHandlerRegistry.BOOLEAN);
    private double targetX, targetY, targetZ;
    private float targetYaw, targetPitch;
    private int lerpTicks;
    public boolean localSteering;
    public RemoteToolEntity(EntityType<? extends RemoteToolEntity> type, World world) {
        super(type, world);
        setNoGravity(true);
    }
    @Override protected void initDataTracker() {
        dataTracker.startTracking(OWNER, Optional.empty());
        dataTracker.startTracking(STACK, ItemStack.EMPTY);
        dataTracker.startTracking(ACTION,0); dataTracker.startTracking(ACTION_SEQUENCE,0);
        dataTracker.startTracking(ACTION_TICK,0L); dataTracker.startTracking(OCCLUSION,0f);
        dataTracker.startTracking(SELECTED,0); dataTracker.startTracking(CAPACITY,RemoteCapabilities.CARGO_SLOTS);
        dataTracker.startTracking(ATTACK_COOLDOWN,0f);
        dataTracker.startTracking(RETURNING,false);
    }
    public void setup(UUID owner, ItemStack stack) {
        dataTracker.set(OWNER, Optional.of(owner));
        updateStack(stack);
    }
    public UUID owner() { return dataTracker.get(OWNER).orElse(null); }
    public ItemStack stack() { return dataTracker.get(STACK); }
    public RemoteAction action() { return RemoteAction.fromId(dataTracker.get(ACTION)); }
    public int actionSequence() { return dataTracker.get(ACTION_SEQUENCE); }
    public long actionStartedTick() { return dataTracker.get(ACTION_TICK); }
    public float occlusion() { return dataTracker.get(OCCLUSION); }
    public int selectedSlot() { return dataTracker.get(SELECTED); }
    public int capacity() { return dataTracker.get(CAPACITY); }
    public float attackCooldown() { return dataTracker.get(ATTACK_COOLDOWN); }
    public boolean returning() { return dataTracker.get(RETURNING); }
    public void beginReturn() {
        dataTracker.set(RETURNING,true); localSteering=false;
        startAction(RemoteAction.NONE); setOcclusion(0); setAttackCooldown(1);
    }
    public void setAttackCooldown(float value) { dataTracker.set(ATTACK_COOLDOWN,Math.max(0,Math.min(1,value))); }
    public void setOcclusion(float value) { dataTracker.set(OCCLUSION,Math.max(0,Math.min(1,value))); }
    public void inventoryView(RemoteCargoInventory inventory) {
        updateStack(returning()?inventory.displayStack():inventory.selectedStack()); dataTracker.set(SELECTED,inventory.selectedSlot());
        dataTracker.set(CAPACITY,inventory.unlockedSlots());
    }
    public void startAction(RemoteAction action) {
        dataTracker.set(ACTION,action.ordinal()); dataTracker.set(ACTION_TICK,getWorld().getTime());
        dataTracker.set(ACTION_SEQUENCE,actionSequence()+1);
    }
    public void updateStack(ItemStack stack) { dataTracker.set(STACK, RemoteCargoInventory.carriedView(stack)); }
    @Override public void tick() {
        super.tick();
        if (!getWorld().isClient && !RemoteToolServer.owns(this)) { discard(); return; }
        if (!getWorld().isClient && action()!=RemoteAction.NONE && action()!=RemoteAction.MINING
                && getWorld().getTime()-actionStartedTick()>=action().durationTicks()) startAction(RemoteAction.NONE);
        if (getWorld().isClient && lerpTicks > 0) {
            setPosition(getX() + (targetX-getX())/lerpTicks, getY() + (targetY-getY())/lerpTicks,
                    getZ() + (targetZ-getZ())/lerpTicks);
            if (!localSteering) {
                setYaw(getYaw()+MathHelper.wrapDegrees(targetYaw-getYaw())/lerpTicks);
                setPitch(getPitch()+(targetPitch-getPitch())/lerpTicks);
            }
            lerpTicks--;
        }
    }
    @Override public void updateTrackedPositionAndAngles(double x, double y, double z, float yaw, float pitch, int steps, boolean interpolate) {
        targetX=x; targetY=y; targetZ=z; lerpTicks=2;
        targetYaw=yaw; targetPitch=pitch;
    }
    @Override protected void readCustomDataFromNbt(NbtCompound nbt) {}
    @Override protected void writeCustomDataToNbt(NbtCompound nbt) {}
    @Override public Packet<ClientPlayPacketListener> createSpawnPacket() { return new EntitySpawnS2CPacket(this); }
}
