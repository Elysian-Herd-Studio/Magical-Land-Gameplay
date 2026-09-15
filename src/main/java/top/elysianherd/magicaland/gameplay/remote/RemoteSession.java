package top.elysianherd.magicaland.gameplay.remote;

import net.minecraft.block.BlockState;
import net.minecraft.item.ItemStack;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Vec3d;

final class RemoteSession {
    final ServerPlayerEntity player;
    final RemoteToolEntity tool;
    final RemoteCargoInventory cargo;
    final RemoteSessionRules rules=new RemoteSessionRules();
    final RemoteReturnNavigator navigation;
    final long request,token;
    final int sourceSlot;
    final Vec3d origin;
    final ItemStack sourceItem;
    ItemStack bodyStack;
    int inputTick,keys,previousKeys,pressedKeys,stateSequence,lastAttackTick,lastSwingTick;
    int sourceCargoSlot=-1;
    float yaw,pitch,progress;
    BlockPos mining;
    BlockState miningState;
    Vec3d motion=Vec3d.ZERO;
    RemoteProtocol.Control pendingDrop;
    RemoteSession(ServerPlayerEntity player,RemoteToolEntity tool,RemoteCargoInventory cargo,long request,long token) {
        this.player=player; this.tool=tool; this.cargo=cargo; this.request=request; this.token=token;
        navigation=new RemoteReturnNavigator((from,to) -> RemoteFlightCollision.clear(tool,from,to));
        Vec3d home=player.getEyePos().add(0,-.2,0);
        navigation.record(new RemoteReturnNavigator.Point(home.x,home.y,home.z));
        navigation.record(new RemoteReturnNavigator.Point(tool.getX(),tool.getY(),tool.getZ()));
        sourceSlot=player.getInventory().selectedSlot; origin=player.getPos();
        sourceItem=player.getInventory().main.get(sourceSlot).copy(); bodyStack=sourceItem.copy();
        yaw=player.getYaw(); pitch=player.getPitch(); inputTick=player.getServer().getTicks();
        lastAttackTick=inputTick; lastSwingTick=inputTick-10;
    }
}
