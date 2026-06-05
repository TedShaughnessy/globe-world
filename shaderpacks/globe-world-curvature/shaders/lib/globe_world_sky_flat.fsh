varying vec4 globeWorld_color;

#include "/lib/globe_world_sky.glsl"

void main() {
    gl_FragData[0] = globeWorld_skyPassThrough(globeWorld_color);
}
