#version 150
uniform vec4 Rect;
out vec2 cloudCoord;
void main() {
    cloudCoord = vec2(gl_VertexID & 1, gl_VertexID >> 1) * 2.0 - 1.0;
    gl_Position = vec4((Rect.xy + cloudCoord * Rect.zw) * 2.0 - 1.0, 0.0, 1.0);
}
