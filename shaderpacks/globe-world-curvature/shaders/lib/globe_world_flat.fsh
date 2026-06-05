varying vec4 globeWorld_color;

#include "/lib/globe_world_fog.glsl"

void main() {
    gl_FragData[0] = globeWorld_applyWorldFog(globeWorld_color);
}
