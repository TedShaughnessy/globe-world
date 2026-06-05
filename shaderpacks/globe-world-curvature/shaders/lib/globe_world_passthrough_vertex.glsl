varying vec4 globeWorld_color;
varying vec2 globeWorld_texcoord;
varying vec2 globeWorld_lmcoord;
varying float globeWorld_skyViewY;

void main() {
    globeWorld_color = gl_Color;
    globeWorld_texcoord = (gl_TextureMatrix[0] * gl_MultiTexCoord0).xy;
    globeWorld_lmcoord = (gl_TextureMatrix[1] * gl_MultiTexCoord1).xy;

    vec3 viewPos = (gl_ModelViewMatrix * gl_Vertex).xyz;
    globeWorld_skyViewY = length(viewPos) > 0.0 ? normalize(viewPos).y : 0.0;
    gl_Position = ftransform();
    gl_FogFragCoord = length(viewPos);
}
