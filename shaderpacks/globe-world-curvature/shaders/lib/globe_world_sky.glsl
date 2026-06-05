uniform vec3 fogColor;
uniform vec3 skyColor;

varying float globeWorld_skyViewY;

float globeWorld_maxChannel(vec3 color) {
    return max(max(color.r, color.g), color.b);
}

vec4 globeWorld_skyPassThrough(vec4 color) {
    return color;
}

vec4 globeWorld_skyBasicColor(vec4 color) {
    float sceneBrightness = globeWorld_maxChannel(max(fogColor, skyColor));
    float daylight = smoothstep(0.03, 0.35, sceneBrightness);
    float darkDisc = 1.0 - smoothstep(0.02, 0.16, globeWorld_maxChannel(color.rgb));
    float lowerSky = smoothstep(-0.08, 0.25, -globeWorld_skyViewY);
    float horizon = 1.0 - smoothstep(0.02, 0.32, abs(globeWorld_skyViewY));
    float fogBlend = max(darkDisc * lowerSky, horizon * 0.45) * daylight;

    return vec4(mix(color.rgb, fogColor, clamp(fogBlend, 0.0, 1.0)), color.a);
}
