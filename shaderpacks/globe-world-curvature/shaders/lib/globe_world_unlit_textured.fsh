uniform sampler2D gtexture;

varying vec4 globeWorld_color;
varying vec2 globeWorld_texcoord;

#include "/lib/globe_world_fog.glsl"

void main() {
    vec4 albedo = texture2D(gtexture, globeWorld_texcoord) * globeWorld_color;
    if (albedo.a < 0.1) {
        discard;
    }

    gl_FragData[0] = globeWorld_applyWorldFog(albedo);
}
