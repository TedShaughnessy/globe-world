uniform vec3 fogColor;
uniform float fogStart;
uniform float fogEnd;
uniform float far;

float globeWorld_fogValue(float distance, float start, float end) {
    if (end <= start) {
        return 0.0;
    }

    return clamp((distance - start) / (end - start), 0.0, 1.0);
}

float globeWorld_renderFogEnd() {
    if (fogEnd > 0.0 && far > 0.0) {
        return min(fogEnd, far);
    }

    return max(fogEnd, far);
}

vec4 globeWorld_applyWorldFog(vec4 color) {
    float fog = globeWorld_fogValue(gl_FogFragCoord, fogStart, globeWorld_renderFogEnd());
    return vec4(mix(color.rgb, fogColor, fog), color.a);
}
