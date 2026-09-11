package net.minecraft.entity.ai;
import java.util.function.Predicate;
import net.minecraft.entity.player.PlayerEntity;
public final class TargetPredicate {
    private final Predicate<PlayerEntity> food;
    public TargetPredicate(Predicate<PlayerEntity> food) { this.food = food; }
    public boolean test(PlayerEntity player) { return food.test(player); }
}
