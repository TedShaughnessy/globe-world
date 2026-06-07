package globe.world.util;

import globe.world.GlobeWorld;
import net.minecraft.core.Registry;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.Identifier;
import net.minecraft.world.level.levelgen.structure.pieces.StructurePieceType;

public final class ForcedProgressionStructurePieces {
    public static final StructurePieceType FORTRESS_PROGRESSION_CHEST = Registry.register(
            BuiltInRegistries.STRUCTURE_PIECE,
            Identifier.fromNamespaceAndPath(GlobeWorld.MOD_ID, "fortress_progression_chest"),
            (StructurePieceType.ContextlessType) ForcedFortressProgressionChestPiece::new
    );
    public static final StructurePieceType FORTRESS_WART_PATCH = Registry.register(
            BuiltInRegistries.STRUCTURE_PIECE,
            Identifier.fromNamespaceAndPath(GlobeWorld.MOD_ID, "fortress_wart_patch"),
            (StructurePieceType.ContextlessType) ForcedFortressWartPatchPiece::new
    );

    private ForcedProgressionStructurePieces() {
    }

    public static void register() {
    }
}
