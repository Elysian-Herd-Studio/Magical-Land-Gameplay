package top.elysianherd.magicaland.gameplay.remote;

import java.lang.ref.WeakReference;
import java.util.Map;
import java.util.WeakHashMap;
import java.util.function.Predicate;
import net.minecraft.entity.ai.brain.MemoryModuleType;
import net.minecraft.entity.mob.PathAwareEntity;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.item.ItemStack;

public final class RemoteBrainTemptation {
    private record Attraction(WeakReference<RemoteToolEntity> tool, WeakReference<PlayerEntity> owner,
                              Predicate<ItemStack> food) {}
    private static final Map<PathAwareEntity, Attraction> TARGETS = new WeakHashMap<>();

    private RemoteBrainTemptation() {}

    public static void sense(PathAwareEntity mob, Predicate<ItemStack> food) {
        TARGETS.remove(mob);
        if (mob.getBrain().getOptionalRegisteredMemory(MemoryModuleType.TEMPTING_PLAYER).isPresent()) return;
        RemoteToolEntity tool = RemoteToolServer.temptingTool(mob, food);
        if (tool == null) return;
        PlayerEntity owner = mob.getWorld().getPlayerByUuid(tool.owner());
        if (owner == null || owner.isSpectator() || mob.hasPassenger(owner)) return;
        TARGETS.put(mob, new Attraction(new WeakReference<>(tool), new WeakReference<>(owner), food));
        mob.getBrain().remember(MemoryModuleType.TEMPTING_PLAYER, owner);
    }

    public static boolean assigned(PathAwareEntity mob) {
        return TARGETS.containsKey(mob);
    }

    public static RemoteToolEntity target(PathAwareEntity mob) {
        Attraction attraction = TARGETS.get(mob);
        if (attraction == null) return null;
        RemoteToolEntity tool = attraction.tool().get();
        PlayerEntity owner = attraction.owner().get();
        if (tool == null || owner == null || owner.isSpectator() || mob.hasPassenger(owner)
                || mob.getBrain().getOptionalRegisteredMemory(MemoryModuleType.TEMPTING_PLAYER).orElse(null) != owner
                || mob.getWorld().getPlayerByUuid(tool.owner()) != owner
                || !RemoteToolServer.isTempting(tool, mob, attraction.food())) return null;
        return tool;
    }

    public static void forget(PathAwareEntity mob) {
        Attraction attraction = TARGETS.remove(mob);
        if (attraction != null && mob.getBrain().getOptionalRegisteredMemory(MemoryModuleType.TEMPTING_PLAYER)
                .orElse(null) == attraction.owner().get()) {
            mob.getBrain().forget(MemoryModuleType.TEMPTING_PLAYER);
        }
    }
}
