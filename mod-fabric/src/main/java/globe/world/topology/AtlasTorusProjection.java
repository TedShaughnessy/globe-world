package globe.world.topology;

import globe.world.util.DimensionTiling;
import net.minecraft.world.phys.Vec3;

/**
 * Rectangular Atlas coordinates for a world's lattice quotient.
 *
 * <p>The texture remains an ordinary rectangular torus. For square worlds its
 * U/V axes are world X/Z. For coupled lattice worlds they are coefficients of
 * the A/B basis, so every lattice alias has exactly the same texture
 * coordinate.</p>
 */
public final class AtlasTorusProjection {
    private static final String REVISION = "atlas-ab-v1";

    private final TileGeometry geometry;
    private final double ax;
    private final double az;
    private final double bx;
    private final double bz;
    private final double determinant;

    private AtlasTorusProjection(final TileGeometry geometry) {
        this.geometry = geometry;
        TileGeometry.LatticeBasis basis = geometry.latticeBasis();
        this.ax = basis.a().x() * 16.0D;
        this.az = basis.a().z() * 16.0D;
        this.bx = basis.b().x() * 16.0D;
        this.bz = basis.b().z() * 16.0D;
        this.determinant = this.ax * this.bz - this.az * this.bx;
        if (this.determinant == 0.0D) {
            throw new IllegalArgumentException("Atlas lattice basis must be invertible");
        }
    }

    public static AtlasTorusProjection create(final DimensionTiling tiling) {
        return new AtlasTorusProjection(TileGeometry.create(tiling));
    }

    public TileGeometry geometry() {
        return this.geometry;
    }

    public String identity() {
        TileGeometry.LatticeBasis basis = this.geometry.latticeBasis();
        return this.geometry.geometryRevision()
                + ":" + basis.a().x() + "," + basis.a().z()
                + ":" + basis.b().x() + "," + basis.b().z()
                + ":" + REVISION;
    }

    public UnitPoint project(final double x, final double z) {
        double u = (x * this.bz - z * this.bx) / this.determinant;
        double v = (this.ax * z - this.az * x) / this.determinant;
        return new UnitPoint(wrapUnit(u + 0.5D), wrapUnit(v + 0.5D));
    }

    public UnitPoint project(final Vec3 position) {
        return this.project(position.x(), position.z());
    }

    public Pixel pixel(final double x, final double z, final int resolution) {
        UnitPoint point = this.project(x, z);
        return new Pixel(pixel(point.u(), resolution), pixel(point.v(), resolution));
    }

    public Vec3 canonicalSample(final double u, final double v) {
        double latticeU = wrapUnit(u) - 0.5D;
        double latticeV = wrapUnit(v) - 0.5D;
        Vec3 representative = new Vec3(
                latticeU * this.ax + latticeV * this.bx,
                0.0D,
                latticeU * this.az + latticeV * this.bz);
        return this.geometry.canonicalBlock(representative);
    }

    public Vec3 canonicalPixelCenter(final int pixelU, final int pixelV, final int resolution) {
        return this.canonicalSample(
                (pixelU + 0.5D) / resolution,
                (pixelV + 0.5D) / resolution);
    }

    public Vec3 canonicalPixelSample(
            final int pixelU,
            final int pixelV,
            final int resolution,
            final double sampleU,
            final double sampleV) {
        return this.canonicalSample(
                (pixelU + sampleU) / resolution,
                (pixelV + sampleV) / resolution);
    }

    public PixelRadius pixelRadius(final double radiusBlocks, final int resolution) {
        double inverseUFromX = this.bz / this.determinant;
        double inverseUFromZ = -this.bx / this.determinant;
        double inverseVFromX = -this.az / this.determinant;
        double inverseVFromZ = this.ax / this.determinant;
        int u = (int)Math.ceil(radiusBlocks
                * (Math.abs(inverseUFromX) + Math.abs(inverseUFromZ))
                * resolution) + 1;
        int v = (int)Math.ceil(radiusBlocks
                * (Math.abs(inverseVFromX) + Math.abs(inverseVFromZ))
                * resolution) + 1;
        return new PixelRadius(Math.min(u, resolution / 2), Math.min(v, resolution / 2));
    }

    public int canonicalChunkCount() {
        return this.geometry.canonicalChunkCount();
    }

    public double canonicalBlockArea() {
        return this.canonicalChunkCount() * 16.0D * 16.0D;
    }

    public double maximumBasisLengthBlocks() {
        return Math.max(Math.hypot(this.ax, this.az), Math.hypot(this.bx, this.bz));
    }

    private static int pixel(final double unit, final int resolution) {
        return Math.floorMod((int)Math.floor(unit * resolution), resolution);
    }

    private static double wrapUnit(final double value) {
        return value - Math.floor(value);
    }

    public record UnitPoint(double u, double v) {
    }

    public record Pixel(int u, int v) {
    }

    public record PixelRadius(int u, int v) {
    }
}
