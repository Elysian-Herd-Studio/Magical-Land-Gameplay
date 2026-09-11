package top.csituka.magicaland.gameplay.levitation;

import static top.csituka.magicaland.gameplay.levitation.UnicornLevitationMath.Mode;

public final class UnicornLevitationRulesTest {
    private static int checks;
    public static void main(String[] args) {
        var rules = new UnicornLevitationRules(100);
        check(rules.input(1, 0, true, 0), "first baseline");
        check(!rules.input(1, 0, true, 1), "duplicate input cannot renew");
        check(!rules.ready(false, true, true), "unarmed cannot charge");
        for (int i = 1; i < 7; i++) check(!rules.ready(true, true, i == 1), "ground-origin jump still charges " + i);
        check(rules.ready(true, true, false), "seventh ground-origin tick lifts");
        boolean latch = UnicornLevitationRules.groundedPress(false, true, true, false);
        latch = UnicornLevitationRules.groundedPress(true, true, false, latch);
        latch = UnicornLevitationRules.groundedPress(true, true, false, latch);
        check(latch, "same-tick yaw/Shift packets preserve grounded Space edge");
        var queued = new UnicornLevitationRules(); queued.input(1, 0, true, 0);
        check(!queued.ready(true, true, latch), "queued packets do not bypass ground charge");
        for (int tick = 2; tick < 7; tick++) check(!queued.ready(true, true, false), "held ground origin remains charging");
        check(queued.ready(true, true, false), "queued held press lifts only seventh tick");
        check(!UnicornLevitationRules.groundedPress(true, false, false, latch), "release clears pending ground edge");
        check(!rules.ready(true, false, false), "release stops");
        check(rules.ready(true, true, false), "new airborne press immediate");
        check(!rules.ready(false, true, false), "disarm stops");
        check(rules.input(2, 0, true, 5), "new token resets sequence");
        check(!rules.ready(true, true, true), "new token resets charge");
        check(!rules.expired(25) && rules.expired(26), "20 tick lease");
        rules.close(); check(!rules.input(2, 4, true, 27), "closed token cannot resurrect");
        check(rules.input(3, 0, true, 28), "fresh token restart");
        check(rules.input(3, 1, false, 29) && !rules.open(), "disable closes");
        check(!rules.input(2, 100, false, 30), "old stop ignored");
        check(!rules.input(3, 2, true, 31), "late heartbeat cannot undo stop");
        check(rules.input(4, 0, true, 32) && rules.expired(31), "clock rewind fails closed");
        for (double legacy : new double[]{0, 10, 100, -1, Double.NaN, Double.POSITIVE_INFINITY}) {
            var unlimited = new UnicornLevitationRules(legacy);
            for (int tick = 0; tick < 10000; tick++) {
                unlimited.input(1, tick, true, tick);
                check(unlimited.open() && unlimited.mana() == 100, "legacy mana does not limit ability");
            }
        }
        System.out.println("PASS UnicornLevitationRulesTest: " + checks);
    }
    private static void check(boolean value, String label) { checks++; if (!value) throw new AssertionError(label); }
}
