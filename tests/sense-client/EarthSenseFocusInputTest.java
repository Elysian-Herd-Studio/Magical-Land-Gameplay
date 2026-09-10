package top.csituka.magicaland.gameplay.client.sense;

import net.minecraft.client.input.Input;

public final class EarthSenseFocusInputTest {
    private static int checks;
    public static void main(String[] args) {
        check(!EarthSenseFocusInput.apply(null, true, false, .3f), "null input is harmless");
        for (int mask = 0; mask < 64; mask++) for (boolean focusing : new boolean[]{false, true})
            for (boolean slowDown : new boolean[]{false, true}) for (float factor : new float[]{.15f, .3f, .6f}) {
            Input input = input(mask);
            float forward = axis(input.pressingForward, input.pressingBack);
            float side = axis(input.pressingLeft, input.pressingRight);
            input.movementForward = forward * (slowDown ? factor : 1);
            input.movementSideways = side * (slowDown ? factor : 1);
            float oldForward = input.movementForward, oldSide = input.movementSideways;
            boolean jump = (mask & 16) != 0;
            check(EarthSenseFocusInput.requestedJump(input) == jump, "only jumping requests exit");
            check(EarthSenseFocusInput.apply(input, focusing, slowDown, factor) == (focusing && jump), "horizontal movement does not end focus");
            check(input.pressingForward == ((mask & 1) != 0) && input.pressingBack == ((mask & 2) != 0)
                    && input.pressingLeft == ((mask & 4) != 0) && input.pressingRight == ((mask & 8) != 0)
                    && input.jumping == ((mask & 16) != 0), "direction keys and exit jump survive the same tick");
            check(input.sneaking == (focusing && !jump || (mask & 32) != 0), "focus crouches; normal input stays unchanged");
            near(focusing && !jump ? forward * factor * .8f : oldForward, input.movementForward, "first and later ticks equal 80 percent of vanilla crouch");
            near(focusing && !jump ? side * factor * .8f : oldSide, input.movementSideways, "diagonal or opposite keys preserve 80 percent ratio");
        }
        for (float axis : new float[]{-1, -.3f, .3f, 1}) for (boolean sideways : new boolean[]{false, true}) {
            var input = new Input();
            if (sideways) input.movementSideways = axis; else input.movementForward = axis;
            check(!EarthSenseFocusInput.apply(input, true, false, .3f), "analog horizontal movement retains focus");
            near(axis * .24f, sideways ? input.movementSideways : input.movementForward, "analog motion uses first-tick crouch speed");
            check(input.sneaking, "analog focus forces crouch");
        }
        Input input = new Input();
        check(!EarthSenseFocusInput.apply(input, true, false, .3f) && input.sneaking, "stationary focus crouches");
        input.sneaking = false;
        check(!EarthSenseFocusInput.apply(input, false, false, .3f) && !input.sneaking, "ordinary tick restores physical sneak state");
        for (int tick = 0; tick < 20; tick++) {
            input.movementForward = tick == 0 ? 1 : .3f;
            EarthSenseFocusInput.apply(input, true, tick != 0, .3f);
            near(.24, input.movementForward, "normal key refresh prevents repeated or missed crouch slowdown");
        }
        input.movementForward = Float.NaN; input.movementSideways = Float.POSITIVE_INFINITY;
        EarthSenseFocusInput.apply(input, true, false, .3f);
        near(0, input.movementForward, "invalid forward motion fails safe"); near(0, input.movementSideways, "invalid sideways motion fails safe");
        System.out.println("PASS EarthSenseFocusInputTest: " + checks + " real Input movement/crouch checks");
    }
    private static float axis(boolean positive, boolean negative) { return positive == negative ? 0 : positive ? 1 : -1; }
    private static void near(double expected, double actual, String message) { check(Double.isFinite(actual) && Math.abs(expected - actual) < .000001, message); }
    private static Input input(int mask) {
        var input = new Input();
        input.pressingForward = (mask & 1) != 0; input.pressingBack = (mask & 2) != 0;
        input.pressingLeft = (mask & 4) != 0; input.pressingRight = (mask & 8) != 0;
        input.jumping = (mask & 16) != 0; input.sneaking = (mask & 32) != 0;
        return input;
    }
    private static void check(boolean result, String message) { checks++; if (!result) throw new AssertionError(message); }
}
