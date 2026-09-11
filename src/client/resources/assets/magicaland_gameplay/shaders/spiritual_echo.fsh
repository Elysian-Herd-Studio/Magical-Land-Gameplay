#version 150
uniform sampler2D Scene;
uniform sampler2D WorldDepth;
uniform mat4 InverseWorldClip;
uniform vec3 PulseOrigin;
uniform float PhaseSeconds;
uniform vec3 PreviousPulseOrigin;
uniform float PreviousPhaseSeconds;
uniform vec4 PulseTiming;
uniform float Occlusion;
uniform vec3 MagicColor;
in vec2 TexCoord;
out vec4 FragColor;

vec3 surface(vec2 uv, float depth) {
    vec4 p = InverseWorldClip * vec4(uv * 2.0 - 1.0, depth * 2.0 - 1.0, 1.0);
    return p.xyz / p.w;
}
float vignette(vec2 uv) {
    if (Occlusion >= 1.0) return 1.0;
    if (Occlusion <= 0.0) return 0.0;
    float opening = 1.4 - 1.72 * Occlusion;
    return smoothstep(0.0, 1.0, (length(uv * 2.0 - 1.0) - opening) / .32);
}
vec3 normalBetween(vec3 a, vec3 b) {
    vec3 n = cross(a, b);
    return n / max(length(n), .0000001);
}
vec2 pulseSignal(vec3 point, vec3 origin, float phase) {
    if (phase < 0.0) return vec2(0.0);
    float radius = length(point - origin);
    float since = phase - radius * (PulseTiming.x / PulseTiming.w);
    if (radius > PulseTiming.w || since <= 0.0 || since >= PulseTiming.y) return vec2(0.0);
    float fade = smoothstep(0.0, PulseTiming.z, since) * (1.0 - smoothstep(PulseTiming.z, PulseTiming.y, since));
    float front = 1.0 - smoothstep(0.0, .09, since);
    float rangeFade = 1.0 - smoothstep(PulseTiming.w - .5, PulseTiming.w, radius);
    return vec2(fade, fade * front) * rangeFade;
}
void main() {
    vec4 scene = texture(Scene, TexCoord);
    FragColor = scene;
    float mask = vignette(TexCoord);
    float depth = texture(WorldDepth, TexCoord).r;
    if (mask <= 0.0 || depth >= .9999999) return;
    vec3 center = surface(TexCoord, depth);
    vec2 signal = max(pulseSignal(center, PulseOrigin, PhaseSeconds),
                      pulseSignal(center, PreviousPulseOrigin, PreviousPhaseSeconds));
    if (signal.x <= 0.0) return;

    vec2 pixel = 1.0 / vec2(textureSize(WorldDepth, 0));
    vec2 leftUv = clamp(TexCoord - vec2(pixel.x, 0), pixel * .5, 1.0 - pixel * .5);
    vec2 rightUv = clamp(TexCoord + vec2(pixel.x, 0), pixel * .5, 1.0 - pixel * .5);
    vec2 downUv = clamp(TexCoord - vec2(0, pixel.y), pixel * .5, 1.0 - pixel * .5);
    vec2 upUv = clamp(TexCoord + vec2(0, pixel.y), pixel * .5, 1.0 - pixel * .5);
    float dl = texture(WorldDepth, leftUv).r, dr = texture(WorldDepth, rightUv).r;
    float dd = texture(WorldDepth, downUv).r, du = texture(WorldDepth, upUv).r;
    vec3 left = dl >= .9999999 ? center : surface(leftUv, dl);
    vec3 right = dr >= .9999999 ? center : surface(rightUv, dr);
    vec3 down = dd >= .9999999 ? center : surface(downUv, dd);
    vec3 up = du >= .9999999 ? center : surface(upUv, du);
    vec3 xp = right - center, xm = center - left;
    vec3 yp = up - center, ym = center - down;
    float nearest = min(min(length(xp), length(xm)), min(length(yp), length(ym)));
    float farthest = max(max(length(xp), length(xm)), max(length(yp), length(ym)));
    float discontinuity = smoothstep(.08 + nearest * 2.0, .22 + nearest * 6.0, farthest);
    float crease = 0.0;
    if (nearest > .000001) {
        vec3 a = normalBetween(xp, yp), b = normalBetween(xm, yp);
        vec3 c = normalBetween(xm, ym), d = normalBetween(xp, ym);
        crease = smoothstep(.01, .10, 1.0 - min(min(dot(a, b), dot(b, c)), min(dot(c, d), dot(d, a))));
    }
    float silhouette = max(max(step(.9999999, dl), step(.9999999, dr)),
                           max(step(.9999999, dd), step(.9999999, du)));
    float edge = max(max(discontinuity, crease), silhouette);
    float strength = mask * (.66 * edge * signal.x + .024 * signal.y);
    // Only depth makes the signal. Scene RGB is used solely for the final blend.
    FragColor = vec4(min(vec3(1.0), scene.rgb + MagicColor * strength), scene.a);
}
