package net.minecraft.world;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.entity.ai.TargetPredicate;
import net.minecraft.entity.mob.PathAwareEntity;
public final class World {
    public final Map<UUID, PlayerEntity> players = new HashMap<>();
    public TargetPredicate lastPredicate;
    public int playerQueries;
    public PlayerEntity getPlayerByUuid(UUID uuid) { return players.get(uuid); }
    public PlayerEntity getClosestPlayer(TargetPredicate predicate, PathAwareEntity mob) {
        lastPredicate = predicate; playerQueries++;
        return players.values().stream().filter(player -> !player.spectator && player.distance < 10 && predicate.test(player))
                .min(java.util.Comparator.comparingDouble(player -> player.distance)).orElse(null);
    }
}
