package top.elysianherd.magicaland.gameplay.remote;

import java.util.ArrayList;
import java.util.List;
import java.util.function.BiFunction;
import java.util.function.Supplier;
import net.minecraft.block.Block;
import net.minecraft.block.BlockState;
import net.minecraft.block.ShapeContext;
import net.minecraft.entity.Entity;
import net.minecraft.registry.RegistryKeys;
import net.minecraft.registry.tag.TagKey;
import net.minecraft.util.Identifier;
import net.minecraft.util.hit.BlockHitResult;
import net.minecraft.util.hit.HitResult;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Box;
import net.minecraft.util.math.Direction;
import net.minecraft.util.math.Vec3d;
import net.minecraft.util.shape.VoxelShape;
import net.minecraft.util.shape.VoxelShapes;
import net.minecraft.world.BlockView;
import net.minecraft.world.RaycastContext;

public final class TelekinesisSight {
    public static final TagKey<Block> PASSTHROUGH = TagKey.of(RegistryKeys.BLOCK,
            new Identifier("magicaland_gameplay", "telekinesis_sight_passthrough"));
    private static final double INSET = .00001;
    private static final int MAX_SURFACES = 96, MAX_BOXES = 8;
    private static final Direction[] SIDES = Direction.values();
    public record Surface(Vec3d point, Direction side) {}
    private TelekinesisSight() {}

    public static BlockHitResult raycast(Entity viewer, Vec3d from, Vec3d to) {
        return viewer.getWorld().raycast(context(viewer, from, to));
    }

    public static BlockHitResult blockHit(Entity viewer, Vec3d from, BlockPos target) {
        return blockHit(viewer, from, target, Double.POSITIVE_INFINITY);
    }

    public static BlockHitResult blockHit(Entity viewer, Vec3d from, BlockPos target, double reach) {
        var world = viewer.getWorld();
        if (!world.isChunkLoaded(target) || !world.getWorldBorder().contains(target)) return null;
        Vec3d look = from.squaredDistanceTo(viewer.getEyePos()) < .00000001 ? viewer.getRotationVec(1) : null;
        return blockHit(from, look, target, reach, () -> surfacePoints(viewer, target), (start, end) -> {
            if (!world.isRegionLoaded(BlockPos.ofFloored(Math.min(start.x, end.x), Math.min(start.y, end.y), Math.min(start.z, end.z)),
                    BlockPos.ofFloored(Math.max(start.x, end.x), Math.max(start.y, end.y), Math.max(start.z, end.z)))) return null;
            return raycast(viewer, start, end);
        });
    }

    static BlockHitResult blockHit(Vec3d from, Vec3d look, BlockPos target, Supplier<List<Vec3d>> points,
                                   BiFunction<Vec3d, Vec3d, BlockHitResult> ray) {
        return blockHit(from, look, target, Double.POSITIVE_INFINITY, points, ray);
    }

    static BlockHitResult blockHit(Vec3d from, Vec3d look, BlockPos target, double reach, Supplier<List<Vec3d>> points,
                                   BiFunction<Vec3d, Vec3d, BlockHitResult> ray) {
        if (Double.isNaN(reach) || reach < 0) return null;
        if (look != null) {
            var hit = ray.apply(from, from.add(look.multiply(from.distanceTo(Vec3d.ofCenter(target)) + 2)));
            if (matches(hit, target) && from.squaredDistanceTo(hit.getPos()) <= reach * reach) return hit;
        }
        for (Vec3d point : points.get()) {
            Vec3d end = point.add(point.subtract(from).normalize().multiply(INSET));
            var hit = ray.apply(from, end);
            if (matches(hit, target) && from.squaredDistanceTo(hit.getPos()) <= reach * reach) return hit;
        }
        return null;
    }

    private static boolean matches(BlockHitResult hit, BlockPos target) {
        return hit != null && hit.getType() == HitResult.Type.BLOCK && hit.getBlockPos().equals(target);
    }

    public static List<Vec3d> surfacePoints(Entity viewer, BlockPos target) {
        return surfaces(viewer, target).stream().map(Surface::point).toList();
    }

    public static List<Surface> surfaces(Entity viewer, BlockPos target) {
        var world = viewer.getWorld();
        if (!world.isChunkLoaded(target) || !world.getWorldBorder().contains(target)) return List.of();
        var state = world.getBlockState(target);
        return surfaces(state.getOutlineShape(world, target, ShapeContext.of(viewer)).getBoundingBoxes(), target);
    }

    static List<Surface> surfaces(List<Box> boxes, BlockPos target) {
        int count = Math.min(MAX_BOXES, boxes.size());
        if (count == 0) return List.of();
        var selected = new ArrayList<Box>(count);
        for (int i = 0; i < count; i++) selected.add(boxes.get(count == 1 ? 0 : i * (boxes.size() - 1) / (count - 1)));
        var points = new ArrayList<Surface>(MAX_SURFACES);
        for (Box box : selected) for (Direction side : SIDES) points.add(surface(box, target, side, 0, 0));
        int extra = Math.min(48, MAX_SURFACES / count - 6);
        for (Box box : selected) for (int i = 0; i < extra; i++) {
            int face = i % 6, pair = (i / 6 * 8 / ((extra + 5) / 6) + face * 3) % 8;
            int a = pair / 3 - 1, b = pair % 3 - 1;
            if (pair >= 4) { pair++; a = pair / 3 - 1; b = pair % 3 - 1; }
            points.add(surface(box, target, SIDES[face], a, b));
        }
        return List.copyOf(points);
    }

    private static Surface surface(Box box, BlockPos pos, Direction side, int a, int b) {
        double x = coordinate(box.minX, box.maxX, 0), y = coordinate(box.minY, box.maxY, 0), z = coordinate(box.minZ, box.maxZ, 0);
        switch (side.getAxis()) {
            case X -> { x = coordinate(box.minX, box.maxX, side.getOffsetX()); y = coordinate(box.minY, box.maxY, a); z = coordinate(box.minZ, box.maxZ, b); }
            case Y -> { y = coordinate(box.minY, box.maxY, side.getOffsetY()); x = coordinate(box.minX, box.maxX, a); z = coordinate(box.minZ, box.maxZ, b); }
            case Z -> { z = coordinate(box.minZ, box.maxZ, side.getOffsetZ()); x = coordinate(box.minX, box.maxX, a); y = coordinate(box.minY, box.maxY, b); }
        }
        return new Surface(new Vec3d(pos.getX() + x, pos.getY() + y, pos.getZ() + z), side);
    }

    private static double coordinate(double low, double high, int direction) {
        double inset = Math.min(INSET, (high - low) * .01);
        return direction < 0 ? low + inset : direction > 0 ? high - inset : (low + high) / 2;
    }

    static RaycastContext context(Entity viewer, Vec3d from, Vec3d to) {
        var collision = ShapeContext.of(viewer);
        return new RaycastContext(from, to, RaycastContext.ShapeType.OUTLINE, RaycastContext.FluidHandling.NONE, viewer) {
            @Override public VoxelShape getBlockShape(BlockState state, BlockView world, BlockPos pos) {
                if (state.isIn(PASSTHROUGH) && state.getCollisionShape(world, pos, collision).isEmpty())
                    return VoxelShapes.empty();
                return super.getBlockShape(state, world, pos);
            }
        };
    }
}
