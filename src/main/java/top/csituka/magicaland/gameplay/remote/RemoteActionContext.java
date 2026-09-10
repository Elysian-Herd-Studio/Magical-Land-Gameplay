package top.csituka.magicaland.gameplay.remote;

import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.server.network.ServerPlayerEntity;

public final class RemoteActionContext implements AutoCloseable {
    private static final ThreadLocal<RemoteActionContext> CURRENT=new ThreadLocal<>();
    private final RemoteActionContext previous;
    public final ServerPlayerEntity player;
    public final RemoteToolEntity tool;
    private RemoteActionContext(ServerPlayerEntity player,RemoteToolEntity tool) {
        previous=CURRENT.get(); this.player=player; this.tool=tool; CURRENT.set(this);
    }
    public static RemoteActionContext open(ServerPlayerEntity player,RemoteToolEntity tool) {
        return new RemoteActionContext(player,tool);
    }
    public static RemoteActionContext current() { return CURRENT.get(); }
    public static RemoteToolEntity toolFor(PlayerEntity player) {
        var action=CURRENT.get(); return action!=null && action.player==player?action.tool:null;
    }
    @Override public void close() {
        if (previous==null) CURRENT.remove(); else CURRENT.set(previous);
    }
}
