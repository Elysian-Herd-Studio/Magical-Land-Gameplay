package top.csituka.magicaland.gameplay.easteregg;

import java.util.UUID;
import net.minecraft.entity.ai.goal.Goal;
import net.minecraft.entity.ai.goal.GoalSelector;
import net.minecraft.util.profiler.DummyProfiler;

public final class CarrotMisunderstandingStateTest {
    private static int checks;
    private static final UUID PLAYER = new UUID(1, 1), OTHER = new UUID(1, 2), HORSE = new UUID(2, 1), HORSE2 = new UUID(2, 2);
    private static void check(boolean value, String message) {
        checks++; if (!value) throw new AssertionError(message);
    }
    public static void main(String[] args) {
        association(); cooldownAndClaims(); cleanup(); bounds(); contactAabb(); route(); vanillaSelector();
        System.out.println("PASS CarrotMisunderstandingStateTest: " + checks + " checks");
    }
    private static void association() {
        var state = new CarrotMisunderstandingState();
        check(state.takeFeed(PLAYER, "a", 100) == null, "eating without successful feed never triggers");
        state.fed(PLAYER, HORSE, "a", 100);
        check(state.takeFeed(OTHER, "a", 100) == null, "feed association belongs only to feeder");
        state.fed(PLAYER, HORSE2, "a", 120);
        var feed = state.takeFeed(PLAYER, "a", 121);
        check(feed.horse().equals(HORSE2), "only newest horse, no fallback to previous horse");
        check(state.takeFeed(PLAYER, "a", 121) == null, "consumed feed cannot replay");
        state.fed(PLAYER, HORSE, "a", 100);
        check(state.takeFeed(PLAYER, "b", 101) == null, "world change cancels");
        check(state.takeFeed(PLAYER, "a", 102) == null, "returning world does not resurrect consumed feed");
        for (int elapsed = -20; elapsed < 340; elapsed++) {
            state.fed(PLAYER, HORSE, "a", 500);
            check((state.takeFeed(PLAYER, "a", 500 + elapsed) != null) == (elapsed >= 0 && elapsed <= 300),
                    "15-second window exact boundary / reversed clock");
        }
    }
    private static void cooldownAndClaims() {
        var state = new CarrotMisunderstandingState();
        state.fed(PLAYER, HORSE, "a", 100);
        var feed = state.takeFeed(PLAYER, "a", 100);
        check(state.claim(PLAYER, feed, 100), "first valid claim accepted");
        check(!state.claim(PLAYER, feed, 100), "one active horse per player");
        check(!state.claim(OTHER, feed, 100), "one active player per horse");
        check(state.claim(OTHER, new CarrotMisunderstandingState.Feed(HORSE2, "a", 100), 100), "players isolated");
        state.release(PLAYER);
        check(state.cooling(PLAYER, 6099), "cancelled encounter still on cooldown");
        state.fed(PLAYER, HORSE, "a", 6099);
        check(state.takeFeed(PLAYER, "a", 6099) == null, "cannot preload feed while cooling");
        check(!state.cooling(PLAYER, 6100), "five minute cooldown precise end");
        state.fed(PLAYER, HORSE, "a", 6100);
        check(state.claim(PLAYER, state.takeFeed(PLAYER, "a", 6100), 6100), "eligible again after five minutes");
        for (int elapsed = -2; elapsed <= 205; elapsed++)
            check(state.expired(PLAYER, 6100 + elapsed) == (elapsed < 0 || elapsed >= 200), "10-second duration bounded");
        state.release(PLAYER); state.release(PLAYER);
        check(state.expired(PLAYER, 6110), "cleanup idempotent");
        state.clear();
        check(!state.cooling(PLAYER, 0), "server restart clears stale cooldown clock");
        check(state.claim(OTHER, new CarrotMisunderstandingState.Feed(HORSE, "a", 0), 0), "horse reservation released");
    }
    private static void cleanup() {
        for (boolean alive : new boolean[] {false, true}) for (boolean sameWorld : new boolean[] {false, true}) {
            var state = new CarrotMisunderstandingState();
            state.fed(PLAYER, HORSE, "a", 20);
            state.prune(21, (id, dimension) -> alive && sameWorld);
            check((state.takeFeed(PLAYER, "a", 22) != null) == (alive && sameWorld), "death/disconnect/dimension prune feed");
        }
        var state = new CarrotMisunderstandingState();
        state.fed(PLAYER, HORSE, "a", 100);
        state.prune(401, (id, dimension) -> true);
        check(state.takeFeed(PLAYER, "a", 401) == null, "expired pending record pruned");
        for (int i = 0; i < CarrotMisunderstandingState.MAX_ACTIVE + 10; i++) {
            check(state.claim(new UUID(10, i), new CarrotMisunderstandingState.Feed(new UUID(20, i), "a", 0), 0)
                    == (i < CarrotMisunderstandingState.MAX_ACTIVE), "global transient goal bound");
        }
    }
    private static void bounds() {
        for (int i = 0; i <= 20000; i++) {
            float health = i / 1000f;
            float damage = CarrotMisunderstandingState.damage(health);
            check(damage == 0 || damage == 1, "only zero or one raw damage");
            check(damage == 0 || health - damage > 0, "one bump cannot kill in vanilla damage chain");
        }
        check(CarrotMisunderstandingState.damage(Float.NaN) == 0, "nonfinite health safe");
        check(CarrotMisunderstandingState.damage(Float.POSITIVE_INFINITY) == 0, "infinite health safe");
        check(CarrotMisunderstandingState.contact(1.1, 0, 0, 1.4, .6), "close body-edge contact accepted");
        check(!CarrotMisunderstandingState.contact(1.3, 0, 0, 1.4, .6), "no remote collision");
        check(!CarrotMisunderstandingState.contact(0, .7, 0, 1.4, .6), "no collision through floor level");
        check(!CarrotMisunderstandingState.contact(Double.NaN, 0, 0, 1.4, .6), "bad coordinates rejected");
    }
    private static void contactAabb() {
        check(CarrotMisunderstandingState.contact(.99, 0, .99, 1.396484375, .6),
                "real diagonal horse/player collision triggers without requiring impossible center overlap");
        for (double horseWidth : new double[] {1.4, 1.396484375})
            for (double playerWidth : new double[] {.6, .6000000238418579})
                for (int sx : new int[] {-1, 1}) for (int sz : new int[] {-1, 1}) {
                    double half = (horseWidth + playerWidth) * .5;
                    check(CarrotMisunderstandingState.contact(sx * (half + .149), 0, 0, horseWidth, playerWidth), "front contact");
                    check(CarrotMisunderstandingState.contact(0, 0, sz * (half + .149), horseWidth, playerWidth), "side contact");
                    check(!CarrotMisunderstandingState.contact(sx * (half + .151), 0, 0, horseWidth, playerWidth), "outside front edge");
                    check(!CarrotMisunderstandingState.contact(0, 0, sz * (half + .151), horseWidth, playerWidth), "outside side edge");
                    check(CarrotMisunderstandingState.contact(sx * half, .65, sz * half, horseWidth, playerWidth), "AABB corner touch");
                    check(!CarrotMisunderstandingState.contact(sx * half, .65001, sz * half, horseWidth, playerWidth), "height guard retained");
                    for (int step = 0; step <= 90; step++) {
                        double angle = Math.toRadians(step);
                        for (double gap : new double[] {.149, .151}) {
                            double x = sx * (half + gap * Math.cos(angle));
                            double z = sz * (half + gap * Math.sin(angle));
                            check(CarrotMisunderstandingState.contact(x, 0, z, horseWidth, playerWidth) == (gap < .15),
                                    "rounded AABB corner gap has consistent inside/outside boundary");
                        }
                    }
                }
        for (double bad : new double[] {Double.NaN, Double.POSITIVE_INFINITY, Double.NEGATIVE_INFINITY}) {
            check(!CarrotMisunderstandingState.contact(bad, 0, 0, 1.4, .6), "nonfinite X rejected");
            check(!CarrotMisunderstandingState.contact(0, bad, 0, 1.4, .6), "nonfinite Y rejected");
            check(!CarrotMisunderstandingState.contact(0, 0, bad, 1.4, .6), "nonfinite Z rejected");
        }
        for (double bad : new double[] {0, -1, Double.NaN, Double.POSITIVE_INFINITY, Double.NEGATIVE_INFINITY}) {
            check(!CarrotMisunderstandingState.contact(0, 0, 0, bad, .6), "invalid horse width rejected");
            check(!CarrotMisunderstandingState.contact(0, 0, 0, 1.4, bad), "invalid player width rejected");
        }
        check(!CarrotMisunderstandingState.contact(0, 0, 0, Double.MAX_VALUE, Double.MAX_VALUE), "overflowing width sum rejected");
    }
    private static void route() {
        var route = new CarrotMisunderstandingState.RouteWatch();
        for (int tick = 0; tick < 60; tick++)
            check(route.moving(tick, 1, 2, 3) == (tick < 40), "stuck for two seconds cancels");
        route = new CarrotMisunderstandingState.RouteWatch();
        for (int tick = 0; tick < 200; tick++) check(route.moving(tick, tick * .05, 0, 0), "normal walk never falsely stuck");
        check(!route.moving(199, Double.NaN, 0, 0), "bad navigation coordinate cancels");
    }
    private static void vanillaSelector() {
        var selector = new GoalSelector(() -> DummyProfiler.INSTANCE);
        class TestGoal extends Goal {
            boolean enabled, running; int starts, stops;
            TestGoal(boolean enabled) { this.enabled = enabled; setControls(java.util.EnumSet.of(Control.MOVE, Control.LOOK)); }
            @Override public boolean canStart() { return enabled; }
            @Override public boolean shouldContinue() { return enabled; }
            @Override public void start() { running = true; starts++; }
            @Override public void stop() { running = false; stops++; }
        }
        var breed = new TestGoal(true); var temptation = new TestGoal(true); var easter = new TestGoal(true);
        selector.add(2, breed); selector.add(3, temptation); selector.add(2, easter);
        selector.tick();
        check(breed.running && !easter.running, "existing first-registered breed wins equal priority");
        selector.remove(easter);
        check(breed.running && breed.stops == 0, "removing never-started temporary goal leaves breed intact");
        breed.enabled = false; selector.tick();
        check(temptation.running, "original temptation can run");
        selector.add(2, easter); selector.tick();
        check(easter.running && !temptation.running, "temporary goal can replace held golden-carrot temptation");
        breed.enabled = true; easter.enabled = false; selector.tick();
        check(breed.running && !easter.running, "new genuine mate reclaims controls after immediate yield");
        selector.remove(easter);
        check(breed.running && breed.stops == 1, "temporary cleanup never stops newly resumed breeding goal");
    }
}
