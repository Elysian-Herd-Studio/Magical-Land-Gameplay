package top.csituka.magicaland.gameplay.remote;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import net.fabricmc.fabric.api.command.v2.CommandRegistrationCallback;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerLifecycleEvents;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerTickEvents;
import net.fabricmc.fabric.api.entity.event.v1.ServerLivingEntityEvents;
import net.fabricmc.fabric.api.networking.v1.PacketByteBufs;
import net.fabricmc.fabric.api.networking.v1.ServerPlayConnectionEvents;
import net.fabricmc.fabric.api.networking.v1.ServerPlayNetworking;
import net.minecraft.command.argument.EntityArgumentType;
import net.minecraft.entity.EntityType;
import net.minecraft.entity.MovementType;
import net.minecraft.entity.SpawnGroup;
import net.minecraft.item.ItemStack;
import net.minecraft.item.MiningToolItem;
import net.minecraft.item.SwordItem;
import net.minecraft.item.ItemUsageContext;
import net.minecraft.entity.ItemEntity;
import net.minecraft.entity.LivingEntity;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.entity.projectile.ProjectileUtil;
import net.minecraft.block.*;
import net.minecraft.util.Hand;
import net.minecraft.util.ActionResult;
import net.minecraft.util.hit.BlockHitResult;
import net.fabricmc.fabric.api.event.player.AttackEntityCallback;
import net.fabricmc.fabric.api.event.player.AttackBlockCallback;
import net.fabricmc.fabric.api.event.player.UseBlockCallback;
import net.minecraft.registry.Registries;
import net.minecraft.registry.Registry;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.text.Text;
import net.minecraft.util.Identifier;
import net.minecraft.util.hit.HitResult;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Vec3d;
import net.minecraft.world.RaycastContext;
import net.minecraft.world.GameRules;
import static net.minecraft.server.command.CommandManager.*;

public final class RemoteToolServer {
    public static final Identifier CONTROL = new Identifier("magicaland_gameplay", "remote_control");
    public static final Identifier STATE = new Identifier("magicaland_gameplay", "remote_state");
    public static final String GRANT = "magicaland.remote_tool";
    public static final EntityType<RemoteToolEntity> TYPE = Registry.register(Registries.ENTITY_TYPE,
            new Identifier("magicaland_gameplay", "remote_tool"), EntityType.Builder
            .<RemoteToolEntity>create(RemoteToolEntity::new, SpawnGroup.MISC).setDimensions(.3f, .3f)
            .maxTrackingRange(8).trackingTickInterval(1).disableSaving().disableSummon().build("magicaland_gameplay:remote_tool"));
    private static final Map<UUID, Session> ACTIVE = new HashMap<>();
    private static final Map<UUID, Long> PACKETS = new ConcurrentHashMap<>();
    private static final Map<UUID, Long> STOPS = new ConcurrentHashMap<>();
    private static final Map<UUID, Integer> STARTS = new HashMap<>();
    private RemoteToolServer() {}
    private static final class Session {
        final ServerPlayerEntity player;
        final RemoteToolEntity tool;
        ItemStack original;
        final int slot;
        final Vec3d origin;
        int inputTick, blocked, keys;
        float yaw, pitch;
        boolean used;
        final RemoteCargoInventory cargo;
        BlockPos mining;
        float progress;
        BlockState miningState;
        Vec3d motion=Vec3d.ZERO;
        Session(ServerPlayerEntity player, RemoteToolEntity tool) {
            this.player=player; this.tool=tool; original=player.getMainHandStack().copy();
            slot=player.getInventory().selectedSlot; origin=player.getPos();
            cargo=RemoteCargoState.get(player.getServer()).inventory(player.getUuid());
            yaw=player.getYaw(); pitch=player.getPitch(); inputTick=player.getServer().getTicks();
        }
    }
    public static boolean active(ServerPlayerEntity player) { return ACTIVE.containsKey(player.getUuid()); }
    public static boolean owns(RemoteToolEntity tool) {
        Session session=ACTIVE.get(tool.owner()); return session != null && session.tool == tool;
    }
    public static void register() {
        ServerPlayNetworking.registerGlobalReceiver(CONTROL, (server, player, handler, buf, sender) -> {
            try {
                if (buf.readableBytes() > 16) return;
                int op=buf.readUnsignedByte(), id=op == 0 ? -1 : buf.readInt();
                float yaw=op == 2 ? buf.readFloat() : 0, pitch=op == 2 ? buf.readFloat() : 0;
                int keys=op == 2 ? buf.readUnsignedByte() : 0;
                if (op > 2 || buf.isReadable() || !Float.isFinite(yaw) || !Float.isFinite(pitch)) return;
                long now=System.nanoTime(); Long previous=(op==1?STOPS:PACKETS).put(player.getUuid(), now);
                if (previous != null && now-previous < 20_000_000L) return;
                server.execute(() -> {
                    if (server.getPlayerManager().getPlayer(player.getUuid()) != player) return;
                    if (op == 0) { start(player); return; }
                    Session session=ACTIVE.get(player.getUuid());
                    if (session == null || session.tool.getId() != id) return;
                    if (op == 1) stop(player);
                    else { session.yaw=RemoteToolMath.wrap(yaw); session.pitch=Math.max(-89, Math.min(89,pitch));
                        session.keys=keys; session.inputTick=server.getTicks(); }
                });
            } catch (RuntimeException ignored) {}
        });
        ServerTickEvents.END_SERVER_TICK.register(server -> {
            for (Session session : ACTIVE.values().toArray(Session[]::new)) tick(session, server.getTicks());
        });
        ServerPlayConnectionEvents.DISCONNECT.register((handler, server) -> {
            stop(handler.player); PACKETS.remove(handler.player.getUuid()); STOPS.remove(handler.player.getUuid()); STARTS.remove(handler.player.getUuid());
        });
        ServerLivingEntityEvents.AFTER_DEATH.register((entity,source) -> {
            if (!(entity instanceof ServerPlayerEntity player)) return;
            stop(player);
            var cargo=RemoteCargoState.get(player.getServer()).inventory(player.getUuid());
            if (!player.getWorld().getGameRules().getBoolean(GameRules.KEEP_INVENTORY)) {
                ItemStack dropped=cargo.removeStack(0);
                if (!dropped.isEmpty()) player.dropItem(dropped,true,false);
            }
        });
        ServerLifecycleEvents.SERVER_STOPPING.register(server -> {
            for (Session session : ACTIVE.values().toArray(Session[]::new)) stop(session.player);
            PACKETS.clear(); STOPS.clear(); STARTS.clear();
        });
        CommandRegistrationCallback.EVENT.register((dispatcher, registry, environment) -> {
            var command=literal("magicaland").requires(source -> source.hasPermissionLevel(2));
            var remote=literal("remote_tool");
            for (boolean grant : new boolean[] {true,false}) remote.then(literal(grant ? "grant" : "revoke")
                    .then(argument("players", EntityArgumentType.players()).executes(context -> {
                        var players=EntityArgumentType.getPlayers(context,"players");
                        for (var player : players) {
                            if (grant) player.addCommandTag(GRANT);
                            else { player.removeScoreboardTag(GRANT); stop(player); }
                        }
                        context.getSource().sendFeedback(() -> Text.literal(grant ? "已授予远控测试能力" : "已撤销远控测试能力"), true);
                        return players.size();
                    })));
            dispatcher.register(command.then(remote));
        });
    }
    private static void start(ServerPlayerEntity player) {
        if (active(player) || !ServerPlayNetworking.canSend(player, STATE)) return;
        if (ACTIVE.size()>=64) { reject(player,"busy"); return; }
        int now=player.getServer().getTicks(); Integer previous=STARTS.put(player.getUuid(), now);
        if (previous != null && now-previous < 10) return;
        if (!player.getCommandTags().contains(GRANT)) { reject(player,"grant"); return; }
        if (!eligible(player)) { reject(player,"stance"); return; }
        var cargo=RemoteCargoState.get(player.getServer()).inventory(player.getUuid());
        if (!player.getMainHandStack().isEmpty() && !cargo.isEmpty()) { reject(player,"occupied"); return; }
        RemoteToolEntity tool=new RemoteToolEntity(TYPE,player.getWorld());
        Vec3d pos=player.getEyePos().add(player.getRotationVec(1).multiply(1.2));
        tool.setPosition(pos); tool.setYaw(player.getYaw()); tool.setPitch(player.getPitch());
        if (!player.getWorld().isSpaceEmpty(tool) || !visible(player,tool.getEyePos())) { reject(player,"blocked"); return; }
        tool.setup(player.getUuid(),player.getMainHandStack());
        if (!tool.carriesOriginal()) tool.updateStack(cargo.getStack(0));
        ACTIVE.put(player.getUuid(),new Session(player,tool));
        if (!player.getServerWorld().spawnEntity(tool)) { ACTIVE.remove(player.getUuid()); reject(player,"blocked"); return; }
        sync(player,tool.getId());
    }
    private static boolean eligible(ServerPlayerEntity player) {
        return player.isAlive() && !player.isSpectator() && player.isOnGround() && !player.hasVehicle()
                && !player.isSleeping() && !player.isUsingItem() && !player.isTouchingWater()
                && !player.isInLava() && !player.getAbilities().flying && player.hurtTime == 0
                && player.currentScreenHandler==player.playerScreenHandler;
    }
    private static boolean visible(ServerPlayerEntity player, Vec3d pos) {
        return player.getWorld().raycast(new RaycastContext(player.getEyePos(),pos,
                RaycastContext.ShapeType.COLLIDER,RaycastContext.FluidHandling.NONE,player)).getType() == HitResult.Type.MISS;
    }
    private static void tick(Session s, int now) {
        var p=s.player; var tool=s.tool;
        if (!eligible(p) || tool.isRemoved() || p.getWorld()!=tool.getWorld() || !p.getCommandTags().contains(GRANT)
                || p.getPos().squaredDistanceTo(s.origin) > .09 || p.getInventory().selectedSlot!=s.slot
                || !ItemStack.areEqual(p.getMainHandStack(),s.original) || now-s.inputTick > 40) { stop(p); return; }
        if (now-s.inputTick > 5) s.keys=0;
        double[] move=RemoteToolMath.movement(s.yaw,s.pitch,s.keys);
        s.motion=s.motion.lerp(new Vec3d(move[0],move[1],move[2]),.4);
        Vec3d delta=s.motion, next=tool.getPos().add(delta);
        if (tool.getEyePos().add(delta).squaredDistanceTo(p.getEyePos()) <= RemoteToolMath.RANGE*RemoteToolMath.RANGE
                && p.getWorld().isChunkLoaded(BlockPos.ofFloored(next)) && p.getWorld().getWorldBorder().contains(BlockPos.ofFloored(next))
                && next.y > p.getWorld().getBottomY()+1 && next.y < p.getWorld().getTopY()-1) tool.move(MovementType.SELF,delta);
        tool.setYaw(s.yaw); tool.setPitch(s.pitch);
        if (!visible(p,tool.getEyePos())) { if (++s.blocked >= 6) { reject(p,"lost"); stop(p); return; } }
        else s.blocked=0;
        if (s.blocked==0) {
            try (var action=RemoteActionContext.open(p,tool)) { interact(s); }
        }
        else clearMining(s);
        if (tool.carriesOriginal() && p.getMainHandStack().isEmpty()) { stop(p); return; }
        Vec3d target=tool.getEyePos().subtract(p.getEyePos());
        float[] pose=RemoteToolMath.facing(target.x,target.y,target.z,p.bodyYaw,p.headYaw,p.getPitch());
        p.bodyYaw=pose[0]; p.setHeadYaw(pose[1]); p.setYaw(pose[1]); p.setPitch(pose[2]);
        if (now%10 == 0) sync(p,tool.getId());
    }
    public static void stop(ServerPlayerEntity player) {
        Session session=ACTIVE.remove(player.getUuid());
        if (session != null) {
            clearMining(session);
            if (player.isAlive()) {
                session.cargo.returnTo(player.getInventory(),player.getInventory().main.size());
                if (!session.cargo.isEmpty()) player.sendMessage(Text.translatable("text.magicaland_gameplay.remote.cargo_retained"),false);
            }
            session.tool.discard(); sync(player,-1);
        }
    }
    private static void clearMining(Session s) {
        if (s.mining!=null) s.player.getServerWorld().setBlockBreakingInfo(s.tool.getId(),s.mining,-1);
        s.mining=null; s.miningState=null; s.progress=0;
    }
    private static void interact(Session s) {
        var p=s.player; var world=p.getServerWorld(); var tool=s.tool;
        Vec3d from=tool.getEyePos(), to=from.add(tool.getRotationVec(1).multiply(3));
        BlockHitResult hit=world.raycast(new RaycastContext(from,to,RaycastContext.ShapeType.OUTLINE,RaycastContext.FluidHandling.NONE,tool));
        boolean attack=(s.keys&64)!=0, use=(s.keys&128)!=0;
        boolean usePressed=use && !s.used; s.used=use;
        boolean melee=p.getMainHandStack().getItem() instanceof MiningToolItem || p.getMainHandStack().getItem() instanceof SwordItem;
        double reach=hit.getType()==HitResult.Type.MISS ? 9 : from.squaredDistanceTo(hit.getPos());
        var victim=ProjectileUtil.raycast(tool,from,to,tool.getBoundingBox().stretch(to.subtract(from)).expand(1),
                entity -> entity!=p && entity instanceof LivingEntity && entity.isAlive() && !entity.isSpectator() && entity.canHit(),reach);
        if (attack && melee && victim!=null) {
            clearMining(s);
            var target=victim.getEntity();
            if (p.getAttackCooldownProgress(0)>=1 && (!(target instanceof PlayerEntity other) || p.shouldDamagePlayer(other))
                    && AttackEntityCallback.EVENT.invoker().interact(p,world,Hand.MAIN_HAND,target,victim)==ActionResult.PASS) p.attack(target);
        } else if (attack && p.getMainHandStack().getItem() instanceof MiningToolItem && hit.getType()==HitResult.Type.BLOCK) {
            BlockPos pos=hit.getBlockPos(); var state=world.getBlockState(pos);
            if (!world.canPlayerModifyAt(p,pos) || !p.canModifyBlocks() || state.getHardness(world,pos)<0) { clearMining(s); return; }
            if (!pos.equals(s.mining) || state!=s.miningState) {
                clearMining(s);
                if (AttackBlockCallback.EVENT.invoker().interact(p,world,Hand.MAIN_HAND,pos,hit.getSide())!=ActionResult.PASS) return;
                s.mining=pos; s.miningState=state;
            }
            s.progress+=p.isCreative()?1:state.calcBlockBreakingDelta(p,world,pos);
            world.setBlockBreakingInfo(tool.getId(),pos,Math.min(9,(int)(s.progress*10)));
            if (s.progress>=1) { p.interactionManager.tryBreakBlock(pos); clearMining(s); }
        } else clearMining(s);
        if (usePressed && hit.getType()==HitResult.Type.BLOCK && world.canPlayerModifyAt(p,hit.getBlockPos())) {
            var state=world.getBlockState(hit.getBlockPos()); var block=state.getBlock();
            ActionResult callback=UseBlockCallback.EVENT.invoker().interact(p,world,Hand.MAIN_HAND,hit);
            if (callback==ActionResult.PASS) {
                if (block instanceof DoorBlock || block instanceof TrapdoorBlock || block instanceof FenceGateBlock
                        || block instanceof LeverBlock || block instanceof ButtonBlock) state.onUse(world,p,Hand.MAIN_HAND,hit);
                else if (p.getMainHandStack().getItem() instanceof MiningToolItem)
                    p.getMainHandStack().useOnBlock(new ItemUsageContext(p,Hand.MAIN_HAND,hit));
            }
        }
        for (ItemEntity item : world.getEntitiesByClass(ItemEntity.class,tool.getBoundingBox().expand(.45),
                item -> !item.isRemoved() && !item.cannotPickup() && (item.getOwner()==null || p.getUuid().equals(item.getOwner())))) {
            if (tool.carriesOriginal()) break;
            if (world.raycast(new RaycastContext(from,item.getPos(),RaycastContext.ShapeType.COLLIDER,RaycastContext.FluidHandling.NONE,tool)).getType()!=HitResult.Type.MISS) continue;
            ItemStack remainder=s.cargo.addStack(item.getStack());
            if (remainder.getCount()==item.getStack().getCount()) continue;
            if (remainder.isEmpty()) item.discard();
            else item.setStack(remainder);
            tool.updateStack(s.cargo.getStack(0));
            sync(p,tool.getId());
        }
        if (!ItemStack.areEqual(s.original,p.getMainHandStack())) {
            s.original=p.getMainHandStack().copy(); tool.updateStack(s.original);
        }
    }
    private static void reject(ServerPlayerEntity player, String reason) {
        player.sendMessage(Text.translatable("text.magicaland_gameplay.remote."+reason),true);
        sync(player,-1);
    }
    private static void sync(ServerPlayerEntity player,int id) {
        if (ServerPlayNetworking.canSend(player,STATE)) {
            var state=PacketByteBufs.create(); state.writeInt(id);
            Session session=ACTIVE.get(player.getUuid());
            state.writeItemStack(session!=null && session.tool.carriesOriginal()?session.tool.stack():
                    RemoteCargoState.get(player.getServer()).inventory(player.getUuid()).getStack(0));
            ServerPlayNetworking.send(player,STATE,state);
        }
    }
}
