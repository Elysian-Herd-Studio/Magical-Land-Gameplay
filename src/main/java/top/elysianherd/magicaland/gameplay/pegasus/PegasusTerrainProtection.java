package top.elysianherd.magicaland.gameplay.pegasus;

import net.minecraft.entity.Entity;
import net.minecraft.util.hit.HitResult;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Box;
import net.minecraft.util.math.Vec3d;
import net.minecraft.world.World;
import net.minecraft.world.RaycastContext;
import static top.elysianherd.magicaland.gameplay.pegasus.PegasusFlightMath.*;

public final class PegasusTerrainProtection {
    private static final double CLEARANCE = .45, MAX_LOOK_AHEAD = 32;
    private PegasusTerrainProtection() {}

    public static Step apply(Entity entity, Mode mode, Step step, boolean waterProtection,
                             boolean groundProtection, boolean impactReady) {
        if (entity == null || entity.isRemoved() || mode != Mode.GLIDE) return step;
        Step protectedStep = groundProtection ? ground(entity, step, impactReady) : step;
        return waterProtection ? PegasusWaterSkim.apply(entity, mode, protectedStep) : protectedStep;
    }

    private static Step ground(Entity entity, Step step, boolean impactReady) {
        Motion velocity = step.motion();
        if (velocity.y() > .03 || velocity.speed() < .05) return step;
        double duration = lookAhead(velocity);
        Box body = entity.getBoundingBox();
        Vec3d origin = body.getCenter();
        Vec3d movement = new Vec3d(velocity.x(), velocity.y(), velocity.z());
        Contact nearest = null;
        double checkedUntil = 0;
        World world = entity.getWorld();
        // 分段查询碰撞体，避免为长斜线扫描整片包围区域。
        for (double time = 0; time < duration; time += 1) {
            double end = Math.min(duration, time + 1);
            Box segment = body.offset(movement.multiply(time)).stretch(movement.multiply(end - time)).expand(.001);
            if (!loaded(world, segment)) break;
            checkedUntil = end;
            for (var shape : world.getBlockCollisions(entity, segment)) {
                for (Box obstacle : shape.getBoundingBoxes()) {
                    Contact contact = contact(body, origin, velocity, obstacle, duration);
                    if (contact != null && (nearest == null || contact.time() < nearest.time())) nearest = contact;
                }
            }
            if (nearest != null && nearest.time() <= end) break;
        }
        if (nearest == null || nearest.time() > checkedUntil || nearest.normal().y() <= 0
                || mayImpact(impactReady, velocity, nearest.blocked())) return step;
        Vec3d feet = new Vec3d(origin.x, body.minY + .01, origin.z);
        var fluid = world.raycast(new RaycastContext(feet, feet.add(movement.multiply(nearest.time())),
                RaycastContext.ShapeType.COLLIDER, RaycastContext.FluidHandling.ANY, entity));
        if (fluid.getType() == HitResult.Type.BLOCK && !world.getFluidState(fluid.getBlockPos()).isEmpty()
                && feet.distanceTo(fluid.getPos()) + .02 < movement.length() * nearest.time()) return step;
        double gap = body.minY - nearest.surface();
        if (gap < 0) return step;
        Step lifted = pullUp(step, gap);
        double rise = Math.max(.15, lifted.motion().y());
        Box overhead = body.offset(0, .001, 0).stretch(0, rise, 0);
        if (!loaded(world, overhead) || world.getBlockCollisions(entity, overhead).iterator().hasNext()) return step;
        return lifted;
    }

    static double lookAhead(Motion velocity) {
        return Math.min(MAX_LOOK_AHEAD / Math.max(.05, velocity.speed()),
                Math.min(24, 5 + Math.max(0, -velocity.y()) / .10));
    }

    static boolean mayImpact(boolean ready, Motion velocity, Motion blocked) {
        return ready && directImpact(blocked.speed(), velocity.speed());
    }

    static Step pullUp(Step step, double gap) {
        Motion velocity = step.motion();
        double speed = velocity.speed(), down = Math.max(0, -velocity.y());
        if (speed < .05 || velocity.y() > .03 || !Double.isFinite(gap) || gap < 0) return step;
        Motion horizontal = new Motion(velocity.x(), 0, velocity.z()).normalized();
        if (horizontal.speed() < .1) {
            Motion forward = step.dynamics().body().forward();
            horizontal = new Motion(forward.x(), 0, forward.z()).normalized();
            if (horizontal.speed() < .1) {
                Motion up = step.dynamics().body().up();
                horizontal = new Motion(up.x(), 0, up.z()).normalized();
            }
            if (horizontal.speed() < .1) horizontal = new Motion(0, 0, 1);
        }
        double braking = down * down / (2 * Math.max(.15, gap - CLEARANCE));
        double turn = Math.min(.24, .09 + braking / speed);
        Motion lifted = turnVelocity(velocity, horizontal.add(new Motion(0, .06, 0)), turn, new Motion(0, 1, 0));
        // 近地剩余空间不足时先减小下坠速度，给转向留出余量。
        double safeDescent = -Math.max(0, gap - .12) * .6;
        if (lifted.y() < safeDescent) lifted = new Motion(lifted.x(), safeDescent, lifted.z());
        Motion axis = velocity.normalized().cross(lifted.normalized());
        double angle = Math.acos(Math.max(-1, Math.min(1, velocity.normalized().dot(lifted.normalized()))));
        Dynamics previous = step.dynamics();
        Attitude body = Attitude.rotation(axis.normalized().scale(Math.min(MAX_ANGULAR_SPEED, angle))).multiply(previous.body());
        return new Step(lifted, step.stamina(), step.boosting(), step.exhausted(),
                new Dynamics(body, previous.angularVelocity(), previous.thrust()));
    }

    static Contact contact(Box body, Vec3d origin, Motion velocity, Box obstacle, double duration) {
        Box expanded = obstacle.expand((body.maxX - body.minX) / 2, (body.maxY - body.minY) / 2,
                (body.maxZ - body.minZ) / 2);
        double[] starts = {origin.x, origin.y, origin.z};
        double[] motion = {velocity.x(), velocity.y(), velocity.z()};
        double[] minimum = {expanded.minX, expanded.minY, expanded.minZ};
        double[] maximum = {expanded.maxX, expanded.maxY, expanded.maxZ};
        double[] entry = new double[3];
        double first = Double.NEGATIVE_INFINITY, last = duration;
        for (int axis = 0; axis < 3; axis++) {
            if (Math.abs(motion[axis]) < 1e-9) {
                if (starts[axis] <= minimum[axis] || starts[axis] >= maximum[axis]) return null;
                entry[axis] = Double.NEGATIVE_INFINITY;
                continue;
            }
            double a = (minimum[axis] - starts[axis]) / motion[axis];
            double b = (maximum[axis] - starts[axis]) / motion[axis];
            entry[axis] = Math.min(a, b);
            first = Math.max(first, entry[axis]);
            last = Math.min(last, Math.max(a, b));
            if (first > last) return null;
        }
        if (first < -1e-7 || first > duration || last <= 0) return null;
        boolean x = Math.abs(entry[0] - first) < 1e-7, y = Math.abs(entry[1] - first) < 1e-7,
                z = Math.abs(entry[2] - first) < 1e-7;
        Motion blocked = new Motion(x ? velocity.x() : 0, y ? velocity.y() : 0, z ? velocity.z() : 0);
        return new Contact(Math.max(0, first), blocked.normalized().scale(-1), blocked, obstacle.maxY);
    }

    private static boolean loaded(World world, Box box) {
        return box.minY >= world.getBottomY() && box.maxY <= world.getTopY()
                && world.getWorldBorder().contains(box)
                && world.isRegionLoaded(BlockPos.ofFloored(box.minX, box.minY, box.minZ),
                        BlockPos.ofFloored(box.maxX, box.maxY, box.maxZ));
    }

    record Contact(double time, Motion normal, Motion blocked, double surface) {}
}
