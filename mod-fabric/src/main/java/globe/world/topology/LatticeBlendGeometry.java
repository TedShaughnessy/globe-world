package globe.world.topology;

import net.minecraft.world.level.ChunkPos;

/**
 * Continuous block-space Voronoi geometry used to blend scalar worldgen fields
 * around a lattice tile's ideal boundary. This is deliberately separate from
 * the whole-chunk ownership boundary exposed by {@link TileGeometry}.
 */
public final class LatticeBlendGeometry {
    private static final double MIN_BLEND_WIDTH_BLOCKS = 64.0D;
    private static final double MAX_BLEND_WIDTH_BLOCKS = 256.0D;

    private final long ax;
    private final long az;
    private final long bx;
    private final long bz;
    private final long determinant;
    private final double aLength;
    private final double bLength;
    private final double cLength;
    private final double inradius;
    private final double blendWidth;
    private final int candidateRadius;

    public LatticeBlendGeometry(TileGeometry.LatticeBasis basis) {
        ChunkPos a = basis.a();
        ChunkPos b = basis.b();
        this.ax = a.x() * 16L;
        this.az = a.z() * 16L;
        this.bx = b.x() * 16L;
        this.bz = b.z() * 16L;
        this.determinant = this.ax * this.bz - this.az * this.bx;
        if (this.determinant == 0L) {
            throw new IllegalArgumentException("Blend lattice basis must be invertible");
        }

        this.aLength = Math.hypot(this.ax, this.az);
        this.bLength = Math.hypot(this.bx, this.bz);
        this.cLength = Math.hypot(this.ax - this.bx, this.az - this.bz);
        this.inradius = Math.min(this.aLength, Math.min(this.bLength, this.cLength)) * 0.5D;
        this.blendWidth = Math.clamp(
                this.inradius * 0.25D,
                MIN_BLEND_WIDTH_BLOCKS,
                MAX_BLEND_WIDTH_BLOCKS);

        double minAltitude = Math.min(
                Math.abs((double) this.determinant) / this.aLength,
                Math.abs((double) this.determinant) / this.bLength);
        this.candidateRadius = Math.max(2, (int) Math.ceil(this.blendWidth / minAltitude) + 2);
    }

    public double inradius() {
        return this.inradius;
    }

    public double blendWidth() {
        return this.blendWidth;
    }

    public int candidateRadius() {
        return this.candidateRadius;
    }

    public long translationX(long k, long l) {
        return k * this.ax + l * this.bx;
    }

    public long translationZ(long k, long l) {
        return k * this.az + l * this.bz;
    }

    public long baseK(double blockX, double blockZ) {
        return floorQuotient(blockX * this.bz - blockZ * this.bx, this.determinant);
    }

    public long baseL(double blockX, double blockZ) {
        return floorQuotient(this.ax * blockZ - this.az * blockX, this.determinant);
    }

    public double signedDistance(double blockX, double blockZ, long centerK, long centerL) {
        double qx = blockX - translationX(centerK, centerL);
        double qz = blockZ - translationZ(centerK, centerL);
        return Math.min(
                sideDistance(qx, qz, this.ax, this.az, this.aLength),
                Math.min(
                        sideDistance(qx, qz, -this.ax, -this.az, this.aLength),
                        Math.min(
                                sideDistance(qx, qz, this.bx, this.bz, this.bLength),
                                Math.min(
                                        sideDistance(qx, qz, -this.bx, -this.bz, this.bLength),
                                        Math.min(
                                                sideDistance(
                                                        qx,
                                                        qz,
                                                        this.ax - this.bx,
                                                        this.az - this.bz,
                                                        this.cLength),
                                                sideDistance(
                                                        qx,
                                                        qz,
                                                        this.bx - this.ax,
                                                        this.bz - this.az,
                                                        this.cLength))))));
    }

    public double weight(double blockX, double blockZ, long centerK, long centerL) {
        double qx = blockX - translationX(centerK, centerL);
        double qz = blockZ - translationZ(centerK, centerL);
        double weight = gate(sideDistance(qx, qz, this.ax, this.az, this.aLength));
        if (weight == 0.0D) {
            return 0.0D;
        }
        weight *= gate(sideDistance(qx, qz, -this.ax, -this.az, this.aLength));
        if (weight == 0.0D) {
            return 0.0D;
        }
        weight *= gate(sideDistance(qx, qz, this.bx, this.bz, this.bLength));
        if (weight == 0.0D) {
            return 0.0D;
        }
        weight *= gate(sideDistance(qx, qz, -this.bx, -this.bz, this.bLength));
        if (weight == 0.0D) {
            return 0.0D;
        }
        weight *= gate(sideDistance(
                qx,
                qz,
                this.ax - this.bx,
                this.az - this.bz,
                this.cLength));
        if (weight == 0.0D) {
            return 0.0D;
        }
        weight *= gate(sideDistance(
                qx,
                qz,
                this.bx - this.ax,
                this.bz - this.az,
                this.cLength));
        return weight;
    }

    private double gate(double signedDistance) {
        if (signedDistance <= -this.blendWidth) {
            return 0.0D;
        }
        if (signedDistance >= this.blendWidth) {
            return 1.0D;
        }
        double value = (signedDistance + this.blendWidth) / (2.0D * this.blendWidth);
        return value * value * (3.0D - 2.0D * value);
    }

    private static double sideDistance(double qx, double qz, long tx, long tz, double length) {
        return (length * length * 0.5D - qx * tx - qz * tz) / length;
    }

    private static long floorQuotient(double numerator, long denominator) {
        return (long) Math.floor(numerator / denominator);
    }
}
