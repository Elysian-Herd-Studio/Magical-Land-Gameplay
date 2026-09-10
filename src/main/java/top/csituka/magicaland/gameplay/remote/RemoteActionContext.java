package top.csituka.magicaland.gameplay.remote;

import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.entity.attribute.EntityAttribute;
import net.minecraft.entity.attribute.EntityAttributeInstance;
import net.minecraft.entity.attribute.EntityAttributes;
import net.minecraft.item.ItemStack;
import net.minecraft.particle.ItemStackParticleEffect;
import net.minecraft.particle.ParticleTypes;
import net.minecraft.sound.SoundCategory;
import net.minecraft.sound.SoundEvents;
import net.minecraft.server.network.ServerPlayerEntity;

public final class RemoteActionContext implements AutoCloseable {
    private static final ThreadLocal<RemoteActionContext> CURRENT=new ThreadLocal<>();
    private final RemoteActionContext previous;
    public final ServerPlayerEntity player;
    public final RemoteToolEntity tool;
    private final RemoteSession session;
    private final ItemStack initialStack;
    private boolean hit;
    private RemoteActionContext(RemoteSession session) {
        previous=CURRENT.get(); this.session=session; this.player=session.player; this.tool=session.tool;
        initialStack=session.cargo.selectedStack().copy(); CURRENT.set(this);
    }
    static RemoteActionContext open(RemoteSession session) {
        return new RemoteActionContext(session);
    }
    public static RemoteActionContext current() { return CURRENT.get(); }
    public static RemoteToolEntity toolFor(PlayerEntity player) {
        var action=CURRENT.get(); return action!=null && action.player==player?action.tool:null;
    }
    public static RemoteActionContext forPlayer(PlayerEntity player) {
        var action=CURRENT.get(); return action!=null && action.player==player?action:null;
    }
    public ItemStack stack() { return session.cargo.selectedStack(); }
    public void setStack(ItemStack stack) { session.cargo.setStack(session.cargo.selectedSlot(),stack); }
    public double attributeValue(EntityAttribute attribute) {
        EntityAttributeInstance original=player.getAttributeInstance(attribute);
        if (original==null) return attribute.getDefaultValue();
        return RemoteAttributes.project(original,session.sourceItem,session.bodyStack,stack());
    }
    public float attackCooldown(float partial) {
        double speed=attributeValue(EntityAttributes.GENERIC_ATTACK_SPEED);
        return (float)Math.max(0,Math.min(1,(player.getServer().getTicks()-session.lastAttackTick+partial)*speed/20));
    }
    public void resetAttackCooldown() { session.lastAttackTick=player.getServer().getTicks(); }
    public void toolBroken() {
        tool.startAction(RemoteAction.TOOL_BREAK);
        player.getServerWorld().playSound(null,tool.getX(),tool.getY(),tool.getZ(),SoundEvents.ENTITY_ITEM_BREAK,SoundCategory.PLAYERS,.8f,1);
        if (!initialStack.isEmpty()) player.getServerWorld().spawnParticles(new ItemStackParticleEffect(ParticleTypes.ITEM,initialStack),
                tool.getX(),tool.getY()+.15,tool.getZ(),8,.1,.1,.1,.05);
    }
    public void recordHit(boolean result) { hit|=result; }
    public boolean hit() { return hit; }
    public void resetHit() { hit=false; }
    @Override public void close() {
        if (previous==null) CURRENT.remove(); else CURRENT.set(previous);
    }
}
