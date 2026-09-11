package top.csituka.magicaland.gameplay.remote;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.UUID;
import java.util.function.Predicate;
import net.minecraft.entity.ai.brain.MemoryModuleType;
import net.minecraft.entity.mob.PathAwareEntity;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.item.ItemStack;
import net.minecraft.world.World;

public final class RemoteBrainTemptationTest {
    private static final Predicate<ItemStack> FOOD = stack -> stack.item.equals("wheat");
    private static int checks;
    private record Fixture(World world, PlayerEntity owner, PathAwareEntity mob, RemoteToolEntity tool) {}

    public static void main(String[] args) throws Exception {
        Fixture f = fixture();
        RemoteBrainTemptation.sense(f.mob(), FOOD);
        check(RemoteBrainTemptation.assigned(f.mob()), "remote food selected");
        check(RemoteBrainTemptation.target(f.mob()) == f.tool(), "physical target is the orb");
        check(memory(f.mob()) == f.owner(), "identity memory remains the real player");

        PlayerEntity ordinary = new PlayerEntity(UUID.randomUUID());
        f.mob().getBrain().remember(MemoryModuleType.TEMPTING_PLAYER, ordinary);
        RemoteBrainTemptation.sense(f.mob(), FOOD);
        check(!RemoteBrainTemptation.assigned(f.mob()) && memory(f.mob()) == ordinary, "ordinary temptation wins and removes stale association");
        RemoteBrainTemptation.forget(f.mob());
        check(memory(f.mob()) == ordinary, "unowned memory is retained");

        f = fixture();
        f.owner().spectator = true;
        RemoteBrainTemptation.sense(f.mob(), FOOD);
        check(!RemoteBrainTemptation.assigned(f.mob()), "spectator cannot tempt");
        f.owner().spectator = false;
        f.mob().passenger = f.owner();
        RemoteBrainTemptation.sense(f.mob(), FOOD);
        check(!RemoteBrainTemptation.assigned(f.mob()), "own passenger cannot tempt");
        f.mob().passenger = null;
        f.world().players.clear();
        RemoteBrainTemptation.sense(f.mob(), FOOD);
        check(!RemoteBrainTemptation.assigned(f.mob()), "missing owner cannot enter memory");

        for (int kind = 0; kind < 12; kind++) {
            for (int repeat = 0; repeat < 40; repeat++) {
                f = fixture();
                RemoteBrainTemptation.sense(f.mob(), FOOD);
                check(RemoteBrainTemptation.target(f.mob()) == f.tool(), "valid before transition");
                switch (kind) {
                    case 0 -> f.tool().stack = new ItemStack("sword");
                    case 1 -> f.tool().returning = true;
                    case 2 -> f.tool().active = false;
                    case 3 -> f.tool().visible = false;
                    case 4 -> f.tool().distance = 10;
                    case 5 -> f.world().players.clear();
                    case 6 -> f.mob().world = new World();
                    case 7 -> f.mob().valid = false;
                    case 8 -> f.owner().spectator = true;
                    case 9 -> f.mob().passenger = f.owner();
                    case 10 -> RemoteToolServer.tools.clear();
                    case 11 -> f.world().players.put(f.owner().uuid, new PlayerEntity(f.owner().uuid));
                    default -> throw new AssertionError();
                }
                check(RemoteBrainTemptation.target(f.mob()) == null, "invalid transition " + kind);
                RemoteBrainTemptation.forget(f.mob());
                check(!RemoteBrainTemptation.assigned(f.mob()) && memory(f.mob()) == null, "invalid owner is not followed at body position");
                RemoteBrainTemptation.forget(f.mob());
                check(memory(f.mob()) == null, "idempotent cleanup");
            }
        }

        f = fixture();
        RemoteBrainTemptation.sense(f.mob(), FOOD);
        f.mob().getBrain().remember(MemoryModuleType.TEMPTING_PLAYER, ordinary);
        check(RemoteBrainTemptation.target(f.mob()) == null, "external replacement invalidates only our binding");
        RemoteBrainTemptation.forget(f.mob());
        check(memory(f.mob()) == ordinary, "cleanup never deletes a different target");

        f = fixture();
        PathAwareEntity other = new PathAwareEntity(f.world());
        RemoteBrainTemptation.sense(f.mob(), FOOD);
        RemoteBrainTemptation.sense(other, stack -> stack.item.equals("cactus"));
        check(RemoteBrainTemptation.target(f.mob()) == f.tool() && !RemoteBrainTemptation.assigned(other), "per-mob predicates stay separate");
        RemoteBrainTemptation.forget(other);
        check(RemoteBrainTemptation.target(f.mob()) == f.tool(), "unrelated mob does not clear target");

        Path repo = Path.of(args[0]);
        Path mixins = repo.resolve("src/main/java/top/csituka/magicaland/gameplay/mixin");
        String sensor = Files.readString(mixins.resolve("RemoteTemptationsSensorMixin.java"));
        String task = Files.readString(mixins.resolve("RemoteTemptTaskMixin.java"));
        check(sensor.contains("sense(Lnet/minecraft/server/world/ServerWorld;Lnet/minecraft/entity/mob/PathAwareEntity;)V")
                && sensor.contains("@At(\"TAIL\")"), "only typed sensor after vanilla selection");
        check(sensor.contains("RemoteBrainTemptation.sense(mob, ingredient)"), "uses original species ingredient");
        check(task.contains("shouldKeepRunning(Lnet/minecraft/server/world/ServerWorld;Lnet/minecraft/entity/mob/PathAwareEntity;J)Z")
                && task.contains("@At(\"RETURN\")"), "ordinary keep-running decision survives");
        check(task.contains("keepRunning(Lnet/minecraft/server/world/ServerWorld;Lnet/minecraft/entity/mob/PathAwareEntity;J)V"), "only typed follow task redirected");
        check(task.contains("new EntityLookTarget(tool, true)") && task.contains("new EntityLookTarget(tool, false)")
                && task.contains("mob.squaredDistanceTo(tool)"), "look, walk and distance all use orb");
        check(task.contains("stopDistanceGetter.apply(mob)") && task.contains("getSpeed(mob), 2"), "species movement functions and completion range retained");
        check(!task.contains("finishRunning") && !task.contains("TEMPTATION_COOLDOWN_TICKS")
                && !task.contains("BREED_TARGET") && !task.contains("IS_PANICKING") && !task.contains("method = \"run("), "no edits to breeding, fear, cooldown or activity startup");
        System.out.println("PASS RemoteBrainTemptationTest: " + checks + " actual helper lifecycle and source boundaries (stub world; not in-game AI)");
    }

    private static Fixture fixture() {
        RemoteToolServer.tools.clear();
        World world = new World();
        PlayerEntity owner = new PlayerEntity(UUID.randomUUID());
        world.players.put(owner.uuid, owner);
        PathAwareEntity mob = new PathAwareEntity(world);
        RemoteToolEntity tool = new RemoteToolEntity(world, owner.uuid);
        RemoteToolServer.tools.add(tool);
        return new Fixture(world, owner, mob, tool);
    }
    private static PlayerEntity memory(PathAwareEntity mob) {
        return mob.getBrain().getOptionalRegisteredMemory(MemoryModuleType.TEMPTING_PLAYER).orElse(null);
    }
    private static void check(boolean condition, String message) {
        checks++;
        if (!condition) throw new AssertionError(message);
    }
}
