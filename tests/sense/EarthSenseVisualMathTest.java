package top.csituka.magicaland.gameplay.client.sense;

import java.util.Random;
import java.util.List;
import java.util.ArrayList;
import org.joml.Matrix4f;
import org.joml.Vector4f;

public final class EarthSenseVisualMathTest {
    private static int checks;
    public static void main(String[] args) {
        var random = new Random(90310);
        for (int i = 0; i < 12000; i++) {
            float r = random.nextFloat(), g = random.nextFloat(), b = random.nextFloat(), a = random.nextFloat();
            for (float amount : new float[] {0, .1f, .4f, .8f, 1}) {
                float[] result = EarthSenseVisualMath.desaturate(r, g, b, a, amount);
                near(EarthSenseVisualMath.luminance(r, g, b),
                        EarthSenseVisualMath.luminance(result[0], result[1], result[2]), "luminance retained");
                near(a, result[3], "alpha retained");
                for (int c = 0; c < 3; c++) check(result[c] >= 0 && result[c] <= 1, "bounded channel");
                if (amount == 0) { near(r, result[0], "zero r"); near(g, result[1], "zero g"); near(b, result[2], "zero b"); }
                if (amount == 1) { near(result[0], result[1], "gray rg"); near(result[1], result[2], "gray gb"); }
            }
        }
        near(0, EarthSenseVisualMath.clamp(Float.NaN), "NaN disabled");
        near(0, EarthSenseVisualMath.clamp(Float.POSITIVE_INFINITY), "infinite disabled");
        near(0, EarthSenseVisualMath.pulse(-1), "negative pulse age is silent");
        near(0, EarthSenseVisualMath.pulse(Double.NaN), "invalid pulse age is silent");
        for (int fps : new int[] {30, 60, 144}) for (int i = 0; i <= fps * 2; i++) {
            double ticks = i * 20.0 / fps;
            near(Math.max(0, ticks >= 18 ? 0 : Math.pow(1 - ticks / 18, 2)), EarthSenseVisualMath.pulse(ticks), "frame independent");
        }
        projectionAndClouds();
        edgeClouds();
        System.out.println("PASS earth sense visual math: " + checks);
    }
    private static void edgeClouds() {
        for (float aspect : new float[] {.75f, 1, 16f / 9, 32f / 9}) {
            var projection = new Matrix4f().perspective((float) Math.toRadians(70), aspect, .05f, 100);
            var identity = new Matrix4f();
            var center = EarthSenseVisualMath.place(projection, identity, 0, 0, -5, 1, 2);
            near(.5, center.x(), "centered cloud retains true projection x");
            near(.5, center.y(), "centered cloud retains true projection y");
            near(.66 * projection.m00() / 10 * 1.35, center.radiusX(), "cloud width increases 35 percent");
            near(1.3 * projection.m11() / 10, center.radiusY(), "cloud height unchanged");
            for (float y : new float[] {-2, 0, 2}) {
                EarthSenseVisualMath.Projected previous = null;
                var placements = new EarthSenseVisualMath.Placements();
                for (int i = -18000; i <= 18001; i++) {
                    double angle = Math.toRadians(i / 100.0), x = Math.sin(angle) * 5, z = -Math.cos(angle) * 5;
                    var desired = EarthSenseVisualMath.place(projection, identity, x, y, z, 1, 2);
                    placements.begin(1, true, (i + 18000) / 300.0, List.of(new EarthSenseVisualMath.Cloud(target(1, x, .9f, 4), 1, 0, 0)));
                    var current = placements.apply(1, desired);
                    check(current != null && current.onScreen(), "each 360-degree target yields exactly one drawable cloud");
                    check(current.x() >= .02499f && current.x() <= .97501f
                            && current.y() >= .02499f && current.y() <= .97501f, "edge center is bounded");
                    check(Float.isFinite(current.radiusX()) && current.radiusX() > 0 && current.radiusX() <= .48f
                            && Float.isFinite(current.radiusY()) && current.radiusY() > 0 && current.radiusY() <= .65f, "near-plane cloud size bounded");
                    if (z >= 0) near(.475, Math.max(Math.abs(desired.x() - .5), Math.abs(desired.y() - .5)), "rear destination stays on edge");
                    if (previous != null) {
                        check(Math.hypot(current.x() - previous.x(), current.y() - previous.y()) < .002,
                                "no front-side-rear edge jump at " + i + ", aspect=" + aspect);
                        check(Math.abs(current.radiusX() - previous.radiusX()) < .002
                                && Math.abs(current.radiusY() - previous.radiusY()) < .002, "cloud size continuous across camera plane");
                    }
                    previous = current;
                }
            }
            var rear = EarthSenseVisualMath.place(projection, identity, 0, 0, 5, 1, 2);
            near(.5, rear.x(), "rear centered on lower edge"); near(.025, rear.y(), "rear uses GL bottom not HUD top");
            var sideA = EarthSenseVisualMath.place(projection, identity, 5, 0, -.000001, 1, 2);
            var sideB = EarthSenseVisualMath.place(projection, identity, 5, 0, .000001, 1, 2);
            near(sideA.x(), sideB.x(), "W crossing has no horizontal mirror");
            near(sideA.y(), sideB.y(), "W crossing continuous vertical");
            for (float y : new float[] {-5, 5}) for (float x : new float[] {-.000001f, 0, .000001f}) {
                var vertical = EarthSenseVisualMath.place(projection, identity, x, y, 0, 1, 2);
                check(vertical != null && Float.isFinite(vertical.x()) && Float.isFinite(vertical.y()), "near-vertical direction has finite bounded fallback");
                var before = EarthSenseVisualMath.place(projection, identity, x, y, -.001, 1, 2);
                var after = EarthSenseVisualMath.place(projection, identity, x, y, .001, 1, 2);
                check(Math.hypot(before.x() - after.x(), before.y() - after.y()) < .00001, "tiny vertical pitch crossing cannot jump top-to-bottom");
            }
            for (int yaw = -180; yaw <= 180; yaw += 15) for (int pitch = -85; pitch <= 85; pitch += 17) {
                var view = new Matrix4f().rotateX((float) Math.toRadians(pitch)).rotateY((float) Math.toRadians(yaw));
                var local = view.transform(new Vector4f(4, 1, -3, 1));
                var actual = EarthSenseVisualMath.place(new Matrix4f(projection).mul(view), view, 4, 1, -3, 1, 2);
                var expected = EarthSenseVisualMath.place(projection, identity, local.x, local.y, local.z, 1, 2);
                near(expected.x(), actual.x(), "front/rear camera coordinates equivalent x");
                near(expected.y(), actual.y(), "front/rear camera coordinates equivalent y");
            }
        }
        check(EarthSenseVisualMath.place(new Matrix4f().zero(), new Matrix4f(), 0, 0, -5, 1, 2) == null, "invalid clip has no blob");
        check(EarthSenseVisualMath.place(new Matrix4f(), new Matrix4f(), Double.NaN, 0, -5, 1, 2) == null, "invalid target has no blob");
        for (float activity : new float[] {0, .12f, .65f, 1}) {
            float previous = 1;
            for (int ring = 0; ring <= 100; ring++) {
                float alpha = EarthSenseVisualMath.softness(ring / 100f, activity, 0);
                check(alpha >= 0 && alpha <= previous, "fallback contour fades continuously outward"); previous = alpha;
            }
            near(0, previous, "fallback outer contour transparent, not a solid circle");
        }
        for (int fps : new int[] {30, 60, 144}) {
            var positions = new EarthSenseVisualMath.Placements();
            var clouds = List.of(new EarthSenseVisualMath.Cloud(target(1, 0, .9f, 4), 1, 0, 0));
            var start = new EarthSenseVisualMath.Projected(.975f, .8f, .05f, .1f, true);
            var end = new EarthSenseVisualMath.Projected(.025f, .8f, .05f, .1f, true);
            positions.begin(1, true, 0, clouds); positions.apply(1, start);
            var previous = start;
            for (int frame = 1; frame <= fps * 3; frame++) {
                positions.begin(1, true, frame * 20.0 / fps, clouds);
                var current = positions.apply(1, end);
                check(Math.hypot(current.x() - previous.x(), current.y() - previous.y()) <= .086 * 20 / fps + .00001,
                        "rare rear seam uses bounded border travel at " + fps + "fps");
                near(.475, Math.max(Math.abs(current.x() - .5), Math.abs(current.y() - .5)), "seam smoothing stays on border");
                previous = current;
            }
            check(Math.hypot(previous.x() - end.x(), previous.y() - end.y()) < .001, "edge smoother catches up");
            positions.begin(1, true, 60, clouds);
            var paused = positions.apply(1, start);
            near(previous.x(), paused.x(), "same tick/multi-pass edge cannot advance");
            near(previous.y(), paused.y(), "same tick edge y stable");
            positions.begin(2, true, 60, clouds);
            near(start.x(), positions.apply(1, start).x(), "new session ID never inherits old edge path");
            positions.begin(2, true, 1, clouds);
            near(end.x(), positions.apply(1, end).x(), "clock rollback resets edge path");
        }
    }
    private static void projectionAndClouds() {
        var projection = new Matrix4f().perspective((float) Math.toRadians(70), 16f / 9, .05f, 100);
        var front = new Matrix4f(projection).rotateY((float) Math.PI);
        var center = EarthSenseVisualMath.project(front, 0, 0, 5, 1, 2);
        near(.5, center.x(), "world +Z centered facing south"); near(.5, center.y(), "center height correct");
        check(center.onScreen() && center.radiusY() > center.radiusX(), "body cloud is taller, not a square");
        check(EarthSenseVisualMath.project(front, 1, 0, 5, 1, 2).x() < .5f, "world +X projects left facing south");
        check(EarthSenseVisualMath.project(front, 0, 0, -5, 1, 2) == null, "behind camera is direction only");
        check(EarthSenseVisualMath.project(front, 50, 0, 5, 1, 2) != null
                && !EarthSenseVisualMath.project(front, 50, 0, 5, 1, 2).onScreen(), "offscreen avoids mirrored cloud");
        check(EarthSenseVisualMath.project(new Matrix4f().zero(), 0, 0, 5, 1, 2) == null, "singular projection safe");
        check(EarthSenseVisualMath.project(front, Double.NaN, 0, 5, 1, 2) == null, "invalid coordinate safe");
        for (int yaw = -180; yaw <= 180; yaw += 15) for (int pitch = -85; pitch <= 85; pitch += 17)
            for (int distance : new int[] {1, 3, 7, 14}) {
                var view = new Matrix4f().rotateX((float) Math.toRadians(pitch)).rotateY((float) Math.toRadians(yaw + 180));
                var point = new Matrix4f(view).invert().transform(new Vector4f(0, 0, -distance, 1));
                var p = EarthSenseVisualMath.project(new Matrix4f(projection).mul(view), point.x, point.y, point.z, 1, 2);
                check(p != null && p.onScreen() && p.radiusX() > 0 && p.radiusY() > 0, "yaw/pitch cloud projection valid");
                near(.5, p.x(), "projection follows actual camera yaw"); near(.5, p.y(), "projection follows actual camera pitch");
            }
        double reference = -1;
        for (int fps : new int[] {30, 60, 144}) {
            var clouds = new EarthSenseVisualMath.Clouds();
            List<EarthSenseVisualMath.Cloud> result = List.of();
            for (int frame = 0; frame <= fps * 2; frame++) {
                double ticks = frame * 20.0 / fps;
                var target = target(1, ticks < 20 ? 0 : 1, .12f, 4);
                result = clouds.update(List.of(target), ticks);
                check(result.size() == 1 && result.get(0).fade() >= 0 && result.get(0).fade() <= 1, "bounded entering fade");
            }
            check(result.get(0).fade() == 1, "fully faded in");
            double x = result.get(0).target().x();
            if (reference < 0) reference = x; else check(Math.abs(reference - x) < .0001, "time-based smoothing across frame rates");
            var same = clouds.update(List.of(target(1, 1, .12f, 4)), 40);
            near(x, same.get(0).target().x(), "paused/multi-pass frame never advances twice");
            check(clouds.update(List.of(), 42).get(0).fade() > 0, "removed cloud fades instead of popping");
            check(clouds.update(List.of(), 46).isEmpty(), "removed cloud expires without ghost");
            var reset = clouds.update(List.of(target(1, 8, .12f, 4)), 1);
            near(8, reset.get(0).target().x(), "clock/world reset never reuses old location");
        }
        var clouds = new EarthSenseVisualMath.Clouds();
        clouds.epoch(1, true);
        clouds.update(List.of(target(1, 0, .12f, 4)), 0);
        clouds.update(List.of(target(1, 0, .12f, 4)), 5);
        clouds.epoch(2, false);
        check(clouds.update(List.of(), 6).get(0).fade() > 0, "stop token preserves fade-out");
        clouds.epoch(3, true);
        var newSession = clouds.update(List.of(target(1, 8, .12f, 4)), 6);
        near(8, newSession.get(0).target().x(), "new session ID one does not slide from previous creature");
        near(0, newSession.get(0).fade(), "new session color cloud starts softly");
        clouds.clear();
        for (int generation = 0; generation < 80; generation++) {
            var batch = new ArrayList<EarthSenseVisualMath.Target>();
            for (int i = 0; i < 32; i++) batch.add(target(generation * 32 + i + 1, i, .65f, 2));
            check(clouds.update(batch, generation * 2).size() <= 32, "churn retains render budget");
        }
        var near = new EarthSenseVisualMath.Cloud(target(1, 0, .12f, 4), 1, 0, 0);
        var far = new EarthSenseVisualMath.Cloud(target(2, 0, .12f, 1), 1, 0, 0);
        var active = new EarthSenseVisualMath.Cloud(target(3, 0, .9f, 4), 1, 0, 0);
        check(EarthSenseVisualMath.blob(center, near, 1).alpha() > EarthSenseVisualMath.blob(center, far, 1).alpha(), "distance independent from quiet activity");
        check(EarthSenseVisualMath.blob(center, active, 1).alpha() > EarthSenseVisualMath.blob(center, near, 1).alpha(), "activity strengthens quiet near cloud");
        near(0, EarthSenseVisualMath.blob(center, near, 0).alpha(), "exit opacity fully hides cloud");
    }
    private static EarthSenseVisualMath.Target target(int id, double x, float activity, int strength) {
        return new EarthSenseVisualMath.Target(id, 0, x, 1, 5, 1, 2, activity, strength, 0);
    }
    private static void near(double expected, double actual, String label) {
        check(Double.isFinite(actual) && Math.abs(expected - actual) < 2e-6, label);
    }
    private static void check(boolean pass, String label) { checks++; if (!pass) throw new AssertionError(label); }
}
