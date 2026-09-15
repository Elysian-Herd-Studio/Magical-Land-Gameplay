package top.elysianherd.magicaland.gameplay.levitation;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import net.minecraft.block.ShapeContext;
import net.minecraft.entity.Entity;
import net.minecraft.registry.tag.FluidTags;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Box;
import net.minecraft.util.math.MathHelper;
import net.minecraft.world.World;

public final class UnicornLevitationGround {
    public enum Kind { SOLID, WATER, LAVA }
    public record Support(Kind kind, double y, double distance, boolean ahead) {}
    /** 碰撞箱与液面高度均为方块局部坐标；null fluid 表示无可托举液体。 */
    public record Cell(boolean loaded, List<Box> collisions, Kind fluid, double fluidHeight) {
        public Cell { collisions = List.copyOf(collisions); }
        public static final Cell UNKNOWN = new Cell(false, List.of(), null, 0);
        public static final Cell AIR = new Cell(true, List.of(), null, 0);
    }
    @FunctionalInterface public interface Cells { Cell at(int x, int y, int z); }
    public record Scan(Support support, int probes, boolean complete) {}
    public static final int MAX_PROBES = 768, MAX_SHAPE_BOXES = 32;
    public static final double MAX_LOOK_AHEAD = .6;
    private static final double EPSILON = .00001;

    private UnicornLevitationGround() {}

    public static Support find(World world, Entity entity, UnicornLevitationMath.Motion velocity) {
        if (world == null || entity == null || entity.getWorld() != world || entity.isRemoved()) return null;
        ShapeContext context = ShapeContext.of(entity);
        Cells cells = (x, y, z) -> {
            BlockPos pos = new BlockPos(x, y, z);
            if (!world.isInBuildLimit(pos) || !world.isChunkLoaded(pos)) return Cell.UNKNOWN;
            var state = world.getBlockState(pos);
            List<Box> boxes = state.getCollisionShape(world, pos, context).getBoundingBoxes();
            if (boxes.size() > MAX_SHAPE_BOXES) return Cell.UNKNOWN;
            var fluid = state.getFluidState();
            Kind kind = fluid.isIn(FluidTags.WATER) ? Kind.WATER : fluid.isIn(FluidTags.LAVA) ? Kind.LAVA : null;
            double height = kind == null ? 0 : fluid.getHeight(world, pos);
            return new Cell(true, boxes, kind, height);
        };
        return scan(cells, entity.getBoundingBox(), velocity, MAX_PROBES).support();
    }

    public static Scan scan(Cells cells, Box body, UnicornLevitationMath.Motion velocity, int budget) {
        if (cells == null || velocity == null || !validBody(body)) return new Scan(null, 0, false);
        Search search = new Search(cells, Math.max(0, Math.min(MAX_PROBES, budget)));
        Support current = search.below(body, UnicornLevitationMath.probeDepth(velocity.y()), false);
        if (!search.complete || search.submergedCurrent) return search.result(null);
        double speed = velocity.horizontalSpeed();
        if (speed < .0001) return search.result(current);
        double dx = velocity.x() / speed * MAX_LOOK_AHEAD, dz = velocity.z() / speed * MAX_LOOK_AHEAD;
        if (!search.clear(body.stretch(dx, 0, dz))) return search.result(current);
        Support ahead = search.below(body.offset(dx, 0, dz), .6, true);
        if (!search.complete) return search.result(null);
        if (ahead != null && ahead.kind != Kind.SOLID && ahead.distance <= UnicornLevitationMath.SURFACE_CLEARANCE + .25
                && (current == null || current.kind == Kind.SOLID || ahead.y > current.y)) {
            double rise = Math.max(0, ahead.y + UnicornLevitationMath.SURFACE_CLEARANCE - body.minY);
            if (rise == 0 || search.clear(body.stretch(dx, rise, dz))) current = ahead;
        }
        return search.result(current);
    }

    private static boolean validBody(Box box) {
        return box != null && Double.isFinite(box.minX) && Double.isFinite(box.minY) && Double.isFinite(box.minZ)
                && Double.isFinite(box.maxX) && Double.isFinite(box.maxY) && Double.isFinite(box.maxZ)
                && Math.abs(box.minX) < 30_000_000 && Math.abs(box.minZ) < 30_000_000 && Math.abs(box.minY) < 30_000_000
                && box.maxX - box.minX > 0 && box.maxX - box.minX <= 1.5 && box.maxZ - box.minZ > 0 && box.maxZ - box.minZ <= 1.5
                && box.maxY - box.minY > 0 && box.maxY - box.minY <= 3;
    }

    private static final class Search {
        final Cells cells;
        final int budget;
        final Map<BlockPos, Cell> cache = new HashMap<>();
        boolean complete = true;
        boolean submergedCurrent;
        Search(Cells cells, int budget) { this.cells = cells; this.budget = budget; }
        Cell cell(int x, int y, int z) {
            BlockPos pos = new BlockPos(x, y, z);
            Cell result = cache.get(pos);
            if (result != null) return result;
            if (cache.size() >= budget) { complete = false; return Cell.UNKNOWN; }
            result = cells.at(x, y, z);
            if (result == null || !result.loaded || result.collisions.size() > MAX_SHAPE_BOXES) result = Cell.UNKNOWN;
            cache.put(pos, result);
            if (!result.loaded) complete = false;
            return result;
        }

        Support below(Box footprint, double depth, boolean ahead) {
            Support nearest = null;
            double fluidTolerance = ahead ? UnicornLevitationMath.SURFACE_MAX_RISE : UnicornLevitationMath.SURFACE_TOLERANCE;
            int top = MathHelper.floor(footprint.minY + fluidTolerance);
            int bottom = MathHelper.floor(footprint.minY - depth);
            for (int x = MathHelper.floor(footprint.minX + EPSILON); x <= MathHelper.floor(footprint.maxX - EPSILON); x++) {
                for (int z = MathHelper.floor(footprint.minZ + EPSILON); z <= MathHelper.floor(footprint.maxZ - EPSILON); z++) {
                    boolean submerged = false;
                    for (int y = top; y >= bottom; y--) {
                        Cell cell = cell(x, y, z);
                        if (!complete) return null;
                        Support found = null;
                        for (Box shape : cell.collisions) {
                            Box bounds = shape.offset(x, y, z);
                            if (!horizontalOverlap(footprint, bounds)) continue;
                            if (bounds.maxY > footprint.minY + UnicornLevitationMath.SURFACE_TOLERANCE) {
                                if (bounds.minY < footprint.minY + EPSILON) return null;
                                continue;
                            }
                            double distance = footprint.minY - bounds.maxY;
                            if (distance >= -UnicornLevitationMath.SURFACE_TOLERANCE && distance <= depth
                                    && (found == null || bounds.maxY > found.y)) found = new Support(Kind.SOLID, bounds.maxY, distance, ahead);
                        }
                        if (cell.fluid != null && Double.isFinite(cell.fluidHeight) && cell.fluidHeight > 0 && cell.fluidHeight <= 1) {
                            double surface = y + cell.fluidHeight, distance = footprint.minY - surface;
                            if (distance < -fluidTolerance) {
                                submerged = true;
                                if (!ahead) submergedCurrent = true;
                            }
                            if (!submerged && distance >= -fluidTolerance && distance <= depth
                                    && (found == null || surface > found.y + EPSILON))
                                found = new Support(cell.fluid, surface, distance, ahead);
                        }
                        if (found != null) {
                            boolean preferFluid = ahead && found.kind != Kind.SOLID;
                            boolean preserveFluid = ahead && nearest != null && nearest.kind != Kind.SOLID && found.kind == Kind.SOLID;
                            if (nearest == null || preferFluid && nearest.kind == Kind.SOLID
                                    || !preserveFluid && (found.y > nearest.y || found.y == nearest.y && found.kind == Kind.SOLID)) nearest = found;
                            break;
                        }
                    }
                }
            }
            return nearest;
        }

        boolean clear(Box sweep) {
            for (int x = MathHelper.floor(sweep.minX + EPSILON); x <= MathHelper.floor(sweep.maxX - EPSILON); x++)
                for (int z = MathHelper.floor(sweep.minZ + EPSILON); z <= MathHelper.floor(sweep.maxZ - EPSILON); z++)
                    for (int y = MathHelper.floor(sweep.minY + EPSILON) - 1; y <= MathHelper.floor(sweep.maxY - EPSILON); y++) {
                        Cell cell = cell(x, y, z);
                        if (!complete) return false;
                        for (Box shape : cell.collisions) if (shape.offset(x, y, z).intersects(sweep)) return false;
                    }
            return true;
        }

        Scan result(Support support) { return new Scan(complete ? support : null, cache.size(), complete); }
    }

    private static boolean horizontalOverlap(Box a, Box b) {
        return b.maxX > a.minX + EPSILON && b.minX < a.maxX - EPSILON
                && b.maxZ > a.minZ + EPSILON && b.minZ < a.maxZ - EPSILON;
    }
}
