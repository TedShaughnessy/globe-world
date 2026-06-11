#include "/lib/globe_world_curvature.glsl"

varying vec4 globeWorld_color;

uniform float viewHeight;
uniform float viewWidth;
uniform mat4 gbufferModelView;
uniform mat4 gbufferModelViewInverse;

void main() {
    globeWorld_color = gl_Color;

    vec4 viewPos = gl_ModelViewMatrix * gl_Vertex;
    vec4 playerPos = gbufferModelViewInverse * viewPos;
    vec3 fogPos = globeWorld_fogPosition(playerPos.xyz);

    vec3 lineStart = globeWorld_applyCurvature(playerPos.xyz);
    vec4 lineEndViewPos = gl_ModelViewMatrix * vec4(gl_Vertex.xyz + gl_Normal, 1.0);
    vec3 lineEnd = globeWorld_applyCurvature((gbufferModelViewInverse * lineEndViewPos).xyz);

    float lineWidth = 2.0;
    vec2 screenSize = vec2(viewWidth, viewHeight);
    mat4 viewScale = mat4(mat3(1.0 - 0.00390625));
    mat4 transform = gl_ProjectionMatrix * viewScale * gbufferModelView;
    vec4 linePosStart = transform * vec4(lineStart, 1.0);
    vec4 linePosEnd = transform * vec4(lineEnd, 1.0);

    vec3 ndcStart = linePosStart.xyz / linePosStart.w;
    vec3 ndcEnd = linePosEnd.xyz / linePosEnd.w;
    vec2 lineScreenDirection = normalize((ndcEnd.xy - ndcStart.xy) * screenSize);
    vec2 lineOffset = vec2(-lineScreenDirection.y, lineScreenDirection.x) * lineWidth / screenSize;
    if (lineOffset.x < 0.0) {
        lineOffset *= -1.0;
    }

    if (gl_VertexID % 2 == 0) {
        gl_Position = vec4((ndcStart + vec3(lineOffset, 0.0)) * linePosStart.w, linePosStart.w);
    } else {
        gl_Position = vec4((ndcStart - vec3(lineOffset, 0.0)) * linePosStart.w, linePosStart.w);
    }

    gl_FogFragCoord = length(fogPos);
}
