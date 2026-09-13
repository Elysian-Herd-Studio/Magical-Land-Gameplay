package top.csituka.magicaland.gameplay.remote;

final class TelekinesisOrbit {
    static final int PERIOD_TICKS = 400;
    private static final double[] RADII = {2, 1.6, 1.2, .9};
    private TelekinesisOrbit() {}

    static int freeSlot(int occupied, int slots) {
        for (int slot = 0; slot < slots; slot++) if ((occupied & (1 << slot)) == 0) return slot;
        return -1;
    }
    static double phase(long tick, int slot, int slots) {
        return Math.PI * 2 * (Math.floorMod(tick, PERIOD_TICKS) / (double) PERIOD_TICKS + slot / (double) slots);
    }
    static double height(double feet, double eyes) { return Math.max(feet + 1.3, eyes - .15); }
    static RemoteReturnNavigator.Point position(long tick, int slot, int slots, RemoteReturnNavigator.Point center, double radius) {
        double angle = phase(tick, slot, slots);
        return new RemoteReturnNavigator.Point(center.x() + Math.cos(angle) * radius,
                center.y() + Math.sin(angle * 2) * .08, center.z() + Math.sin(angle) * radius);
    }
    static RemoteReturnNavigator.Point goal(long tick, int slot, int slots, RemoteReturnNavigator.Point center,
                                           RemoteReturnNavigator.Point current, RemoteReturnNavigator.Collision collision) {
        // 环绕只试有限条直达轨道；受阻时由调用方改为寻路追赶本体。
        for (double radius : RADII) {
            var wanted = position(tick, slot, slots, center, radius);
            if (collision.clear(center, wanted) && collision.clear(current, wanted)) return wanted;
        }
        return null;
    }

    static RemoteReturnNavigator.Point followGoal(RemoteReturnNavigator.Point preferred, RemoteReturnNavigator.Collision collision) {
        for (double lower : new double[]{0, .35, .7}) {
            var point = new RemoteReturnNavigator.Point(preferred.x(), preferred.y() - lower, preferred.z());
            if (collision.clear(point, point)) return point;
        }
        return null;
    }
}
