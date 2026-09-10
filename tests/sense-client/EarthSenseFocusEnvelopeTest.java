package top.csituka.magicaland.gameplay.client.sense;

import java.util.Random;
import top.csituka.magicaland.gameplay.sense.EarthSenseViewerMotion;

public final class EarthSenseFocusEnvelopeTest {
    private static int checks;
    public static void main(String[] args) {
        for (int fps : new int[]{30,60,144}) {
            var envelope = new EarthSenseFocusEnvelope(); envelope.observe(0, 1);
            float previous = 0;
            for (int frame = 0; frame <= fps; frame++) {
                double tick = frame * 20d / fps, t = Math.min(1, tick / 9);
                float value = envelope.sample(tick);
                near(1 - Math.pow(1 - t, 3), value, "nine tick ease-out cubic entry");
                near(value, envelope.sample(tick), "same render time does not accumulate");
                check(value >= previous && value <= 1, "entry monotonic"); previous = value;
            }
            near(1, envelope.sample(9), "entry reaches target at nine ticks");
            check(envelope.sample(3) > .7f, "focus established quickly before slow final approach");
            envelope.observe(20, 0);
            near(1, envelope.sample(20), "exit starts from visible amount");
            previous = 1;
            for (int frame = 0; frame <= fps; frame++) {
                double tick = 20 + frame * 20d / fps, t = Math.min(1, (tick - 20) / 5);
                float value = envelope.sample(tick);
                near(1 - t * t * (3 - 2 * t), value, "five tick smoothstep exit");
                check(value <= previous && value >= 0, "exit monotonic"); previous = value;
            }
            near(0, envelope.sample(25), "exit finishes at five ticks");
        }
        var random = new Random(9011);
        var envelope = new EarthSenseFocusEnvelope();
        double ticks = 0;
        for (int step = 0; step < 5000; step++) {
            ticks += random.nextDouble() * 3;
            float visible = envelope.sample(ticks), target = step % 3 == 0 ? .3f : step % 3 == 1 ? 1 : 0;
            envelope.observe(ticks, target);
            near(visible, envelope.sample(ticks), "reverse/ground confirmation continues exactly from visible value");
            for (int frame = 0; frame < 8; frame++) {
                float value = envelope.sample(ticks + frame / 8d);
                check(value >= 0 && value <= 1, "rapid reversals remain bounded");
            }
        }
        envelope.reset(); near(0, envelope.sample(ticks), "world reset clears lingering opacity");
        envelope.observe(100, 1); envelope.observe(1, 1);
        near(0, envelope.sample(1), "clock rollback starts at zero");
        envelope.observe(Double.NaN, 1); near(0, envelope.sample(3), "invalid observation resets");
        near(0, envelope.sample(Double.NaN), "invalid render time is invisible");
        envelope.reset(); envelope.observe(0, 1);
        var motion = new EarthSenseViewerMotion(0, 0, 0, .05);
        check(motion.quality() > .4 && motion.quality() < .5, "already moving start does not briefly show full-quality signals");
        motion = new EarthSenseViewerMotion(0, 0, 10);
        motion.observe(.2, 0, 11);
        check(motion.quality() < 1 && motion.quality() > EarthSenseViewerMotion.MIN_QUALITY, "push dims signals over two ticks");
        motion.observe(.4, 0, 12);
        near(EarthSenseViewerMotion.MIN_QUALITY, motion.quality(), "fast push reaches minimum signal clarity");
        near(1, envelope.sample(12), "movement quality cannot lower full-focus envelope");
        near(EarthSenseViewerMotion.MIN_QUALITY, motion.observe(.4, 0, 12), "paused/repeated client sample cannot recover quality early");
        for (int tick = 13; tick <= 22; tick++) {
            motion.observe(.4, 0, tick);
            near(1, envelope.sample(tick), "recovering signals leave FOV/filter/audio envelope unchanged");
            if (tick < 22) check(motion.quality() < 1, "stationary clarity takes ten ticks to recover");
        }
        near(1, motion.quality(), "stationary signal clarity fully returns");
        System.out.println("PASS EarthSenseFocusEnvelopeTest: " + checks);
    }
    private static void near(double expected, double actual, String message) { check(Double.isFinite(actual) && Math.abs(expected - actual) < .000001, message); }
    private static void check(boolean okay, String message) { checks++; if (!okay) throw new AssertionError(message); }
}
