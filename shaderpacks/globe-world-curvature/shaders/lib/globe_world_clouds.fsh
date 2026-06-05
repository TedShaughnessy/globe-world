varying vec4 globeWorld_color;

void main() {
    vec4 color = globeWorld_color;

    if (color.a <= 0.0) {
        discard;
    }

    gl_FragData[0] = color;
}
