package top.csituka.magicaland.gameplay.levitation;

import static top.csituka.magicaland.gameplay.levitation.UnicornLevitationMath.*;

public final class UnicornLevitationBudgetTest {
    private static int checks;
    public static void main(String[] args) {
        for (int batch : new int[]{1, 2, 3}) {
            var budget = new UnicornLevitationBudget(0, 0, 0, Motion.ZERO); Motion velocity = Motion.ZERO;
            double x = 0, y = 0, z = 0;
            for (int tick = 1; tick <= 600; tick++) {
                budget.advance(tick, 0, 1, 0, Mode.ASCEND, y, Double.NaN);
                velocity = step(velocity, 0, 1, 0, Mode.ASCEND, y, Double.NaN);
                x += velocity.x(); y += velocity.y(); z += velocity.z();
                if (tick % batch == 0) check(budget.accept(tick, x, y, z), "normal physics/lag batch " + batch + " tick " + tick);
            }
        }
        for (double multiplier : new double[]{1.2, 2, 5, 20}) {
            var budget = new UnicornLevitationBudget(0, 0, 0, Motion.ZERO); double y = 0, z = 0; boolean rejected = false;
            for (int tick = 1; tick <= 300; tick++) {
                budget.advance(tick, 0, 1, 0, Mode.ASCEND, y, Double.NaN);
                y += .16 * multiplier; z += .18 * multiplier;
                if (!budget.accept(tick, 0, y, z)) { rejected = true; break; }
            }
            check(rejected, "sustained speed cannot self-amplify " + multiplier);
        }
        var burst = new UnicornLevitationBudget(0, 0, 0, Motion.ZERO);
        for (int tick = 1; tick <= 10000; tick++) burst.advance(tick, 0, 1, 0, Mode.ASCEND, 0, Double.NaN);
        check(!burst.accept(10000, 0, 1, 1), "idle time does not bank unlimited movement");
        var packets = new UnicornLevitationBudget(0, 0, 0, Motion.ZERO); packets.advance(1, 0, 0, 0, Mode.ASCEND, 0, Double.NaN);
        for (int i = 0; i < 5; i++) check(packets.accept(1, 0, 0, 0), "five packets allowed");
        check(!packets.accept(1, 0, 0, 0), "sixth packet rejected even zero movement");
        check(!packets.accept(2, Double.NaN, 0, 0), "nonfinite rejected");
        for (int speed = 1; speed <= 100; speed++) {
            Motion motion = new Motion(.18, -speed, 0); var budget = new UnicornLevitationBudget(0, 1000, 0, motion);
            double x = 0, y = 1000;
            for (int tick = 1; tick <= 20; tick++) {
                budget.advance(tick, -90, 1, 0, Mode.ASCEND, y, Double.NaN);
                motion = step(motion, -90, 1, 0, Mode.ASCEND, y, Double.NaN);
                x += motion.x(); y += motion.y();
                check(budget.accept(tick, x, y, 0), "initial falling momentum retained " + speed);
            }
        }
        System.out.println("PASS UnicornLevitationBudgetTest: " + checks);
    }
    private static void check(boolean value, String label) { checks++; if (!value) throw new AssertionError(label); }
}
