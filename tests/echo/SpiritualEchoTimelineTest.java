package top.csituka.magicaland.gameplay.client.echo;

public final class SpiritualEchoTimelineTest {
    private static int checks;
    public static void main(String[] args) {
        var clock = new SpiritualEchoTimeline();
        check(clock.update(100, 0, 0, 0, 0) == null, "clear vision has no echo");
        check(clock.update(100, .6f, 0, 0, 0) == null, "partial vignette alone does not start echo");
        var first = clock.update(100, 1, 1, 2, 3);
        check(first.phase() == 0 && first.visible(), "full occlusion begins the first pulse");
        var moving = clock.update(100.5, 1, 50, 60, 70);
        check(moving.phase() == .5f && moving.x() == 1 && moving.y() == 2 && moving.z() == 3,
                "moving does not drag an emitted wave along");
        check(clock.update(101.01, 1, 0, 0, 0).visible(), "afterglow remains beyond the former brief flash");
        check(clock.update(102.99, 1, 0, 0, 0).visible(), "old wave remains until the next cycle");
        var next = clock.update(103, 1, 4, 5, 6);
        check(next.phase() == 0 && next.x() == 4, "three-second pulse recenters on current orb");
        var previous = clock.previous();
        check(previous != null && previous.phase() == 3 && previous.x() == 1,
                "previous wave keeps its original emission time and origin");
        var paused = clock.update(103, 1, 7, 8, 9);
        check(paused.equals(next) && clock.previous().equals(previous), "paused game clock does not advance either pulse");
        var partial = clock.update(103.2, .3f, 7, 8, 9);
        check(partial != null && partial.x() == 4 && clock.previous().x() == 1, "recovery can mask both residual pulses smoothly");
        check(clock.update(103.3, 0, 1, 2, 3) == null && clock.previous() == null, "clear vision removes both echoes");
        check(clock.update(104, .5f, 8, 8, 8) == null && clock.previous() == null, "partial re-occlusion cannot revive old waves");
        check(clock.update(104, 1, 8, 8, 8).phase() == 0, "new blindness starts a new wave");
        clock.update(107, 1, 6, 6, 6);
        var rewind = clock.update(1, 1, 9, 9, 9);
        check(rewind.phase() == 0 && rewind.x() == 9 && clock.previous() == null, "clock replacement cannot resurrect either old wave");
        check(clock.update(Double.NaN, 1, 0, 0, 0) == null, "nonfinite clock clears state");
        check(clock.update(1, Float.NaN, 0, 0, 0) == null, "nonfinite coverage clears state");
        check(clock.update(1, 1, Double.POSITIVE_INFINITY, 0, 0) == null, "nonfinite origin clears state");
        clock.update(1, 1, 0, 0, 0); clock.update(4, 1, 1, 1, 1); clock.reset();
        check(clock.previous() == null && clock.update(5, .5f, 0, 0, 0) == null, "session reset removes both residual pulses");
        overlap(); delayedFrames();
        check(SpiritualEchoTimeline.PERIOD == 3 && SpiritualEchoTimeline.EXPANSION == .7
                && SpiritualEchoTimeline.RADIUS == 8 && SpiritualEchoTimeline.AFTERGLOW == SpiritualEchoTimeline.PERIOD,
                "cadence and radius remain fixed while afterglow lasts one whole period");
        for (double distance : new double[] {0, .25, 1, 4, 7.9, 8}) {
            double arrival = distance / SpiritualEchoTimeline.RADIUS * SpiritualEchoTimeline.EXPANSION;
            check(SpiritualEchoTimeline.surface(distance, arrival - .01) == 0, "surface stays hidden before wave");
            check(SpiritualEchoTimeline.surface(distance, arrival) == 0, "arrival begins at zero brightness");
            check(Math.abs(SpiritualEchoTimeline.surface(distance, arrival + SpiritualEchoTimeline.ATTACK / 2) - .5) < 1e-6,
                    "smoothstep attack reaches half brightness at its midpoint");
            check(SpiritualEchoTimeline.surface(distance, arrival + SpiritualEchoTimeline.ATTACK) > .999999,
                    "attack reaches full brightness before slow decay");
            check(SpiritualEchoTimeline.surface(distance, arrival + .3) > .95, "contour stays bright after the former 0.3-second lifetime");
            check(Math.abs(SpiritualEchoTimeline.surface(distance, arrival
                    + (SpiritualEchoTimeline.ATTACK + SpiritualEchoTimeline.AFTERGLOW) / 2) - .5) < 1e-6,
                    "smoothstep decay reaches half brightness at its midpoint");
            float brightness = 1;
            for (int sample = 1; sample <= 1000; sample++) {
                double age = SpiritualEchoTimeline.ATTACK
                        + (SpiritualEchoTimeline.AFTERGLOW - SpiritualEchoTimeline.ATTACK) * sample / 1000;
                float alpha = SpiritualEchoTimeline.surface(distance, arrival + age);
                check(alpha >= 0 && alpha <= brightness + 1e-7f, "afterglow fades monotonically without another flash");
                brightness = alpha;
            }
            check(SpiritualEchoTimeline.surface(distance, arrival + 2.99) > 0, "afterglow remains until close to the next sweep");
            check(SpiritualEchoTimeline.surface(distance, arrival + 3.001) == 0, "afterglow fully disappears after one period");
        }
        for (double distance : new double[] {-1, 8.001, 100, Double.NaN, Double.POSITIVE_INFINITY})
            check(SpiritualEchoTimeline.surface(distance, .3) == 0, "outside radius never revealed");
        for (double phase : new double[]{Double.NaN, Double.POSITIVE_INFINITY, Double.NEGATIVE_INFINITY})
            check(SpiritualEchoTimeline.surface(1, phase) == 0, "nonfinite phase never reveals a contour");
        System.out.println("PASS SpiritualEchoTimelineTest: " + checks);
    }
    private static void overlap() {
        var clock = new SpiritualEchoTimeline();
        clock.update(0, 1, 1, 2, 3);
        var next = clock.update(3, 1, 10, 20, 30);
        check(next.x() == 10 && clock.previous().x() == 1, "two overlapping waves retain different frozen origins");
        check(SpiritualEchoTimeline.surface(8, clock.previous().phase()) > 0, "far afterglow is not cut off by a new cycle");
        clock.update(3.5, 1, 100, 200, 300);
        check(clock.previous() != null && clock.previous().phase() == 3.5f
                && clock.previous().x() == 1, "previous phase measures actual elapsed time beyond PERIOD");
        check(SpiritualEchoTimeline.surface(8, clock.previous().phase()) > 0, "outer wave fades while next wave expands");
        check(clock.update(3.69, 1, 100, 200, 300).x() == 10 && clock.previous() != null, "both origins stay frozen across the overlap");
        clock.update(3.7, 1, 100, 200, 300);
        check(clock.previous() == null, "previous pulse expires at expansion plus afterglow");
        next = clock.update(6, 1, 40, 50, 60);
        check(next.x() == 40 && clock.previous().x() == 10, "only the most recent emitted predecessor survives another cycle");
        for (int cycle = 3; cycle < 100; cycle++) {
            next = clock.update(cycle * 3, 1, cycle, 0, 0);
            check(next.phase() == 0 && next.x() == cycle, "new cycle emits exactly one current pulse");
            check(clock.previous() != null && clock.previous().phase() == 3, "previous cycle is retained at its real age");
        }
        for (double distance : new double[]{.25, 1, 4, 7.9, 8}) {
            double arrival = distance / SpiritualEchoTimeline.RADIUS * SpiritualEchoTimeline.EXPANSION;
            double after = arrival + SpiritualEchoTimeline.ATTACK;
            check(SpiritualEchoTimeline.surface(distance, 3) > 0, "previous afterglow still exists at next emission");
            check(SpiritualEchoTimeline.surface(distance, after) > .999f, "next sweep reaches full brightness as previous fades");
            check(SpiritualEchoTimeline.surface(distance, 3 + after) == 0, "completed predecessor never retains a permanent outline");
        }
    }
    private static void delayedFrames() {
        var clock = new SpiritualEchoTimeline();
        clock.update(0, 1, 1, 1, 1);
        var delayed = clock.update(3.25, 1, 2, 2, 2);
        check(delayed.phase() == 0 && delayed.x() == 2, "late frame emits now rather than inventing an earlier origin");
        check(clock.previous() != null && clock.previous().phase() == 3.25f && clock.previous().x() == 1,
                "late frame retains only the actual still-visible predecessor");
        delayed = clock.update(3.5, 1, 3, 3, 3);
        check(delayed.phase() == .25f && delayed.x() == 2, "phase follows actual delayed emission time");
        delayed = clock.update(9.75, 1, 4, 4, 4);
        check(delayed.phase() == 0 && delayed.x() == 4 && clock.previous() == null, "skipping multiple cycles cannot fabricate a recent previous pulse");
        delayed = clock.update(1000, 1, 5, 5, 5);
        check(delayed.phase() == 0 && clock.previous() == null, "large time jump emits once and drops expired history");
        clock.update(1003, 1, 6, 6, 6);
        check(clock.previous() != null && clock.previous().x() == 5, "normal cycles resume after a jump");
        check(clock.update(999, .5f, 7, 7, 7) == null && clock.previous() == null, "rewinding into partial visibility cannot revive a wave");
    }
    private static void check(boolean condition, String label) {
        checks++; if (!condition) throw new AssertionError(label);
    }
}
