uniform vec3 fogColor;
uniform vec3 skyColor;

varying vec4 globeWorld_color;

float globeWorld_maxChannel(vec3 color) {
    return max(max(color.r, color.g), color.b);
}

vec3 globeWorld_cloudTintFallback() {
    float sceneBrightness = globeWorld_maxChannel(max(fogColor, skyColor));
    float daylight = smoothstep(0.03, 0.35, sceneBrightness);
    vec3 horizonTint = mix(skyColor, fogColor, 0.65);
    return mix(horizonTint, vec3(1.0), 0.55 * daylight);
}

void main() {
    vec4 color = globeWorld_color;

    if (color.a <= 0.0) {
        discard;
    }

    float missingTint = 1.0 - smoothstep(0.01, 0.08, globeWorld_maxChannel(color.rgb));
    color.rgb = mix(color.rgb, globeWorld_cloudTintFallback(), missingTint);

    gl_FragData[0] = color;
}
