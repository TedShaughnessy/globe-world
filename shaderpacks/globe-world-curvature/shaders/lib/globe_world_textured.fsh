uniform sampler2D gtexture;
uniform sampler2D lightmap;

varying vec4 globeWorld_color;
varying vec2 globeWorld_texcoord;
varying vec2 globeWorld_lmcoord;

void main() {
    vec4 albedo = texture2D(gtexture, globeWorld_texcoord) * globeWorld_color;
    if (albedo.a < 0.1) {
        discard;
    }

    vec3 light = texture2D(lightmap, globeWorld_lmcoord).rgb;
    gl_FragData[0] = vec4(albedo.rgb * light, albedo.a);
}
