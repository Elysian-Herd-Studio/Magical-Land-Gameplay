#version 150

uniform sampler2D Scene;
uniform float Amount;
uniform float Blur;
in vec2 texCoord;
out vec4 fragColor;

void main() {
    vec4 scene = texture(Scene, texCoord);
    vec2 stepSize = vec2(Blur) / vec2(textureSize(Scene, 0));
    vec3 soft = scene.rgb * 0.4;
    soft += texture(Scene, texCoord + vec2(stepSize.x, 0)).rgb * 0.1;
    soft += texture(Scene, texCoord - vec2(stepSize.x, 0)).rgb * 0.1;
    soft += texture(Scene, texCoord + vec2(0, stepSize.y)).rgb * 0.1;
    soft += texture(Scene, texCoord - vec2(0, stepSize.y)).rgb * 0.1;
    soft += texture(Scene, texCoord + stepSize).rgb * 0.05;
    soft += texture(Scene, texCoord - stepSize).rgb * 0.05;
    soft += texture(Scene, texCoord + vec2(stepSize.x, -stepSize.y)).rgb * 0.05;
    soft += texture(Scene, texCoord + vec2(-stepSize.x, stepSize.y)).rgb * 0.05;
    float gray = dot(soft, vec3(0.2126, 0.7152, 0.0722));
    fragColor = vec4(mix(soft, vec3(gray), clamp(Amount, 0.0, 1.0)), scene.a);
}
