package top.csituka.magicaland.gameplay.pegasus;

import net.minecraft.entity.Entity;
import net.minecraft.registry.tag.FluidTags;
import net.minecraft.util.hit.HitResult;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Box;
import net.minecraft.util.math.Vec3d;
import net.minecraft.world.RaycastContext;
import net.minecraft.world.World;
import static top.csituka.magicaland.gameplay.pegasus.PegasusFlightMath.*;

public final class PegasusWaterSkim {
    private static final double CLEARANCE = .45;
    private PegasusWaterSkim() {}

    public static Step apply(Entity entity, Mode mode, Step step) {
        if (entity == null || entity.isRemoved() || mode != Mode.GLIDE || step.motion().y() > .03
                || protectionWeight(step.motion()) <= 0 || belowWater(entity, .01)) return step;
        World world = entity.getWorld();
        Motion velocity = step.motion();
        double lookAhead = Math.min(12, 4 + Math.max(0, -velocity.y()) / .2);
        Vec3d start = new Vec3d(entity.getX(), entity.getBoundingBox().minY + .05, entity.getZ());
        Vec3d end = start.add(velocity.x()*lookAhead, velocity.y()*lookAhead - CLEARANCE, velocity.z()*lookAhead);
        Box search = new Box(start, end).expand(.05);
        if (!loaded(world, search) || !world.getWorldBorder().contains(search)) return step;
        var fluidHit = world.raycast(new RaycastContext(start, end, RaycastContext.ShapeType.COLLIDER,
                RaycastContext.FluidHandling.ANY, entity));
        if (fluidHit.getType() != HitResult.Type.BLOCK) return step;
        BlockPos pos = fluidHit.getBlockPos();
        var fluid = world.getFluidState(pos);
        if (!fluid.isIn(FluidTags.WATER)) return step;
        var solidHit = world.raycast(new RaycastContext(start, fluidHit.getPos(), RaycastContext.ShapeType.COLLIDER,
                RaycastContext.FluidHandling.NONE, entity));
        if (solidHit.getType() != HitResult.Type.MISS) return step;
        double surface = pos.getY() + fluid.getHeight(world, pos);
        double gap = entity.getBoundingBox().minY - surface;
        if (gap <= .01 || fluidHit.getPos().y < surface - .12) return step;
        return aboveSurface(step, gap);
    }

    public static Step aboveSurface(Step step, double gap) {
        Motion velocity = step.motion();
        Motion lifted = lift(velocity, gap);
        Motion axis = velocity.normalized().cross(lifted.normalized());
        double angle = Math.acos(Math.max(-1, Math.min(1, velocity.normalized().dot(lifted.normalized()))));
        var previous = step.dynamics();
        Attitude body = Attitude.rotation(axis.normalized().scale(Math.min(.08, angle))).multiply(previous.body());
        return new Step(lifted, step.stamina(), step.boosting(), step.exhausted(),
                new Dynamics(body, previous.angularVelocity(), previous.thrust()));
    }

    public static boolean enteredWater(Entity entity, Mode mode) {
        return mode == Mode.GLIDE && belowWater(entity, .25);
    }

    public static double protectionWeight(Motion velocity) {
        double horizontal = Math.hypot(velocity.x(), velocity.z());
        if (horizontal < .1 || velocity.y() > .03) return 0;
        double degrees = Math.toDegrees(Math.atan2(Math.max(0, -velocity.y()), horizontal));
        double blend = Math.max(0, Math.min(1, (55 - degrees) / 12));
        return blend * blend * (3 - 2 * blend);
    }

    public static Motion lift(Motion velocity, double gap) {
        double weight = protectionWeight(velocity), speed = velocity.speed();
        if (weight <= 0 || gap <= .01 || !Double.isFinite(gap)) return velocity;
        double down = Math.max(0, -velocity.y());
        double braking = down * down / (2 * Math.max(.15, gap - CLEARANCE));
        double turn = Math.min(.16, .085 + braking / speed) * weight;
        Motion heading = new Motion(velocity.x(), 0, velocity.z()).normalized().add(new Motion(0, .025, 0));
        return turnVelocity(velocity, heading, turn, new Motion(0, 1, 0));
    }

    private static boolean belowWater(Entity entity, double depth) {
        if (entity == null || entity.isRemoved()) return false;
        World world = entity.getWorld();
        double y = entity.getBoundingBox().minY + depth;
        BlockPos pos = BlockPos.ofFloored(entity.getX(), y, entity.getZ());
        if (!world.isInBuildLimit(pos) || !world.isChunkLoaded(pos)) return false;
        var fluid = world.getFluidState(pos);
        return fluid.isIn(FluidTags.WATER) && y < pos.getY() + fluid.getHeight(world, pos) - 1e-5;
    }

    private static boolean loaded(World world, Box box) {
        return box.minY >= world.getBottomY() && box.maxY <= world.getTopY()
                && world.isRegionLoaded(BlockPos.ofFloored(box.minX, box.minY, box.minZ),
                        BlockPos.ofFloored(box.maxX, box.maxY, box.maxZ));
    }
}
