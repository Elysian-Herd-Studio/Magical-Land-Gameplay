package top.csituka.magicaland.gameplay.remote;

import java.util.*;
import net.fabricmc.fabric.api.entity.event.v1.ServerLivingEntityEvents;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerLifecycleEvents;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerTickEvents;
import net.fabricmc.fabric.api.networking.v1.PacketByteBufs;
import net.fabricmc.fabric.api.networking.v1.ServerPlayConnectionEvents;
import net.fabricmc.fabric.api.networking.v1.ServerPlayNetworking;
import net.minecraft.block.BlockState;
import net.minecraft.entity.Entity;
import net.minecraft.entity.EntityPose;
import net.minecraft.entity.ItemEntity;
import net.minecraft.entity.LivingEntity;
import net.minecraft.entity.MovementType;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.item.ItemStack;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.util.TypeFilter;
import net.minecraft.util.hit.BlockHitResult;
import net.minecraft.util.hit.HitResult;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Box;
import net.minecraft.util.math.Vec3d;
import net.minecraft.world.GameRules;
import net.minecraft.world.RaycastContext;
import top.csituka.magicaland.gameplay.race.RaceServer;
import static top.csituka.magicaland.gameplay.remote.TelekinesisProtocol.*;

public final class TelekinesisServer {
    private static final Map<Long, Job> JOBS = new LinkedHashMap<>();
    private static final Map<UUID, Long> REQUESTS = new HashMap<>();
    private static final Map<UUID, int[]> RATE = new HashMap<>();
    private static final int MAX_SERVER_TASKS = 48;
    private static long stateSequence;
    private static int cursor;
    private TelekinesisServer() {}

    private static final class Job {
        final long id;
        final RemoteSession session;
        final RemoteReturnNavigator route;
        final int orbitSlot;
        final TelekinesisChain chain = new TelekinesisChain(TelekinesisRules.MAX_CHAIN);
        final Set<BlockPos> skippedBlocks = new HashSet<>();
        Task task;
        Mode mode;
        Preference preference;
        String preferredId;
        Phase phase = Phase.IDLE;
        LivingEntity enemy;
        BlockPos block;
        List<Vec3d> miningApproaches = List.of();
        RemoteReturnNavigator.Status routeStatus;
        int stalled, retryAt, miningApproach, miningStalled;
        boolean idleRouting, bodyTarget;
        Job(long id, RemoteSession session, Control input, int orbitSlot) {
            this.id = id; this.session = session;
            this.orbitSlot = orbitSlot;
            mode = input.mode(); preference = input.preference(); preferredId = input.preferredId();
            task = mode == Mode.AUTO ? Task.GUARD : input.entityId() >= 0 ? Task.GUARD : Task.GATHER;
            route = new RemoteReturnNavigator((from, to) -> RemoteFlightCollision.clear(session.tool, from, to));
        }
    }
    public static boolean owns(RemoteToolEntity tool) {
        if (!tool.autonomous()) return false;
        for (var job : JOBS.values()) if (job.session.tool == tool) return true;
        return false;
    }
    public static void assist(ServerPlayerEntity player, LivingEntity target) {
        if (JOBS.isEmpty() || RemoteActionContext.forPlayer(player) != null || !valid(player) || !assistEligible(player, target)) return;
        boolean changed = false;
        for (var job : JOBS.values()) {
            if (job.session.player != player || job.mode != Mode.AUTO || job.task != Task.GUARD || !canWork(job.session.tool)) continue;
            if (job.bodyTarget && job.enemy == target) continue;
            clearTarget(job); job.enemy = target; job.bodyTarget = true;
            job.retryAt = 0; job.phase = Phase.TRAVELLING; changed = true;
        }
        if (changed) sync(player, "");
    }
    static boolean canWork(RemoteToolEntity tool) {
        for (var job : JOBS.values()) if (job.session.tool == tool)
            return !returning(job) && valid(job.session.player) && job.session.player.getWorld() == tool.getWorld()
                    && TelekinesisRules.supports(job.task, job.session.cargo.getStack(0))
                    && !TelekinesisRules.needsRepair(job.session.cargo.getStack(0))
                    && TelekinesisToken.matches(job.session.player, job.id, job.session.sourceSlot);
        return false;
    }
    public static boolean allowsAttack(ServerPlayerEntity player, RemoteToolEntity tool, Entity target) {
        if (!canWork(tool)) return false;
        for (var job : JOBS.values()) if (job.session.tool == tool && job.session.player == player)
            return job.task == Task.GUARD && (job.mode != Mode.DIRECT && !job.bodyTarget || job.enemy == target)
                    && target instanceof LivingEntity living && enemy(job, living);
        return false;
    }
    static boolean allowsGather(RemoteToolEntity tool, BlockPos pos) {
        if (!canWork(tool)) return false;
        for (var job : JOBS.values()) if (job.session.tool == tool)
            return pos.equals(job.block) && gatherable(job, pos, job.mode == Mode.AUTO);
        return false;
    }
    public static void register() {
        ServerPlayNetworking.registerGlobalReceiver(CONTROL, (server, player, handler, buf, sender) -> {
            try {
                var input = readControl(buf);
                server.execute(() -> {
                    if (server.getPlayerManager().getPlayer(player.getUuid()) == player) {
                        try { receive(player, input); }
                        catch (RuntimeException failure) {
                            org.slf4j.LoggerFactory.getLogger("Magical-Land/Telekinesis").warn("御物请求未完成，携带物等待归还", failure);
                            sync(player, "invalid");
                        }
                    }
                });
            } catch (RuntimeException ignored) {}
        });
        ServerTickEvents.END_SERVER_TICK.register(TelekinesisServer::tick);
        ServerPlayConnectionEvents.JOIN.register((handler, sender, server) -> {
            REQUESTS.remove(handler.player.getUuid()); RATE.remove(handler.player.getUuid());
            settle(handler.player); sync(handler.player, "");
        });
        ServerPlayConnectionEvents.DISCONNECT.register((handler, server) -> {
            detach(handler.player); REQUESTS.remove(handler.player.getUuid()); RATE.remove(handler.player.getUuid());
        });
        ServerLivingEntityEvents.AFTER_DEATH.register((entity, source) -> {
            if (!(entity instanceof ServerPlayerEntity player)) return;
            detach(player);
            var state = TelekinesisCargoState.get(player.getServer());
            for (var entry : state.entries().entrySet()) if (entry.getValue().owner().equals(player.getUuid())) TelekinesisToken.clear(player, entry.getKey());
            if (!player.getWorld().getGameRules().getBoolean(GameRules.KEEP_INVENTORY)) {
                for (var entry : state.entries().entrySet()) if (entry.getValue().owner().equals(player.getUuid())) {
                    entry.getValue().inventory().dropOnDeath(stack -> spawnDrop(player, stack)); state.prune(entry.getKey());
                }
            }
        });
        ServerLifecycleEvents.SERVER_STOPPING.register(server -> {
            for (var job : List.copyOf(JOBS.values())) remove(job);
            JOBS.clear(); REQUESTS.clear(); RATE.clear(); cursor = 0; stateSequence = 0;
        });
    }
    private static boolean valid(ServerPlayerEntity player) {
        return player.getServer() != null && player.getServer().getPlayerManager().getPlayer(player.getUuid()) == player
                && player.isAlive() && !player.isRemoved() && !player.isSpectator() && RaceServer.isUnicorn(player);
    }
    private static void receive(ServerPlayerEntity player, Control input) {
        UUID owner = player.getUuid();
        if (input.sequence() <= REQUESTS.getOrDefault(owner, -1L)) return;
        REQUESTS.put(owner, input.sequence());
        int now = player.getServer().getTicks();
        int[] rate = RATE.computeIfAbsent(owner, key -> new int[]{now, 0});
        if (rate[0] != now) { rate[0] = now; rate[1] = 0; }
        if (input.operation() != Operation.STOP && ++rate[1] > 8) { sync(player, "busy"); return; }
        if (input.operation() == Operation.STOP) {
            if (input.taskId() != 0 && !selectedTask(player, input)) { sync(player, "invalid"); return; }
            for (var job : List.copyOf(JOBS.values())) if (job.session.player == player && (input.taskId() == 0 || job.id == input.taskId())) recall(job);
            sync(player, ""); return;
        }
        if (!valid(player)) { sync(player, "unavailable"); return; }
        if (input.operation() == Operation.START) { start(player, input); return; }
        if (input.operation() == Operation.CONFIG && input.taskId() == 0) { sync(player, ""); return; }
        Job job = JOBS.get(input.taskId());
        if (!selectedTask(player, input) || job.phase == Phase.RETURNING) { sync(player, "invalid"); return; }
        if (recallForRepair(job)) return;
        if (input.operation() == Operation.CONFIG) {
            if (input.mode() == Mode.DIRECT) {
                if (!assign(job, input)) { sync(player, "target"); return; }
            } else {
                Task automatic = TelekinesisRules.automaticTask(job.session.cargo.getStack(0));
                if (automatic == null) { sync(player, "aim"); return; }
                clearTarget(job);
                job.task = automatic; job.mode = Mode.AUTO;
            }
            job.preference = input.preference(); job.preferredId = input.preferredId();
        } else if (input.operation() == Operation.TARGET) {
            if (!assign(job, input)) { sync(player, "target"); return; }
        }
        sync(player, "");
    }
    private static boolean selectedTask(ServerPlayerEntity player, Control input) {
        var job = JOBS.get(input.taskId());
        return job != null && job.session.player == player && job.session.sourceSlot == input.sourceSlot()
                && player.getInventory().selectedSlot == input.sourceSlot();
    }
    private static void start(ServerPlayerEntity player, Control input) {
        if (input.taskId() != 0 || input.sourceSlot() < 0 || player.getInventory().selectedSlot != input.sourceSlot()) { sync(player, "invalid"); return; }
        long count = JOBS.values().stream().filter(job -> job.session.player == player).count();
        if (count >= TelekinesisRules.MAX_TASKS) { sync(player, "limit"); return; }
        int occupied = 0;
        for (var other : JOBS.values()) if (other.session.player == player) occupied |= 1 << other.orbitSlot;
        int orbitSlot = TelekinesisOrbit.freeSlot(occupied, TelekinesisRules.MAX_TASKS);
        if (orbitSlot < 0) { sync(player, "limit"); return; }
        if (JOBS.size() >= MAX_SERVER_TASKS) { sync(player, "busy"); return; }
        if (RemoteToolServer.active(player)) { sync(player, "busy"); return; }
        ItemStack source = player.getInventory().getMainHandStack();
        if (source.getCount() != 1 || !TelekinesisRules.supports(source)) { sync(player, "tool"); return; }
        if (TelekinesisRules.needsRepair(source)) { sync(player, "repair"); return; }
        if (input.mode() == Mode.AUTO && TelekinesisRules.automaticTask(source) == null) { sync(player, "aim"); return; }
        settle(player);
        var state = TelekinesisCargoState.get(player.getServer());
        long id = state.create(player.getUuid(), player.getInventory().selectedSlot);
        var tool = new RemoteToolEntity(RemoteToolServer.TYPE, player.getWorld());
        Vec3d origin = player.getEyePos().add(player.getRotationVec(1).multiply(.45)).add(0, -.2, 0);
        tool.setPosition(origin); tool.setYaw(player.getYaw()); tool.setPitch(player.getPitch()); tool.setAutonomous(true);
        ItemStack shown = source.copy(); shown.setCount(1); tool.setup(player.getUuid(), shown);
        var session = new RemoteSession(player, tool, state.cargo(id).inventory(), id, id);
        var job = new Job(id, session, input, orbitSlot);
        if (!RemoteFlightCollision.clear(tool, point(origin), point(origin))
                || input.mode() == Mode.DIRECT && !assign(job, input)) {
            state.prune(id); sync(player, "target"); return;
        }
        if (!player.getServerWorld().spawnEntity(tool)) { state.prune(id); sync(player, "busy"); return; }
        if (!ItemStack.areEqual(source, player.getInventory().getStack(input.sourceSlot())) || source.getCount() != 1) {
            tool.discard(); state.prune(id); sync(player, "busy"); return;
        }
        JOBS.put(id, job);
        session.cargo.setStack(0, player.getInventory().removeStack(session.sourceSlot, 1));
        if (!TelekinesisToken.reserve(player, id, session.sourceSlot)) { remove(job); settle(player); sync(player, "invalid"); return; }
        session.sourceCargoSlot = 0; session.bodyStack = player.getInventory().getMainHandStack().copy();
        player.getInventory().markDirty(); session.cargo.markDirty(); tool.inventoryView(session.cargo);
        player.playerScreenHandler.sendContentUpdates(); sync(player, "");
    }
    private static boolean assign(Job job, Control input) {
        ItemStack carried = JOBS.containsKey(job.id) ? job.session.cargo.getStack(0) : job.session.sourceItem;
        boolean delivering = job.phase == Phase.DELIVERING;
        if (input.entityId() >= 0) {
            Entity entity = job.session.player.getServerWorld().getEntityById(input.entityId());
            if (!TelekinesisRules.supports(Task.GUARD, carried) || !(entity instanceof LivingEntity target)
                    || !eligible(job.session.player, target, Mode.DIRECT)) return false;
            clearTarget(job); job.task = Task.GUARD; job.enemy = target;
        } else {
            if (input.block() == null || !gatherable(job, input.block(), false)
                    || TelekinesisSight.blockHit(job.session.player, job.session.player.getEyePos(), input.block()) == null) return false;
            clearTarget(job); job.task = Task.GATHER; job.block = input.block();
            job.chain.start(job.block, job.session.player.getWorld().getBlockState(job.block));
        }
        if (delivering) { job.session.navigation.reset(); job.session.navigation.record(point(job.session.tool.getPos())); }
        job.mode = Mode.DIRECT; job.phase = Phase.TRAVELLING; return true;
    }
    private static void tick(MinecraftServer server) {
        var jobs = new ArrayList<>(JOBS.values()); int navigationBudget = 512;
        for (int index = 0; index < jobs.size(); index++) {
            var job = jobs.get(Math.floorMod(cursor + index, jobs.size()));
            if (!JOBS.containsKey(job.id)) continue;
            try {
                navigationBudget -= tick(job, server.getTicks(), Math.min(32, navigationBudget));
            } catch (RuntimeException failure) {
                org.slf4j.LoggerFactory.getLogger("Magical-Land/Telekinesis").warn("御物任务已停止，携带物保留等待归还", failure);
                remove(job); sync(job.session.player, "invalid");
            }
        }
        if (!jobs.isEmpty()) cursor = (cursor + 1) % jobs.size();
        if (server.getTicks() % 20 == 0) for (var player : server.getPlayerManager().getPlayerList()) { settle(player); sync(player, ""); }
    }
    private static int tick(Job job, int now, int navigationBudget) {
        var s = job.session; var player = s.player; var tool = s.tool;
        if (!valid(player) || player.getWorld() != tool.getWorld() || tool.isRemoved()
                || !player.getWorld().isChunkLoaded(tool.getBlockPos())) { remove(job); return 0; }
        if (s.cargo.isEmpty()) { remove(job); return 0; }
        if (tool.squaredDistanceTo(player) > TelekinesisRules.RANGE * TelekinesisRules.RANGE * 4) { remove(job); return 0; }
        if (!TelekinesisRules.supports(s.cargo.getStack(0)) || !TelekinesisToken.matches(player, job.id, s.sourceSlot)) recall(job);
        recallForRepair(job);
        if (returning(job)) return returnHome(job, now, navigationBudget);
        if (job.enemy != null && !enemy(job, job.enemy) || job.block != null && !gatherable(job, job.block, job.mode == Mode.AUTO)) targetFinished(job);
        if (returning(job)) return returnHome(job, now, navigationBudget);
        if (job.mode == Mode.AUTO && now >= job.retryAt && now % 10 == job.id % 10) automaticGuard(job);
        int nodes;
        if (job.enemy != null) {
            Vec3d at = job.enemy.getBoundingBox().getCenter(); Vec3d away = tool.getPos().subtract(at);
            if (away.lengthSquared() < .01) away = player.getEyePos().subtract(at);
            Vec3d goal = at.add(away.normalize().multiply(1.5)).add(0, -.1, 0);
            nodes = move(job, goal, navigationBudget, false);
            face(tool, at);
            job.phase = tool.getEyePos().squaredDistanceTo(at) <= 2.8 * 2.8 ? Phase.WORKING : Phase.TRAVELLING;
            if (job.phase == Phase.WORKING) {
                RemoteToolServer.automaticAttack(s, job.enemy, now);
                if (!recallForRepair(job) && !job.enemy.isAlive()) targetFinished(job);
            }
        } else if (job.block != null) {
            nodes = gather(job, now, navigationBudget);
        } else { job.phase = Phase.IDLE; nodes = idle(job, now, navigationBudget); }
        tool.inventoryView(s.cargo);
        if (job.stalled >= 100 && job.enemy != null) {
            targetFinished(job); job.retryAt = now + 20;
        }
        if (now % 5 == job.id % 5) sync(player, "");
        return nodes;
    }
    private static boolean returning(Job job) { return job.phase == Phase.RETURNING || job.phase == Phase.DELIVERING; }
    private static int gather(Job job, int now, int budget) {
        var tool = job.session.tool;
        if (job.miningApproaches.isEmpty()) job.miningApproaches = TelekinesisMiningApproach.goals(tool, job.block);
        if (job.miningApproach >= job.miningApproaches.size()) { skipGather(job, now); return 0; }
        Vec3d goal = job.miningApproaches.get(job.miningApproach);
        int nodes = move(job, goal, budget, false);
        BlockHitResult hit = TelekinesisMiningApproach.closeEnough(tool, job.block)
                ? TelekinesisSight.blockHit(tool, tool.getEyePos(), job.block, 2.6) : null;
        face(tool, hit == null ? Vec3d.ofCenter(job.block) : hit.getPos());
        if (hit != null && tool.getEyePos().squaredDistanceTo(hit.getPos()) <= 2.6 * 2.6) {
            job.phase = Phase.WORKING; job.miningStalled = 0;
            harvest(job, hit, now);
        } else {
            RemoteToolServer.clearMining(job.session); job.phase = Phase.TRAVELLING;
            if (job.session.motion.lengthSquared() > .000001) job.miningStalled = 0;
            else if (job.routeStatus != RemoteReturnNavigator.Status.SEARCHING) job.miningStalled++;
            if (job.routeStatus == RemoteReturnNavigator.Status.BLOCKED || job.miningStalled >= 40) {
                job.miningApproach++; job.miningStalled = 0; job.route.reset();
                if (job.miningApproach >= job.miningApproaches.size()) skipGather(job, now);
            }
        }
        return nodes;
    }
    private static void skipGather(Job job, int now) {
        job.skippedBlocks.add(job.block); targetFinished(job); job.retryAt = now + 20;
    }
    private static void resetMiningApproach(Job job) {
        job.miningApproaches = List.of(); job.miningApproach = job.miningStalled = 0; job.routeStatus = null;
    }
    private static int returnHome(Job job, int now, int budget) {
        var s = job.session; var tool = s.tool; var player = s.player; Vec3d home = shoulder(job);
        int nodes = move(job, home, budget, true);
        if (tool.squaredDistanceTo(home) > .7 * .7 || !RemoteFlightCollision.clear(tool, point(tool.getPos()), point(home))) return nodes;
        remove(job); settle(player); sync(player, "");
        return nodes;
    }
    private static void automaticGuard(Job job) {
        if (job.task != Task.GUARD) return;
        LivingEntity next = selectEnemy(job);
        if (next != null) {
            if (next != job.enemy) { clearTarget(job); job.enemy = next; }
        } else if (job.enemy != null) clearTarget(job);
    }
    private static void targetFinished(Job job) {
        if (job.task == Task.GATHER && job.block != null) {
            job.chain.defer(job.block);
            RemoteToolServer.clearMining(job.session); job.route.reset(); job.stalled = 0;
            resetMiningApproach(job);
            job.block = nextChain(job);
            if (job.block != null) { job.phase = Phase.TRAVELLING; return; }
        }
        if (job.mode == Mode.DIRECT) recall(job);
        else clearTarget(job);
    }
    private static boolean recallForRepair(Job job) {
        if (!TelekinesisRules.needsRepair(job.session.cargo.getStack(0))) return false;
        if (!returning(job)) { recall(job); sync(job.session.player, "repair"); }
        return true;
    }
    private static int move(Job job, Vec3d goal, int budget, boolean returning) {
        var tool = job.session.tool; Vec3d before = tool.getPos();
        var navigation = returning ? job.session.navigation : job.route;
        var step = navigation.next(point(before), point(goal), budget);
        job.routeStatus = step.status();
        if (step.status() == RemoteReturnNavigator.Status.MOVING || step.status() == RemoteReturnNavigator.Status.ARRIVED) {
            var next = RemoteReturnMotion.safeStep(point(before), step.waypoint(), point(job.session.motion),
                    (from, to) -> RemoteFlightCollision.clear(tool, from, to));
            tool.move(MovementType.SELF, new Vec3d(next.x(), next.y(), next.z()).subtract(before));
        }
        job.session.motion = tool.getPos().subtract(before); tool.setVelocity(job.session.motion);
        if (job.session.motion.lengthSquared() > .000001) {
            if (!returning) job.session.navigation.record(point(tool.getPos()));
            job.stalled = 0;
        } else if (before.squaredDistanceTo(goal) > 1) job.stalled++;
        return step.expandedNodes();
    }
    private static Vec3d shoulder(Job job) {
        var player = job.session.player; double angle = Math.toRadians(player.bodyYaw);
        int index = 0; for (var other : JOBS.values()) { if (other == job) break; if (other.session.player == player) index++; }
        double side = index % 2 == 0 ? 1 : -1;
        Vec3d wanted = player.getEyePos().add(Math.cos(angle) * .8 * side + Math.sin(angle) * .3,
                -.35 + index * .12, Math.sin(angle) * .8 * side - Math.cos(angle) * .3);
        return RemoteFlightCollision.clear(job.session.tool, point(wanted), point(wanted)) ? wanted : player.getEyePos().add(0, -.2, 0);
    }
    private static int idle(Job job, int now, int budget) {
        var s = job.session; var player = s.player; var tool = s.tool; Vec3d before = tool.getPos();
        double eyes = player.getPose() == EntityPose.CROUCHING ? player.getY() + player.getEyeHeight(EntityPose.STANDING) : player.getEyeY();
        var center = new RemoteReturnNavigator.Point(player.getX(), TelekinesisOrbit.height(player.getY(), eyes), player.getZ());
        RemoteReturnNavigator.Collision collision = (from, to) -> RemoteFlightCollision.clear(tool, from, to);
        center = TelekinesisOrbit.followGoal(center, collision);
        if (center == null) { s.motion = Vec3d.ZERO; tool.setVelocity(Vec3d.ZERO); return 0; }
        var goal = TelekinesisOrbit.goal(now, job.orbitSlot, TelekinesisRules.MAX_TASKS, center, point(before),
                collision);
        if (goal == null || job.idleRouting && before.squaredDistanceTo(center.x(), center.y(), center.z()) > 9) {
            if (!job.idleRouting) { job.route.reset(); job.idleRouting = true; }
            // 绕障时追本体位置，追上后再恢复环绕，避免目标随环绕不断变化。
            return move(job, new Vec3d(center.x(), center.y(), center.z()), budget, false);
        }
        if (job.idleRouting) { job.route.reset(); job.idleRouting = false; }
        var next = RemoteReturnMotion.safeStep(point(before), goal, point(s.motion), collision);
        tool.move(MovementType.SELF, new Vec3d(next.x(), next.y(), next.z()).subtract(before));
        tool.setYaw(RemoteToolMath.approach(tool.getYaw(), (float)Math.toDegrees(TelekinesisOrbit.phase(now, job.orbitSlot, TelekinesisRules.MAX_TASKS)), 2));
        tool.setPitch(RemoteToolMath.approach(tool.getPitch(), 0, 2));
        s.motion = tool.getPos().subtract(before); tool.setVelocity(s.motion); job.stalled = 0;
        if (s.motion.lengthSquared() > .000001) s.navigation.record(point(tool.getPos()));
        return 0;
    }
    private static void face(RemoteToolEntity tool, Vec3d target) {
        Vec3d d = target.subtract(tool.getEyePos());
        tool.setYaw(RemoteToolMath.approach(tool.getYaw(), (float)Math.toDegrees(Math.atan2(-d.x, d.z)), 18));
        tool.setPitch(RemoteToolMath.approach(tool.getPitch(), (float)-Math.toDegrees(Math.atan2(d.y, d.horizontalLength())), 12));
    }
    private static LivingEntity selectEnemy(Job job) {
        if (job.bodyTarget && job.enemy != null && enemy(job, job.enemy)) return job.enemy;
        var player = job.session.player; var targets = new ArrayList<LivingEntity>();
        player.getServerWorld().collectEntitiesByType(TypeFilter.instanceOf(LivingEntity.class),
                player.getBoundingBox().expand(TelekinesisRules.RANGE), target -> enemy(job, target), targets, 64);
        return targets.stream().min(Comparator.comparing(target -> new TelekinesisRules.GuardPriority(
                assignedElsewhere(job, target), TelekinesisRules.threatens(player, target),
                target == job.enemy, target.getHealth() + target.getAbsorptionAmount(), player.squaredDistanceTo(target), target.getUuid()))).orElse(null);
    }
    private static boolean assignedElsewhere(Job job, LivingEntity target) {
        for (var other : JOBS.values()) if (other != job && other.session.player == job.session.player && other.task == Task.GUARD
                && !returning(other) && other.enemy == target && enemy(other, target)) return true;
        return false;
    }
    private static boolean enemy(Job job, LivingEntity target) {
        if (job.bodyTarget) return job.enemy == target && assistEligible(job.session.player, target);
        return eligible(job.session.player, target, job.mode);
    }
    private static boolean assistEligible(ServerPlayerEntity player, LivingEntity target) {
        return TelekinesisRules.assistTarget(player, target) && inSight(player, target);
    }
    private static boolean eligible(ServerPlayerEntity player, LivingEntity target, Mode mode) {
        return (mode == Mode.DIRECT ? TelekinesisRules.directTarget(player, target) : TelekinesisRules.enemy(player, target)) && inSight(player, target);
    }
    private static boolean inSight(ServerPlayerEntity player, LivingEntity target) {
        return target.getWorld() == player.getWorld() && within(player, target.getBoundingBox().getCenter())
                && visible(player, target.getBoundingBox().getCenter());
    }
    private static boolean claimed(Job job, BlockPos pos) {
        return JOBS.values().stream().anyMatch(other -> other != job && other.session.player.getWorld() == job.session.player.getWorld() && pos.equals(other.block));
    }
    private static boolean gatherable(Job job, BlockPos pos, boolean automatic) {
        var p = job.session.player; var world = p.getServerWorld();
        if (!within(p, Vec3d.ofCenter(pos)) || !world.isChunkLoaded(pos) || !world.getWorldBorder().contains(pos)
                || !world.canPlayerModifyAt(p, pos) || !p.canModifyBlocks()
                || p.isBlockBreakingRestricted(world, pos, p.interactionManager.getGameMode())) return false;
        BlockState state = world.getBlockState(pos);
        ItemStack carried = JOBS.containsKey(job.id) ? job.session.cargo.selectedStack() : job.session.sourceItem;
        return state.getHardness(world, pos) >= 0 && TelekinesisRules.canGather(carried, state, automatic);
    }
    private static boolean visible(PlayerEntity player, Vec3d target) {
        return player.getWorld().getWorldBorder().contains(BlockPos.ofFloored(target))
                && loadedBetween(player.getWorld(), player.getEyePos(), target)
                && player.getWorld().raycast(new RaycastContext(player.getEyePos(), target, RaycastContext.ShapeType.COLLIDER,
                RaycastContext.FluidHandling.NONE, player)).getType() == HitResult.Type.MISS;
    }
    static boolean loadedBetween(net.minecraft.world.WorldView world, Vec3d from, Vec3d to) {
        return world.isRegionLoaded(BlockPos.ofFloored(Math.min(from.x, to.x), Math.min(from.y, to.y), Math.min(from.z, to.z)),
                BlockPos.ofFloored(Math.max(from.x, to.x), Math.max(from.y, to.y), Math.max(from.z, to.z)));
    }
    private static boolean within(PlayerEntity player, Vec3d point) { return player.getEyePos().squaredDistanceTo(point) <= TelekinesisRules.RANGE * TelekinesisRules.RANGE; }
    private static void harvest(Job job, BlockHitResult hit, int now) {
        var s = job.session; var world = s.player.getServerWorld(); BlockPos pos = job.block;
        Set<UUID> oldItems = new HashSet<>();
        var existing = new ArrayList<ItemEntity>();
        world.collectEntitiesByType(TypeFilter.instanceOf(ItemEntity.class), new Box(pos).expand(1.25), entity -> true, existing, 64);
        if (existing.size() >= 64) { recall(job); return; }
        for (var item : existing) oldItems.add(item.getUuid());
        if (!RemoteToolServer.automaticMine(s, hit, now)) {
            if (s.mining == null) {
                skipGather(job, now);
            }
            return;
        }
        var drops = new ArrayList<ItemEntity>();
        world.collectEntitiesByType(TypeFilter.instanceOf(ItemEntity.class), new Box(pos).expand(1.25), entity -> !oldItems.contains(entity.getUuid()), drops, 64);
        boolean full = drops.size() >= 64;
        for (var item : drops) {
            if (item.isRemoved()) continue;
            ItemStack remainder = s.cargo.addGoodsStack(item.getStack(), 0);
            if (remainder.isEmpty()) item.discard(); else { item.setStack(remainder); full = true; }
        }
        job.chain.mined(pos, next -> world.isChunkLoaded(next) ? world.getBlockState(next) : null);
        job.skippedBlocks.clear();
        job.block = null; job.route.reset(); resetMiningApproach(job);
        job.block = nextChain(job);
        if (!recallForRepair(job) && (!TelekinesisRules.supports(s.cargo.getStack(0))
                || job.block == null || full || goodsFull(job))) recall(job);
    }
    private static BlockPos nextChain(Job job) {
        var world = job.session.player.getServerWorld();
        return job.chain.next(pos -> world.isChunkLoaded(pos) ? world.getBlockState(pos) : null,
                pos -> {
                    if (job.skippedBlocks.contains(pos) || !gatherable(job, pos, job.mode == Mode.AUTO) || claimed(job, pos)) return false;
                    var approaches = TelekinesisMiningApproach.goals(job.session.tool, pos);
                    if (approaches.isEmpty()) return false;
                    job.miningApproaches = approaches; return true;
                });
    }
    private static boolean goodsFull(Job job) {
        for (int slot = 1; slot < job.session.cargo.unlockedSlots(); slot++) {
            var stack = job.session.cargo.getStack(slot);
            if (stack.isEmpty() || stack.getCount() < Math.min(RemoteCargoInventory.CAPACITY, stack.getMaxCount())) return false;
        }
        return true;
    }
    private static void clearTarget(Job job) {
        RemoteToolServer.clearMining(job.session); job.enemy = null; job.block = null;
        resetMiningApproach(job);
        job.chain.clear(); job.skippedBlocks.clear(); job.route.reset(); job.idleRouting = job.bodyTarget = false; job.stalled = 0; job.phase = Phase.IDLE;
    }
    private static void recall(Job job) {
        if (job.phase == Phase.RETURNING) return;
        clearTarget(job); job.phase = Phase.RETURNING; job.session.rules.close(); job.session.tool.beginReturn();
    }
    private static void remove(Job job) {
        if (!JOBS.remove(job.id, job)) return;
        RemoteToolServer.clearMining(job.session); job.session.rules.close(); job.session.tool.discard(); job.session.cargo.markDirty();
    }
    private static void detach(ServerPlayerEntity player) { for (var job : List.copyOf(JOBS.values())) if (job.session.player == player) remove(job); }
    private static void settle(ServerPlayerEntity player) {
        if (!player.isAlive() || player.getServer() == null) return;
        TelekinesisToken.reconcile(player);
        var state = TelekinesisCargoState.get(player.getServer());
        for (var entry : state.entries().entrySet()) {
            var cargo = entry.getValue();
            if (!cargo.owner().equals(player.getUuid()) || JOBS.containsKey(entry.getKey())) continue;
            TelekinesisToken.clear(player, entry.getKey());
            RemoteToolServer.deliverToBody(player, () -> {
                cargo.inventory().returnTo(player.getInventory(), player.getInventory().main.size(), cargo.sourceSlot(), 0);
                cargo.inventory().dropRemainder(stack -> spawnDrop(player, stack));
                return null;
            });
            state.prune(entry.getKey());
        }
        player.playerScreenHandler.sendContentUpdates();
    }
    private static boolean spawnDrop(ServerPlayerEntity player, ItemStack stack) {
        var item = new ItemEntity(player.getWorld(), player.getX(), player.getY() + .1, player.getZ(), stack);
        item.setVelocity(0, .08, 0); item.setPickupDelay(10); item.setThrower(player.getUuid());
        return player.getServerWorld().spawnEntity(item);
    }
    private static void sync(ServerPlayerEntity player, String reason) {
        if (player.getServer() == null || !ServerPlayNetworking.canSend(player, STATE)) return;
        var tasks = new ArrayList<TaskView>();
        for (var job : JOBS.values()) if (job.session.player == player) tasks.add(new TaskView(job.id, job.session.sourceSlot, job.task, job.mode,
                job.preference, job.preferredId, job.phase, job.session.tool.getId(), job.block, job.enemy == null ? -1 : job.enemy.getId(), job.session.cargo.getStack(0)));
        var buf = PacketByteBufs.create(); writeState(buf, new State(++stateSequence, REQUESTS.getOrDefault(player.getUuid(), -1L), tasks, reason));
        ServerPlayNetworking.send(player, STATE, buf);
    }
    private static RemoteReturnNavigator.Point point(Vec3d pos) { return new RemoteReturnNavigator.Point(pos.x, pos.y, pos.z); }
}
