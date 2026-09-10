package top.csituka.magicaland.gameplay.client.sense;

import java.nio.ByteBuffer;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Arrays;
import java.util.List;
import org.joml.Matrix4f;
import org.lwjgl.opengl.GL;
import org.lwjgl.system.Configuration;
import static org.lwjgl.glfw.GLFW.*;
import static org.lwjgl.opengl.GL33C.*;

public final class EarthSenseFilterGpuTest {
    private static int checks;
    private record Scene(int fbo, int texture, int depth, int width, int height, byte[] pixels) implements AutoCloseable {
        @Override public void close() { glDeleteFramebuffers(fbo); glDeleteTextures(texture); glDeleteRenderbuffers(depth); }
    }
    public static void main(String[] args) throws Exception {
        boolean gl32 = args.length < 2 || !args[1].equals("native");
        if (gl32) {
            Configuration.OPENGL_MAXVERSION.set("3.2");
            Configuration.OPENGL_EXTENSION_FILTER.set("GL_ARB_sampler_objects");
        }
        if (!glfwInit()) throw new AssertionError("GLFW init");
        glfwWindowHint(GLFW_VISIBLE, GLFW_FALSE);
        glfwWindowHint(GLFW_CONTEXT_VERSION_MAJOR, 3);
        glfwWindowHint(GLFW_CONTEXT_VERSION_MINOR, 2);
        glfwWindowHint(GLFW_OPENGL_FORWARD_COMPAT, GLFW_TRUE);
        glfwWindowHint(GLFW_OPENGL_PROFILE, GLFW_OPENGL_CORE_PROFILE);
        long window = glfwCreateWindow(64, 64, "Earth sense offscreen regression", 0, 0);
        if (window == 0) throw new AssertionError("Hidden context");
        glfwMakeContextCurrent(window);
        GL.createCapabilities();
        if (gl32) check(GL.getCapabilities().OpenGL32 && !GL.getCapabilities().OpenGL33
                && !GL.getCapabilities().GL_ARB_sampler_objects, "strict 3.2 capability table without sampler objects");
        String vertex = Files.readString(Path.of(args[0], "earth_sense.vsh"));
        String fragment = Files.readString(Path.of(args[0], "earth_sense.fsh"));
        String blobVertex = Files.readString(Path.of(args[0], "earth_sense_blob.vsh"));
        String blobFragment = Files.readString(Path.of(args[0], "earth_sense_blob.fsh"));
        int spareVao = glGenVertexArrays(), texture = glGenTextures(), sampler = samplers() ? glGenSamplers() : 0, unpack = glGenBuffers();
        int spareProgram = program(vertex, fragment);
        try (EarthSenseFilter filter = new EarthSenseFilter(vertex, fragment, blobVertex, blobFragment)) {
            for (int[] size : new int[][] {{32, 17}, {1, 1}, {63, 47}, {7, 5}}) {
                for (float amount : new float[] {0, .04f, .4f, .8f, 1}) {
                    normalState();
                    try (Scene scene = scene(size[0], size[1]); Scene other = scene(3, 3)) {
                        glBindFramebuffer(GL_DRAW_FRAMEBUFFER, scene.fbo);
                        glBindFramebuffer(GL_READ_FRAMEBUFFER, other.fbo);
                        glBindVertexArray(spareVao);
                        glUseProgram(spareProgram);
                        if (samplers()) glBindSampler(0, sampler);
                        glActiveTexture(GL_TEXTURE0); glBindTexture(GL_TEXTURE_2D, texture);
                        glActiveTexture(GL_TEXTURE5); glBindTexture(GL_TEXTURE_2D, other.texture);
                        glBindBuffer(GL_PIXEL_UNPACK_BUFFER, unpack);
                        glBufferData(GL_PIXEL_UNPACK_BUFFER, 16, GL_STATIC_DRAW);
                        glViewport(2, 3, 9, 11);
                        glScissor(1, 1, 2, 2);
                        glEnable(GL_SCISSOR_TEST); glEnable(GL_BLEND); glEnable(GL_DEPTH_TEST);
                        glEnable(GL_STENCIL_TEST); glEnable(GL_CULL_FACE); glEnable(GL_RASTERIZER_DISCARD);
                        glEnable(GL_FRAMEBUFFER_SRGB); glEnable(GL_DITHER);
                        glEnable(GL_CLIP_DISTANCE0); glEnable(GL_COLOR_LOGIC_OP); glLogicOp(GL_XOR);
                        glDepthFunc(GL_GREATER); glDepthMask(true);
                        glBlendFuncSeparate(GL_ONE, GL_DST_COLOR, GL_ONE_MINUS_DST_ALPHA, GL_SRC_ALPHA);
                        glColorMask(false, true, false, true);
                        glPolygonMode(GL_FRONT_AND_BACK, GL_LINE);
                        int[] before = snapshot();
                        boolean drawn = filter.render(scene.fbo, scene.width, scene.height, amount);
                        check(drawn == (amount > 0), "zero is a no-op, positive amount draws");
                        check(Arrays.equals(before, snapshot()), "all touched and neighbouring GL states restored");
                        pixels(scene, amount);
                        depth(scene);
                        check(glGetError() == GL_NO_ERROR, "no GL errors");
                    }
                }
            }
            normalState();
            try (Scene scene = scene(128, 96)) {
                byte[] beforeBlur = read(scene);
                check(filter.render(scene.fbo, scene.width, scene.height, 0, .35f, List.of()), "default micro blur operates without desaturation");
                byte[] afterBlur = read(scene);
                check(variation(afterBlur) < variation(beforeBlur) * .97, "micro blur reduces high-frequency contrast");
                for (int i = 3; i < afterBlur.length; i += 4) check(afterBlur[i] == beforeBlur[i], "blur preserves scene alpha");
                depth(scene);
                for (int kind = 0; kind < 4; kind++) {
                    solid(scene);
                    int rgb = EarthSenseVisualMath.kindColor(kind);
                    var blob = new EarthSenseVisualMath.Blob(.5f, .5f, .25f, .35f,
                            ((rgb >> 16) & 255) / 255f, ((rgb >> 8) & 255) / 255f, (rgb & 255) / 255f, .9f, .55f, 1);
                    int[] saved = snapshot();
                    check(filter.render(scene.fbo, scene.width, scene.height, .8f, .35f, List.of(blob)), "world cloud composites after gray blur");
                    check(Arrays.equals(saved, snapshot()), "cloud blend/shader state restored");
                    byte[] pixels = read(scene);
                    int center = (48 * 128 + 64) * 4, corner = (78 * 128 + 94) * 4;
                    for (int c = 0; c < 3; c++) {
                        int expected = Math.round(51 * .45f + ((rgb >> (16 - c * 8)) & 255) * .55f);
                        check(Math.abs((pixels[center + c] & 255) - expected) < 4, "cloud keeps its requested RGB over gray world");
                        check((pixels[corner + c] & 255) == 51, "transparent rectangle corners do not become a box");
                    }
                    for (int i = 3; i < pixels.length; i += 4) check((pixels[i] & 255) == 110, "cloud never changes scene alpha");
                    int shoulder = (48 * 128 + 82) * 4;
                    int activeDifference = Math.abs((pixels[shoulder] & 255) - 51)
                            + Math.abs((pixels[shoulder + 1] & 255) - 51) + Math.abs((pixels[shoulder + 2] & 255) - 51);
                    solid(scene);
                    var idle = new EarthSenseVisualMath.Blob(.5f, .5f, .25f, .35f, blob.red(), blob.green(), blob.blue(), .12f, .55f, 1);
                    filter.render(scene.fbo, scene.width, scene.height, .8f, .35f, List.of(idle));
                    byte[] idlePixels = read(scene);
                    int idleDifference = Math.abs((idlePixels[shoulder] & 255) - 51)
                            + Math.abs((idlePixels[shoulder + 1] & 255) - 51) + Math.abs((idlePixels[shoulder + 2] & 255) - 51);
                    check(activeDifference > idleDifference, "activity makes the coarse edge clearer but still soft");
                    depth(scene);
                }
                var projection = new Matrix4f().perspective((float) Math.toRadians(70), 128f / 96, .05f, 100);
                var view = new Matrix4f();
                for (int angle : new int[] {0, 45, 60, 90, 135, 180, 225, 270, 315}) {
                    solid(scene);
                    double radians = Math.toRadians(angle), x = Math.sin(radians) * 5, z = -Math.cos(radians) * 5;
                    var position = EarthSenseVisualMath.place(projection, view, x, 0, z, 1, 2);
                    var cloud = new EarthSenseVisualMath.Cloud(new EarthSenseVisualMath.Target(1, 0, x, 0, z, 1, 2, .9f, 4, 0), 1, 0, 0);
                    var blob = EarthSenseVisualMath.blob(position, cloud, 1);
                    int[] saved = snapshot();
                    check(filter.render(scene.fbo, scene.width, scene.height, .8f, .35f, List.of(blob)), "same shader draws world and edge cloud " + angle);
                    check(Arrays.equals(saved, snapshot()), "edge cloud restores GPU state " + angle);
                    byte[] drawn = read(scene);
                    int cx = Math.min(scene.width - 1, Math.round(blob.x() * scene.width));
                    int cy = Math.min(scene.height - 1, Math.round(blob.y() * scene.height));
                    int index = (cy * scene.width + cx) * 4;
                    check((drawn[index] & 255) > 75 && (drawn[index + 1] & 255) < (drawn[index] & 255), "edge cloud is visible, not clipped away " + angle);
                    if (angle != 0) {
                        int middle = (scene.height / 2 * scene.width + scene.width / 2) * 4;
                        check((drawn[middle] & 255) == 51 && (drawn[middle + 1] & 255) == 51, "no second center/HUD marker " + angle);
                    }
                    for (int i = 3; i < drawn.length; i += 4) check((drawn[i] & 255) == 110, "edge blending retains source alpha");
                    depth(scene);
                }
            }
            normalState();
            try (Scene scene = scene(19, 13); Scene other = scene(2, 2)) {
                glBindFramebuffer(GL_FRAMEBUFFER, other.fbo);
                int[] before = snapshot();
                check(!filter.render(scene.fbo, scene.width, scene.height, .8f), "non-main bound target skipped");
                check(Arrays.equals(before, snapshot()), "foreign target bindings retained");
                pixels(scene, 0);
                glBindFramebuffer(GL_FRAMEBUFFER, scene.fbo);
                before = snapshot();
                boolean failed = false;
                try { filter.render(scene.fbo, glGetInteger(GL_MAX_TEXTURE_SIZE) + 1, 10, .8f); }
                catch (IllegalArgumentException expected) { failed = true; }
                check(failed, "allocation bound rejected before draw");
                check(Arrays.equals(before, snapshot()), "allocation rejection state restored");
                pixels(scene, 0);
                glEnable(-1);
                failed = false;
                try { filter.render(scene.fbo, scene.width, scene.height, .8f); }
                catch (IllegalStateException expected) { failed = true; }
                check(failed, "pre-existing GPU failure disables pass before scene mutation");
                pixels(scene, 0);
                before = snapshot();
                failed = false;
                glUseProgram(spareProgram);
                before = snapshot();
                try (var broken = new EarthSenseFilter(vertex, "#version 150\nthis is invalid")) {}
                catch (IllegalStateException expected) { failed = true; }
                check(failed, "bad resource shader fails safely");
                check(Arrays.equals(before, snapshot()), "shader failure state retained");
                pixels(scene, 0);
                try (var reloaded = new EarthSenseFilter(vertex, fragment)) {
                    check(reloaded.render(scene.fbo, scene.width, scene.height, .8f), "fresh resource shader recovers");
                    pixels(scene, .8f);
                    depth(scene);
                }
                // A HUD layer drawn after the filter keeps its original RGB.
                glBindFramebuffer(GL_FRAMEBUFFER, scene.fbo);
                glEnable(GL_SCISSOR_TEST); glScissor(2, 2, 1, 1);
                glClearColor(0, 1, 0, 1); glClear(GL_COLOR_BUFFER_BIT); glDisable(GL_SCISSOR_TEST);
                ByteBuffer hud = ByteBuffer.allocateDirect(4);
                glReadPixels(2, 2, 1, 1, GL_RGBA, GL_UNSIGNED_BYTE, hud);
                check(hud.get(0) == 0 && (hud.get(1) & 255) == 255 && hud.get(2) == 0, "later HUD is not desaturated");
            }
            check(glGetError() == GL_NO_ERROR, "final GL error clear");
            System.out.println("PASS earth sense actual filter/cloud GPU: " + checks + "; requested GL3.2, capabilities 3.3="
                    + GL.getCapabilities().OpenGL33 + ", sampler=" + samplers() + "; driver=" + glGetString(GL_VERSION));
        } finally {
            glDeleteVertexArrays(spareVao); glDeleteTextures(texture); if (sampler != 0) glDeleteSamplers(sampler); glDeleteBuffers(unpack);
            glUseProgram(0); glDeleteProgram(spareProgram);
            glfwDestroyWindow(window); glfwTerminate();
        }
    }
    private static void normalState() {
        glBindBuffer(GL_PIXEL_UNPACK_BUFFER, 0);
        glUseProgram(0); glBindVertexArray(0); glActiveTexture(GL_TEXTURE0); if (samplers()) glBindSampler(0, 0);
        for (int cap : new int[] {GL_SCISSOR_TEST, GL_BLEND, GL_DEPTH_TEST, GL_STENCIL_TEST, GL_CULL_FACE,
                GL_RASTERIZER_DISCARD, GL_FRAMEBUFFER_SRGB, GL_CLIP_DISTANCE0, GL_COLOR_LOGIC_OP}) glDisable(cap);
        glColorMask(true, true, true, true); glDepthMask(true); glStencilMask(255);
        glPolygonMode(GL_FRONT_AND_BACK, GL_FILL);
    }
    private static Scene scene(int w, int h) {
        byte[] pixels = new byte[w * h * 4];
        for (int i = 0; i < pixels.length; i += 4) {
            pixels[i] = (byte) ((i * 31) % 256); pixels[i + 1] = (byte) ((i * 11 + 62) % 256);
            pixels[i + 2] = (byte) ((i * 17 + 121) % 256); pixels[i + 3] = (byte) ((i * 3 + 91) % 256);
        }
        if (pixels.length > 4) Arrays.fill(pixels, 0, 4, (byte) 0);
        int texture = glGenTextures(); glBindTexture(GL_TEXTURE_2D, texture);
        glTexParameteri(GL_TEXTURE_2D, GL_TEXTURE_MIN_FILTER, GL_NEAREST); glTexParameteri(GL_TEXTURE_2D, GL_TEXTURE_MAG_FILTER, GL_NEAREST);
        ByteBuffer bytes = ByteBuffer.allocateDirect(pixels.length); bytes.put(pixels).flip();
        glTexImage2D(GL_TEXTURE_2D, 0, GL_RGBA8, w, h, 0, GL_RGBA, GL_UNSIGNED_BYTE, bytes);
        int depth = glGenRenderbuffers(); glBindRenderbuffer(GL_RENDERBUFFER, depth);
        glRenderbufferStorage(GL_RENDERBUFFER, GL_DEPTH24_STENCIL8, w, h);
        int fbo = glGenFramebuffers(); glBindFramebuffer(GL_FRAMEBUFFER, fbo);
        glFramebufferTexture2D(GL_FRAMEBUFFER, GL_COLOR_ATTACHMENT0, GL_TEXTURE_2D, texture, 0);
        glFramebufferRenderbuffer(GL_FRAMEBUFFER, GL_DEPTH_STENCIL_ATTACHMENT, GL_RENDERBUFFER, depth);
        check(glCheckFramebufferStatus(GL_FRAMEBUFFER) == GL_FRAMEBUFFER_COMPLETE, "test framebuffer complete");
        glClearDepth(.37); glClearStencil(3); glClear(GL_DEPTH_BUFFER_BIT | GL_STENCIL_BUFFER_BIT);
        return new Scene(fbo, texture, depth, w, h, pixels);
    }
    private static int[] snapshot() {
        var list = new java.util.ArrayList<Integer>();
        for (int name : new int[] {GL_READ_FRAMEBUFFER_BINDING, GL_DRAW_FRAMEBUFFER_BINDING, GL_CURRENT_PROGRAM,
                GL_VERTEX_ARRAY_BINDING, GL_ACTIVE_TEXTURE, GL_TEXTURE_BINDING_2D, GL_PIXEL_UNPACK_BUFFER_BINDING,
                GL_ARRAY_BUFFER_BINDING, GL_DEPTH_FUNC, GL_DEPTH_WRITEMASK, GL_BLEND_SRC_RGB, GL_BLEND_DST_RGB,
                GL_BLEND_SRC_ALPHA, GL_BLEND_DST_ALPHA, GL_BLEND_EQUATION_RGB, GL_BLEND_EQUATION_ALPHA, GL_READ_BUFFER, GL_DRAW_BUFFER0}) list.add(glGetInteger(name));
        int active = glGetInteger(GL_ACTIVE_TEXTURE); glActiveTexture(GL_TEXTURE0);
        list.add(glGetInteger(GL_TEXTURE_BINDING_2D)); list.add(samplers() ? glGetIntegeri(GL_SAMPLER_BINDING, 0) : 0); glActiveTexture(active);
        for (int name : new int[] {GL_VIEWPORT, GL_SCISSOR_BOX, GL_COLOR_WRITEMASK}) {
            int[] values = new int[4]; glGetIntegerv(name, values); for (int value : values) list.add(value);
        }
        int[] polygon = new int[2]; glGetIntegerv(GL_POLYGON_MODE, polygon); for (int p : polygon) list.add(p);
        for (int cap : new int[] {GL_SCISSOR_TEST, GL_BLEND, GL_DEPTH_TEST, GL_STENCIL_TEST, GL_CULL_FACE,
                GL_RASTERIZER_DISCARD, GL_FRAMEBUFFER_SRGB, GL_DITHER, GL_CLIP_DISTANCE0, GL_COLOR_LOGIC_OP}) list.add(glIsEnabled(cap) ? 1 : 0);
        return list.stream().mapToInt(Integer::intValue).toArray();
    }
    private static void pixels(Scene scene, float amount) {
        int read = glGetInteger(GL_READ_FRAMEBUFFER_BINDING); glBindFramebuffer(GL_READ_FRAMEBUFFER, scene.fbo);
        ByteBuffer result = ByteBuffer.allocateDirect(scene.pixels.length);
        glReadPixels(0, 0, scene.width, scene.height, GL_RGBA, GL_UNSIGNED_BYTE, result);
        for (int i = 0; i < scene.pixels.length; i += 4) {
            float[] expected = EarthSenseVisualMath.desaturate((scene.pixels[i] & 255) / 255f,
                    (scene.pixels[i + 1] & 255) / 255f, (scene.pixels[i + 2] & 255) / 255f, (scene.pixels[i + 3] & 255) / 255f, amount);
            for (int c = 0; c < 4; c++) check(Math.abs((result.get(i + c) & 255) - Math.round(expected[c] * 255)) <= 1,
                    "pixel/texture/alpha preserved at " + i + " channel " + c);
        }
        glBindFramebuffer(GL_READ_FRAMEBUFFER, read);
    }
    private static void depth(Scene scene) {
        int read = glGetInteger(GL_READ_FRAMEBUFFER_BINDING); glBindFramebuffer(GL_READ_FRAMEBUFFER, scene.fbo);
        float[] depth = new float[scene.width * scene.height];
        int[] stencil = new int[scene.width * scene.height];
        glReadPixels(0, 0, scene.width, scene.height, GL_DEPTH_COMPONENT, GL_FLOAT, depth);
        glReadPixels(0, 0, scene.width, scene.height, GL_STENCIL_INDEX, GL_UNSIGNED_INT, stencil);
        for (int i = 0; i < depth.length; i++) {
            check(Math.abs(depth[i] - .37f) < .00001f, "world depth unchanged");
            check(stencil[i] == 3, "world stencil unchanged");
        }
        glBindFramebuffer(GL_READ_FRAMEBUFFER, read);
    }
    private static void check(boolean value, String message) { checks++; if (!value) throw new AssertionError(message); }
    private static boolean samplers() { return GL.getCapabilities().OpenGL33 || GL.getCapabilities().GL_ARB_sampler_objects; }
    private static byte[] read(Scene scene) {
        glBindFramebuffer(GL_READ_FRAMEBUFFER, scene.fbo);
        ByteBuffer result = ByteBuffer.allocateDirect(scene.width * scene.height * 4);
        glReadPixels(0, 0, scene.width, scene.height, GL_RGBA, GL_UNSIGNED_BYTE, result);
        byte[] pixels = new byte[result.remaining()]; result.get(pixels); return pixels;
    }
    private static double variation(byte[] pixels) {
        double total = 0;
        for (int i = 4; i < pixels.length; i += 4) for (int c = 0; c < 3; c++) total += Math.abs((pixels[i + c] & 255) - (pixels[i - 4 + c] & 255));
        return total;
    }
    private static void solid(Scene scene) {
        normalState(); glBindFramebuffer(GL_FRAMEBUFFER, scene.fbo); glClearColor(.2f, .2f, .2f, 110 / 255f); glClear(GL_COLOR_BUFFER_BIT);
        for (int i = 0; i < scene.pixels.length; i += 4) { scene.pixels[i] = scene.pixels[i + 1] = scene.pixels[i + 2] = 51; scene.pixels[i + 3] = 110; }
    }
    private static int program(String vertex, String fragment) {
        int p = glCreateProgram();
        for (int type : new int[] {GL_VERTEX_SHADER, GL_FRAGMENT_SHADER}) {
            int shader = glCreateShader(type);
            glShaderSource(shader, type == GL_VERTEX_SHADER ? vertex : fragment); glCompileShader(shader);
            check(glGetShaderi(shader, GL_COMPILE_STATUS) != 0, "sentinel shader compiles");
            glAttachShader(p, shader); glDeleteShader(shader);
        }
        glLinkProgram(p); check(glGetProgrami(p, GL_LINK_STATUS) != 0, "sentinel shader links");
        return p;
    }
}
