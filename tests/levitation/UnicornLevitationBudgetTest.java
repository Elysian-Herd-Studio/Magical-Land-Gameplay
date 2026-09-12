package top.csituka.magicaland.gameplay.levitation;

import static top.csituka.magicaland.gameplay.levitation.UnicornLevitationMath.*;

public final class UnicornLevitationBudgetTest {
    private static int checks;
    public static void main(String[] args) {
        for (int batch : new int[]{1, 2, 3, 6}) {
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
        check(!burst.accept(10000, 0, 2.5, 2.5), "idle time does not bank unlimited movement");
        var packets = new UnicornLevitationBudget(0, 0, 0, Motion.ZERO); packets.advance(1, 0, 0, 0, Mode.ASCEND, 0, Double.NaN);
        for (int i = 0; i < UnicornLevitationRules.MOVE_PACKET_BURST; i++) check(packets.accept(1, 0, 0, 0), "bounded stationary/rotation backlog allowed");
        check(!packets.accept(1, 0, 0, 0), "burst credit is finite even without displacement");
        for (int i = 0; i < UnicornLevitationRules.MAX_MOVE_PACKETS; i++) check(packets.accept(2, 0, 0, 0), "packet credit refills at bounded rate");
        check(!packets.accept(2, 0, 0, 0), "one tick cannot refill the whole burst");
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
        shiftReleasePhaseSkew(); finiteTransitionMargin(); queuedMovement(); sustainedPacketFlood(); teleports();
        System.out.println("PASS UnicornLevitationBudgetTest: " + checks);
    }

    private static void queuedMovement() {
        for (Mode mode : new Mode[]{Mode.HOVER, Mode.ASCEND}) for (int batch : new int[]{6, 8}) {
            var budget = new UnicornLevitationBudget(0, 64, 0, Motion.ZERO);
            Motion motion = Motion.ZERO;
            double y = 64, z = 0;
            double[][] queue = new double[batch][2];
            for (int tick = 1; tick <= 1200; tick++) {
                budget.advance(tick, 0, 1, 0, mode, y, Double.NaN);
                motion = step(motion, 0, 1, 0, mode, y, Double.NaN);
                y += motion.y(); z += motion.z();
                queue[(tick - 1) % batch] = new double[]{y, z};
                if (tick % batch == 0) {
                    for (double[] position : queue) check(budget.accept(tick, 0, position[0], position[1]),
                            "queued movement shares accumulated credit: " + mode + " batch " + batch + " tick " + tick);
                    for (int rotation = 0; rotation < 3; rotation++) check(budget.accept(tick, 0, y, z), "rotation after batch is harmless");
                }
            }
            check(budget.validate(1201, 100, y, z) == UnicornLevitationBudget.Verdict.REJECT,
                    "packet tolerance does not authorize client teleport");
        }
    }

    private static void sustainedPacketFlood() {
        var budget = new UnicornLevitationBudget(0, 0, 0, Motion.ZERO);
        boolean rejected = false;
        for (int tick = 0; tick < 100 && !rejected; tick++)
            for (int packet = 0; packet < UnicornLevitationRules.MAX_MOVE_PACKETS + 1; packet++)
                if (!budget.accept(tick, 0, 0, 0)) { rejected = true; break; }
        check(rejected, "sustained packet rate above replenishment still rejected");
        budget = new UnicornLevitationBudget(0, 0, 0, Motion.ZERO);
        for (int packet = 0; packet < UnicornLevitationRules.MOVE_PACKET_BURST; packet++)
            check(budget.accept(20, 0, 0, 0), "consume initial burst");
        check(!budget.accept(19, 0, 0, 0), "clock rollback cannot mint packet credit");
    }

    private static void teleports() {
        for (Mode mode : new Mode[]{Mode.HOVER, Mode.ASCEND}) {
            var budget = new UnicornLevitationBudget(0, 64, 0, new Motion(.216, -10, 0));
            budget.advance(0, 0, 1, 0, mode, 64, Double.NaN);
            budget.teleport(1000, 128, -500, false, false, false);
            check(budget.expected().equals(Motion.ZERO), "authorized teleport clears old falling prediction");
            Motion motion = Motion.ZERO;
            double x = 1000, y = 128, z = -500;
            for (int tick = 1; tick <= 1000; tick++) {
                motion = step(motion, 0, 1, 0, mode, y, Double.NaN);
                double beforeY = y; y += motion.y(); z += motion.z();
                check(budget.accept(tick, x, y, z), "flight continues from authorized destination " + mode);
                budget.advance(tick, 0, 1, 0, mode, beforeY, Double.NaN);
            }
            check(budget.validate(1001, 0, 64, 0) == UnicornLevitationBudget.Verdict.REJECT,
                    "old pre-teleport coordinates are no longer accepted");
        }
        var spent = new UnicornLevitationBudget(0, 64, 0, Motion.ZERO);
        check(spent.accept(0, .5, 64, 0), "spend nearly all initial horizontal credit");
        for (int tick = 1; tick <= 100; tick++) {
            spent.teleport(tick * 10, 64, 0, false, false, false);
            check(spent.validate(tick, tick * 10 + .5, 64, 0) == UnicornLevitationBudget.Verdict.REJECT,
                    "repeated server teleports do not refill spent displacement credit");
        }
        var packets = new UnicornLevitationBudget(0, 64, 0, Motion.ZERO);
        for (int i = 0; i < UnicornLevitationRules.MOVE_PACKET_BURST; i++) check(packets.accept(0, 0, 64, 0), "consume packet credit before teleport");
        packets.teleport(10, 64, 0, false, false, false);
        check(!packets.accept(0, 10, 64, 0), "teleport does not refill packet-rate credit");
    }

    private static void shiftReleasePhaseSkew() {
        for (int phase = -2; phase <= 2; phase++) for (int batch = 1; batch <= 3; batch++)
            for (int offset = 0; offset < batch; offset++) for (float yaw : new float[]{0, 45, -90, 170}) {
                var budget = new UnicornLevitationBudget(0, 64, 0, Motion.ZERO);
                Motion motion = Motion.ZERO;
                double x = 0, y = 64, z = 0;
                for (int tick = 0; tick < 1800; tick++) {
                    Mode mode = transitionMode(tick);
                    double beforeY = y;
                    motion = step(motion, yaw, transitionForward(tick), 0, mode, y, Double.NaN);
                    x += motion.x(); y += motion.y(); z += motion.z();
                    if ((tick + offset) % batch == 0) check(budget.accept(tick, x, y, z),
                            "Shift/W release inertia phase " + phase + " batch " + batch + " offset " + offset + " tick " + tick);
                    int inputTick = Math.max(0, tick - phase);
                    budget.advance(tick, yaw, transitionForward(inputTick), 0, transitionMode(inputTick), beforeY, Double.NaN);
                }
            }
    }

    private static Mode transitionMode(int tick) {
        int phase = tick % 180;
        return phase < 90 ? Mode.HOVER : phase < 120 ? Mode.ASCEND : Mode.HOVER;
    }
    private static float transitionForward(int tick) {
        int phase = tick % 180;
        return phase < 60 || phase >= 90 ? 1 : 0;
    }
    private static void finiteTransitionMargin() {
        var budget = new UnicornLevitationBudget(0, 64, 0, Motion.ZERO);
        check(budget.accept(0, .3, 64, 0), "one bounded asynchronous input offset is allowed");
        for (int tick = 1; tick <= 10000; tick++) {
            budget.advance(tick, 0, 0, 0, tick % 2 == 0 ? Mode.HOVER : Mode.ASCEND, 64, Double.NaN);
            check(budget.accept(tick, .3, 64, 0), "stationary packets remain valid");
        }
        check(!budget.accept(10001, .6, 64, 0), "mode switches, waiting and zero packets cannot refill consumed horizontal margin");
        for (double multiplier : new double[]{1.2, 2, 5}) {
            budget = new UnicornLevitationBudget(0, 64, 0, Motion.ZERO);
            Motion motion = Motion.ZERO;
            double y = 64, z = 0;
            boolean rejected = false;
            for (int tick = 0; tick < 1800; tick++) {
                Mode mode = transitionMode(tick);
                motion = step(motion, 0, transitionForward(tick), 0, mode, y, Double.NaN);
                y += motion.y(); z += motion.z() * multiplier;
                if (!budget.accept(tick, 0, y, z)) { rejected = true; break; }
                budget.advance(tick, 0, transitionForward(tick), 0, mode, y - motion.y(), Double.NaN);
            }
            check(rejected, "repeated legitimate mode switches cannot launder sustained excess speed " + multiplier);
        }
    }
    private static void check(boolean value, String label) { checks++; if (!value) throw new AssertionError(label); }
}
