package top.csituka.magicaland.gameplay.easteregg;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;
import java.util.function.BiPredicate;

/** 只记录短期喂食关联和冷却，不保存实体引用。 */
public final class CarrotMisunderstandingState {
    public static final int FEED_WINDOW = 15 * 20, DURATION = 10 * 20, COOLDOWN = 5 * 60 * 20;
    public static final int CONTACT_DELAY = 12, STUCK_LIMIT = 40, MAX_ACTIVE = 64;
    public static final double RANGE_SQUARED = 8 * 8;
    public record Feed(UUID horse, String world, long tick) {}
    public record Claim(UUID horse, long tick) {}
    public record Meal(String world, long tick) {}
    private final Map<UUID, Feed> feeds = new HashMap<>();
    private final Map<UUID, Meal> meals = new HashMap<>();
    private final Map<UUID, Claim> active = new HashMap<>();
    private final Map<UUID, UUID> horses = new HashMap<>();
    private final Map<UUID, Long> cooldowns = new HashMap<>();

    public void fed(UUID player, UUID horse, String world, long now) {
        if (!active.containsKey(player) && !cooling(player, now)) feeds.put(player, new Feed(horse, world, now));
    }
    public void ate(UUID player, String world, long now) {
        if (!active.containsKey(player) && !cooling(player,now)) meals.put(player,new Meal(world,now));
    }
    public Feed takeMatch(UUID player, String world, long now) {
        Meal meal=meals.get(player);
        if (meal==null || !meal.world.equals(world) || !age(now,meal.tick,FEED_WINDOW)) return null;
        Feed feed=takeFeed(player,world,now);
        if (feed!=null) meals.remove(player);
        return feed;
    }
    public Feed takeFeed(UUID player, String world, long now) {
        Feed feed = feeds.remove(player);
        return feed != null && feed.world.equals(world) && age(now, feed.tick, FEED_WINDOW)
                && !cooling(player, now) && !active.containsKey(player) ? feed : null;
    }
    public boolean claim(UUID player, Feed feed, long now) {
        if (feed == null || active.size() >= MAX_ACTIVE || active.containsKey(player)
                || horses.containsKey(feed.horse) || cooling(player, now) || !age(now, feed.tick, FEED_WINDOW)) return false;
        active.put(player, new Claim(feed.horse, now));
        horses.put(feed.horse, player);
        cooldowns.put(player, now + COOLDOWN);
        return true;
    }
    public void release(UUID player) {
        feeds.remove(player);
        meals.remove(player);
        Claim claim = active.remove(player);
        if (claim != null) horses.remove(claim.horse, player);
    }
    public boolean cooling(UUID player, long now) { return now < cooldowns.getOrDefault(player, Long.MIN_VALUE); }
    public boolean expired(UUID player, long now) {
        Claim claim = active.get(player);
        return claim == null || now < claim.tick || now - claim.tick >= DURATION;
    }
    public void prune(long now, BiPredicate<UUID, String> validPlayer) {
        feeds.entrySet().removeIf(entry -> !age(now, entry.getValue().tick, FEED_WINDOW)
                || !validPlayer.test(entry.getKey(), entry.getValue().world));
        meals.entrySet().removeIf(entry -> !age(now,entry.getValue().tick,FEED_WINDOW)
                || !validPlayer.test(entry.getKey(),entry.getValue().world));
        cooldowns.values().removeIf(until -> now >= until);
    }
    public void clear() { feeds.clear(); meals.clear(); active.clear(); horses.clear(); cooldowns.clear(); }
    public static boolean age(long now, long then, long limit) { return now >= then && now - then <= limit; }
    public static float damage(float health) { return Float.isFinite(health) && health > 1 ? 1 : 0; }
    public static boolean contact(double x, double y, double z, double horseWidth, double playerWidth) {
        if (!Double.isFinite(x) || !Double.isFinite(y) || !Double.isFinite(z)
                || !Double.isFinite(horseWidth) || !Double.isFinite(playerWidth)
                || horseWidth <= 0 || playerWidth <= 0 || Math.abs(y) > .65) return false;
        double half = (horseWidth + playerWidth) * .5;
        double gapX = Math.max(0, Math.abs(x) - half), gapZ = Math.max(0, Math.abs(z) - half);
        return Double.isFinite(half) && gapX * gapX + gapZ * gapZ <= .15 * .15;
    }
    public static final class RouteWatch {
        private long moved;
        private double x, y, z;
        private boolean initialized;
        public boolean moving(long tick, double x, double y, double z) {
            if (!Double.isFinite(x + y + z)) return false;
            double dx = this.x - x, dy = this.y - y, dz = this.z - z;
            if (!initialized || dx * dx + dy * dy + dz * dz > .0025) {
                initialized = true; moved = tick; this.x = x; this.y = y; this.z = z;
            }
            return tick >= moved && tick - moved < STUCK_LIMIT;
        }
    }
}
