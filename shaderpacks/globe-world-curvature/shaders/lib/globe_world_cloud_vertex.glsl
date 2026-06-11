#include "/lib/globe_world_curvature.glsl"

varying vec4 globeWorld_color;
varying vec2 globeWorld_texcoord;
varying vec2 globeWorld_lmcoord;

uniform mat4 gbufferModelView;
uniform mat4 gbufferModelViewInverse;

void main() {
    globeWorld_color = gl_Color;
    globeWorld_texcoord = (gl_TextureMatrix[0] * gl_MultiTexCoord0).xy;
    globeWorld_lmcoord = (gl_TextureMatrix[1] * gl_MultiTexCoord1).xy;

    vec4 viewPos = gl_ModelViewMatrix * gl_Vertex;
    vec4 playerPos = gbufferModelViewInverse * viewPos;
    vec3 fogPos = globeWorld_cloudFogPosition(playerPos.xyz);
    vec3 curvedPos = globeWorld_applyCloudCurvature(playerPos.xyz);

    vec4 curvedViewPos = gbufferModelView * vec4(curvedPos, 1.0);
    gl_Position = gl_ProjectionMatrix * curvedViewPos;

    gl_FogFragCoord = length(fogPos);
}
