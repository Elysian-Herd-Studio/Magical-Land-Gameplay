#version 150

out vec2 texCoord;

void main() {
    vec2 point = vec2((gl_VertexID << 1) & 2, gl_VertexID & 2);
    texCoord = point;
    gl_Position = vec4(point * 2.0 - 1.0, 0.0, 1.0);
}
