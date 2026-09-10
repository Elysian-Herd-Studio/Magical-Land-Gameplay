package top.csituka.magicaland.gameplay.easteregg;

import java.util.EnumSet;
import net.minecraft.entity.ai.goal.Goal;
import net.minecraft.entity.passive.AbstractHorseEntity;
import net.minecraft.entity.passive.HorseEntity;
import net.minecraft.server.network.ServerPlayerEntity;

final class CarrotApproachGoal extends Goal {
    final ServerPlayerEntity player;
    final HorseEntity horse;
    private final long created;
    private final CarrotMisunderstandingState.RouteWatch route = new CarrotMisunderstandingState.RouteWatch();
    private boolean finished, running;
    private long nextPath, nextHeart;
    CarrotApproachGoal(ServerPlayerEntity player, HorseEntity horse, long created) {
        this.player = player; this.horse = horse; this.created = created;
        setControls(EnumSet.of(Control.MOVE, Control.LOOK));
    }
    boolean finished() { return finished; }
    static boolean hasMate(HorseEntity horse) {
        return !horse.getWorld().getEntitiesByClass(AbstractHorseEntity.class,
                horse.getBoundingBox().expand(8), horse::canBreedWith).isEmpty();
    }
    private boolean valid() {
        long time = CarrotMisunderstanding.now();
        return !finished && time >= created && time - created < CarrotMisunderstandingState.DURATION
                && CarrotMisunderstanding.eligible(player, horse) && !hasMate(horse);
    }
    @Override public boolean canStart() { return valid(); }
    @Override public boolean shouldContinue() { return running && valid(); }
    @Override public boolean shouldRunEveryTick() { return true; }
    @Override public void start() { running = true; nextPath = nextHeart = CarrotMisunderstanding.now(); }
    @Override public void stop() {
        finished = true;
        if (running) horse.getNavigation().stop();
        running = false;
    }
    private boolean near() {
        return CarrotMisunderstandingState.contact(player.getX() - horse.getX(), player.getY() - horse.getY(),
                player.getZ() - horse.getZ(), horse.getWidth(), player.getWidth());
    }
    @Override public void tick() {
        if (!valid()) { stop(); return; }
        long time = CarrotMisunderstanding.now();
        horse.getLookControl().lookAt(player, 20, 20);
        if (time >= nextHeart) { CarrotMisunderstanding.hearts(horse); nextHeart = time + 20; }
        if (near()) {
            horse.getNavigation().stop();
            if (time - created >= CarrotMisunderstandingState.CONTACT_DELAY) {
                stop();
                CarrotMisunderstanding.bump(player, horse);
            }
            return;
        }
        if (!route.moving(time, horse.getX(), horse.getY(), horse.getZ())) { stop(); return; }
        if (time >= nextPath || horse.getNavigation().isIdle()) {
            nextPath = time + 10;
            var path = horse.getNavigation().findPathTo(player, 0);
            if (path == null || !path.reachesTarget() || !horse.getNavigation().startMovingAlong(path, .9)) stop();
        }
    }
}
