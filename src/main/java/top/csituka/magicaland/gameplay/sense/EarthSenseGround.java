package top.csituka.magicaland.gameplay.sense;

import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.PriorityQueue;
import java.util.Set;

/** 有界实心邻接近似；同层优先，预算用尽时只承认已经证实的连接。 */
public final class EarthSenseGround {
    public record Cell(int x, int y, int z) {}
    public record Shape(double minX, double minY, double minZ, double maxX, double maxY, double maxZ) {
        public Shape {
            if (!Double.isFinite(minX) || !Double.isFinite(minY) || !Double.isFinite(minZ)
                    || !Double.isFinite(maxX) || !Double.isFinite(maxY) || !Double.isFinite(maxZ)
                    || minX >= maxX || minY >= maxY || minZ >= maxZ)
                throw new IllegalArgumentException("Invalid conductor shape");
        }
    }
    @FunctionalInterface public interface Conductor {
        boolean test(int x, int y, int z);
        default boolean connects(Cell from, Cell to) { return true; }
    }
    public record Reach(Set<Cell> cells, int probes, boolean limited) {
        public Reach { cells = Set.copyOf(cells); }
        public boolean contains(Cell cell) { return cells.contains(cell); }
    }
    private EarthSenseGround() {}

    public static boolean touches(Shape a, Cell from, Shape b, Cell to) {
        double x = Math.min(a.maxX+from.x, b.maxX+to.x)-Math.max(a.minX+from.x, b.minX+to.x);
        double y = Math.min(a.maxY+from.y, b.maxY+to.y)-Math.max(a.minY+from.y, b.minY+to.y);
        double z = Math.min(a.maxZ+from.z, b.maxZ+to.z)-Math.max(a.minZ+from.z, b.minZ+to.z);
        if (from.x != to.x) return x >= -.00001 && y > .00001 && z > .00001;
        if (from.y != to.y) return y >= -.00001 && x > .00001 && z > .00001;
        return z >= -.00001 && x > .00001 && y > .00001;
    }

    public static Reach search(Cell origin, Conductor conductor, int nodeBudget, int probeBudget) {
        if (nodeBudget < 1 || probeBudget < 1) return new Reach(Set.of(), 0, true);
        var known = new HashMap<Cell, Boolean>();
        var reached = new HashSet<Cell>();
        var pending = new PriorityQueue<Cell>(Comparator.<Cell>comparingInt(cell ->
                Math.abs(cell.y - origin.y) * 1024 + (cell.x-origin.x)*(cell.x-origin.x) + (cell.z-origin.z)*(cell.z-origin.z))
                .thenComparingInt(Cell::x).thenComparingInt(Cell::z).thenComparingInt(Cell::y));
        boolean initial = conductor.test(origin.x, origin.y, origin.z);
        known.put(origin, initial);
        int probes = 1;
        if (initial) { pending.add(origin); reached.add(origin); }
        boolean limited = false;
        search: while (!pending.isEmpty()) {
            Cell cell = pending.remove();
            for (int[] direction : DIRECTIONS) {
                Cell next = new Cell(cell.x + direction[0], cell.y + direction[1], cell.z + direction[2]);
                int dx = next.x-origin.x, dz = next.z-origin.z;
                if (Math.abs(next.y-origin.y) > 2 || dx*dx + dz*dz > EarthSenseRules.RANGE*EarthSenseRules.RANGE
                        || reached.contains(next)) continue;
                Boolean conductive = known.get(next);
                if (conductive == null) {
                    if (probes >= probeBudget) { limited = true; break search; }
                    probes++;
                    conductive = conductor.test(next.x, next.y, next.z);
                    known.put(next, conductive);
                }
                if (!conductive || !conductor.connects(cell, next)) continue;
                if (reached.size() >= nodeBudget) { limited = true; break search; }
                reached.add(next); pending.add(next);
            }
        }
        return new Reach(reached, probes, limited);
    }
    private static final int[][] DIRECTIONS = {{1,0,0},{-1,0,0},{0,0,1},{0,0,-1},{0,1,0},{0,-1,0}};
}
