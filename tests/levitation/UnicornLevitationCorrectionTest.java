package top.csituka.magicaland.gameplay.levitation;

import static top.csituka.magicaland.gameplay.levitation.UnicornLevitationBudget.Verdict.*;
import static top.csituka.magicaland.gameplay.levitation.UnicornLevitationMath.*;

public final class UnicornLevitationCorrectionTest {
    private static int checks;

    public static void main(String[] args) {
        classifications(); unchangedCredit(); frequency(); recovery();
        System.out.println("PASS UnicornLevitationCorrectionTest: " + checks + " finite correction and sustained-abuse checks");
    }

    private static UnicornLevitationBudget budget() { return new UnicornLevitationBudget(0, 64, 0, Motion.ZERO); }
    private static void classifications() {
        check(budget().validate(0, .55, 64, 0) == ACCEPT, "normal two-frame allowance remains unchanged");
        check(budget().validate(0, .70, 64, 0) == CORRECT, "small horizontal excess requests correction");
        check(budget().validate(0, 0, 64.6, 0) == CORRECT, "small upward excess requests correction");
        check(budget().validate(0, 0, 63.75, 0) == CORRECT, "small downward excess requests correction");
        check(budget().validate(0, 1, 64, 0) == REJECT, "clearly excessive horizontal step remains rejected");
        check(budget().validate(0, 0, 65, 0) == REJECT, "clearly excessive upward step remains rejected");
        check(budget().validate(0, 0, 63, 0) == REJECT, "clearly excessive downward step remains rejected");
        check(budget().validate(0, Double.NaN, 64, 0) == REJECT, "nonfinite is never downgraded to a correction");
        check(budget().validate(0, Double.MAX_VALUE, 64, -Double.MAX_VALUE) == REJECT, "huge finite coordinates remain rejected");
        var budget = budget();
        for (int packet = 0; packet < 5; packet++) check(budget.validate(0, 0, 64, 0) == ACCEPT, "ordinary packet allowance");
        check(budget.validate(0, 0, 64, 0) == REJECT, "packet flood remains hard rejection");
    }

    private static void unchangedCredit() {
        var budget = budget();
        check(budget.validate(0, .7, 64, 0) == CORRECT, "initial correction");
        budget.reanchor(0, 64, 0);
        check(budget.validate(1, .5, 64, 0) == ACCEPT, "rejected position was not consumed or committed");
        for (int tick = 2; tick < 1000; tick++) {
            check(budget.validate(tick, .7, 64, 0) == CORRECT, "repeated corrections cannot mint new movement allowance");
            budget.reanchor(.5, 64, 0);
            check(budget.validate(tick, .5, 64, 0) == ACCEPT, "confirmed stationary position remains valid");
        }
        check(budget.validate(1000, .555, 64, 0) == CORRECT, "correction preserves the original spent balance");
        check(budget.validate(1001, .55, 64, 0) == ACCEPT, "remaining genuine balance is still usable");
        var expected = budget.expected();
        budget.reanchor(-1, 65, 3);
        check(budget.expected().equals(expected), "position correction does not rewrite trusted inertia");
        try { budget.reanchor(Double.NaN, 0, 0); throw new AssertionError("nonfinite anchor accepted"); }
        catch (IllegalArgumentException expectedFailure) { checks++; }
    }

    private static void frequency() {
        var guard = new UnicornLevitationMovementGuard();
        for (int tick : new int[]{0, 10, 20}) {
            check(guard.observe(CORRECT, tick) == CORRECT, "isolated mild deviation preserves session");
            check(guard.observe(ACCEPT, tick + 1) == ACCEPT, "valid intervening packets stay accepted");
        }
        check(guard.observe(CORRECT, 39) == REJECT, "four corrections inside two seconds are sustained abnormal movement");
        check(guard.observe(CORRECT, 40) == CORRECT, "oldest correction ages out at exact window boundary");
        check(guard.observe(CORRECT, 41) == REJECT, "a valid packet never wipes the correction history");
        guard.reset();
        check(guard.observe(CORRECT, 42) == CORRECT, "explicit new activation clears previous activation history");
        check(guard.observe(REJECT, 42) == REJECT, "severe rejection never gets a grace downgrade");
        for (int tick = 100; tick < 10000; tick += 41)
            check(guard.observe(CORRECT, tick) == CORRECT, "occasional bounded corrections need not end the ability");
    }

    private static void recovery() {
        var budget = budget();
        var guard = new UnicornLevitationMovementGuard();
        var rules = new UnicornLevitationRules();
        rules.input(1, 0, true, 0);
        check(guard.observe(budget.validate(0, .7, 64, 0), 0) == CORRECT, "mild outlier is recoverable");
        check(rules.open() && rules.protectsFall(true, 0), "correction leaves armed fall protection available");
        budget.reanchor(0, 64, 0);
        Motion motion = Motion.ZERO;
        double y = 64, z = 0;
        for (int tick = 1; tick <= 2000; tick++) {
            rules.input(1, tick, true, tick);
            motion = step(motion, 0, 1, 0, Mode.HOVER, y, Double.NaN);
            y += motion.y(); z += motion.z();
            check(guard.observe(budget.validate(tick, 0, y, z), tick) == ACCEPT, "normal movement resumes after correction");
            budget.advance(tick, 0, 1, 0, Mode.HOVER, y, Double.NaN);
            check(rules.open() && rules.protectsFall(true, tick), "recovery needs no new V activation");
        }
    }

    private static void check(boolean condition, String label) {
        if (!condition) throw new AssertionError(label);
        checks++;
    }
}
