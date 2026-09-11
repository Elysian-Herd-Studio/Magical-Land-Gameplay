package top.csituka.magicaland.gameplay.remote;

import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.util.UUID;
import net.minecraft.entity.ai.TargetPredicate;
import net.minecraft.entity.mob.PathAwareEntity;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.item.ItemStack;
import net.minecraft.recipe.Ingredient;
import net.minecraft.util.math.Vec3d;
import net.minecraft.world.World;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;
import top.csituka.magicaland.gameplay.mixin.RemoteTemptGoalMixin;

public final class RemoteTemptGoalTest {
    private static int checks;
    private static final Method CONTINUE = method("remoteContinue");
    private static final Method FOOD = method("remoteFood");
    private static final class Goal extends RemoteTemptGoalMixin {
        int starts;
        boolean scared;
        @Override protected boolean canBeScared() { return scared; }
        @Override public void start() {
            starts++;
            check(get(this, "magicaland$food") == null, "handoff reaches ordinary startup without remote interception");
        }
    }
    private record Fixture(World world, PathAwareEntity mob, RemoteToolEntity tool, PlayerEntity owner,
                           Ingredient food, TargetPredicate predicate, Goal goal) {}

    public static void main(String[] args) throws Exception {
        for (int yaw = -180; yaw <= 180; yaw += 12) {
            Fixture f = fixture();
            PlayerEntity ordinary = new PlayerEntity(UUID.randomUUID());
            ordinary.held = new ItemStack("wheat"); ordinary.yaw = yaw; ordinary.pitch = yaw / 2f;
            f.world().players.put(ordinary.uuid, ordinary);
            CallbackInfoReturnable<Boolean> result = run(f.goal());
            check(result.isCancelled() && result.getReturnValueZ(), "ordinary food immediately takes over");
            check(get(f.goal(), "closestPlayer") == ordinary && get(f.goal(), "magicaland$food") == null, "handoff removes remote and chooses player");
            check(f.world().lastPredicate == f.predicate(), "uses exact original per-goal predicate");
            check(f.goal().starts == 1 && (int)get(f.goal(), "cooldown") == 0, "single original startup without cooldown");
            check((double)get(f.goal(), "lastPlayerYaw") == ordinary.yaw
                    && (double)get(f.goal(), "lastPlayerPitch") == ordinary.pitch, "reanchors both fear angles");
            int queries = f.world().playerQueries;
            result = run(f.goal());
            check(!result.isCancelled() && f.world().playerQueries == queries, "later ordinary continuation stays vanilla");
        }

        Fixture f = fixture();
        PlayerEntity ordinary = new PlayerEntity(UUID.randomUUID());
        ordinary.held = new ItemStack("sword");
        f.world().players.put(ordinary.uuid, ordinary);
        for (int tick = 0; tick < 20; tick++) {
            check(run(f.goal()).getReturnValueZ(), "unmatched ordinary hand does not steal target");
            check(get(f.goal(), "magicaland$food") == f.tool() && f.goal().starts == 0, "remote keeps its existing animation and anchor");
        }
        ordinary.held = new ItemStack("wheat"); ordinary.distance = 12;
        check(run(f.goal()).getReturnValueZ() && f.goal().starts == 0, "ordinary candidate still respects original range");
        ordinary.distance = 3; ordinary.spectator = true;
        check(run(f.goal()).getReturnValueZ() && f.goal().starts == 0, "spectator does not take over");
        ordinary.spectator = false; f.tool().returning = true;
        check(run(f.goal()).getReturnValueZ() && f.goal().starts == 1, "ordinary handoff also works when orb becomes invalid");

        f = fixture(); f.goal().scared = true;
        f.tool().position = new Vec3d(.05, 0, 0);
        check(run(f.goal()).getReturnValueZ(), "small nearby drift initially allowed");
        f.tool().position = new Vec3d(.09, 0, 0);
        check(run(f.goal()).getReturnValueZ(), "nearby movement still measured from original anchor");
        f.tool().position = new Vec3d(.15, 0, 0);
        check(!run(f.goal()).getReturnValueZ() && f.goal().starts == 0, "accumulated nearby movement scares animal without resetting anchor");

        f = fixture(); f.goal().scared = true; f.tool().distance = 7;
        f.tool().position = new Vec3d(3, 0, 0);
        check(run(f.goal()).getReturnValueZ(), "distant motion refreshes vanilla-style position anchor");
        f.tool().distance = 2; f.tool().position = new Vec3d(3.05, 0, 0);
        check(run(f.goal()).getReturnValueZ(), "new nearby phase starts from last distant anchor");
        f.tool().yaw = 10;
        check(!run(f.goal()).getReturnValueZ(), "fast turn still scares animal");
        System.out.println("PASS RemoteTemptGoalTest: " + checks + " actual mixin callback checks (stub world; original startup is spied, not a game AI test)");
    }

    private static Fixture fixture() throws Exception {
        RemoteToolServer.tools.clear();
        World world = new World();
        PlayerEntity owner = new PlayerEntity(UUID.randomUUID());
        world.players.put(owner.uuid, owner);
        PathAwareEntity mob = new PathAwareEntity(world);
        RemoteToolEntity tool = new RemoteToolEntity(world, owner.uuid);
        RemoteToolServer.tools.add(tool);
        Ingredient food = new Ingredient("wheat");
        TargetPredicate predicate = new TargetPredicate(player -> food.test(player.held));
        Goal goal = new Goal();
        set(goal, "mob", mob); set(goal, "food", food); set(goal, "predicate", predicate);
        CallbackInfoReturnable<Boolean> result = new CallbackInfoReturnable<>("canStart", true, false);
        FOOD.invoke(goal, result);
        check(result.getReturnValueZ() && get(goal, "magicaland$food") == tool, "remote initial selection");
        return new Fixture(world, mob, tool, owner, food, predicate, goal);
    }
    private static CallbackInfoReturnable<Boolean> run(Goal goal) throws Exception {
        CallbackInfoReturnable<Boolean> result = new CallbackInfoReturnable<>("shouldContinue", true, false);
        CONTINUE.invoke(goal, result);
        return result;
    }
    private static Method method(String name) {
        try {
            Method result = RemoteTemptGoalMixin.class.getDeclaredMethod(name, CallbackInfoReturnable.class);
            result.setAccessible(true); return result;
        } catch (ReflectiveOperationException e) { throw new AssertionError(e); }
    }
    private static Object get(Goal goal, String name) {
        try {
            Field field = RemoteTemptGoalMixin.class.getDeclaredField(name);
            field.setAccessible(true); return field.get(goal);
        } catch (ReflectiveOperationException e) { throw new AssertionError(e); }
    }
    private static void set(Goal goal, String name, Object value) throws Exception {
        Field field = RemoteTemptGoalMixin.class.getDeclaredField(name);
        field.setAccessible(true); field.set(goal, value);
    }
    private static void check(boolean condition, String message) {
        checks++; if (!condition) throw new AssertionError(message);
    }
}
