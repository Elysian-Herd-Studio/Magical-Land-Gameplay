package top.csituka.magicaland.gameplay.client.echo;

import java.nio.ByteBuffer;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Arrays;
import org.joml.Matrix4f;
import org.joml.Vector3f;
import org.joml.Vector4f;
import org.lwjgl.opengl.GL;
import org.lwjgl.system.Configuration;
import static org.lwjgl.glfw.GLFW.*;
import static org.lwjgl.opengl.GL33C.*;

public final class SpiritualEchoFilterGpuTest {
    private static int checks;
    private static final Vector3f ORIGIN = new Vector3f();
    private static final Matrix4f PROJECTION = new Matrix4f().ortho(-4, 4, -3, 3, .05f, 40);
    private static final Matrix4f INVERSE = new Matrix4f(PROJECTION).invert();
    private record Scene(int fbo, int texture, int depth, boolean depthTexture, boolean stencil,
                         int width, int height, byte[] pixels, float[] depths) implements AutoCloseable {
        @Override public void close() {
            glDeleteFramebuffers(fbo); glDeleteTextures(texture);
            if (depthTexture) glDeleteTextures(depth); else glDeleteRenderbuffers(depth);
        }
    }

    public static void main(String[] args) throws Exception {
        boolean strict = args.length < 2 || !args[1].equals("native");
        if (strict) {
            Configuration.OPENGL_MAXVERSION.set("3.2");
            Configuration.OPENGL_EXTENSION_FILTER.set("GL_ARB_sampler_objects,GL_ARB_draw_buffers_blend");
        }
        if (!glfwInit()) throw new AssertionError("GLFW init");
        glfwWindowHint(GLFW_VISIBLE, GLFW_FALSE);
        glfwWindowHint(GLFW_CONTEXT_VERSION_MAJOR, strict ? 3 : 4);
        glfwWindowHint(GLFW_CONTEXT_VERSION_MINOR, strict ? 2 : 0);
        glfwWindowHint(GLFW_OPENGL_FORWARD_COMPAT, GLFW_TRUE);
        glfwWindowHint(GLFW_OPENGL_PROFILE, GLFW_OPENGL_CORE_PROFILE);
        long window = glfwCreateWindow(64, 64, "Spiritual echo offscreen regression", 0, 0);
        if (window == 0) throw new AssertionError("Hidden context");
        glfwMakeContextCurrent(window); GL.createCapabilities();
        if (strict) check(GL.getCapabilities().OpenGL32 && !GL.getCapabilities().OpenGL33
                && !GL.getCapabilities().GL_ARB_sampler_objects && !GL.getCapabilities().GL_ARB_draw_buffers_blend,
                "strict GL3.2 without sampler objects or indexed blend parameters");
        else check(GL.getCapabilities().OpenGL40, "native core 4.0 path");
        String vertex = Files.readString(Path.of(args[0], "spiritual_echo.vsh"));
        String fragment = Files.readString(Path.of(args[0], "spiritual_echo.fsh"));
        try (var filter = new SpiritualEchoFilter(vertex, fragment)) {
            stateAndFormats(filter, vertex, fragment);
            pulsePixels(filter);
            overlappingPulses(filter);
            geometryOnly(filter);
            perspectiveAndMask(filter);
            eligibility(filter, vertex, fragment);
            multipleDrawTargets(filter);
            check(glGetError() == GL_NO_ERROR, "final GL error clear");
            System.out.println("PASS spiritual echo actual GPU: " + checks + "; strict3.2=" + strict
                    + "; samplers=" + samplers() + "; driver=" + glGetString(GL_VERSION));
        } finally {
            glfwDestroyWindow(window); glfwTerminate();
        }
    }

    private static void stateAndFormats(SpiritualEchoFilter filter, String vertex, String fragment) {
        int spareVao = glGenVertexArrays(), texture0 = glGenTextures(), texture1 = glGenTextures();
        int sampler0 = samplers() ? glGenSamplers() : 0, sampler1 = samplers() ? glGenSamplers() : 0;
        int unpack = glGenBuffers(), pack = glGenBuffers(), spareProgram = program(vertex, fragment);
        try {
            for (int format : new int[] {GL_DEPTH_COMPONENT24, GL_DEPTH_COMPONENT32F, GL_DEPTH24_STENCIL8}) {
                for (int[] size : new int[][] {{64, 48}, {1, 1}, {47, 31}, {7, 5}}) {
                    normal();
                    try (Scene scene = scene(size[0], size[1], format, 0, false);
                         Scene foreign = scene(3, 3, GL_DEPTH_COMPONENT24, 0, false)) {
                        glBindFramebuffer(GL_DRAW_FRAMEBUFFER, scene.fbo);
                        glBindFramebuffer(GL_READ_FRAMEBUFFER, foreign.fbo);
                        glUseProgram(spareProgram); glBindVertexArray(spareVao);
                        glActiveTexture(GL_TEXTURE0); glBindTexture(GL_TEXTURE_2D, texture0);
                        glActiveTexture(GL_TEXTURE1); glBindTexture(GL_TEXTURE_2D, texture1);
                        glActiveTexture(GL_TEXTURE5); glBindTexture(GL_TEXTURE_2D, foreign.texture);
                        if (samplers()) {
                            glSamplerParameteri(sampler0, GL_TEXTURE_MIN_FILTER, GL_NEAREST_MIPMAP_LINEAR);
                            glSamplerParameteri(sampler1, GL_TEXTURE_COMPARE_MODE, GL_COMPARE_REF_TO_TEXTURE);
                            glBindSampler(0, sampler0); glBindSampler(1, sampler1);
                        }
                        glBindBuffer(GL_PIXEL_UNPACK_BUFFER, unpack); glBufferData(GL_PIXEL_UNPACK_BUFFER, 16, GL_STATIC_DRAW);
                        glBindBuffer(GL_PIXEL_PACK_BUFFER, pack); glBufferData(GL_PIXEL_PACK_BUFFER, 16, GL_STATIC_DRAW);
                        glViewport(2, 3, 9, 11); glScissor(1, 1, 2, 2);
                        for (int cap : CAPS) glEnable(cap);
                        glEnablei(GL_BLEND, 0); glDisablei(GL_BLEND, 1);
                        glColorMaski(0, false, true, false, true);
                        glColorMaski(1, true, false, true, false);
                        glEnable(GL_CLIP_DISTANCE0); glLogicOp(GL_XOR);
                        glDepthFunc(GL_GREATER); glDepthMask(true);
                        glBlendFuncSeparate(GL_ONE, GL_DST_COLOR, GL_ONE_MINUS_DST_ALPHA, GL_SRC_ALPHA);
                        glBlendEquationSeparate(GL_FUNC_REVERSE_SUBTRACT, GL_FUNC_SUBTRACT);
                        glPolygonMode(GL_FRONT_AND_BACK, GL_LINE);
                        int[] before = snapshot();
                        filter.beginFrame();
                        check(filter.captureDepth(scene.fbo, scene.width, scene.height), "captures depth format " + format);
                        check(Arrays.equals(before, snapshot()), "depth copy restores all neighboring state");
                        check(filter.render(scene.fbo, scene.width, scene.height, INVERSE, ORIGIN, .43f, 1, 0x8855ff), "offscreen render");
                        check(Arrays.equals(before, snapshot()), "render restores GL units 0/1, samplers, indexed masks, buffers, FBOs and raster state");
                        check(filter.captureDepth(scene.fbo, scene.width, scene.height), "capture for overlapping waves");
                        check(filter.render(scene.fbo, scene.width, scene.height, INVERSE, new Vector3f(0, 0, -5), .1f,
                                ORIGIN, 3.1f, 1, 0x8855ff), "overlapping wave render");
                        check(Arrays.equals(before, snapshot()), "both-wave render restores every GL state");
                        glBindBuffer(GL_PIXEL_PACK_BUFFER, 0);
                        invariants(scene);
                        check(glGetError() == GL_NO_ERROR, "no GL errors after format/resize");
                    }
                }
            }
        } finally {
            normal(); glUseProgram(0); glDeleteProgram(spareProgram); glDeleteVertexArrays(spareVao);
            glDeleteTextures(texture0); glDeleteTextures(texture1);
            if (samplers()) { glDeleteSamplers(sampler0); glDeleteSamplers(sampler1); }
            glDeleteBuffers(unpack); glDeleteBuffers(pack);
        }
    }

    private static void pulsePixels(SpiritualEchoFilter filter) {
        normal();
        try (Scene scene = scene(128, 96, GL_DEPTH_COMPONENT32F, 0, false)) {
            byte[] early = draw(filter, scene, .04f, 1, ORIGIN, 0xffffff);
            check(Arrays.equals(early, scene.pixels), "no echo before wave arrives");
            byte[] first = draw(filter, scene, .40f, 1, ORIGIN, 0xffffff);
            byte[] later = draw(filter, scene, .65f, 1, ORIGIN, 0xffffff);
            int edge = (48 * 128 + 63) * 4;
            check(delta(first, scene, edge) > 95, "visible near wall silhouette");
            check(delta(later, scene, edge) > 20 && delta(later, scene, edge) < delta(first, scene, edge),
                    "each location fades after wave passes");
            byte[] oneSecond = draw(filter, scene, 1.5f, 1, ORIGIN, 0xffffff);
            byte[] twoSeconds = draw(filter, scene, 2.5f, 1, ORIGIN, 0xffffff);
            check(delta(oneSecond, scene, edge) > 50, "outline persists beyond one second");
            check(delta(twoSeconds, scene, edge) > 10 && delta(twoSeconds, scene, edge) < delta(oneSecond, scene, edge),
                    "outline remains visible beyond two seconds while fading");
            int previous = delta(draw(filter, scene, 2.8f, 1, ORIGIN, 0xffffff), scene, edge);
            for (float phase : new float[] {2.9f, 3, 3.1f, 3.2f, 3.3f, 3.35f}) {
                int faded = delta(draw(filter, scene, phase, 1, ORIGIN, 0xffffff), scene, edge);
                check(faded <= previous && previous - faded <= 10, "three-second local afterglow finishes gradually");
                previous = faded;
            }
            check(previous == 0, "near wall disappears three seconds after its arrival time");
            byte[] gap = draw(filter, scene, 3.7f, 1, ORIGIN, 0xffffff);
            check(Arrays.equals(gap, scene.pixels), "a fully expired single wave leaves no residual signal");
            byte[] repeat = draw(filter, scene, .40f, 1, ORIGIN, 0xffffff);
            check(Arrays.equals(first, repeat), "next pulse is deterministic");
            check(Arrays.equals(draw(filter, scene, .43f, 0, ORIGIN, 0xffffff), scene.pixels), "occlusion zero leaves RGB unchanged");
            byte[] partial = draw(filter, scene, .43f, .5f, ORIGIN, 0xffffff);
            check(delta(partial, scene, edge) == 0, "open center never receives echo");
            byte[] black = draw(filter, scene, .40f, 1, ORIGIN, 0);
            check(delta(black, scene, edge) > 40, "black magic retains a visible luminance floor");
            byte[] farOrigin = draw(filter, scene, .55f, 1, new Vector3f(0, 0, 5), 0xffffff);
            check(Arrays.equals(farOrigin, scene.pixels), "8-block sphere is relative to light orb, not render camera");
            filter.beginFrame(); glBindFramebuffer(GL_FRAMEBUFFER, scene.fbo);
            check(filter.captureDepth(scene.fbo, scene.width, scene.height), "capture before simulated hand depth clear");
            glDepthMask(true); glClearDepth(1); glClear(GL_DEPTH_BUFFER_BIT);
            color(scene);
            check(filter.render(scene.fbo, scene.width, scene.height, INVERSE, ORIGIN, .40f, 1, 0xffffff), "uses saved world depth");
            check(Arrays.equals(first, read(scene)), "later first-person hand depth clear cannot erase world echo");
            float[] now = depth(scene);
            for (float d : now) check(Math.abs(d - 1) < .00001, "render never restores or overwrites current main depth");
        }
    }

    private static void overlappingPulses(SpiritualEchoFilter filter) {
        normal();
        try (Scene scene = scene(128, 96, GL_DEPTH_COMPONENT32F, 0, false)) {
            int farEdge = (48 * 128 + 64) * 4;
            byte[] firstStart = drawPair(filter, scene, ORIGIN, 0, null, 3, 1, 0xffffff);
            check(Arrays.equals(firstStart, scene.pixels), "initial blackout never fabricates a previous pulse");

            byte[] before = draw(filter, scene, 2.999f, 1, ORIGIN, 0xffffff);
            byte[] atBoundary = drawPair(filter, scene, ORIGIN, 0, ORIGIN, 3, 1, 0xffffff);
            for (int i = 0; i < before.length; i++)
                check(Math.abs((before[i] & 255) - (atBoundary[i] & 255)) <= 1, "cycle boundary retains previous far-side afterglow");
            check(delta(atBoundary, scene, farEdge) > 5, "far wall remains visibly outlined at three-second rollover");

            byte[] noTail = drawPair(filter, scene, ORIGIN, .04f, null, 3.04f, 1, 0xffffff);
            check(Arrays.equals(noTail, scene.pixels), "current wave cannot reveal surfaces before reaching them");
            byte[] oldTail = draw(filter, scene, 3.04f, 1, ORIGIN, 0xffffff);
            byte[] withTail = drawPair(filter, scene, new Vector3f(0, 0, -2), .04f, ORIGIN, 3.04f, 1, 0xffffff);
            check(Arrays.equals(oldTail, withTail), "old tail keeps its own launch point while new wave expands elsewhere");

            Vector3f moved = new Vector3f(0, 0, -5);
            byte[] current = draw(filter, scene, .1f, 1, moved, 0xffffff);
            byte[] previous = draw(filter, scene, 3.1f, 1, ORIGIN, 0xffffff);
            byte[] combined = drawPair(filter, scene, moved, .1f, ORIGIN, 3.1f, 1, 0xffffff);
            check(delta(current, scene, farEdge) > 10 && delta(previous, scene, farEdge) > 5,
                    "both differently anchored pulses genuinely reach the same pixel");
            for (int i = 0; i < combined.length; i++)
                check(Math.abs((combined[i] & 255) - Math.max(current[i] & 255, previous[i] & 255)) <= 1,
                        "overlap takes the brighter signal, never sums repeated echoes");
            check(Arrays.equals(combined, drawPair(filter, scene, moved, .1f, ORIGIN, 3.1f, 1, 0xffffff)),
                    "re-rendering does not accumulate framebuffer history");
            check(Arrays.equals(drawPair(filter, scene, moved, .1f, ORIGIN, 3.1f, 0, 0xffffff), scene.pixels),
                    "occlusion zero suppresses both current and previous waves");
            for (float invalid : new float[] {3.7f, 4, -1, Float.NaN, Float.POSITIVE_INFINITY}) {
                check(Arrays.equals(drawPair(filter, scene, ORIGIN, .4f, ORIGIN, invalid, 1, 0xffffff), scene.pixels),
                        "expired or nonfinite previous phase safely rejects the pass");
            }
            check(Arrays.equals(drawPair(filter, scene, ORIGIN, .4f, new Vector3f(Float.NaN, 0, 0), 3.1f, 1, 0xffffff),
                    scene.pixels), "nonfinite previous launch point cannot contaminate framebuffer");
            check(Arrays.equals(drawPair(filter, scene, ORIGIN, Float.NaN, ORIGIN, 3.1f, 1, 0xffffff), scene.pixels),
                    "nonfinite current phase remains rejected with a valid previous pulse");
        }
    }

    private static void geometryOnly(SpiritualEchoFilter filter) {
        normal();
        try (Scene plain = scene(128, 96, GL_DEPTH_COMPONENT24, 0, false);
             Scene textured = scene(128, 96, GL_DEPTH_COMPONENT24, 0, true)) {
            byte[] a = draw(filter, plain, .43f, 1, ORIGIN, 0xffffff);
            byte[] b = draw(filter, textured, .43f, 1, ORIGIN, 0xffffff);
            for (int i = 0; i < a.length; i++) if (i % 4 != 3)
                check(Math.abs(((a[i] & 255) - (plain.pixels[i] & 255))
                        - ((b[i] & 255) - (textured.pixels[i] & 255))) <= 1,
                        "ore-like RGB checkerboard cannot change echo shape");
            byte[] dualPlain = drawPair(filter, plain, new Vector3f(0, 0, -5), .1f, ORIGIN, 3.1f, 1, 0xffffff);
            byte[] dualTexture = drawPair(filter, textured, new Vector3f(0, 0, -5), .1f, ORIGIN, 3.1f, 1, 0xffffff);
            for (int i = 0; i < dualPlain.length; i++) if (i % 4 != 3)
                check(Math.abs(((dualPlain[i] & 255) - (plain.pixels[i] & 255))
                        - ((dualTexture[i] & 255) - (textured.pixels[i] & 255))) <= 1,
                        "long-lived overlapping echoes never recover RGB texture edges");
            int edge = (48 * 128 + 63) * 4, flat = (48 * 128 + 50) * 4;
            check(delta(a, plain, edge) > delta(a, plain, flat) * 8, "geometry outlines dominate faint flat-surface sweep");
        }
        for (int geometry : new int[] {1, 2, 3}) {
            try (Scene scene = scene(128, 96, GL_DEPTH_COMPONENT32F, geometry, false)) {
                byte[] pixels = draw(filter, scene, geometry == 2 ? .65f : .4f, 1, ORIGIN, 0xffffff);
                if (geometry == 1) check(Arrays.equals(pixels, scene.pixels), "sky never reveals a surface");
                if (geometry == 2) check(Arrays.equals(pixels, scene.pixels), "surfaces beyond 8 blocks remain hidden");
                if (geometry == 3) {
                    int corner = (48 * 128 + 63) * 4;
                    check(delta(pixels, scene, corner) > 35, "continuous wall corner detected by local normal change");
                }
            }
        }
    }

    private static void eligibility(SpiritualEchoFilter filter, String vertex, String fragment) {
        normal();
        try (Scene scene = scene(32, 24, GL_DEPTH_COMPONENT24, 0, false);
             Scene foreign = scene(4, 4, GL_DEPTH_COMPONENT24, 0, false)) {
            glBindFramebuffer(GL_FRAMEBUFFER, foreign.fbo);
            int[] saved = snapshot();
            check(!filter.captureDepth(scene.fbo, scene.width, scene.height), "foreign bound framebuffer rejected");
            check(Arrays.equals(saved, snapshot()), "foreign framebuffer untouched");
            glBindFramebuffer(GL_FRAMEBUFFER, scene.fbo);
            check(!filter.captureDepth(scene.fbo, scene.width + 1, scene.height), "wrong physical size rejected");
            check(!filter.captureDepth(scene.fbo, glGetInteger(GL_MAX_TEXTURE_SIZE) + 1, 10), "oversize target rejected");
            check(!filter.render(scene.fbo, scene.width, scene.height, INVERSE, ORIGIN, .43f, 1, 0xffffff), "failed capture cannot reuse old depth");
            check(filter.captureDepth(scene.fbo, scene.width, scene.height), "capture available");
            filter.beginFrame();
            check(!filter.render(scene.fbo, scene.width, scene.height, INVERSE, ORIGIN, .43f, 1, 0xffffff), "new frame invalidates stale depth");
            check(filter.captureDepth(scene.fbo, scene.width, scene.height), "recapture available");
            glBindFramebuffer(GL_FRAMEBUFFER, foreign.fbo);
            check(!filter.render(scene.fbo, scene.width, scene.height, INVERSE, ORIGIN, .43f, 1, 0xffffff), "foreign render rejected");
            check(Arrays.equals(scene.pixels, read(scene)), "rejected paths do not mutate scene");
            glBindFramebuffer(GL_FRAMEBUFFER, scene.fbo);
            saved = snapshot();
            boolean failed = false;
            try (var broken = new SpiritualEchoFilter(vertex, "#version 150\ninvalid syntax")) {}
            catch (IllegalStateException expected) { failed = true; }
            check(failed && Arrays.equals(saved, snapshot()), "bad shader fails with preserved state");
            glEnable(-1);
            failed = false;
            try { filter.captureDepth(scene.fbo, scene.width, scene.height); }
            catch (IllegalStateException expected) { failed = true; }
            check(failed, "preexisting error fails before scene mutation");
            check(Arrays.equals(scene.pixels, read(scene)), "GPU failure leaves original scene intact");
        }
        normal();
        int color = glGenRenderbuffers(), depth = glGenRenderbuffers(), fbo = glGenFramebuffers();
        try {
            glBindRenderbuffer(GL_RENDERBUFFER, color); glRenderbufferStorageMultisample(GL_RENDERBUFFER, 2, GL_RGBA8, 16, 16);
            glBindRenderbuffer(GL_RENDERBUFFER, depth); glRenderbufferStorageMultisample(GL_RENDERBUFFER, 2, GL_DEPTH_COMPONENT24, 16, 16);
            glBindFramebuffer(GL_FRAMEBUFFER, fbo);
            glFramebufferRenderbuffer(GL_FRAMEBUFFER, GL_COLOR_ATTACHMENT0, GL_RENDERBUFFER, color);
            glFramebufferRenderbuffer(GL_FRAMEBUFFER, GL_DEPTH_ATTACHMENT, GL_RENDERBUFFER, depth);
            check(glCheckFramebufferStatus(GL_FRAMEBUFFER) == GL_FRAMEBUFFER_COMPLETE, "multisample test framebuffer complete");
            int[] saved = snapshot();
            check(!filter.captureDepth(fbo, 16, 16) && Arrays.equals(saved, snapshot()), "unsupported multisampling skipped without mutation");
        } finally { glDeleteFramebuffers(fbo); glDeleteRenderbuffers(color); glDeleteRenderbuffers(depth); }
    }

    private static void perspectiveAndMask(SpiritualEchoFilter filter) {
        normal();
        try (Scene scene = scene(192, 128, GL_DEPTH_COMPONENT32F, 0, false)) {
            Matrix4f clip = new Matrix4f().perspective((float) Math.toRadians(72), 1.5f, .05f, 128)
                    .rotateY(.31f).rotateZ(.09f);
            Matrix4f inverse = new Matrix4f(clip).invert();
            for (int y = 0; y < scene.height; y++) for (int x = 0; x < scene.width; x++) {
                float nx = (x + .5f) / scene.width * 2 - 1, ny = (y + .5f) / scene.height * 2 - 1;
                Vector3f near = inverse.transformProject(new Vector3f(nx, ny, -1));
                Vector3f far = inverse.transformProject(new Vector3f(nx, ny, 1));
                Vector3f ray = far.sub(near);
                float planeZ = x < scene.width / 2 ? -4 : -6;
                Vector3f point = new Vector3f(ray).mul((planeZ - near.z) / ray.z).add(near);
                scene.depths[y * scene.width + x] = clip.transformProject(point).z * .5f + .5f;
            }
            glActiveTexture(GL_TEXTURE0); glBindTexture(GL_TEXTURE_2D, scene.depth);
            glTexSubImage2D(GL_TEXTURE_2D, 0, 0, 0, scene.width, scene.height, GL_DEPTH_COMPONENT, GL_FLOAT, scene.depths);
            glBindFramebuffer(GL_FRAMEBUFFER, scene.fbo);
            filter.beginFrame(); check(filter.captureDepth(scene.fbo, scene.width, scene.height), "perspective world capture");
            check(filter.render(scene.fbo, scene.width, scene.height, inverse, ORIGIN, .43f, .6f, 0xffffff),
                    "perspective and rotated camera render");
            byte[] pixels = read(scene);
            int maximum = 0;
            for (int y = 0; y < scene.height; y++) for (int x = 0; x < scene.width; x++) {
                int index = (y * scene.width + x) * 4;
                float nx = (x + .5f) / scene.width * 2 - 1, ny = (y + .5f) / scene.height * 2 - 1;
                float mask = Math.max(0, Math.min(1, ((float) Math.hypot(nx, ny) - (1.4f - 1.72f * .6f)) / .32f));
                if (mask == 0) check(delta(pixels, scene, index) == 0, "elliptical open area unchanged with rotated view");
                maximum = Math.max(maximum, delta(pixels, scene, index));
            }
            check(maximum > 50, "world-space corner remains visible with perspective and camera rotation");
            invariants(scene);
        }
    }

    private static byte[] draw(SpiritualEchoFilter filter, Scene scene, float phase, float occlusion, Vector3f origin, int magic) {
        normal(); color(scene); glBindFramebuffer(GL_FRAMEBUFFER, scene.fbo);
        filter.beginFrame();
        check(filter.captureDepth(scene.fbo, scene.width, scene.height), "fresh world capture");
        boolean rendered = filter.render(scene.fbo, scene.width, scene.height, INVERSE, origin, phase, occlusion, magic);
        check(rendered == (phase >= 0 && phase < SpiritualEchoTimeline.EXPANSION + SpiritualEchoTimeline.AFTERGLOW
                && occlusion > 0), "pulse/occlusion eligibility");
        invariants(scene);
        return read(scene);
    }

    private static byte[] drawPair(SpiritualEchoFilter filter, Scene scene, Vector3f origin, float phase,
                                   Vector3f previousOrigin, float previousPhase, float occlusion, int magic) {
        normal(); color(scene); glBindFramebuffer(GL_FRAMEBUFFER, scene.fbo);
        filter.beginFrame();
        int[] before = snapshot();
        check(filter.captureDepth(scene.fbo, scene.width, scene.height), "fresh depth for dual pulse");
        check(Arrays.equals(before, snapshot()), "dual-pulse capture preserves state");
        float end = (float) (SpiritualEchoTimeline.EXPANSION + SpiritualEchoTimeline.AFTERGLOW);
        boolean current = Float.isFinite(phase) && phase >= 0 && phase < end && origin != null && origin.isFinite();
        boolean previous = previousOrigin == null || previousOrigin.isFinite()
                && Float.isFinite(previousPhase) && previousPhase >= 0 && previousPhase < end;
        boolean rendered = filter.render(scene.fbo, scene.width, scene.height, INVERSE, origin, phase,
                previousOrigin, previousPhase, occlusion, magic);
        check(rendered == (Float.isFinite(occlusion) && occlusion > 0 && current && previous), "current and previous pulse eligibility");
        check(Arrays.equals(before, snapshot()), "dual-pulse success and rejection preserve GL state");
        invariants(scene);
        return read(scene);
    }

    private static Scene scene(int width, int height, int format, int geometry, boolean textured) {
        normal();
        byte[] pixels = new byte[width * height * 4];
        float[] depths = new float[width * height];
        for (int y = 0; y < height; y++) for (int x = 0; x < width; x++) {
            int i = y * width + x;
            pixels[i * 4] = (byte) (textured ? ((x / 3 + y / 3) % 2) * 75 : 10);
            pixels[i * 4 + 1] = (byte) (textured ? (x * 11 + y * 7) % 80 : 10);
            pixels[i * 4 + 2] = 10; pixels[i * 4 + 3] = (byte) (71 + (i % 130));
            float worldX = (x + .5f) / width * 8 - 4;
            float z = geometry == 2 ? 9 : geometry == 3 ? 4 + Math.max(0, worldX) * .85f : x < width / 2 ? 4 : 6;
            Vector4f clip = PROJECTION.transform(new Vector4f(worldX, 0, -z, 1));
            depths[i] = geometry == 1 ? 1 : clip.z / clip.w * .5f + .5f;
        }
        int texture = glGenTextures(); glActiveTexture(GL_TEXTURE0); glBindTexture(GL_TEXTURE_2D, texture);
        glTexParameteri(GL_TEXTURE_2D, GL_TEXTURE_MIN_FILTER, GL_NEAREST); glTexParameteri(GL_TEXTURE_2D, GL_TEXTURE_MAG_FILTER, GL_NEAREST);
        ByteBuffer bytes = ByteBuffer.allocateDirect(pixels.length); bytes.put(pixels).flip();
        glTexImage2D(GL_TEXTURE_2D, 0, GL_RGBA8, width, height, 0, GL_RGBA, GL_UNSIGNED_BYTE, bytes);
        boolean depthTexture = format != GL_DEPTH24_STENCIL8;
        int depth = depthTexture ? glGenTextures() : glGenRenderbuffers();
        if (depthTexture) {
            glBindTexture(GL_TEXTURE_2D, depth);
            glTexParameteri(GL_TEXTURE_2D, GL_TEXTURE_MIN_FILTER, GL_NEAREST); glTexParameteri(GL_TEXTURE_2D, GL_TEXTURE_MAG_FILTER, GL_NEAREST);
            glTexImage2D(GL_TEXTURE_2D, 0, format, width, height, 0, GL_DEPTH_COMPONENT, GL_FLOAT, depths);
        } else {
            glBindRenderbuffer(GL_RENDERBUFFER, depth); glRenderbufferStorage(GL_RENDERBUFFER, format, width, height);
        }
        int fbo = glGenFramebuffers(); glBindFramebuffer(GL_FRAMEBUFFER, fbo);
        glFramebufferTexture2D(GL_FRAMEBUFFER, GL_COLOR_ATTACHMENT0, GL_TEXTURE_2D, texture, 0);
        if (depthTexture) glFramebufferTexture2D(GL_FRAMEBUFFER, GL_DEPTH_ATTACHMENT, GL_TEXTURE_2D, depth, 0);
        else {
            glFramebufferRenderbuffer(GL_FRAMEBUFFER, GL_DEPTH_STENCIL_ATTACHMENT, GL_RENDERBUFFER, depth);
            glClearDepth(depths[0]); glClearStencil(3); glClear(GL_DEPTH_BUFFER_BIT | GL_STENCIL_BUFFER_BIT);
            Arrays.fill(depths, depths[0]);
        }
        check(glCheckFramebufferStatus(GL_FRAMEBUFFER) == GL_FRAMEBUFFER_COMPLETE, "test target complete");
        return new Scene(fbo, texture, depth, depthTexture, !depthTexture, width, height, pixels, depths);
    }

    private static final int[] CAPS = {GL_SCISSOR_TEST, GL_DEPTH_TEST, GL_STENCIL_TEST, GL_CULL_FACE, GL_RASTERIZER_DISCARD,
            GL_FRAMEBUFFER_SRGB, GL_DITHER, GL_COLOR_LOGIC_OP, GL_SAMPLE_ALPHA_TO_COVERAGE, GL_SAMPLE_ALPHA_TO_ONE, GL_SAMPLE_COVERAGE};
    private static void normal() {
        glUseProgram(0); glBindVertexArray(0);
        glBindBuffer(GL_PIXEL_UNPACK_BUFFER, 0); glBindBuffer(GL_PIXEL_PACK_BUFFER, 0);
        for (int i = 0; i < 2; i++) {
            glActiveTexture(GL_TEXTURE0 + i); if (samplers()) glBindSampler(i, 0);
            glDisablei(GL_BLEND, i); glColorMaski(i, true, true, true, true);
        }
        glActiveTexture(GL_TEXTURE0);
        for (int cap : CAPS) glDisable(cap);
        for (int i = 0; i < glGetInteger(GL_MAX_CLIP_DISTANCES); i++) glDisable(GL_CLIP_DISTANCE0 + i);
        glDepthMask(true); glStencilMask(255); glPolygonMode(GL_FRONT_AND_BACK, GL_FILL);
    }
    private static int[] snapshot() {
        var result = new java.util.ArrayList<Integer>();
        for (int parameter : new int[] {GL_READ_FRAMEBUFFER_BINDING, GL_DRAW_FRAMEBUFFER_BINDING, GL_CURRENT_PROGRAM,
                GL_VERTEX_ARRAY_BINDING, GL_RENDERBUFFER_BINDING, GL_ACTIVE_TEXTURE, GL_TEXTURE_BINDING_2D,
                GL_PIXEL_UNPACK_BUFFER_BINDING, GL_PIXEL_PACK_BUFFER_BINDING, GL_ARRAY_BUFFER_BINDING,
                GL_DEPTH_FUNC, GL_DEPTH_WRITEMASK, GL_STENCIL_WRITEMASK, GL_BLEND_SRC_RGB, GL_BLEND_DST_RGB,
                GL_BLEND_SRC_ALPHA, GL_BLEND_DST_ALPHA, GL_BLEND_EQUATION_RGB, GL_BLEND_EQUATION_ALPHA, GL_READ_BUFFER,
                GL_DRAW_BUFFER0, GL_DRAW_BUFFER1})
            result.add(glGetInteger(parameter));
        int active = glGetInteger(GL_ACTIVE_TEXTURE);
        for (int i = 0; i < 2; i++) {
            glActiveTexture(GL_TEXTURE0 + i);
            result.add(glGetInteger(GL_TEXTURE_BINDING_2D)); result.add(samplers() ? glGetIntegeri(GL_SAMPLER_BINDING, i) : 0);
            result.add(glIsEnabledi(GL_BLEND, i) ? 1 : 0);
            ByteBuffer mask = ByteBuffer.allocateDirect(4); glGetBooleani_v(GL_COLOR_WRITEMASK, i, mask);
            for (int c = 0; c < 4; c++) result.add((int) mask.get(c));
        }
        glActiveTexture(active);
        for (int parameter : new int[] {GL_VIEWPORT, GL_SCISSOR_BOX}) {
            int[] value = new int[4]; glGetIntegerv(parameter, value); for (int component : value) result.add(component);
        }
        int[] polygon = new int[2]; glGetIntegerv(GL_POLYGON_MODE, polygon); for (int value : polygon) result.add(value);
        for (int cap : CAPS) result.add(glIsEnabled(cap) ? 1 : 0);
        for (int i = 0; i < glGetInteger(GL_MAX_CLIP_DISTANCES); i++) result.add(glIsEnabled(GL_CLIP_DISTANCE0 + i) ? 1 : 0);
        return result.stream().mapToInt(Integer::intValue).toArray();
    }
    private static void color(Scene scene) {
        glBindBuffer(GL_PIXEL_UNPACK_BUFFER, 0); glActiveTexture(GL_TEXTURE0); glBindTexture(GL_TEXTURE_2D, scene.texture);
        ByteBuffer data = ByteBuffer.allocateDirect(scene.pixels.length); data.put(scene.pixels).flip();
        glTexSubImage2D(GL_TEXTURE_2D, 0, 0, 0, scene.width, scene.height, GL_RGBA, GL_UNSIGNED_BYTE, data);
    }
    private static void multipleDrawTargets(SpiritualEchoFilter filter) {
        normal();
        try (Scene scene = scene(16, 16, GL_DEPTH_COMPONENT24, 0, false);
             Scene auxiliary = scene(16, 16, GL_DEPTH_COMPONENT24, 0, false)) {
            glBindFramebuffer(GL_FRAMEBUFFER, scene.fbo);
            glFramebufferTexture2D(GL_FRAMEBUFFER, GL_COLOR_ATTACHMENT1, GL_TEXTURE_2D, auxiliary.texture, 0);
            glDrawBuffers(new int[] {GL_COLOR_ATTACHMENT0, GL_COLOR_ATTACHMENT1});
            glClearBufferfv(GL_COLOR, 1, new float[] {.8f, .1f, .6f, .7f});
            check(glCheckFramebufferStatus(GL_FRAMEBUFFER) == GL_FRAMEBUFFER_COMPLETE, "MRT framebuffer complete");
            byte[] primary = read(scene), extra = read(auxiliary);
            glColorMaski(0, false, true, false, true); glColorMaski(1, true, false, true, false);
            glEnablei(GL_BLEND, 0); glDisablei(GL_BLEND, 1);
            int[] before = snapshot();
            check(!filter.captureDepth(scene.fbo, scene.width, scene.height), "MRT depth capture skipped");
            check(Arrays.equals(before, snapshot()), "MRT capture rejection preserves state");
            glDrawBuffers(new int[] {GL_COLOR_ATTACHMENT0});
            check(filter.captureDepth(scene.fbo, scene.width, scene.height), "single output permits depth capture");
            glDrawBuffers(new int[] {GL_COLOR_ATTACHMENT0, GL_COLOR_ATTACHMENT1});
            before = snapshot();
            check(!filter.render(scene.fbo, scene.width, scene.height, INVERSE, ORIGIN, .43f, 1, 0xffffff),
                    "MRT enabled after depth capture prevents final color blit");
            check(Arrays.equals(before, snapshot()), "MRT render rejection preserves state");
            check(Arrays.equals(primary, read(scene)), "MRT primary attachment unchanged");
            check(Arrays.equals(extra, read(auxiliary)), "MRT auxiliary attachment unchanged");
            invariants(scene);
        }
        normal();
    }
    private static byte[] read(Scene scene) {
        int previous = glGetInteger(GL_READ_FRAMEBUFFER_BINDING);
        glBindFramebuffer(GL_READ_FRAMEBUFFER, scene.fbo);
        ByteBuffer result = ByteBuffer.allocateDirect(scene.pixels.length);
        glReadPixels(0, 0, scene.width, scene.height, GL_RGBA, GL_UNSIGNED_BYTE, result);
        byte[] bytes = new byte[result.remaining()]; result.get(bytes);
        glBindFramebuffer(GL_READ_FRAMEBUFFER, previous); return bytes;
    }
    private static float[] depth(Scene scene) {
        int previous = glGetInteger(GL_READ_FRAMEBUFFER_BINDING);
        glBindFramebuffer(GL_READ_FRAMEBUFFER, scene.fbo);
        float[] result = new float[scene.depths.length];
        glReadPixels(0, 0, scene.width, scene.height, GL_DEPTH_COMPONENT, GL_FLOAT, result);
        glBindFramebuffer(GL_READ_FRAMEBUFFER, previous); return result;
    }
    private static void invariants(Scene scene) {
        byte[] pixels = read(scene);
        for (int i = 3; i < pixels.length; i += 4) check(pixels[i] == scene.pixels[i], "scene alpha unchanged");
        float[] depth = depth(scene);
        for (int i = 0; i < depth.length; i++) check(Math.abs(depth[i] - scene.depths[i]) < .000001, "main depth unchanged");
        if (scene.stencil) {
            int previous = glGetInteger(GL_READ_FRAMEBUFFER_BINDING);
            glBindFramebuffer(GL_READ_FRAMEBUFFER, scene.fbo);
            int[] stencil = new int[scene.depths.length];
            glReadPixels(0, 0, scene.width, scene.height, GL_STENCIL_INDEX, GL_UNSIGNED_INT, stencil);
            for (int value : stencil) check(value == 3, "main stencil unchanged");
            glBindFramebuffer(GL_READ_FRAMEBUFFER, previous);
        }
    }
    private static int delta(byte[] output, Scene scene, int index) { return (output[index] & 255) - (scene.pixels[index] & 255); }
    private static boolean samplers() { return GL.getCapabilities().OpenGL33 || GL.getCapabilities().GL_ARB_sampler_objects; }
    private static void check(boolean value, String message) { checks++; if (!value) throw new AssertionError(message); }
    private static int program(String vertex, String fragment) {
        int p = glCreateProgram();
        for (int type : new int[] {GL_VERTEX_SHADER, GL_FRAGMENT_SHADER}) {
            int shader = glCreateShader(type); glShaderSource(shader, type == GL_VERTEX_SHADER ? vertex : fragment);
            glCompileShader(shader); check(glGetShaderi(shader, GL_COMPILE_STATUS) != 0, "sentinel shader compiles");
            glAttachShader(p, shader); glDeleteShader(shader);
        }
        glLinkProgram(p); check(glGetProgrami(p, GL_LINK_STATUS) != 0, "sentinel shader links"); return p;
    }
}
