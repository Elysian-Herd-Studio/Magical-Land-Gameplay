package top.csituka.magicaland.gameplay.remote;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;
import java.util.function.Predicate;
import java.util.concurrent.ConcurrentHashMap;
import net.fabricmc.fabric.api.command.v2.CommandRegistrationCallback;
import net.fabricmc.fabric.api.entity.event.v1.ServerLivingEntityEvents;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerLifecycleEvents;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerTickEvents;
import net.fabricmc.fabric.api.event.player.AttackBlockCallback;
import net.fabricmc.fabric.api.event.player.AttackEntityCallback;
import net.fabricmc.fabric.api.event.player.UseBlockCallback;
import net.fabricmc.fabric.api.event.player.UseEntityCallback;
import net.fabricmc.fabric.api.networking.v1.PacketByteBufs;
import net.fabricmc.fabric.api.networking.v1.ServerPlayConnectionEvents;
import net.fabricmc.fabric.api.networking.v1.ServerPlayNetworking;
import net.minecraft.block.BlockState;
import net.minecraft.block.ButtonBlock;
import net.minecraft.block.DoorBlock;
import net.minecraft.block.FenceGateBlock;
import net.minecraft.block.LeverBlock;
import net.minecraft.block.TrapdoorBlock;
import net.minecraft.command.argument.EntityArgumentType;
import net.minecraft.entity.Entity;
import net.minecraft.entity.EntityType;
import net.minecraft.entity.ItemEntity;
import net.minecraft.entity.mob.PathAwareEntity;
import net.minecraft.entity.passive.AnimalEntity;
import net.minecraft.entity.MovementType;
import net.minecraft.entity.SpawnGroup;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.entity.projectile.ProjectileUtil;
import net.minecraft.item.ItemStack;
import net.minecraft.item.ItemUsageContext;
import net.minecraft.item.MiningToolItem;
import net.minecraft.particle.BlockStateParticleEffect;
import net.minecraft.particle.ParticleTypes;
import net.minecraft.registry.Registries;
import net.minecraft.registry.Registry;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.sound.SoundCategory;
import net.minecraft.sound.SoundEvents;
import net.minecraft.text.Text;
import net.minecraft.util.ActionResult;
import net.minecraft.util.Hand;
import net.minecraft.util.Identifier;
import net.minecraft.util.hit.BlockHitResult;
import net.minecraft.util.hit.HitResult;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Box;
import net.minecraft.util.math.ChunkPos;
import net.minecraft.util.math.Vec3d;
import net.minecraft.world.GameRules;
import net.minecraft.world.RaycastContext;
import net.minecraft.world.World;
import static com.mojang.brigadier.arguments.IntegerArgumentType.*;
import static net.minecraft.server.command.CommandManager.*;

public final class RemoteToolServer {
    public static final Identifier CONTROL=new Identifier("magicaland_gameplay","remote_control_v2");
    public static final Identifier STATE=new Identifier("magicaland_gameplay","remote_state_v2");
    public static final String GRANT="magicaland.remote_tool";
    public static final EntityType<RemoteToolEntity> TYPE=Registry.register(Registries.ENTITY_TYPE,
            new Identifier("magicaland_gameplay","remote_tool"),EntityType.Builder
            .<RemoteToolEntity>create(RemoteToolEntity::new,SpawnGroup.MISC).setDimensions(.3f,.3f)
            .maxTrackingRange(8).trackingTickInterval(1).disableSaving().disableSummon().build("magicaland_gameplay:remote_tool"));
    private static final Map<UUID,RemoteSession> ACTIVE=new HashMap<>();
    private static final Map<UUID,ReturnFlight> RETURNS=new HashMap<>();
    private static final Map<UUID,Delivery> PENDING=new HashMap<>();
    private record Delivery(RemoteCargoInventory cargo,int sourceSlot,int sourceCargoSlot) {}
    private static final class ReturnFlight {
        final RemoteSession session;
        final int departAt;
        int stalled;
        boolean warned;
        ReturnFlight(RemoteSession session) {
            this.session=session; departAt=session.player.getServer().getTicks()+6;
        }
    }
    private static final Map<UUID,Long> PACKETS=new ConcurrentHashMap<>(),STOPS=new ConcurrentHashMap<>(),DROPS=new ConcurrentHashMap<>();
    private static final Map<UUID,Integer> STARTS=new HashMap<>();
    private static final Map<UUID,Long> REQUESTS=new HashMap<>();
    private static long nextSession;
    private static int returnCursor;
    private RemoteToolServer() {}
    public static boolean active(ServerPlayerEntity player) { return ACTIVE.containsKey(player.getUuid()); }
    static <T> T deliverToBody(ServerPlayerEntity player, java.util.function.Supplier<T> delivery) {
        RemoteSession session=ACTIVE.get(player.getUuid());
        boolean unchanged=session!=null && session.player==player && player.getInventory().selectedSlot==session.sourceSlot
                && ItemStack.areEqual(player.getInventory().main.get(session.sourceSlot),session.bodyStack);
        try { return delivery.get(); }
        finally {
            // 本体正常收货不等于玩家在远控期间切换了手持物。
            if (unchanged && ACTIVE.get(player.getUuid())==session && player.getInventory().selectedSlot==session.sourceSlot)
                session.bodyStack=player.getInventory().main.get(session.sourceSlot).copy();
        }
    }
    public static boolean owns(RemoteToolEntity tool) {
        RemoteSession session=ACTIVE.get(tool.owner());
        ReturnFlight flight=RETURNS.get(tool.owner());
        return session!=null && session.tool==tool || flight!=null && flight.session.tool==tool;
    }
    public static RemoteToolEntity temptingTool(PathAwareEntity mob,Predicate<ItemStack> food) {
        RemoteToolEntity nearest=null;
        double distance=100;
        for (RemoteSession session:ACTIVE.values()) {
            double next=mob.squaredDistanceTo(session.tool);
            if (next<distance && isTempting(session.tool,mob,food)) { nearest=session.tool; distance=next; }
        }
        return nearest;
    }
    public static boolean isTempting(RemoteToolEntity tool,PathAwareEntity mob,Predicate<ItemStack> food) {
        RemoteSession session=ACTIVE.get(tool.owner());
        return session!=null && session.tool==tool && session.rules.canInteract() && canReturn(session)
                && mob.getWorld()==tool.getWorld() && mob.isAlive() && !mob.hasVehicle() && !tool.returning()
                && mob.squaredDistanceTo(tool)<100 && food.test(session.cargo.selectedStack())
                && clearRay(tool,mob.getEyePos(),tool.getEyePos());
    }
    public static void register() {
        ServerPlayNetworking.registerGlobalReceiver(CONTROL,(server,player,handler,buf,sender) -> {
            try {
                RemoteProtocol.Control input=RemoteProtocol.readControl(buf);
                if (input.operation()!=0) {
                    var limiter=input.operation()==1?STOPS:input.operation()==3?DROPS:PACKETS;
                    long now=System.nanoTime(); Long previous=limiter.put(player.getUuid(),now);
                    if (previous!=null && now-previous<20_000_000L) return;
                }
                server.execute(() -> {
                    if (server.getPlayerManager().getPlayer(player.getUuid())!=player) return;
                    receive(player,input);
                });
            } catch (RuntimeException ignored) {}
        });
        ServerTickEvents.END_SERVER_TICK.register(server -> {
            for (RemoteSession session:ACTIVE.values().toArray(RemoteSession[]::new)) tick(session,server.getTicks());
            ReturnFlight[] returning=RETURNS.values().toArray(ReturnFlight[]::new);
            int navigationBudget=256;
            for (int i=0;i<returning.length;i++) {
                ReturnFlight flight=returning[Math.floorMod(returnCursor+i,returning.length)];
                navigationBudget-=returnTick(flight,server.getTicks(),Math.max(0,navigationBudget));
            }
            if (returning.length>0) returnCursor=(returnCursor+1)%returning.length;
            if (server.getTicks()%20==0) for (UUID owner:PENDING.keySet().toArray(UUID[]::new)) {
                var player=server.getPlayerManager().getPlayer(owner);
                if (player!=null && player.isAlive()) deliver(player);
            }
        });
        ServerPlayConnectionEvents.DISCONNECT.register((handler,server) -> {
            abort(handler.player,RemoteProtocol.INVALID); UUID owner=handler.player.getUuid();
            PACKETS.remove(owner); STOPS.remove(owner); DROPS.remove(owner); STARTS.remove(owner); REQUESTS.remove(owner);
        });
        ServerPlayConnectionEvents.JOIN.register((handler,sender,server) -> {
            var player=handler.player;
            var cargo=RemoteCargoState.get(server).inventory(player.getUuid());
            if (!cargo.isEmpty() && !active(player) && !RETURNS.containsKey(player.getUuid()))
                PENDING.putIfAbsent(player.getUuid(),new Delivery(cargo,-1,-1));
        });
        ServerLivingEntityEvents.AFTER_DEATH.register((entity,source) -> {
            if (!(entity instanceof ServerPlayerEntity player)) return;
            abort(player,RemoteProtocol.INVALID);
            RemoteCargoInventory cargo=RemoteCargoState.get(player.getServer()).inventory(player.getUuid());
            if (!player.getWorld().getGameRules().getBoolean(GameRules.KEEP_INVENTORY)) {
                cargo.dropOnDeath(stack -> {
                    ItemEntity item=new ItemEntity(player.getWorld(),player.getX(),player.getEyeY()-.3,player.getZ(),stack);
                    item.setPickupDelay(40);
                    double angle=player.getRandom().nextFloat()*Math.PI*2, speed=player.getRandom().nextFloat()*.5;
                    item.setVelocity(-Math.sin(angle)*speed,.2,Math.cos(angle)*speed);
                    return player.getServerWorld().spawnEntity(item);
                });
            }
        });
        ServerLifecycleEvents.SERVER_STOPPING.register(server -> {
            for (RemoteSession session:ACTIVE.values().toArray(RemoteSession[]::new)) abort(session.player,RemoteProtocol.INVALID);
            for (ReturnFlight flight:RETURNS.values().toArray(ReturnFlight[]::new)) abort(flight.session.player,RemoteProtocol.INVALID);
            ACTIVE.clear(); RETURNS.clear(); PENDING.clear();
            PACKETS.clear(); STOPS.clear(); DROPS.clear(); STARTS.clear(); REQUESTS.clear();
        });
        CommandRegistrationCallback.EVENT.register((dispatcher,registry,environment) -> {
            var remote=literal("remote_tool");
            for (boolean grant:new boolean[]{true,false}) remote.then(literal(grant?"grant":"revoke")
                    .then(argument("players",EntityArgumentType.players()).executes(context -> {
                        var players=EntityArgumentType.getPlayers(context,"players");
                        for (var player:players) {
                            if (grant) player.addCommandTag(GRANT);
                            else { player.removeScoreboardTag(GRANT); abort(player,RemoteProtocol.INVALID); }
                        }
                        context.getSource().sendFeedback(() -> Text.literal(grant?"已授予远控测试能力":"已撤销远控测试能力"),true);
                        return players.size();
                    })));
            remote.then(literal("slots").then(argument("players",EntityArgumentType.players())
                    .then(argument("count",integer(1,9)).executes(context -> {
                        int count=getInteger(context,"count"),changed=0;
                        for (var player:EntityArgumentType.getPlayers(context,"players")) {
                            abort(player,RemoteProtocol.INVALID); deliver(player);
                            var inventory=RemoteCargoState.get(player.getServer()).inventory(player.getUuid());
                            if (inventory.unlock(count)) changed++;
                        }
                        int result=changed;
                        context.getSource().sendFeedback(() -> Text.literal("已设置 "+result+" 位玩家的出窍槽数为 "+count+"；有物品的槽不会被裁掉"),true);
                        return changed;
                    }))));
            dispatcher.register(literal("magicaland").requires(source -> source.hasPermissionLevel(2)).then(remote));
        });
    }
    private static void receive(ServerPlayerEntity player,RemoteProtocol.Control input) {
        UUID owner=player.getUuid();
        if (input.operation()==0) {
            long previous=REQUESTS.getOrDefault(owner,0L);
            if (input.request()<=previous) return;
            REQUESTS.put(owner,input.request()); start(player,input.request()); return;
        }
        RemoteSession s=ACTIVE.get(owner);
        if (input.operation()==1) {
            REQUESTS.merge(owner,input.request(),Math::max);
            if (s!=null && s.request==input.request() && (input.session()==0
                    || s.token==input.session() && s.tool.getId()==input.entity())) stop(player,RemoteProtocol.MANUAL);
            return;
        }
        if (s==null || s.token!=input.session() || s.tool.getId()!=input.entity()
                || input.selected()>=s.cargo.unlockedSlots() || !s.rules.acceptInput(input.sequence())) return;
        s.inputTick=player.getServer().getTicks();
        if (input.selected()!=s.cargo.selectedSlot()) {
            clearMining(s); s.cargo.select(input.selected()); s.lastAttackTick=player.getServer().getTicks();
            s.tool.startAction(RemoteAction.NONE); s.tool.inventoryView(s.cargo);
        }
        if (input.operation()==3) {
            if (s.pendingDrop==null) s.pendingDrop=input;
            return;
        }
        s.pressedKeys|=input.keys()&~s.keys;
        s.keys=input.keys(); s.yaw=RemoteToolMath.wrap(input.yaw()); s.pitch=Math.max(-89,Math.min(89,input.pitch()));
    }
    private static void start(ServerPlayerEntity player,long request) {
        if (!ServerPlayNetworking.canSend(player,STATE)) return;
        if (RETURNS.containsKey(player.getUuid()) || PENDING.containsKey(player.getUuid())) {
            reject(player,request,"return_flying"); return;
        }
        if (active(player) || ACTIVE.size()+RETURNS.size()>=64) { reject(player,request,"busy"); return; }
        int now=player.getServer().getTicks(); Integer previous=STARTS.put(player.getUuid(),now);
        if (previous!=null && now-previous<10) { reject(player,request,"busy"); return; }
        if (!top.csituka.magicaland.gameplay.race.RaceServer.isUnicorn(player)) {
            reject(player,request,"race"); return;
        }
        if (!player.getCommandTags().contains(GRANT)) { reject(player,request,"grant"); return; }
        if (!eligible(player)) { reject(player,request,"stance"); return; }
        if (TelekinesisToken.isToken(player.getMainHandStack())) { reject(player,request,"deployed"); return; }
        var cargo=RemoteCargoState.get(player.getServer()).inventory(player.getUuid());
        if (!cargo.canLoad(player.getMainHandStack())) { reject(player,request,"occupied"); return; }
        RemoteToolEntity tool=new RemoteToolEntity(TYPE,player.getWorld());
        Vec3d pos=player.getEyePos().add(player.getRotationVec(1).multiply(1.2));
        tool.setPosition(pos); tool.setYaw(player.getYaw()); tool.setPitch(player.getPitch());
        if (!player.getWorld().isSpaceEmpty(tool) || !clearRay(player,player.getEyePos(),tool.getEyePos())) {
            reject(player,request,"blocked"); return;
        }
        top.csituka.magicaland.gameplay.levitation.UnicornLevitationServer.pauseForRemote(player);
        RemoteSession s=new RemoteSession(player,tool,cargo,request,++nextSession);
        tool.setup(player.getUuid(),ItemStack.EMPTY); ACTIVE.put(player.getUuid(),s);
        if (!player.getServerWorld().spawnEntity(tool)) {
            ACTIVE.remove(player.getUuid()); reject(player,request,"blocked"); return;
        }
        if (!ItemStack.areEqual(player.getInventory().main.get(s.sourceSlot),s.sourceItem)) {
            stop(player,RemoteProtocol.INVALID); return;
        }
        if (!s.sourceItem.isEmpty() && cargo.loadFrom(player.getInventory(),s.sourceSlot)==0) {
            stop(player,RemoteProtocol.INVALID); return;
        }
        if (!s.sourceItem.isEmpty()) s.sourceCargoSlot=cargo.selectedSlot();
        s.bodyStack=player.getInventory().main.get(s.sourceSlot).copy();
        tool.inventoryView(cargo); player.playerScreenHandler.sendContentUpdates(); sync(s,RemoteProtocol.NORMAL);
    }
    private static boolean eligible(ServerPlayerEntity player) {
        return player.isAlive() && !player.isSpectator() && player.isOnGround() && !player.hasVehicle()
                && !player.isSleeping() && !player.isUsingItem() && !player.isTouchingWater()
                && !player.isInLava() && !player.getAbilities().flying && player.hurtTime==0
                && player.currentScreenHandler==player.playerScreenHandler;
    }
    private static boolean clearRay(Entity source,Vec3d from,Vec3d to) {
        return loaded(source.getWorld(),new Box(from,to)) && source.getWorld().raycast(new RaycastContext(from,to,RaycastContext.ShapeType.COLLIDER,
                RaycastContext.FluidHandling.NONE,source)).getType()==HitResult.Type.MISS;
    }
    private static boolean loaded(World world,Box area) {
        return world.isRegionLoaded(BlockPos.ofFloored(area.minX-1,area.minY,area.minZ-1),
                BlockPos.ofFloored(area.maxX+1,area.maxY,area.maxZ+1));
    }
    private static boolean ownerCanTrack(ServerPlayerEntity player,Vec3d position) {
        double tracking=Math.min(player.getServer().adjustTrackingDistance(TYPE.getMaxTrackDistance()*16),
                Math.max(2,Math.min(32,player.getServer().getPlayerManager().getViewDistance()))*16);
        Vec3d offset=position.subtract(player.getPos());
        return offset.x*offset.x+offset.z*offset.z<=tracking*tracking
                && player.getServerWorld().getChunkManager().threadedAnvilChunkStorage
                .getPlayersWatchingChunk(new ChunkPos(BlockPos.ofFloored(position))).contains(player);
    }
    private static void tick(RemoteSession s,int now) {
        var p=s.player; var tool=s.tool;
        if (!eligible(p) || tool.isRemoved() || p.getWorld()!=tool.getWorld() || !p.getCommandTags().contains(GRANT)
                || !top.csituka.magicaland.gameplay.race.RaceServer.isUnicorn(p)
                || p.getPos().squaredDistanceTo(s.origin)>.09 || p.getInventory().selectedSlot!=s.sourceSlot
                || !ItemStack.areEqual(p.getInventory().main.get(s.sourceSlot),s.bodyStack)) {
            stop(p,RemoteProtocol.INVALID); return;
        }
        if (now-s.inputTick>40) { stop(p,RemoteProtocol.TIMEOUT); return; }
        if (!ownerCanTrack(p,tool.getPos()) || !loaded(p.getWorld(),tool.getBoundingBox())) {
            stop(p,RemoteProtocol.INVALID); return;
        }
        if (now-s.inputTick>5) { s.keys=0; s.pressedKeys=0; }
        double[] input=RemoteToolMath.movement(s.yaw,s.pitch,s.keys);
        Vec3d intended=new Vec3d(input[0],input[1],input[2]);
        s.motion=s.motion.lerp(intended,.4);
        Vec3d before=tool.getPos(),next=before.add(s.motion);
        if (tool.getEyePos().add(s.motion).squaredDistanceTo(p.getEyePos())<=RemoteToolMath.RANGE*RemoteToolMath.RANGE
                && p.getWorld().isChunkLoaded(BlockPos.ofFloored(next)) && ownerCanTrack(p,next)
                && loaded(p.getWorld(),tool.getBoundingBox().stretch(s.motion))
                && p.getWorld().getWorldBorder().contains(tool.getBoundingBox().offset(s.motion))
                && next.y>p.getWorld().getBottomY()+1 && next.y<p.getWorld().getTopY()-1) tool.move(MovementType.SELF,s.motion);
        Vec3d actual=tool.getPos().subtract(before);
        if (actual.lengthSquared()>1e-10)
            s.navigation.record(new RemoteReturnNavigator.Point(tool.getX(),tool.getY(),tool.getZ()));
        tool.setYaw(s.yaw); tool.setPitch(s.pitch);
        float coverage=RemoteVisibility.occlusion(p.getWorld(),p,p.getEyePos(),tool.getPos().add(0,.15,0));
        tool.setOcclusion(coverage);
        Vec3d away=tool.getPos().subtract(p.getEyePos());
        if (s.rules.visibility(coverage,tool.getX(),tool.getY(),tool.getZ(),actual.x,actual.y,actual.z,
                intended.x,intended.y,intended.z,away.x,away.y,away.z)) {
            p.sendMessage(Text.translatable("text.magicaland_gameplay.remote.lost"),true); stop(p,RemoteProtocol.LOST); return;
        }
        try (var context=RemoteActionContext.open(s)) {
            if (s.rules.canInteract()) {
                drop(s);
                interact(s,now);
            } else clearMining(s);
            tool.setAttackCooldown(context.attackCooldown(0));
        } finally { s.pendingDrop=null; s.cargo.markDirty(); }
        if (ACTIVE.get(p.getUuid())!=s) return;
        s.previousKeys=s.keys; s.pressedKeys=0;
        tool.inventoryView(s.cargo);
        Vec3d target=tool.getEyePos().subtract(p.getEyePos());
        float[] pose=RemoteToolMath.facing(target.x,target.y,target.z,p.bodyYaw,p.headYaw,p.getPitch());
        p.bodyYaw=pose[0]; p.setHeadYaw(pose[1]); p.setYaw(pose[1]); p.setPitch(pose[2]);
        if (now%2==0) sync(s,RemoteProtocol.NORMAL);
    }
    public static void stop(ServerPlayerEntity player) { stop(player,RemoteProtocol.MANUAL); }
    private static void stop(ServerPlayerEntity player,int reason) {
        RemoteSession s=ACTIVE.get(player.getUuid());
        if (s==null || !s.rules.close()) return;
        ACTIVE.remove(player.getUuid()); clearMining(s);
        s.keys=s.previousKeys=s.pressedKeys=0; s.pendingDrop=null;
        if (s.cargo.isEmpty()) s.tool.discard();
        else if (canReturn(s)) {
            s.motion=s.motion.multiply(.25);
            s.tool.beginReturn(); s.tool.inventoryView(s.cargo);
            RETURNS.put(player.getUuid(),new ReturnFlight(s));
        } else {
            s.tool.discard(); retain(s);
        }
        sync(s,reason);
    }

    private static boolean canReturn(RemoteSession s) {
        var p=s.player;
        return p.isAlive() && p.getServer().getPlayerManager().getPlayer(p.getUuid())==p
                && p.getWorld()==s.tool.getWorld() && !s.tool.isRemoved()
                && top.csituka.magicaland.gameplay.race.RaceServer.isUnicorn(p) && p.getCommandTags().contains(GRANT);
    }
    private static void retain(RemoteSession s) {
        if (!s.cargo.isEmpty()) PENDING.put(s.player.getUuid(),new Delivery(s.cargo,s.sourceSlot,s.sourceCargoSlot));
        s.cargo.markDirty();
    }
    private static void abort(ServerPlayerEntity player,int reason) {
        RemoteSession s=ACTIVE.remove(player.getUuid());
        ReturnFlight flight=RETURNS.remove(player.getUuid());
        if (s!=null) {
            s.rules.close(); clearMining(s); s.tool.discard(); retain(s); sync(s,reason);
        }
        if (flight!=null) { flight.session.tool.discard(); retain(flight.session); }
    }
    private static void deliver(ServerPlayerEntity player) {
        Delivery delivery=PENDING.remove(player.getUuid());
        if (delivery==null) return;
        if (!player.isAlive()) { PENDING.put(player.getUuid(),delivery); return; }
        try {
            delivery.cargo.returnTo(player.getInventory(),player.getInventory().main.size(),delivery.sourceSlot,delivery.sourceCargoSlot);
            delivery.cargo.dropRemainder(stack -> {
                ItemEntity item=new ItemEntity(player.getWorld(),player.getX(),player.getY()+.1,player.getZ(),stack);
                item.setVelocity(0,.08,0); item.setPickupDelay(10); item.setThrower(player.getUuid());
                return player.getServerWorld().spawnEntity(item);
            });
            player.playerScreenHandler.sendContentUpdates();
        } finally {
            delivery.cargo.markDirty();
            if (!delivery.cargo.isEmpty()) PENDING.put(player.getUuid(),delivery);
        }
    }
    private static int returnTick(ReturnFlight flight,int now,int navigationBudget) {
        RemoteSession s=flight.session;
        var p=s.player; var tool=s.tool;
        if (RETURNS.get(p.getUuid())!=flight) return 0;
        if (s.cargo.isEmpty()) { RETURNS.remove(p.getUuid()); tool.discard(); return 0; }
        if (!canReturn(s) || !loaded(tool.getWorld(),tool.getBoundingBox())
                || tool.getPos().squaredDistanceTo(p.getPos())>RemoteToolMath.RANGE*RemoteToolMath.RANGE*4) {
            abort(p,RemoteProtocol.INVALID); return 0;
        }
        if (now<flight.departAt) { tool.setVelocity(Vec3d.ZERO); return 0; }
        Vec3d home=p.getEyePos().add(0,-.2,0);
        var from=new RemoteReturnNavigator.Point(tool.getX(),tool.getY(),tool.getZ());
        var goal=new RemoteReturnNavigator.Point(home.x,home.y,home.z);
        var step=s.navigation.next(from,goal,Math.min(128,navigationBudget));
        if (step.status()==RemoteReturnNavigator.Status.ARRIVED) {
            if (tool.getPos().squaredDistanceTo(home)<=.7*.7 && RemoteFlightCollision.clear(tool,from,goal)
                    && RETURNS.remove(p.getUuid(),flight)) {
                tool.discard(); retain(s); deliver(p);
            }
            return step.expandedNodes();
        }
        Vec3d before=tool.getPos();
        if (step.status()==RemoteReturnNavigator.Status.MOVING) {
            var point=step.waypoint();
            Vec3d toward=new Vec3d(point.x()-from.x(),point.y()-from.y(),point.z()-from.z());
            double[] motion=RemoteReturnMotion.approach(s.motion.x,s.motion.y,s.motion.z,toward.x,toward.y,toward.z);
            Vec3d next=before.add(motion[0],motion[1],motion[2]);
            if (!RemoteFlightCollision.clear(tool,from,new RemoteReturnNavigator.Point(next.x,next.y,next.z))) {
                double distance=toward.length();
                Vec3d safe=distance<1e-6?Vec3d.ZERO:toward.multiply(Math.min(.12,distance)/distance);
                next=before.add(safe);
            }
            if (RemoteFlightCollision.clear(tool,from,new RemoteReturnNavigator.Point(next.x,next.y,next.z)))
                tool.move(MovementType.SELF,next.subtract(before));
        }
        s.motion=tool.getPos().subtract(before);
        tool.setVelocity(s.motion);
        if (s.motion.lengthSquared()>1e-6) {
            float yaw=(float)Math.toDegrees(Math.atan2(-s.motion.x,s.motion.z));
            float pitch=(float)-Math.toDegrees(Math.atan2(s.motion.y,s.motion.horizontalLength()));
            tool.setYaw(RemoteToolMath.approach(tool.getYaw(),yaw,12));
            tool.setPitch(RemoteToolMath.approach(tool.getPitch(),pitch,8));
            flight.stalled=0; flight.warned=false;
        } else if (++flight.stalled>=60 && !flight.warned) {
            p.sendMessage(Text.translatable("text.magicaland_gameplay.remote.return_blocked"),true); flight.warned=true;
        }
        if (now%5==0) tool.inventoryView(s.cargo);
        return step.expandedNodes();
    }
    static void clearMining(RemoteSession s) {
        if (s.mining!=null) s.tool.getWorld().setBlockBreakingInfo(s.tool.getId(),s.mining,-1);
        s.mining=null; s.miningState=null; s.progress=0;
        if (s.tool.action()==RemoteAction.MINING) s.tool.startAction(RemoteAction.NONE);
    }
    public static boolean canAttack(ServerPlayerEntity player,RemoteToolEntity tool,Entity target) {
        return canAttack(player,tool,target,target.getBoundingBox().getCenter());
    }
    private static boolean canAttack(ServerPlayerEntity player,RemoteToolEntity tool,Entity target,Vec3d point) {
        return target!=player && RemoteTargeting.attackable(target) && target.getWorld()==tool.getWorld()
                && (!tool.autonomous() || TelekinesisServer.allowsAttack(player,tool,target))
                && (!(target instanceof PlayerEntity other) || player.shouldDamagePlayer(other))
                && point!=null && clearRay(tool,tool.getEyePos(),point);
    }

    static void automaticAttack(RemoteSession s,net.minecraft.entity.LivingEntity target,int now) {
        if (!TelekinesisServer.canWork(s.tool) || !canAttack(s.player,s.tool,target)
                || s.tool.getEyePos().squaredDistanceTo(target.getBoundingBox().getCenter())>2.8*2.8) return;
        s.bodyStack=s.player.getInventory().main.get(s.player.getInventory().selectedSlot).copy();
        try (var context=RemoteActionContext.open(s)) {
            if (context.attackCooldown(0)<1) return;
            var hit=new net.minecraft.util.hit.EntityHitResult(target);
            if (AttackEntityCallback.EVENT.invoker().interact(s.player,s.player.getWorld(),Hand.MAIN_HAND,target,hit)!=ActionResult.PASS
                    || !TelekinesisServer.canWork(s.tool) || !canAttack(s.player,s.tool,target)) return;
            s.tool.startAction(RemoteAction.SWING); s.lastSwingTick=now;
            context.resetHit(); s.player.attack(target); s.player.resetLastAttackedTicks();
            if (context.hit() && s.tool.action()!=RemoteAction.TOOL_BREAK) s.tool.startAction(RemoteAction.HIT);
            s.tool.setAttackCooldown(context.attackCooldown(0));
        } finally { s.cargo.markDirty(); }
    }

    static boolean automaticMine(RemoteSession s,BlockHitResult hit,int now) {
        if (!TelekinesisServer.allowsGather(s.tool,hit.getBlockPos())) return false;
        var actual=TelekinesisSight.blockHit(s.tool,s.tool.getEyePos(),hit.getBlockPos(),2.6);
        if (actual==null) { clearMining(s); return false; }
        var world=s.player.getServerWorld(); var before=world.getBlockState(hit.getBlockPos());
        s.bodyStack=s.player.getInventory().main.get(s.player.getInventory().selectedSlot).copy();
        try (var context=RemoteActionContext.open(s)) { mine(s,actual,now); }
        finally { s.cargo.markDirty(); }
        return !world.getBlockState(hit.getBlockPos()).equals(before);
    }
    private static void drop(RemoteSession s) {
        var request=s.pendingDrop;
        if (request==null) return;
        var tool=s.tool; var world=s.player.getServerWorld();
        Vec3d from=tool.getPos().add(0,.025,0);
        boolean dropped=s.cargo.dropFrom(request.selected(),request.flags()==1,stack -> {
            ItemEntity item=new ItemEntity(world,from.x,from.y,from.z,stack);
            if (!world.isChunkLoaded(item.getBlockPos()) || !world.getWorldBorder().contains(item.getBoundingBox())
                    || !world.isSpaceEmpty(item) || !clearRay(tool,tool.getEyePos(),item.getBoundingBox().getCenter())) return false;
            item.setVelocity(tool.getRotationVec(1).multiply(.18).add(0,.08,0));
            item.setPickupDelay(40); item.setThrower(s.player.getUuid());
            return world.spawnEntity(item);
        });
        if (dropped) {
            clearMining(s); tool.startAction(RemoteAction.SWING);
            tool.inventoryView(s.cargo);
        }
        sync(s,RemoteProtocol.NORMAL);
    }
    private static void interact(RemoteSession s,int now) {
        var p=s.player; var world=p.getServerWorld(); var tool=s.tool;
        ItemStack stack=s.cargo.selectedStack();
        Vec3d from=tool.getEyePos(),to=from.add(tool.getRotationVec(1).multiply(3));
        if (!loaded(world,new Box(from,to))) { clearMining(s); return; }
        BlockHitResult hit=world.raycast(new RaycastContext(from,to,RaycastContext.ShapeType.OUTLINE,RaycastContext.FluidHandling.NONE,tool));
        boolean attack=(s.keys&64)!=0 || (s.pressedKeys&64)!=0;
        boolean usePressed=(((s.keys&~s.previousKeys)|s.pressedKeys)&128)!=0;
        boolean melee=RemoteCombat.canAttack(stack);
        double reach=hit.getType()==HitResult.Type.MISS?9:from.squaredDistanceTo(hit.getPos());
        var victim=ProjectileUtil.raycast(tool,from,to,tool.getBoundingBox().stretch(to.subtract(from)).expand(1),
                entity -> entity!=p && RemoteTargeting.attackable(entity),reach);
        if (attack && melee && victim!=null) {
            clearMining(s); var target=victim.getEntity();
            if (p.getAttackCooldownProgress(0)>=1) {
                tool.startAction(RemoteAction.SWING); s.lastSwingTick=now;
                if (canAttack(p,tool,target,victim.getPos()) && AttackEntityCallback.EVENT.invoker().interact(p,world,Hand.MAIN_HAND,target,victim)==ActionResult.PASS
                        && canStrike(s,target)) {
                    RemoteActionContext.current().resetHit(); p.attack(target);
                    p.resetLastAttackedTicks();
                    if (tool.action()!=RemoteAction.TOOL_BREAK && RemoteActionContext.current().hit()) tool.startAction(RemoteAction.HIT);
                } else p.resetLastAttackedTicks();
            }
        } else if (attack && stack.getItem() instanceof MiningToolItem && hit.getType()==HitResult.Type.BLOCK) {
            mine(s,hit,now);
        } else {
            clearMining(s);
            if (attack && melee && now-s.lastSwingTick>=6) {
                tool.startAction(RemoteAction.SWING); s.lastSwingTick=now; p.resetLastAttackedTicks();
                world.playSound(null,tool.getX(),tool.getY(),tool.getZ(),SoundEvents.ENTITY_PLAYER_ATTACK_WEAK,SoundCategory.PLAYERS,.6f,1);
            }
        }
        if (s.rules.phase()==RemoteSessionRules.Phase.CLOSED) return;
        if (usePressed && victim!=null && victim.getEntity() instanceof AnimalEntity animal) {
            clearMining(s);
            if (canFeed(s,animal)) {
                ActionResult result=UseEntityCallback.EVENT.invoker().interact(p,world,Hand.MAIN_HAND,animal,victim);
                if (result==ActionResult.PASS && canFeed(s,animal)) result=p.interact(animal,Hand.MAIN_HAND);
                if (s.rules.canInteract()) tool.startAction(result.isAccepted()?RemoteAction.USE:RemoteAction.SWING);
            }
        } else if (usePressed && victim==null && hit.getType()==HitResult.Type.BLOCK && world.canPlayerModifyAt(p,hit.getBlockPos())) {
            clearMining(s);
            var state=world.getBlockState(hit.getBlockPos()); var block=state.getBlock();
            boolean mechanism=block instanceof DoorBlock || block instanceof TrapdoorBlock || block instanceof FenceGateBlock
                    || block instanceof LeverBlock || block instanceof ButtonBlock;
            if (mechanism || stack.getItem() instanceof MiningToolItem) {
                ActionResult result=UseBlockCallback.EVENT.invoker().interact(p,world,Hand.MAIN_HAND,hit);
                if (result==ActionResult.PASS) {
                    if (mechanism) result=state.onUse(world,p,Hand.MAIN_HAND,hit);
                    else result=stack.useOnBlock(new ItemUsageContext(world,p,Hand.MAIN_HAND,stack,hit) {
                        @Override public float getPlayerYaw() { return tool.getYaw(); }
                        @Override public net.minecraft.util.math.Direction getHorizontalPlayerFacing() { return tool.getHorizontalFacing(); }
                    });
                }
                if (tool.action()!=RemoteAction.TOOL_BREAK) tool.startAction(result.isAccepted()?RemoteAction.USE:RemoteAction.SWING);
            }
        }
        if (s.rules.phase()==RemoteSessionRules.Phase.CLOSED) return;
        for (ItemEntity item:world.getEntitiesByClass(ItemEntity.class,tool.getBoundingBox().expand(.45),
                item -> RemotePickup.canPickup(item,p.getUuid()))) {
            if (!RemotePickup.clearPath(world,tool,from,item) || !RemotePickup.collect(s.cargo,item,p.getUuid())) continue;
            world.playSound(null,tool.getX(),tool.getY(),tool.getZ(),SoundEvents.ENTITY_ITEM_PICKUP,SoundCategory.PLAYERS,.2f,1);
        }
    }
    private static boolean canStrike(RemoteSession s,Entity target) {
        var p=s.player; var tool=s.tool;
        if (ACTIVE.get(p.getUuid())!=s || !s.rules.canInteract() || !canReturn(s)
                || !RemoteCombat.canAttack(s.cargo.selectedStack()) || p.getAttackCooldownProgress(0)<1) return false;
        Vec3d from=tool.getEyePos(),to=from.add(tool.getRotationVec(1).multiply(3));
        Vec3d point=RemoteTargeting.hitPoint(target.getBoundingBox().expand(target.getTargetingMargin()),from,to);
        return canAttack(p,tool,target,point);
    }
    private static boolean canFeed(RemoteSession s,AnimalEntity animal) {
        var p=s.player; var tool=s.tool; var world=p.getServerWorld();
        var food=s.cargo.selectedStack();
        if (ACTIVE.get(p.getUuid())!=s || !s.rules.canInteract() || !canReturn(s)
                || animal.getWorld()!=world || !animal.isAlive() || animal.hasPassengers()
                || p.shouldCancelInteraction() || food.isEmpty() || !animal.isBreedingItem(food)
                || !world.canPlayerModifyAt(p,animal.getBlockPos())) return false;
        Vec3d from=tool.getEyePos();
        var box=animal.getBoundingBox().expand(animal.getTargetingMargin());
        Vec3d point=box.contains(from)?from:box.raycast(from,from.add(tool.getRotationVec(1).multiply(3))).orElse(null);
        return point!=null && clearRay(tool,from,point);
    }
    private static void mine(RemoteSession s,BlockHitResult hit,int now) {
        var p=s.player; var world=p.getServerWorld(); var tool=s.tool;
        BlockPos pos=hit.getBlockPos(); BlockState state=world.getBlockState(pos);
        if (!world.canPlayerModifyAt(p,pos) || !p.canModifyBlocks() || state.getHardness(world,pos)<0
                || p.isBlockBreakingRestricted(world,pos,p.interactionManager.getGameMode())) { clearMining(s); return; }
        if (!pos.equals(s.mining) || state!=s.miningState) {
            clearMining(s);
            if (AttackBlockCallback.EVENT.invoker().interact(p,world,Hand.MAIN_HAND,pos,hit.getSide())!=ActionResult.PASS) return;
            if (tool.autonomous() && (!TelekinesisServer.allowsGather(tool,pos) || world.getBlockState(pos)!=state)) return;
            s.mining=pos; s.miningState=state; tool.startAction(RemoteAction.MINING);
        }
        s.progress+=p.isCreative()?1:state.calcBlockBreakingDelta(p,world,pos);
        world.setBlockBreakingInfo(tool.getId(),pos,Math.min(9,(int)(s.progress*10)));
        if (now%4==0) {
            var sound=state.getSoundGroup(); Vec3d at=hit.getPos();
            world.playSound(null,at.x,at.y,at.z,sound.getHitSound(),SoundCategory.BLOCKS,(sound.getVolume()+1)/8,sound.getPitch()*.5f);
            world.spawnParticles(new BlockStateParticleEffect(ParticleTypes.BLOCK,state),at.x,at.y,at.z,2,.03,.03,.03,.02);
        }
        if (s.progress>=1) {
            boolean broken=p.interactionManager.tryBreakBlock(pos); clearMining(s);
            if (broken && tool.action()!=RemoteAction.TOOL_BREAK) tool.startAction(RemoteAction.BREAK);
        }
    }
    private static void reject(ServerPlayerEntity player,long request,String reason) {
        player.sendMessage(Text.translatable("text.magicaland_gameplay.remote."+reason),true);
        sendState(player,request,0,-1,0,-1,RemoteProtocol.REJECTED,0,
                RemoteCargoState.get(player.getServer()).inventory(player.getUuid()));
    }
    private static void sync(RemoteSession s,int reason) {
        sendState(s.player,s.request,s.token,s.rules.phase()==RemoteSessionRules.Phase.CLOSED?-1:s.tool.getId(),
                ++s.stateSequence,s.rules.sequence(),reason,s.tool.occlusion(),s.cargo);
    }
    private static void sendState(ServerPlayerEntity player,long request,long session,int entity,int sequence,int ack,
                                  int reason,float occlusion,RemoteCargoInventory cargo) {
        if (!ServerPlayNetworking.canSend(player,STATE)) return;
        var state=PacketByteBufs.create();
        state.writeLong(request).writeLong(session).writeInt(entity).writeInt(sequence).writeInt(ack).writeByte(reason)
                .writeByte(cargo.unlockedSlots()).writeByte(cargo.selectedSlot()).writeFloat(occlusion);
        for (int i=0;i<cargo.unlockedSlots();i++) state.writeItemStack(cargo.getStack(i));
        ServerPlayNetworking.send(player,STATE,state);
    }
}
