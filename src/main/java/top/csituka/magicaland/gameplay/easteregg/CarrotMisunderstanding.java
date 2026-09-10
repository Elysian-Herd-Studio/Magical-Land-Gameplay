package top.csituka.magicaland.gameplay.easteregg;

import java.util.HashMap;
import java.util.Iterator;
import java.util.Map;
import java.util.UUID;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerLifecycleEvents;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerTickEvents;
import net.fabricmc.fabric.api.networking.v1.ServerPlayConnectionEvents;
import net.minecraft.block.BlockState;
import net.minecraft.block.Blocks;
import net.minecraft.entity.EntityType;
import net.minecraft.entity.damage.DamageSource;
import net.minecraft.entity.damage.DamageTypes;
import net.minecraft.entity.passive.AbstractHorseEntity;
import net.minecraft.entity.passive.HorseEntity;
import net.minecraft.item.Item;
import net.minecraft.item.ItemStack;
import net.minecraft.item.Items;
import net.minecraft.particle.ParticleTypes;
import net.minecraft.registry.RegistryKeys;
import net.minecraft.registry.tag.BlockTags;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.util.Identifier;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Box;
import net.minecraft.util.math.Vec3d;
import top.csituka.magicaland.gameplay.mixin.HorseGoalSelectorAccessor;

public final class CarrotMisunderstanding {
    private static final Identifier ADVANCEMENT = new Identifier("magicaland", "not_what_i_meant");
    private static final CarrotMisunderstandingState STATE = new CarrotMisunderstandingState();
    private static final Map<UUID, CarrotApproachGoal> ACTIVE = new HashMap<>();
    private static MinecraftServer server;
    private static boolean registered;
    private CarrotMisunderstanding() {}

    public static void register() {
        if (registered) return;
        registered = true;
        ServerTickEvents.END_SERVER_TICK.register(CarrotMisunderstanding::tick);
        ServerPlayConnectionEvents.DISCONNECT.register((handler, server) -> cancel(handler.player.getUuid()));
        ServerLifecycleEvents.SERVER_STOPPING.register(ignored -> clear());
    }
    public static void recordFeeding(ServerPlayerEntity player, AbstractHorseEntity animal, ItemStack food) {
        if (!(animal instanceof HorseEntity horse) || horse.getType() != EntityType.HORSE
                || !triggerFood(food.getItem()) || horse.getBreedingAge() != 0 || !horse.isInLove()
                || horse.getLovingPlayer() != player || horse.getWorld() != player.getWorld()
                || player.getServer() == null || !player.isAlive()) return;
        bind(player.getServer());
        STATE.fed(player.getUuid(), horse.getUuid(), worldKey(player), now());
        tryStart(player);
    }
    public static void carrotFinished(ServerPlayerEntity player, Item food) {
        if (!triggerFood(food)) return;
        bind(player.getServer());
        STATE.ate(player.getUuid(),worldKey(player),now());
        tryStart(player);
    }
    private static void tryStart(ServerPlayerEntity player) {
        var feed = STATE.takeMatch(player.getUuid(), worldKey(player), now());
        if (feed == null) return;
        var entity = player.getServerWorld().getEntity(feed.horse());
        if (!(entity instanceof HorseEntity horse) || !eligible(player, horse)
                || CarrotApproachGoal.hasMate(horse) || !STATE.claim(player.getUuid(), feed, now())) return;
        var goal = new CarrotApproachGoal(player, horse, now());
        ACTIVE.put(player.getUuid(), goal);
        // 繁殖 Goal 已先注册；同级不抢繁殖，每 tick 检查配偶让位，只覆盖较低级诱惑/闲逛。
        ((HorseGoalSelectorAccessor) horse).magicaland$getGoalSelector().add(2, goal);
    }
    static boolean triggerFood(Item food) { return food == Items.GOLDEN_CARROT; }
    static boolean eligible(ServerPlayerEntity player, HorseEntity horse) {
        return player.getServer() != null && player.getServer().getPlayerManager().getPlayer(player.getUuid()) == player
                && player.isAlive() && !player.isRemoved() && !player.isSpectator() && !player.isInvisible()
                && !player.isSleeping() && !player.hasVehicle() && !player.isFallFlying()
                && horse.getWorld() == player.getWorld() && horse.getType() == EntityType.HORSE
                && horse.isAlive() && !horse.isRemoved() && !horse.isBaby() && horse.getBreedingAge() == 0
                && !horse.hasPassengers() && !horse.hasVehicle() && !horse.isLeashed() && !horse.isAiDisabled()
                && !horse.isInvisible() && !horse.isOnFire() && horse.hurtTime == 0
                && horse.isInLove() && horse.getLovingPlayer() == player
                && player.getServerWorld().getEntity(horse.getUuid()) == horse
                && horse.squaredDistanceTo(player) <= CarrotMisunderstandingState.RANGE_SQUARED
                && horse.canSee(player) && player.canSee(horse);
    }
    static long now() { return server == null ? 0 : Integer.toUnsignedLong(server.getTicks()); }
    private static String worldKey(ServerPlayerEntity player) { return player.getWorld().getRegistryKey().getValue().toString(); }
    private static void bind(MinecraftServer current) {
        if (server != current) { clear(); server = current; }
    }
    private static void tick(MinecraftServer current) {
        bind(current);
        Iterator<Map.Entry<UUID, CarrotApproachGoal>> iterator = ACTIVE.entrySet().iterator();
        while (iterator.hasNext()) {
            var entry = iterator.next();
            var goal = entry.getValue();
            if (!goal.finished() && !STATE.expired(entry.getKey(), now()) && eligible(goal.player, goal.horse)) continue;
            goal.stop();
            ((HorseGoalSelectorAccessor) goal.horse).magicaland$getGoalSelector().remove(goal);
            STATE.release(entry.getKey());
            iterator.remove();
        }
        STATE.prune(now(), (uuid, dimension) -> {
            var player = current.getPlayerManager().getPlayer(uuid);
            return player != null && player.isAlive() && !player.isSpectator() && worldKey(player).equals(dimension);
        });
    }
    private static void cancel(UUID player) {
        CarrotApproachGoal goal = ACTIVE.remove(player);
        if (goal != null) {
            goal.stop();
            ((HorseGoalSelectorAccessor) goal.horse).magicaland$getGoalSelector().remove(goal);
        }
        STATE.release(player);
    }
    private static void clear() {
        for (var goal : ACTIVE.values()) {
            goal.stop();
            ((HorseGoalSelectorAccessor) goal.horse).magicaland$getGoalSelector().remove(goal);
        }
        ACTIVE.clear(); STATE.clear(); server = null;
    }
    static void hearts(HorseEntity horse) {
        ((ServerWorld) horse.getWorld()).spawnParticles(ParticleTypes.HEART, horse.getX(),
                horse.getBodyY(.8) + .15, horse.getZ(), 2, .18, .12, .18, .015);
    }
    static void bump(ServerPlayerEntity player, HorseEntity horse) {
        if (!eligible(player, horse)) return;
        hearts(horse);
        float amount = CarrotMisunderstandingState.damage(player.getHealth());
        boolean damaged = false;
        if (amount > 0) {
            // 无攻击者的碰撞伤害保留护甲减伤，不引入难度倍率、仇恨或原版强击退。
            var type = player.getServerWorld().getRegistryManager().get(RegistryKeys.DAMAGE_TYPE).entryOf(DamageTypes.MOB_ATTACK);
            damaged = player.damage(new DamageSource(type, horse.getPos()), amount);
        }
        if (damaged) horse.playAngrySound();
        Vec3d away = player.getPos().subtract(horse.getPos()).multiply(1, 0, 1);
        if (damaged && away.lengthSquared() > 1e-6 && safePush(player, away.normalize())) {
            player.takeKnockback(.12, -away.x, -away.z);
            player.velocityModified = true;
        }
        var advancement = player.getServer().getAdvancementLoader().get(ADVANCEMENT);
        if (advancement != null) player.getAdvancementTracker().grantCriterion(advancement, "misunderstanding");
    }
    private static boolean safePush(ServerPlayerEntity player, Vec3d direction) {
        if (!player.isAlive() || player.getHealth() <= 1 || !player.isOnGround() || player.isTouchingWater() || player.isInLava()
                || player.isOnFire() || player.getVelocity().horizontalLengthSquared() > .04) return false;
        ServerWorld world = player.getServerWorld();
        Box swept = player.getBoundingBox().stretch(direction.multiply(1.2));
        if (!world.isSpaceEmpty(player, swept)) return false;
        for (BlockPos pos : BlockPos.iterate(BlockPos.ofFloored(swept.minX, swept.minY - .2, swept.minZ),
                BlockPos.ofFloored(swept.maxX, swept.maxY, swept.maxZ))) {
            BlockState block = world.getBlockState(pos);
            if (!world.getFluidState(pos).isEmpty() || dangerous(block)) return false;
        }
        for (double step : new double[] {0, .3, .6, .9, 1.2}) {
            Vec3d feet = player.getPos().add(direction.multiply(step));
            double radius = player.getWidth() * .5;
            for (double dx : new double[] {-radius, radius}) for (double dz : new double[] {-radius, radius}) {
                BlockPos floor = BlockPos.ofFloored(feet.x + dx, feet.y - .1, feet.z + dz);
                BlockState support = world.getBlockState(floor);
                if (!support.isSolidBlock(world, floor) || support.getBlock().getSlipperiness() > .6f) return false;
            }
        }
        return true;
    }
    private static boolean dangerous(BlockState block) {
        return block.isIn(BlockTags.FIRE) || block.isOf(Blocks.CACTUS) || block.isOf(Blocks.MAGMA_BLOCK)
                || block.isOf(Blocks.POWDER_SNOW) || block.isOf(Blocks.SWEET_BERRY_BUSH)
                || block.isOf(Blocks.WITHER_ROSE) || block.isOf(Blocks.POINTED_DRIPSTONE)
                || block.isOf(Blocks.CAMPFIRE) || block.isOf(Blocks.SOUL_CAMPFIRE);
    }
}
