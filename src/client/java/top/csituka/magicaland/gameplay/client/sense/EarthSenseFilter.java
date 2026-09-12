package top.csituka.magicaland.gameplay.client.sense;

import java.nio.ByteBuffer;
import java.util.List;
import org.lwjgl.opengl.GL;
import org.lwjgl.opengl.GL33C;
import org.lwjgl.opengl.GL40C;
import org.lwjgl.opengl.ARBDrawBuffersBlend;
import org.lwjgl.system.MemoryStack;
import static org.lwjgl.opengl.GL32C.*;

/** Own color-only targets; no Minecraft post processor or cached RenderSystem state is changed. */
public final class EarthSenseFilter implements AutoCloseable {
    private int program, blobProgram, vao, amountUniform, blurUniform;
    private int blobRect, blobColor, blobActivity, blobPulse;
    private Target source, output;

    public EarthSenseFilter(String vertex, String fragment) {
        this(vertex, fragment, null, null);
    }

    public EarthSenseFilter(String vertex, String fragment, String blobVertex, String blobFragment) {
        if (!GL.getCapabilities().OpenGL32) throw new IllegalStateException("OpenGL 3.2 unavailable");
        try (State ignored = new State()) {
            int vs = 0, fs = 0;
            try {
                vs = shader(GL_VERTEX_SHADER, vertex);
                fs = shader(GL_FRAGMENT_SHADER, fragment);
                program = glCreateProgram();
                glAttachShader(program, vs);
                glAttachShader(program, fs);
                glLinkProgram(program);
                if (glGetProgrami(program, GL_LINK_STATUS) == GL_FALSE)
                    throw new IllegalStateException("Earth sense shader link: " + glGetProgramInfoLog(program));
                amountUniform = glGetUniformLocation(program, "Amount");
                blurUniform = glGetUniformLocation(program, "Blur");
                int sampler = glGetUniformLocation(program, "Scene");
                if (amountUniform < 0 || sampler < 0) throw new IllegalStateException("Missing filter uniforms");
                glUseProgram(program);
                glUniform1i(sampler, 0);
                if (blobVertex != null && blobFragment != null) {
                    blobProgram = link(blobVertex, blobFragment);
                    blobRect = uniform(blobProgram, "Rect");
                    blobColor = uniform(blobProgram, "Color");
                    blobActivity = uniform(blobProgram, "Activity");
                    blobPulse = uniform(blobProgram, "Pulse");
                }
                vao = glGenVertexArrays();
                checkError();
            } catch (RuntimeException error) {
                close();
                throw error;
            } finally {
                if (vs != 0) glDeleteShader(vs);
                if (fs != 0) glDeleteShader(fs);
            }
        }
    }

    public boolean render(int framebuffer, int width, int height, float amount) {
        return render(framebuffer, width, height, amount, 0, List.of());
    }

    public boolean render(int framebuffer, int width, int height, float amount, float blur,
                          List<EarthSenseVisualMath.Blob> blobs) {
        amount = EarthSenseVisualMath.clamp(amount);
        blur = EarthSenseVisualMath.clamp(blur);
        if (amount == 0 && blur == 0 && blobs.isEmpty() || framebuffer <= 0 || width <= 0 || height <= 0) return false;
        if (program == 0) throw new IllegalStateException("Filter is closed");
        if (glGetInteger(GL_DRAW_FRAMEBUFFER_BINDING) != framebuffer) return false;
        try (State ignored = new State()) {
            checkError();
            if (width > glGetInteger(GL_MAX_TEXTURE_SIZE) || height > glGetInteger(GL_MAX_TEXTURE_SIZE))
                throw new IllegalArgumentException("Filter framebuffer exceeds texture limit");
            if (glCheckFramebufferStatus(GL_DRAW_FRAMEBUFFER) != GL_FRAMEBUFFER_COMPLETE
                    || glGetInteger(GL_DRAW_BUFFER0) != GL_COLOR_ATTACHMENT0 || glGetInteger(GL_SAMPLES) != 0)
                return false;
            for (int i = 1; i < glGetInteger(GL_MAX_DRAW_BUFFERS); i++)
                if (glGetInteger(GL_DRAW_BUFFER0 + i) != GL_NONE) return false;
            int encoding = glGetFramebufferAttachmentParameteri(GL_DRAW_FRAMEBUFFER, GL_COLOR_ATTACHMENT0,
                    GL_FRAMEBUFFER_ATTACHMENT_COLOR_ENCODING);
            if (encoding != GL_LINEAR || glGetFramebufferAttachmentParameteri(GL_DRAW_FRAMEBUFFER,
                    GL_COLOR_ATTACHMENT0, GL_FRAMEBUFFER_ATTACHMENT_RED_SIZE) != 8) return false;
            prepare(width, height);
            glDisable(GL_SCISSOR_TEST);
            glDisablei(GL_BLEND, 0);
            glDisable(GL_DEPTH_TEST);
            glDisable(GL_STENCIL_TEST);
            glDisable(GL_CULL_FACE);
            glDisable(GL_RASTERIZER_DISCARD);
            glDisable(GL_FRAMEBUFFER_SRGB);
            glDisable(GL_DITHER);
            glDisable(GL_COLOR_LOGIC_OP);
            for (int i = 0; i < glGetInteger(GL_MAX_CLIP_DISTANCES); i++) glDisable(GL_CLIP_DISTANCE0 + i);
            glColorMaski(0, true, true, true, true);
            glDepthMask(false);
            glPolygonMode(GL_FRONT_AND_BACK, GL_FILL);
            glBindFramebuffer(GL_READ_FRAMEBUFFER, framebuffer);
            if (glGetInteger(GL_READ_BUFFER) != GL_COLOR_ATTACHMENT0) return false;
            glBindFramebuffer(GL_DRAW_FRAMEBUFFER, source.framebuffer);
            glBlitFramebuffer(0, 0, width, height, 0, 0, width, height, GL_COLOR_BUFFER_BIT, GL_NEAREST);
            glBindFramebuffer(GL_DRAW_FRAMEBUFFER, output.framebuffer);
            glViewport(0, 0, width, height);
            glUseProgram(program);
            glUniform1f(amountUniform, amount);
            if (blurUniform >= 0) glUniform1f(blurUniform, blur * 2 * Math.max(.5f, Math.min(2, height / 720f)));
            glActiveTexture(GL_TEXTURE0);
            if (samplersAvailable()) GL33C.glBindSampler(0, 0);
            glBindTexture(GL_TEXTURE_2D, source.texture);
            glBindVertexArray(vao);
            glDrawArrays(GL_TRIANGLES, 0, 3);
            if (blobProgram != 0 && !blobs.isEmpty()) {
                glUseProgram(blobProgram);
                glEnablei(GL_BLEND, 0);
                blendEquation(GL_FUNC_ADD, GL_FUNC_ADD);
                blendFunction(GL_ONE, GL_ONE_MINUS_SRC_ALPHA, GL_ZERO, GL_ONE);
                for (int i = 0; i < Math.min(32, blobs.size()); i++) {
                    var blob = blobs.get(i);
                    glUniform4f(blobRect, blob.x(), blob.y(), blob.radiusX(), blob.radiusY());
                    glUniform4f(blobColor, blob.red(), blob.green(), blob.blue(), blob.alpha());
                    glUniform1f(blobActivity, blob.activity());
                    glUniform1f(blobPulse, blob.pulse());
                    glDrawArrays(GL_TRIANGLE_STRIP, 0, 4);
                }
            }
            checkError();
            // The main scene is first touched here, after a complete offscreen pass.
            glBindFramebuffer(GL_READ_FRAMEBUFFER, output.framebuffer);
            glBindFramebuffer(GL_DRAW_FRAMEBUFFER, framebuffer);
            glBlitFramebuffer(0, 0, width, height, 0, 0, width, height, GL_COLOR_BUFFER_BIT, GL_NEAREST);
            checkError();
            return true;
        }
    }

    private void prepare(int width, int height) {
        if (source != null && source.width == width && source.height == height) return;
        Target nextSource = null, nextOutput = null;
        try {
            nextSource = new Target(width, height);
            nextOutput = new Target(width, height);
        } catch (RuntimeException error) {
            if (nextSource != null) nextSource.close();
            if (nextOutput != null) nextOutput.close();
            throw error;
        }
        if (source != null) source.close();
        if (output != null) output.close();
        source = nextSource;
        output = nextOutput;
    }

    @Override public void close() {
        if (source != null) source.close();
        if (output != null) output.close();
        source = output = null;
        if (vao != 0) glDeleteVertexArrays(vao);
        if (program != 0) glDeleteProgram(program);
        if (blobProgram != 0) glDeleteProgram(blobProgram);
        vao = program = blobProgram = 0;
    }

    private static boolean samplersAvailable() {
        var caps = GL.getCapabilities();
        return (caps.OpenGL33 || caps.GL_ARB_sampler_objects) && caps.glBindSampler != 0;
    }

    private static boolean indexedBlend() {
        var caps = GL.getCapabilities();
        return caps.OpenGL40 || caps.GL_ARB_draw_buffers_blend;
    }

    private static int blendState(int name) {
        return indexedBlend() ? glGetIntegeri(name, 0) : glGetInteger(name);
    }

    private static void blendFunction(int srcRgb, int dstRgb, int srcAlpha, int dstAlpha) {
        if (GL.getCapabilities().OpenGL40) GL40C.glBlendFuncSeparatei(0, srcRgb, dstRgb, srcAlpha, dstAlpha);
        else if (GL.getCapabilities().GL_ARB_draw_buffers_blend)
            ARBDrawBuffersBlend.glBlendFuncSeparateiARB(0, srcRgb, dstRgb, srcAlpha, dstAlpha);
        else glBlendFuncSeparate(srcRgb, dstRgb, srcAlpha, dstAlpha);
    }

    private static void blendEquation(int rgb, int alpha) {
        if (GL.getCapabilities().OpenGL40) GL40C.glBlendEquationSeparatei(0, rgb, alpha);
        else if (GL.getCapabilities().GL_ARB_draw_buffers_blend)
            ARBDrawBuffersBlend.glBlendEquationSeparateiARB(0, rgb, alpha);
        else glBlendEquationSeparate(rgb, alpha);
    }

    private static int uniform(int program, String name) {
        int location = glGetUniformLocation(program, name);
        if (location < 0) throw new IllegalStateException("Missing color cloud uniform: " + name);
        return location;
    }

    private static int link(String vertex, String fragment) {
        int vs = 0, fs = 0, program = 0;
        try {
            vs = shader(GL_VERTEX_SHADER, vertex); fs = shader(GL_FRAGMENT_SHADER, fragment);
            program = glCreateProgram(); glAttachShader(program, vs); glAttachShader(program, fs); glLinkProgram(program);
            if (glGetProgrami(program, GL_LINK_STATUS) == GL_FALSE) throw new IllegalStateException(glGetProgramInfoLog(program));
            return program;
        } catch (RuntimeException error) { if (program != 0) glDeleteProgram(program); throw error; }
        finally { if (vs != 0) glDeleteShader(vs); if (fs != 0) glDeleteShader(fs); }
    }

    private static int shader(int type, String text) {
        int shader = glCreateShader(type);
        glShaderSource(shader, text);
        glCompileShader(shader);
        if (glGetShaderi(shader, GL_COMPILE_STATUS) == GL_FALSE) {
            String log = glGetShaderInfoLog(shader);
            glDeleteShader(shader);
            throw new IllegalStateException("Earth sense shader compilation: " + log);
        }
        return shader;
    }

    private static void checkError() {
        int error = glGetError();
        if (error != GL_NO_ERROR) throw new IllegalStateException("Earth sense OpenGL error: " + error);
    }

    private static final class Target implements AutoCloseable {
        final int width, height;
        int framebuffer, texture;
        Target(int width, int height) {
            this.width = width;
            this.height = height;
            try {
                texture = glGenTextures();
                glBindBuffer(GL_PIXEL_UNPACK_BUFFER, 0);
                glActiveTexture(GL_TEXTURE0);
                glBindTexture(GL_TEXTURE_2D, texture);
                glTexParameteri(GL_TEXTURE_2D, GL_TEXTURE_MIN_FILTER, GL_LINEAR);
                glTexParameteri(GL_TEXTURE_2D, GL_TEXTURE_MAG_FILTER, GL_LINEAR);
                glTexParameteri(GL_TEXTURE_2D, GL_TEXTURE_WRAP_S, GL_CLAMP_TO_EDGE);
                glTexParameteri(GL_TEXTURE_2D, GL_TEXTURE_WRAP_T, GL_CLAMP_TO_EDGE);
                glTexImage2D(GL_TEXTURE_2D, 0, GL_RGBA8, width, height, 0, GL_RGBA, GL_UNSIGNED_BYTE, (ByteBuffer) null);
                framebuffer = glGenFramebuffers();
                glBindFramebuffer(GL_FRAMEBUFFER, framebuffer);
                glFramebufferTexture2D(GL_FRAMEBUFFER, GL_COLOR_ATTACHMENT0, GL_TEXTURE_2D, texture, 0);
                if (glCheckFramebufferStatus(GL_FRAMEBUFFER) != GL_FRAMEBUFFER_COMPLETE)
                    throw new IllegalStateException("Incomplete earth sense framebuffer");
                checkError();
            } catch (RuntimeException error) {
                close();
                throw error;
            }
        }
        @Override public void close() {
            if (framebuffer != 0) glDeleteFramebuffers(framebuffer);
            if (texture != 0) glDeleteTextures(texture);
            framebuffer = texture = 0;
        }
    }

    private static final class State implements AutoCloseable {
        final int read = glGetInteger(GL_READ_FRAMEBUFFER_BINDING), draw = glGetInteger(GL_DRAW_FRAMEBUFFER_BINDING);
        final int program = glGetInteger(GL_CURRENT_PROGRAM), vao = glGetInteger(GL_VERTEX_ARRAY_BINDING);
        final int activeTexture = glGetInteger(GL_ACTIVE_TEXTURE), texture0;
        final boolean samplers = samplersAvailable();
        final int sampler0 = samplers ? glGetIntegeri(GL33C.GL_SAMPLER_BINDING, 0) : 0;
        final int srcRgb = blendState(GL_BLEND_SRC_RGB), dstRgb = blendState(GL_BLEND_DST_RGB);
        final int srcAlpha = blendState(GL_BLEND_SRC_ALPHA), dstAlpha = blendState(GL_BLEND_DST_ALPHA);
        final int equationRgb = blendState(GL_BLEND_EQUATION_RGB), equationAlpha = blendState(GL_BLEND_EQUATION_ALPHA);
        final int unpackBuffer = glGetInteger(GL_PIXEL_UNPACK_BUFFER_BINDING);
        final int[] viewport = new int[4], polygon = new int[2];
        final boolean[] color = new boolean[4];
        final boolean depthMask = glGetBoolean(GL_DEPTH_WRITEMASK), blend = glIsEnabledi(GL_BLEND, 0);
        final int[] capabilities = {GL_SCISSOR_TEST, GL_DEPTH_TEST, GL_STENCIL_TEST, GL_CULL_FACE,
                GL_RASTERIZER_DISCARD, GL_FRAMEBUFFER_SRGB, GL_DITHER, GL_COLOR_LOGIC_OP};
        final boolean[] enabled = new boolean[capabilities.length];
        final boolean[] clips = new boolean[glGetInteger(GL_MAX_CLIP_DISTANCES)];
        State() {
            glGetIntegerv(GL_VIEWPORT, viewport);
            glGetIntegerv(GL_POLYGON_MODE, polygon);
            try (MemoryStack stack = MemoryStack.stackPush()) {
                ByteBuffer mask = stack.malloc(4);
                glGetBooleani_v(GL_COLOR_WRITEMASK, 0, mask);
                for (int i = 0; i < 4; i++) color[i] = mask.get(i) != 0;
            }
            for (int i = 0; i < capabilities.length; i++) enabled[i] = glIsEnabled(capabilities[i]);
            for (int i = 0; i < clips.length; i++) clips[i] = glIsEnabled(GL_CLIP_DISTANCE0 + i);
            glActiveTexture(GL_TEXTURE0);
            texture0 = glGetInteger(GL_TEXTURE_BINDING_2D);
            glActiveTexture(activeTexture);
        }
        @Override public void close() {
            glBindFramebuffer(GL_READ_FRAMEBUFFER, read);
            glBindFramebuffer(GL_DRAW_FRAMEBUFFER, draw);
            glUseProgram(program);
            glBindVertexArray(vao);
            glActiveTexture(GL_TEXTURE0);
            glBindTexture(GL_TEXTURE_2D, texture0);
            if (samplers) GL33C.glBindSampler(0, sampler0);
            glActiveTexture(activeTexture);
            glBindBuffer(GL_PIXEL_UNPACK_BUFFER, unpackBuffer);
            glViewport(viewport[0], viewport[1], viewport[2], viewport[3]);
            glColorMaski(0, color[0], color[1], color[2], color[3]);
            glDepthMask(depthMask);
            blendFunction(srcRgb, dstRgb, srcAlpha, dstAlpha);
            blendEquation(equationRgb, equationAlpha);
            if (blend) glEnablei(GL_BLEND, 0); else glDisablei(GL_BLEND, 0);
            glPolygonMode(GL_FRONT_AND_BACK, polygon[0]);
            for (int i = 0; i < capabilities.length; i++) {
                if (enabled[i]) glEnable(capabilities[i]); else glDisable(capabilities[i]);
            }
            for (int i = 0; i < clips.length; i++) {
                if (clips[i]) glEnable(GL_CLIP_DISTANCE0 + i); else glDisable(GL_CLIP_DISTANCE0 + i);
            }
        }
    }
}
