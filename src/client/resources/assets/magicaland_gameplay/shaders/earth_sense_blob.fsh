#version 150
uniform vec4 Color;
uniform float Activity;
uniform float Pulse;
in vec2 cloudCoord;
out vec4 fragColor;
void main() {
    vec2 q = cloudCoord;
    q.x *= 1.0 + 0.055 * sin(q.y * 5.0 + Pulse);
    float radius = length(q);
    float inner = mix(0.04, 0.48, clamp(Activity, 0.0, 1.0));
    float soft = 1.0 - smoothstep(inner, 0.99, radius);
    float alpha = Color.a * soft * (0.72 + 0.28 * exp(-3.0 * radius * radius));
    if (alpha < 0.001) discard;
    fragColor = vec4(Color.rgb * alpha, alpha);
}
