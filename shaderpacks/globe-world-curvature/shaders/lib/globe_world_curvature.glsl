const float GLOBE_WORLD_CURVATURE_RADIUS = 0.0 /*GLOBE_WORLD_CURVATURE_RADIUS_FROM_MOD*/;
const float GLOBE_WORLD_CURVATURE_DROP_CLAMP = 256.0 /*GLOBE_WORLD_CURVATURE_DROP_CLAMP_FROM_MOD*/;
const float GLOBE_WORLD_FOG_DISTANCE_SCALE = 1.0 /*GLOBE_WORLD_FOG_DISTANCE_SCALE_FROM_MOD*/;

float globeWorld_curvatureRadius() {
    return GLOBE_WORLD_CURVATURE_RADIUS;
}

float globeWorld_curvatureDropClamp() {
    return GLOBE_WORLD_CURVATURE_DROP_CLAMP;
}

float globeWorld_fogDistanceScale() {
    return GLOBE_WORLD_FOG_DISTANCE_SCALE;
}

vec3 globeWorld_applyCurvature(vec3 pos) {
    float radius = globeWorld_curvatureRadius();
    if (radius <= 0.0) {
        return pos;
    }

    float distanceSqr = dot(pos.xz, pos.xz);
    float drop = min(distanceSqr / (2.0 * radius), globeWorld_curvatureDropClamp());
    pos.y -= drop;
    return pos;
}

vec3 globeWorld_applyCloudCurvature(vec3 pos) {
    float radius = globeWorld_curvatureRadius();
    if (radius <= 0.0) {
        return pos;
    }

    float cloudRadius = (radius + max(pos.y, 0.0));
    float distanceSqr = dot(pos.xz, pos.xz);
    float drop = min(distanceSqr / (2.0 * cloudRadius), globeWorld_curvatureDropClamp());
    pos.y -= drop;
    return pos;
}

vec3 globeWorld_fogPosition(vec3 pos) {
    if (globeWorld_curvatureRadius() <= 0.0) {
        return pos;
    }

    return vec3(pos.x * globeWorld_fogDistanceScale(), 0.0, pos.z * globeWorld_fogDistanceScale());
}

vec3 globeWorld_cloudFogPosition(vec3 pos) {
    if (globeWorld_curvatureRadius() <= 0.0) {
        return pos;
    }

    return vec3(pos.x, 0.0, pos.z);
}
