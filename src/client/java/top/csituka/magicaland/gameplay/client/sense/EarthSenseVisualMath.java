package top.csituka.magicaland.gameplay.client.sense;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import org.joml.Matrix4f;
import org.joml.Vector4f;

public final class EarthSenseVisualMath {
    public static final float CLOUD_WIDTH = 1.35f;
    private static final float EDGE_INSET = .025f;
    private EarthSenseVisualMath() {}

    public static float clamp(float value) {
        return Float.isFinite(value) ? Math.max(0, Math.min(1, value)) : 0;
    }

    // Bearing 0 is +Z, 4 is -X, following Minecraft yaw. Pitch is deliberately absent.
    public static double angle(int bearing, float viewYaw) {
        if (!Float.isFinite(viewYaw)) return 0;
        return Math.toRadians(Math.IEEEremainder(Math.floorMod(bearing, 16) * 22.5 - viewYaw, 360));
    }

    public static float pulse(double elapsedTicks) {
        if (!Double.isFinite(elapsedTicks) || elapsedTicks < 0 || elapsedTicks >= 18) return 0;
        float remaining = (float) (1 - elapsedTicks / 18);
        return remaining * remaining;
    }

    public static float intensity(int strength, float pulse) {
        int level = Math.max(1, Math.min(4, strength));
        return Math.min(1, .18f + level * .13f + clamp(pulse) * .23f);
    }

    public static float luminance(float red, float green, float blue) {
        return .2126f * red + .7152f * green + .0722f * blue;
    }

    public static float[] desaturate(float red, float green, float blue, float alpha, float amount) {
        float gray = luminance(red, green, blue), mix = clamp(amount);
        return new float[] {red + (gray - red) * mix, green + (gray - green) * mix,
                blue + (gray - blue) * mix, alpha};
    }

    public record Layout(float x, float y, float radiusX, float radiusY) {}

    public record Blob(float x, float y, float radiusX, float radiusY, float red, float green, float blue,
                       float activity, float alpha, float pulse) {}
    public record Projected(float x, float y, float radiusX, float radiusY, boolean onScreen) {}
    public record Target(int id, int kind, double x, double y, double z, float width, float height,
                         float activity, int strength, int pulse) {}
    public record Cloud(Target target, float fade, float stepPulse, float phase) {}

    public static Projected project(Matrix4f cameraRelativeClip, double x, double y, double z,
                                    float width, float height) {
        if (cameraRelativeClip == null || !cameraRelativeClip.isFinite() || !Double.isFinite(x)
                || !Double.isFinite(y) || !Double.isFinite(z) || !Float.isFinite(width) || !Float.isFinite(height)
                || width <= 0 || height <= 0) return null;
        Vector4f center = cameraRelativeClip.transform(new Vector4f((float) x, (float) y, (float) z, 1));
        if (!center.isFinite() || center.w <= .05f || center.z < -center.w) return null;
        float px = center.x / center.w * .5f + .5f, py = center.y / center.w * .5f + .5f;
        float rx = 0, ry = 0;
        double[] radius = {Math.min(8, width) * .66, Math.min(12, height) * .65, Math.min(8, width) * .66};
        for (int axis = 0; axis < 3; axis++) for (int sign : new int[] {-1, 1}) {
            Vector4f edge = cameraRelativeClip.transform(new Vector4f((float) (x + (axis == 0 ? radius[axis] * sign : 0)),
                    (float) (y + (axis == 1 ? radius[axis] * sign : 0)), (float) (z + (axis == 2 ? radius[axis] * sign : 0)), 1));
            if (!edge.isFinite() || edge.w <= .05f) continue;
            rx = Math.max(rx, Math.abs(edge.x / edge.w * .5f + .5f - px));
            ry = Math.max(ry, Math.abs(edge.y / edge.w * .5f + .5f - py));
        }
        rx = Math.max(.003f, Math.min(.48f, rx * CLOUD_WIDTH)); ry = Math.max(.004f, Math.min(.65f, ry));
        return new Projected(px, py, rx, ry, px + rx >= 0 && px - rx <= 1 && py + ry >= 0 && py - ry <= 1);
    }

    /** One cloud per target: perspective inside the image, a continuous edge position outside it. */
    public static Projected place(Matrix4f clip, Matrix4f view, double x, double y, double z, float width, float height) {
        if (clip == null || view == null || !clip.isFinite() || !view.isFinite()
                || !Double.isFinite(x) || !Double.isFinite(y) || !Double.isFinite(z)
                || !Float.isFinite(width) || !Float.isFinite(height) || width <= 0 || height <= 0) return null;
        var local = view.transform(new Vector4f((float) x, (float) y, (float) z, 1));
        var point = clip.transform(new Vector4f((float) x, (float) y, (float) z, 1));
        if (!local.isFinite() || !point.isFinite()) return null;
        float distance = (float) Math.sqrt(local.x * local.x + local.y * local.y + local.z * local.z);
        float sx = (float) Math.sqrt(clip.m00() * clip.m00() + clip.m10() * clip.m10() + clip.m20() * clip.m20());
        float sy = (float) Math.sqrt(clip.m01() * clip.m01() + clip.m11() * clip.m11() + clip.m21() * clip.m21());
        if (distance < .05f || !Float.isFinite(distance) || sx <= 0 || sy <= 0) return null;
        float bound = .5f - EDGE_INSET;
        float dx = point.x, dy = point.y;
        float max = Math.max(Math.abs(dx), Math.abs(dy));
        if (max < .000001f) { dx = 0; dy = -1; max = 1; }
        float edgeX = dx / max, edgeY = dy / max;
        boolean behind = local.z >= 0;
        float px, py, edgeBlend;
        if (behind) {
            // Continue around the border to the rear/bottom, never mirror a negative perspective W.
            float rear = (float) (Math.asin(Math.max(0, Math.min(1, local.z / distance))) / (Math.PI / 2));
            rear = smooth(rear);
            float arc = edgeArc(edgeX, edgeY);
            float destination = local.x >= 0 ? 0 : 8;
            float[] edge = edgePoint(arc + (destination - arc) * rear);
            px = .5f + edge[0] * bound; py = .5f + edge[1] * bound; edgeBlend = 1;
        } else {
            float denominator = Math.max(.000001f, point.w);
            float qx = point.x * .5f / denominator, qy = point.y * .5f / denominator;
            float extent = Math.max(Math.abs(qx), Math.abs(qy)) / bound;
            float scale = extent > 1 ? 1 / extent : 1;
            px = .5f + qx * scale; py = .5f + qy * scale;
            edgeBlend = smooth((extent - .8f) / .4f);
        }
        float edgeRx = Math.max(.018f, Math.min(.12f, Math.min(width, 8) * .66f * CLOUD_WIDTH * sx / (2 * distance)));
        float edgeRy = Math.max(.024f, Math.min(.18f, Math.min(height, 12) * .65f * sy / (2 * distance)));
        var actual = project(clip, x, y, z, width, height);
        float rx = actual == null ? edgeRx : actual.radiusX() + (edgeRx - actual.radiusX()) * edgeBlend;
        float ry = actual == null ? edgeRy : actual.radiusY() + (edgeRy - actual.radiusY()) * edgeBlend;
        return new Projected(px, py, rx, ry, true);
    }

    private static float smooth(float value) { float t = clamp(value); return t * t * (3 - 2 * t); }
    private static float edgeArc(float x, float y) {
        float extent = Math.max(Math.abs(x), Math.abs(y));
        if (extent > 0) { x /= extent; y /= extent; }
        if (y <= -.999999f) return x >= 0 ? x : 8 + x;
        if (x >= .999999f) return 2 + y;
        if (y >= .999999f) return 4 - x;
        return 6 - y;
    }
    private static float[] edgePoint(float arc) {
        if (arc <= 1) return new float[] {arc, -1};
        if (arc <= 3) return new float[] {1, arc - 2};
        if (arc <= 5) return new float[] {4 - arc, 1};
        if (arc <= 7) return new float[] {-1, 6 - arc};
        return new float[] {arc - 8, -1};
    }

    /** A rear/vertical mapping needs a seam; keep its crossing local in time and on the border. */
    public static final class Placements {
        private final LinkedHashMap<Integer, Projected> positions = new LinkedHashMap<>();
        private double previous = Double.NaN;
        private float blend;
        private float maxArcStep;
        private long epoch;
        public void clear() { positions.clear(); previous = Double.NaN; epoch = 0; }
        public void begin(long value, boolean focusing, double ticks, List<Cloud> clouds) {
            if (!Double.isFinite(ticks)) { clear(); blend = 1; return; }
            if (focusing && epoch != value) { clear(); epoch = value; }
            if (Double.isFinite(previous) && (ticks < previous || ticks - previous > 5)) clear();
            double dt = Double.isFinite(previous) ? ticks - previous : 0;
            blend = Double.isFinite(previous) ? (float) (1 - Math.exp(-dt * .8)) : 1;
            maxArcStep = (float) dt * .18f;
            previous = ticks;
            var present = new HashSet<Integer>();
            for (var cloud : clouds) present.add(cloud.target().id());
            positions.keySet().retainAll(present);
        }
        public Projected apply(int id, Projected desired) {
            if (desired == null) { positions.remove(id); return null; }
            Projected old = positions.get(id);
            if (old == null) { if (positions.size() < 32) positions.put(id, desired); return desired; }
            float x, y, bound = .5f - EDGE_INSET;
            if (onEdge(old) && onEdge(desired)) {
                float oldArc = edgeArc((old.x() - .5f) / bound, (old.y() - .5f) / bound);
                float newArc = edgeArc((desired.x() - .5f) / bound, (desired.y() - .5f) / bound);
                float distance = (float) Math.IEEEremainder(newArc - oldArc, 8);
                float step = Math.max(-maxArcStep, Math.min(maxArcStep, distance * blend));
                float arc = (oldArc + step) % 8;
                if (arc < 0) arc += 8;
                var point = edgePoint(arc);
                x = .5f + point[0] * bound; y = .5f + point[1] * bound;
            } else {
                x = old.x() + (desired.x() - old.x()) * blend;
                y = old.y() + (desired.y() - old.y()) * blend;
            }
            var result = new Projected(x, y, old.radiusX() + (desired.radiusX() - old.radiusX()) * blend,
                    old.radiusY() + (desired.radiusY() - old.radiusY()) * blend, true);
            positions.put(id, result);
            return result;
        }
        private static boolean onEdge(Projected p) {
            return Math.abs(Math.max(Math.abs(p.x() - .5f), Math.abs(p.y() - .5f)) - (.5f - EDGE_INSET)) < .00001f;
        }
    }

    /** Same soft profile for the shader-less HUD fallback, expressed as nested ellipse alpha. */
    public static float softness(float radius, float activity, float phase) {
        float inner = .04f + .44f * clamp(activity);
        float falloff = 1 - smooth((radius - inner) / (.99f - inner));
        return falloff * (.72f + .28f * (float) Math.exp(-3 * radius * radius));
    }

    public static int kindColor(int kind) {
        return switch (kind) { case 0 -> 0xFF4038; case 1 -> 0x448EFF; case 2 -> 0xFFE046; default -> 0x45DE76; };
    }

    public static Blob blob(Projected projection, Cloud cloud, float opacity) {
        if (projection == null || !projection.onScreen()) return null;
        Target target = cloud.target();
        float activity = clamp(target.activity());
        float distance = .32f + .17f * Math.max(1, Math.min(4, target.strength()));
        float alpha = clamp(opacity) * cloud.fade() * distance
                * (.16f + .32f * activity + .028f * cloud.stepPulse() + .012f * (float) Math.sin(cloud.phase()));
        float breath = 1 + .018f * (float) Math.sin(cloud.phase());
        int color = kindColor(target.kind());
        return new Blob(projection.x(), projection.y(), projection.radiusX() * breath, projection.radiusY() * breath,
                ((color >> 16) & 255) / 255f, ((color >> 8) & 255) / 255f, (color & 255) / 255f,
                activity, clamp(alpha), cloud.phase());
    }

    public static final class Clouds {
        private final LinkedHashMap<Integer, Track> tracks = new LinkedHashMap<>();
        private double previous = Double.NaN;
        private long epoch;
        public void clear() { tracks.clear(); previous = Double.NaN; epoch = 0; }
        public void epoch(long value, boolean focusing) {
            if (focusing && epoch != value) { clear(); epoch = value; }
        }
        public List<Cloud> update(List<Target> incoming, double ticks) {
            if (!Double.isFinite(ticks)) { clear(); return List.of(); }
            if (Double.isFinite(previous) && ticks < previous) clear();
            double dt = Double.isFinite(previous) ? Math.min(5, ticks - previous) : 0;
            previous = ticks;
            var present = new HashSet<Integer>();
            for (var target : incoming) {
                if (present.size() >= 32) break;
                if (target == null || target.id() < 1 || target.id() > 65535 || !present.add(target.id())) continue;
                var track = tracks.get(target.id());
                if (track == null) { track = new Track(target, ticks); tracks.put(target.id(), track); }
                track.target = target;
                if (track.pulse != target.pulse()) { track.pulse = target.pulse(); track.pulseStart = ticks; }
            }
            var result = new ArrayList<Cloud>();
            for (var iterator = tracks.entrySet().iterator(); iterator.hasNext();) {
                var track = iterator.next().getValue();
                boolean seen = present.contains(track.target.id());
                track.fade = clamp(track.fade + (float) dt / 5 * (seen ? 1 : -1));
                if (!seen && track.fade <= 0) { iterator.remove(); continue; }
                float blend = (float) (1 - Math.exp(-dt * .55));
                double dx = track.target.x() - track.x, dy = track.target.y() - track.y, dz = track.target.z() - track.z;
                if (dx * dx + dy * dy + dz * dz > 16) blend = 1;
                track.x += dx * blend; track.y += dy * blend; track.z += dz * blend;
                track.activity += (clamp(track.target.activity()) - track.activity) * blend;
                var t = track.target;
                var smoothed = new Target(t.id(), t.kind(), track.x, track.y, track.z, t.width(), t.height(), track.activity, t.strength(), t.pulse());
                if (seen || result.size() + present.size() < 32)
                    result.add(new Cloud(smoothed, track.fade, pulse(ticks - track.pulseStart),
                            (float) Math.IEEEremainder(ticks * .095 + t.id() * 2.39996, Math.PI * 2)));
            }
            while (tracks.size() > 64) {
                Integer stale = tracks.keySet().stream().filter(id -> !present.contains(id)).findFirst().orElse(tracks.keySet().iterator().next());
                tracks.remove(stale);
            }
            return result;
        }
        private static final class Track {
            Target target;
            double x, y, z, pulseStart;
            float fade, activity;
            int pulse;
            Track(Target target, double ticks) {
                this.target = target; x = target.x(); y = target.y(); z = target.z();
                activity = clamp(target.activity()); pulse = target.pulse(); pulseStart = ticks;
            }
        }
    }

    public static Layout layout(int width, int height, int legendBottom) {
        float top = Math.max(10, legendBottom + 10), bottom = height - 68;
        if (width < 100 || bottom - top < 34) return null;
        float ry = Math.min(38, (bottom - top - 14) / 3.2f);
        return new Layout(width * .5f, (top + bottom) * .5f, Math.min(105, width * .26f), ry);
    }

    public static final class Pulses {
        private final int[] pulses = new int[64];
        private final double[] starts = new double[64];
        private final boolean[] present = new boolean[64];
        private double lastTime = Double.NaN;

        public void clear() {
            java.util.Arrays.fill(present, false);
            lastTime = Double.NaN;
        }

        public float observe(int bearing, int kind, int pulse, double ticks) {
            if (bearing < 0 || bearing > 15 || kind < 0 || kind > 3 || !Double.isFinite(ticks)) return 0;
            if (Double.isFinite(lastTime) && ticks < lastTime) clear();
            lastTime = ticks;
            int index = bearing * 4 + kind, value = pulse & 255;
            if (!present[index] || pulses[index] != value) {
                present[index] = true;
                pulses[index] = value;
                starts[index] = ticks;
            }
            return EarthSenseVisualMath.pulse(ticks - starts[index]);
        }
    }
}
