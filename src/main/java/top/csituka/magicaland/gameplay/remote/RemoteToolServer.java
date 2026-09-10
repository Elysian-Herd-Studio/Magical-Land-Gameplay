package top.csituka.magicaland.gameplay.remote;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import net.fabricmc.fabric.api.command.v2.CommandRegistrationCallback;
import net.fabricmc.fabric.api.entity.event.v1.ServerLivingEntityEvents;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerLifecycleEvents;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerTickEvents;
import net.fabricmc.fabric.api.event.player.AttackBlockCallback;
import net.fabricmc.fabric.api.event.player.AttackEntityCallback;
import net.fabricmc.fabric.api.event.player.UseBlockCallback;
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
import net.minecraft.entity.LivingEntity;
import net.minecraft.entity.MovementType;
import net.minecraft.entity.SpawnGroup;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.entity.projectile.ProjectileUtil;
import net.minecraft.enchantment.EnchantmentHelper;
import net.minecraft.item.ItemStack;
import net.minecraft.item.ItemUsageContext;
import net.minecraft.item.MiningToolItem;
import net.minecraft.item.SwordItem;
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
import net.minecraft.util.math.Vec3d;
import net.minecraft.world.GameRules;
import net.minecraft.world.RaycastContext;
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
    private static final Map<UUID,Long> PACKETS=new ConcurrentHashMap<>(),STOPS=new ConcurrentHashMap<>(),DROPS=new ConcurrentHashMap<>();
    private static final Map<UUID,Integer> STARTS=new HashMap<>();
    private static final Map<UUID,Long> REQUESTS=new HashMap<>();
    private static long nextSession;
    private RemoteToolServer() {}
    public static boolean active(ServerPlayerEntity player) { return ACTIVE.containsKey(player.getUuid()); }
    public static boolean owns(RemoteToolEntity tool) {
        RemoteSession session=ACTIVE.get(tool.owner()); return session!=null && session.tool==tool;
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
        });
        ServerPlayConnectionEvents.DISCONNECT.register((handler,server) -> {
            stop(handler.player); UUID owner=handler.player.getUuid();
            PACKETS.remove(owner); STOPS.remove(owner); DROPS.remove(owner); STARTS.remove(owner); REQUESTS.remove(owner);
        });
        ServerLivingEntityEvents.AFTER_DEATH.register((entity,source) -> {
            if (!(entity instanceof ServerPlayerEntity player)) return;
            stop(player,RemoteProtocol.INVALID);
            RemoteCargoInventory cargo=RemoteCargoState.get(player.getServer()).inventory(player.getUuid());
            if (!player.getWorld().getGameRules().getBoolean(GameRules.KEEP_INVENTORY)) {
                for (ItemStack stack:cargo.takeAll()) if (!EnchantmentHelper.hasVanishingCurse(stack)
                        && player.dropItem(stack,true,false)==null) cargo.retain(stack);
            }
        });
        ServerLifecycleEvents.SERVER_STOPPING.register(server -> {
            for (RemoteSession session:ACTIVE.values().toArray(RemoteSession[]::new)) stop(session.player);
            PACKETS.clear(); STOPS.clear(); DROPS.clear(); STARTS.clear(); REQUESTS.clear();
        });
        CommandRegistrationCallback.EVENT.register((dispatcher,registry,environment) -> {
            var remote=literal("remote_tool");
            for (boolean grant:new boolean[]{true,false}) remote.then(literal(grant?"grant":"revoke")
                    .then(argument("players",EntityArgumentType.players()).executes(context -> {
                        var players=EntityArgumentType.getPlayers(context,"players");
                        for (var player:players) {
                            if (grant) player.addCommandTag(GRANT);
                            else { player.removeScoreboardTag(GRANT); stop(player,RemoteProtocol.INVALID); }
                        }
                        context.getSource().sendFeedback(() -> Text.literal(grant?"已授予远控测试能力":"已撤销远控测试能力"),true);
                        return players.size();
                    })));
            remote.then(literal("slots").then(argument("players",EntityArgumentType.players())
                    .then(argument("count",integer(1,9)).executes(context -> {
                        int count=getInteger(context,"count"),changed=0;
                        for (var player:EntityArgumentType.getPlayers(context,"players")) {
                            stop(player);
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
        if (active(player) || ACTIVE.size()>=64) { reject(player,request,"busy"); return; }
        int now=player.getServer().getTicks(); Integer previous=STARTS.put(player.getUuid(),now);
        if (previous!=null && now-previous<10) { reject(player,request,"busy"); return; }
        if (!player.getCommandTags().contains(GRANT)) { reject(player,request,"grant"); return; }
        if (!eligible(player)) { reject(player,request,"stance"); return; }
        var cargo=RemoteCargoState.get(player.getServer()).inventory(player.getUuid());
        if (!cargo.canLoad(player.getMainHandStack())) { reject(player,request,"occupied"); return; }
        RemoteToolEntity tool=new RemoteToolEntity(TYPE,player.getWorld());
        Vec3d pos=player.getEyePos().add(player.getRotationVec(1).multiply(1.2));
        tool.setPosition(pos); tool.setYaw(player.getYaw()); tool.setPitch(player.getPitch());
        if (!player.getWorld().isSpaceEmpty(tool) || !clearRay(player,player.getEyePos(),tool.getEyePos())) {
            reject(player,request,"blocked"); return;
        }
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
        return source.getWorld().raycast(new RaycastContext(from,to,RaycastContext.ShapeType.COLLIDER,
                RaycastContext.FluidHandling.NONE,source)).getType()==HitResult.Type.MISS;
    }
    private static void tick(RemoteSession s,int now) {
        var p=s.player; var tool=s.tool;
        if (!eligible(p) || tool.isRemoved() || p.getWorld()!=tool.getWorld() || !p.getCommandTags().contains(GRANT)
                || p.getPos().squaredDistanceTo(s.origin)>.09 || p.getInventory().selectedSlot!=s.sourceSlot
                || !ItemStack.areEqual(p.getInventory().main.get(s.sourceSlot),s.bodyStack)) {
            stop(p,RemoteProtocol.INVALID); return;
        }
        if (now-s.inputTick>40) { stop(p,RemoteProtocol.TIMEOUT); return; }
        if (now-s.inputTick>5) { s.keys=0; s.pressedKeys=0; }
        double[] input=RemoteToolMath.movement(s.yaw,s.pitch,s.keys);
        Vec3d intended=new Vec3d(input[0],input[1],input[2]);
        s.motion=s.motion.lerp(intended,.4);
        Vec3d before=tool.getPos(),next=before.add(s.motion);
        if (tool.getEyePos().add(s.motion).squaredDistanceTo(p.getEyePos())<=RemoteToolMath.RANGE*RemoteToolMath.RANGE
                && p.getWorld().isChunkLoaded(BlockPos.ofFloored(next)) && p.getWorld().getWorldBorder().contains(tool.getBoundingBox().offset(s.motion))
                && next.y>p.getWorld().getBottomY()+1 && next.y<p.getWorld().getTopY()-1) tool.move(MovementType.SELF,s.motion);
        Vec3d actual=tool.getPos().subtract(before);
        tool.setYaw(s.yaw); tool.setPitch(s.pitch);
        float coverage=RemoteVisibility.occlusion(p.getWorld(),p,p.getEyePos(),tool.getPos().add(0,.15,0));
        tool.setOcclusion(coverage);
        Vec3d away=tool.getPos().subtract(p.getEyePos());
        if (s.rules.visibility(coverage,tool.getX(),tool.getY(),tool.getZ(),actual.x,actual.y,actual.z,
                intended.x,intended.y,intended.z,away.x,away.y,away.z)) {
            p.sendMessage(Text.translatable("text.magicaland_gameplay.remote.lost"),true); stop(p,RemoteProtocol.LOST); return;
        }
        try (var context=RemoteActionContext.open(s)) {
            if (coverage<1) {
                drop(s);
                interact(s,now);
            } else clearMining(s);
            tool.setAttackCooldown(context.attackCooldown(0));
        } finally { s.pendingDrop=null; s.cargo.markDirty(); }
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
        if (player.isAlive()) {
            s.cargo.returnTo(player.getInventory(),player.getInventory().main.size(),s.sourceSlot);
            if (!s.cargo.isEmpty()) player.sendMessage(Text.translatable("text.magicaland_gameplay.remote.cargo_retained"),false);
            player.playerScreenHandler.sendContentUpdates();
        }
        s.tool.discard(); sync(s,reason);
    }
    private static void clearMining(RemoteSession s) {
        if (s.mining!=null) s.tool.getWorld().setBlockBreakingInfo(s.tool.getId(),s.mining,-1);
        s.mining=null; s.miningState=null; s.progress=0;
        if (s.tool.action()==RemoteAction.MINING) s.tool.startAction(RemoteAction.NONE);
    }
    public static boolean canAttack(ServerPlayerEntity player,RemoteToolEntity tool,Entity target) {
        return target!=player && target instanceof LivingEntity && target.isAlive() && !target.isSpectator() && target.canHit()
                && target.getWorld()==tool.getWorld() && (!(target instanceof PlayerEntity other) || player.shouldDamagePlayer(other))
                && clearRay(tool,tool.getEyePos(),target.getBoundingBox().getCenter());
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
        BlockHitResult hit=world.raycast(new RaycastContext(from,to,RaycastContext.ShapeType.OUTLINE,RaycastContext.FluidHandling.NONE,tool));
        boolean attack=(s.keys&64)!=0 || (s.pressedKeys&64)!=0;
        boolean usePressed=(((s.keys&~s.previousKeys)|s.pressedKeys)&128)!=0;
        boolean melee=stack.getItem() instanceof MiningToolItem || stack.getItem() instanceof SwordItem;
        double reach=hit.getType()==HitResult.Type.MISS?9:from.squaredDistanceTo(hit.getPos());
        var victim=ProjectileUtil.raycast(tool,from,to,tool.getBoundingBox().stretch(to.subtract(from)).expand(1),
                entity -> entity!=p && entity instanceof LivingEntity && entity.isAlive() && !entity.isSpectator() && entity.canHit(),reach);
        if (attack && melee && victim!=null) {
            clearMining(s); var target=victim.getEntity();
            if (p.getAttackCooldownProgress(0)>=1) {
                tool.startAction(RemoteAction.SWING); s.lastSwingTick=now;
                if (canAttack(p,tool,target) && AttackEntityCallback.EVENT.invoker().interact(p,world,Hand.MAIN_HAND,target,victim)==ActionResult.PASS) {
                    RemoteActionContext.current().resetHit(); p.attack(target);
                    p.resetLastAttackedTicks();
                    if (tool.action()!=RemoteAction.TOOL_BREAK && RemoteActionContext.current().hit()) tool.startAction(RemoteAction.HIT);
                } else p.resetLastAttackedTicks();
            }
        } else if (attack && stack.getItem() instanceof MiningToolItem && hit.getType()==HitResult.Type.BLOCK) {
            mine(s,hit,now);
        } else {
            clearMining(s);
            if (attack && now-s.lastSwingTick>=6) {
                tool.startAction(RemoteAction.SWING); s.lastSwingTick=now; p.resetLastAttackedTicks();
                world.playSound(null,tool.getX(),tool.getY(),tool.getZ(),SoundEvents.ENTITY_PLAYER_ATTACK_WEAK,SoundCategory.PLAYERS,.6f,1);
            }
        }
        if (usePressed && hit.getType()==HitResult.Type.BLOCK && world.canPlayerModifyAt(p,hit.getBlockPos())) {
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
        for (ItemEntity item:world.getEntitiesByClass(ItemEntity.class,tool.getBoundingBox().expand(.45),
                item -> RemotePickup.canPickup(item,p.getUuid()))) {
            if (!RemotePickup.clearPath(world,tool,from,item) || !RemotePickup.collect(s.cargo,item,p.getUuid())) continue;
            world.playSound(null,tool.getX(),tool.getY(),tool.getZ(),SoundEvents.ENTITY_ITEM_PICKUP,SoundCategory.PLAYERS,.2f,1);
        }
    }
    private static void mine(RemoteSession s,BlockHitResult hit,int now) {
        var p=s.player; var world=p.getServerWorld(); var tool=s.tool;
        BlockPos pos=hit.getBlockPos(); BlockState state=world.getBlockState(pos);
        if (!world.canPlayerModifyAt(p,pos) || !p.canModifyBlocks() || state.getHardness(world,pos)<0
                || p.isBlockBreakingRestricted(world,pos,p.interactionManager.getGameMode())) { clearMining(s); return; }
        if (!pos.equals(s.mining) || state!=s.miningState) {
            clearMining(s);
            if (AttackBlockCallback.EVENT.invoker().interact(p,world,Hand.MAIN_HAND,pos,hit.getSide())!=ActionResult.PASS) return;
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
