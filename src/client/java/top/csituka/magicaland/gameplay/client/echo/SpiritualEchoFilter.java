package top.csituka.magicaland.gameplay.client.echo;

import java.nio.ByteBuffer;
import org.joml.Matrix4f;
import org.joml.Vector3f;
import org.lwjgl.opengl.GL;
import org.lwjgl.opengl.GL33C;
import org.lwjgl.system.MemoryStack;
import static org.lwjgl.opengl.GL32C.*;

/** Copies visible world depth before hand rendering; only the completed color pass touches the scene. */
public final class SpiritualEchoFilter implements AutoCloseable {
    private int program, vao, inverseUniform, originUniform, phaseUniform, occlusionUniform, colorUniform;
    private int previousOriginUniform, previousPhaseUniform, timingUniform;
    private int depthTexture, capturedFramebuffer, capturedWidth, capturedHeight;
    private boolean depthReady;
    private Target source, output;

    public SpiritualEchoFilter(String vertex, String fragment) {
        if (!GL.getCapabilities().OpenGL32) throw new IllegalStateException("OpenGL 3.2 unavailable");
        try (State ignored = new State()) {
            try {
                checkError();
                program = link(vertex, fragment);
                inverseUniform = uniform("InverseWorldClip");
                originUniform = uniform("PulseOrigin");
                phaseUniform = uniform("PhaseSeconds");
                previousOriginUniform = uniform("PreviousPulseOrigin");
                previousPhaseUniform = uniform("PreviousPhaseSeconds");
                timingUniform = uniform("PulseTiming");
                occlusionUniform = uniform("Occlusion");
                colorUniform = uniform("MagicColor");
                glUseProgram(program);
                glUniform1i(uniform("Scene"), 0);
                glUniform1i(uniform("WorldDepth"), 1);
                vao = glGenVertexArrays();
                checkError();
            } catch (RuntimeException error) {
                close();
                throw error;
            }
        }
    }

    public void beginFrame() { depthReady = false; }

    public boolean captureDepth(int framebuffer, int width, int height) {
        depthReady = false;
        if (!eligible(framebuffer, width, height)) return false;
        try (State ignored = new State()) {
            checkError();
            if (!compatible(width, height)) return false;
            prepare(width, height);
            glBindFramebuffer(GL_READ_FRAMEBUFFER, framebuffer);
            glActiveTexture(GL_TEXTURE1);
            glBindTexture(GL_TEXTURE_2D, depthTexture);
            // Copying (instead of blitting) permits depth24 and depth32f sources without changing their depth.
            glCopyTexSubImage2D(GL_TEXTURE_2D, 0, 0, 0, 0, 0, width, height);
            checkError();
            capturedFramebuffer = framebuffer;
            capturedWidth = width;
            capturedHeight = height;
            depthReady = true;
            return true;
        }
    }

    public boolean render(int framebuffer, int width, int height, Matrix4f inverseWorldClip,
                          Vector3f pulseOriginRelativeCamera, float phaseSeconds, float occlusion, int color) {
        return render(framebuffer, width, height, inverseWorldClip, pulseOriginRelativeCamera, phaseSeconds,
                null, -1, occlusion, color);
    }

    public boolean render(int framebuffer, int width, int height, Matrix4f inverseWorldClip,
                          Vector3f pulseOriginRelativeCamera, float phaseSeconds,
                          Vector3f previousOriginRelativeCamera, float previousPhaseSeconds, float occlusion, int color) {
        boolean ready = depthReady;
        depthReady = false;
        if (!ready || !eligible(framebuffer, width, height) || framebuffer != capturedFramebuffer
                || width != capturedWidth || height != capturedHeight || !Float.isFinite(phaseSeconds)
                || phaseSeconds < 0 || phaseSeconds >= SpiritualEchoTimeline.EXPANSION + SpiritualEchoTimeline.AFTERGLOW
                || !Float.isFinite(occlusion) || occlusion <= 0
                || inverseWorldClip == null || pulseOriginRelativeCamera == null) return false;
        float[] matrix = inverseWorldClip.get(new float[16]);
        for (float value : matrix) if (!Float.isFinite(value)) return false;
        var origin = pulseOriginRelativeCamera;
        if (!Float.isFinite(origin.x) || !Float.isFinite(origin.y) || !Float.isFinite(origin.z)) return false;
        var previous = previousOriginRelativeCamera;
        if (previous != null && (!Float.isFinite(previous.x) || !Float.isFinite(previous.y) || !Float.isFinite(previous.z)
                || !Float.isFinite(previousPhaseSeconds) || previousPhaseSeconds < 0
                || previousPhaseSeconds >= SpiritualEchoTimeline.EXPANSION + SpiritualEchoTimeline.AFTERGLOW)) return false;
        try (State ignored = new State()) {
            checkError();
            if (!compatible(width, height)) return false;
            glBindFramebuffer(GL_READ_FRAMEBUFFER, framebuffer);
            if (glGetInteger(GL_READ_BUFFER) != GL_COLOR_ATTACHMENT0) return false;
            for (int cap : State.CAPABILITIES) glDisable(cap);
            for (int i = 0; i < glGetInteger(GL_MAX_CLIP_DISTANCES); i++) glDisable(GL_CLIP_DISTANCE0 + i);
            glDisablei(GL_BLEND, 0);
            glColorMaski(0, true, true, true, true);
            glDepthMask(false);
            glPolygonMode(GL_FRONT_AND_BACK, GL_FILL);
            glBindFramebuffer(GL_DRAW_FRAMEBUFFER, source.framebuffer);
            glBlitFramebuffer(0, 0, width, height, 0, 0, width, height, GL_COLOR_BUFFER_BIT, GL_NEAREST);
            glBindFramebuffer(GL_DRAW_FRAMEBUFFER, output.framebuffer);
            glViewport(0, 0, width, height);
            glUseProgram(program);
            glUniformMatrix4fv(inverseUniform, false, matrix);
            glUniform3f(originUniform, origin.x, origin.y, origin.z);
            glUniform1f(phaseUniform, phaseSeconds);
            glUniform3f(previousOriginUniform, previous == null ? 0 : previous.x,
                    previous == null ? 0 : previous.y, previous == null ? 0 : previous.z);
            glUniform1f(previousPhaseUniform, previous == null ? -1 : previousPhaseSeconds);
            glUniform4f(timingUniform, (float)SpiritualEchoTimeline.EXPANSION, (float)SpiritualEchoTimeline.AFTERGLOW,
                    (float)SpiritualEchoTimeline.ATTACK, (float)SpiritualEchoTimeline.RADIUS);
            glUniform1f(occlusionUniform, Math.min(1, occlusion));
            float red = ((color >> 16) & 255) / 255f, green = ((color >> 8) & 255) / 255f, blue = (color & 255) / 255f;
            float brightest = Math.max(red, Math.max(green, blue));
            float lift = Math.max(0, .55f - brightest);
            glUniform3f(colorUniform, red + lift, green + lift, blue + lift);
            glActiveTexture(GL_TEXTURE0);
            glBindTexture(GL_TEXTURE_2D, source.texture);
            glActiveTexture(GL_TEXTURE1);
            glBindTexture(GL_TEXTURE_2D, depthTexture);
            if (samplersAvailable()) { GL33C.glBindSampler(0, 0); GL33C.glBindSampler(1, 0); }
            glBindVertexArray(vao);
            glDrawArrays(GL_TRIANGLES, 0, 3);
            checkError();
            glBindFramebuffer(GL_READ_FRAMEBUFFER, output.framebuffer);
            glBindFramebuffer(GL_DRAW_FRAMEBUFFER, framebuffer);
            glBlitFramebuffer(0, 0, width, height, 0, 0, width, height, GL_COLOR_BUFFER_BIT, GL_NEAREST);
            checkError();
            return true;
        }
    }

    private boolean eligible(int framebuffer, int width, int height) {
        return program != 0 && framebuffer > 0 && width > 0 && height > 0
                && width <= glGetInteger(GL_MAX_TEXTURE_SIZE) && height <= glGetInteger(GL_MAX_TEXTURE_SIZE)
                && glGetInteger(GL_DRAW_FRAMEBUFFER_BINDING) == framebuffer;
    }

    private static boolean compatible(int width, int height) {
        if (glCheckFramebufferStatus(GL_DRAW_FRAMEBUFFER) != GL_FRAMEBUFFER_COMPLETE
                || glGetInteger(GL_DRAW_BUFFER0) != GL_COLOR_ATTACHMENT0 || glGetInteger(GL_SAMPLES) != 0)
            return false;
        if (attachment(GL_COLOR_ATTACHMENT0, GL_FRAMEBUFFER_ATTACHMENT_COLOR_ENCODING) != GL_LINEAR) return false;
        for (int channel : new int[] {GL_FRAMEBUFFER_ATTACHMENT_RED_SIZE, GL_FRAMEBUFFER_ATTACHMENT_GREEN_SIZE,
                GL_FRAMEBUFFER_ATTACHMENT_BLUE_SIZE, GL_FRAMEBUFFER_ATTACHMENT_ALPHA_SIZE})
            if (attachment(GL_COLOR_ATTACHMENT0, channel) != 8) return false;
        return dimensions(GL_COLOR_ATTACHMENT0, width, height) && dimensions(GL_DEPTH_ATTACHMENT, width, height);
    }

    private static int attachment(int attachment, int parameter) {
        return glGetFramebufferAttachmentParameteri(GL_DRAW_FRAMEBUFFER, attachment, parameter);
    }

    private static boolean dimensions(int attachment, int width, int height) {
        int type = attachment(attachment, GL_FRAMEBUFFER_ATTACHMENT_OBJECT_TYPE);
        int name = attachment(attachment, GL_FRAMEBUFFER_ATTACHMENT_OBJECT_NAME);
        if (type == GL_RENDERBUFFER) {
            glBindRenderbuffer(GL_RENDERBUFFER, name);
            return glGetRenderbufferParameteri(GL_RENDERBUFFER, GL_RENDERBUFFER_WIDTH) == width
                    && glGetRenderbufferParameteri(GL_RENDERBUFFER, GL_RENDERBUFFER_HEIGHT) == height;
        }
        if (type != GL_TEXTURE || attachment(attachment, GL_FRAMEBUFFER_ATTACHMENT_LAYERED) != 0
                || attachment(attachment, GL_FRAMEBUFFER_ATTACHMENT_TEXTURE_CUBE_MAP_FACE) != 0) return false;
        int level = attachment(attachment, GL_FRAMEBUFFER_ATTACHMENT_TEXTURE_LEVEL);
        glActiveTexture(GL_TEXTURE0);
        glBindTexture(GL_TEXTURE_2D, name);
        // Non-2D third-party attachments are unsupported; the scene remains untouched.
        if (glGetError() != GL_NO_ERROR) return false;
        return glGetTexLevelParameteri(GL_TEXTURE_2D, level, GL_TEXTURE_WIDTH) == width
                && glGetTexLevelParameteri(GL_TEXTURE_2D, level, GL_TEXTURE_HEIGHT) == height;
    }

    private void prepare(int width, int height) {
        if (source != null && source.width == width && source.height == height) return;
        Target nextSource = null, nextOutput = null;
        int nextDepth = 0;
        try {
            nextSource = new Target(width, height);
            nextOutput = new Target(width, height);
            nextDepth = glGenTextures();
            glActiveTexture(GL_TEXTURE1);
            glBindBuffer(GL_PIXEL_UNPACK_BUFFER, 0);
            glBindTexture(GL_TEXTURE_2D, nextDepth);
            parameters();
            glTexParameteri(GL_TEXTURE_2D, GL_TEXTURE_COMPARE_MODE, GL_NONE);
            glTexImage2D(GL_TEXTURE_2D, 0, GL_DEPTH_COMPONENT32F, width, height, 0,
                    GL_DEPTH_COMPONENT, GL_FLOAT, (ByteBuffer) null);
            checkError();
        } catch (RuntimeException error) {
            if (nextSource != null) nextSource.close();
            if (nextOutput != null) nextOutput.close();
            if (nextDepth != 0) glDeleteTextures(nextDepth);
            throw error;
        }
        if (source != null) source.close();
        if (output != null) output.close();
        if (depthTexture != 0) glDeleteTextures(depthTexture);
        source = nextSource;
        output = nextOutput;
        depthTexture = nextDepth;
    }

    private static void parameters() {
        glTexParameteri(GL_TEXTURE_2D, GL_TEXTURE_MIN_FILTER, GL_NEAREST);
        glTexParameteri(GL_TEXTURE_2D, GL_TEXTURE_MAG_FILTER, GL_NEAREST);
        glTexParameteri(GL_TEXTURE_2D, GL_TEXTURE_WRAP_S, GL_CLAMP_TO_EDGE);
        glTexParameteri(GL_TEXTURE_2D, GL_TEXTURE_WRAP_T, GL_CLAMP_TO_EDGE);
    }

    @Override public void close() {
        beginFrame();
        if (source != null) source.close();
        if (output != null) output.close();
        source = output = null;
        if (depthTexture != 0) glDeleteTextures(depthTexture);
        if (vao != 0) glDeleteVertexArrays(vao);
        if (program != 0) glDeleteProgram(program);
        depthTexture = vao = program = 0;
    }

    private int uniform(String name) {
        int location = glGetUniformLocation(program, name);
        if (location < 0) throw new IllegalStateException("Missing spiritual echo uniform: " + name);
        return location;
    }

    private static int link(String vertex, String fragment) {
        int vs = 0, fs = 0, program = 0;
        try {
            vs = shader(GL_VERTEX_SHADER, vertex);
            fs = shader(GL_FRAGMENT_SHADER, fragment);
            program = glCreateProgram();
            glAttachShader(program, vs); glAttachShader(program, fs); glLinkProgram(program);
            if (glGetProgrami(program, GL_LINK_STATUS) == GL_FALSE) throw new IllegalStateException(glGetProgramInfoLog(program));
            return program;
        } catch (RuntimeException error) {
            if (program != 0) glDeleteProgram(program);
            throw error;
        } finally {
            if (vs != 0) glDeleteShader(vs);
            if (fs != 0) glDeleteShader(fs);
        }
    }

    private static int shader(int type, String source) {
        int shader = glCreateShader(type);
        glShaderSource(shader, source);
        glCompileShader(shader);
        if (glGetShaderi(shader, GL_COMPILE_STATUS) == GL_FALSE) {
            String log = glGetShaderInfoLog(shader);
            glDeleteShader(shader);
            throw new IllegalStateException("Spiritual echo shader compilation: " + log);
        }
        return shader;
    }

    private static void checkError() {
        int error = glGetError();
        if (error != GL_NO_ERROR) throw new IllegalStateException("Spiritual echo OpenGL error: " + error);
    }

    private static boolean samplersAvailable() {
        var capabilities = GL.getCapabilities();
        return (capabilities.OpenGL33 || capabilities.GL_ARB_sampler_objects) && capabilities.glBindSampler != 0;
    }

    private static final class Target implements AutoCloseable {
        final int width, height;
        int framebuffer, texture;
        Target(int width, int height) {
            this.width = width; this.height = height;
            try {
                texture = glGenTextures();
                glActiveTexture(GL_TEXTURE0);
                glBindBuffer(GL_PIXEL_UNPACK_BUFFER, 0);
                glBindTexture(GL_TEXTURE_2D, texture);
                parameters();
                glTexImage2D(GL_TEXTURE_2D, 0, GL_RGBA8, width, height, 0, GL_RGBA, GL_UNSIGNED_BYTE, (ByteBuffer) null);
                framebuffer = glGenFramebuffers();
                glBindFramebuffer(GL_FRAMEBUFFER, framebuffer);
                glFramebufferTexture2D(GL_FRAMEBUFFER, GL_COLOR_ATTACHMENT0, GL_TEXTURE_2D, texture, 0);
                if (glCheckFramebufferStatus(GL_FRAMEBUFFER) != GL_FRAMEBUFFER_COMPLETE)
                    throw new IllegalStateException("Incomplete spiritual echo framebuffer");
                checkError();
            } catch (RuntimeException error) { close(); throw error; }
        }
        @Override public void close() {
            if (framebuffer != 0) glDeleteFramebuffers(framebuffer);
            if (texture != 0) glDeleteTextures(texture);
            framebuffer = texture = 0;
        }
    }

    private static final class State implements AutoCloseable {
        static final int[] CAPABILITIES = {GL_SCISSOR_TEST, GL_DEPTH_TEST, GL_STENCIL_TEST, GL_CULL_FACE,
                GL_RASTERIZER_DISCARD, GL_FRAMEBUFFER_SRGB, GL_DITHER, GL_COLOR_LOGIC_OP,
                GL_SAMPLE_ALPHA_TO_COVERAGE, GL_SAMPLE_ALPHA_TO_ONE, GL_SAMPLE_COVERAGE};
        final int read = glGetInteger(GL_READ_FRAMEBUFFER_BINDING), draw = glGetInteger(GL_DRAW_FRAMEBUFFER_BINDING);
        final int program = glGetInteger(GL_CURRENT_PROGRAM), vao = glGetInteger(GL_VERTEX_ARRAY_BINDING);
        final int renderbuffer = glGetInteger(GL_RENDERBUFFER_BINDING);
        final int activeTexture = glGetInteger(GL_ACTIVE_TEXTURE);
        final int unpackBuffer = glGetInteger(GL_PIXEL_UNPACK_BUFFER_BINDING);
        final boolean samplers = samplersAvailable();
        final int[] textures = new int[2], samplerBindings = new int[2], viewport = new int[4], polygon = new int[2];
        final boolean[] color = new boolean[4], enabled = new boolean[CAPABILITIES.length];
        final boolean[] clips = new boolean[glGetInteger(GL_MAX_CLIP_DISTANCES)];
        final boolean depthMask = glGetBoolean(GL_DEPTH_WRITEMASK), blend = glIsEnabledi(GL_BLEND, 0);
        State() {
            glGetIntegerv(GL_VIEWPORT, viewport);
            glGetIntegerv(GL_POLYGON_MODE, polygon);
            try (MemoryStack stack = MemoryStack.stackPush()) {
                ByteBuffer mask = stack.malloc(4);
                glGetBooleani_v(GL_COLOR_WRITEMASK, 0, mask);
                for (int i = 0; i < 4; i++) color[i] = mask.get(i) != 0;
            }
            for (int i = 0; i < CAPABILITIES.length; i++) enabled[i] = glIsEnabled(CAPABILITIES[i]);
            for (int i = 0; i < clips.length; i++) clips[i] = glIsEnabled(GL_CLIP_DISTANCE0 + i);
            for (int i = 0; i < 2; i++) {
                glActiveTexture(GL_TEXTURE0 + i);
                textures[i] = glGetInteger(GL_TEXTURE_BINDING_2D);
                if (samplers) samplerBindings[i] = glGetIntegeri(GL33C.GL_SAMPLER_BINDING, i);
            }
            glActiveTexture(activeTexture);
        }
        @Override public void close() {
            glBindFramebuffer(GL_READ_FRAMEBUFFER, read);
            glBindFramebuffer(GL_DRAW_FRAMEBUFFER, draw);
            glBindRenderbuffer(GL_RENDERBUFFER, renderbuffer);
            glUseProgram(program);
            glBindVertexArray(vao);
            for (int i = 0; i < 2; i++) {
                glActiveTexture(GL_TEXTURE0 + i);
                glBindTexture(GL_TEXTURE_2D, textures[i]);
                if (samplers) GL33C.glBindSampler(i, samplerBindings[i]);
            }
            glActiveTexture(activeTexture);
            glBindBuffer(GL_PIXEL_UNPACK_BUFFER, unpackBuffer);
            glViewport(viewport[0], viewport[1], viewport[2], viewport[3]);
            glColorMaski(0, color[0], color[1], color[2], color[3]);
            glDepthMask(depthMask);
            glPolygonMode(GL_FRONT_AND_BACK, polygon[0]);
            if (blend) glEnablei(GL_BLEND, 0); else glDisablei(GL_BLEND, 0);
            for (int i = 0; i < CAPABILITIES.length; i++) {
                if (enabled[i]) glEnable(CAPABILITIES[i]); else glDisable(CAPABILITIES[i]);
            }
            for (int i = 0; i < clips.length; i++) {
                if (clips[i]) glEnable(GL_CLIP_DISTANCE0 + i); else glDisable(GL_CLIP_DISTANCE0 + i);
            }
        }
    }
}
