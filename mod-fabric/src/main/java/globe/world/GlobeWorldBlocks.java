package globe.world;

import globe.world.block.GlobeBlock;
import globe.world.block.entity.GlobeBlockEntity;
import net.fabricmc.fabric.api.object.builder.v1.block.entity.FabricBlockEntityTypeBuilder;
import net.fabricmc.fabric.api.creativetab.v1.CreativeModeTabEvents;
import net.minecraft.core.Registry;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.Identifier;
import net.minecraft.resources.ResourceKey;
import net.minecraft.world.item.BlockItem;
import net.minecraft.world.item.CreativeModeTabs;
import net.minecraft.world.item.Item;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.SoundType;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.minecraft.world.level.block.state.BlockBehaviour;
import net.minecraft.world.level.material.MapColor;

public final class GlobeWorldBlocks {
    public static final int IRON_PROJECTOR_COLOR = 0xCCFFFFFF;
    public static final int COPPER_PROJECTOR_COLOR = 0xCCFFB45C;
    public static final int SOUL_PROJECTOR_COLOR = 0xCC66D9FF;
    public static final int IRON_PROJECTION_LIGHT_COLOR = 0xCCFFD2A1;
    public static final int COPPER_PROJECTION_LIGHT_COLOR = 0xCC68E070;
    public static final int SOUL_PROJECTION_LIGHT_COLOR = SOUL_PROJECTOR_COLOR;
    private static final int LIGHT_LEVEL = 14;

    public static final Block GLOBE = registerBlock(
            "globe",
            properties -> newProjectorBlock(properties, MapColor.METAL));
    public static final Block COPPER_ATLAS_PROJECTOR = registerBlock(
            "copper_atlas_projector",
            properties -> newProjectorBlock(properties, MapColor.COLOR_ORANGE));
    public static final Block SOUL_ATLAS_PROJECTOR = registerBlock(
            "soul_atlas_projector",
            properties -> newProjectorBlock(properties, MapColor.COLOR_LIGHT_BLUE));
    public static final BlockEntityType<GlobeBlockEntity> GLOBE_BLOCK_ENTITY = registerBlockEntity(
            "globe",
            FabricBlockEntityTypeBuilder.create(
                    GlobeBlockEntity::new,
                    GLOBE,
                    COPPER_ATLAS_PROJECTOR,
                    SOUL_ATLAS_PROJECTOR).build());
    public static final Item GLOBE_ITEM = registerBlockItem("globe", GLOBE, new Item.Properties());
    public static final Item COPPER_ATLAS_PROJECTOR_ITEM = registerBlockItem(
            "copper_atlas_projector",
            COPPER_ATLAS_PROJECTOR,
            new Item.Properties());
    public static final Item SOUL_ATLAS_PROJECTOR_ITEM = registerBlockItem(
            "soul_atlas_projector",
            SOUL_ATLAS_PROJECTOR,
            new Item.Properties());

    private GlobeWorldBlocks() {
    }

    public static void register() {
        CreativeModeTabEvents.modifyOutputEvent(CreativeModeTabs.FUNCTIONAL_BLOCKS).register(output -> {
            output.accept(GLOBE_ITEM);
            output.accept(COPPER_ATLAS_PROJECTOR_ITEM);
            output.accept(SOUL_ATLAS_PROJECTOR_ITEM);
        });
    }

    public static int projectorColor(final Block block) {
        if (block == COPPER_ATLAS_PROJECTOR) {
            return COPPER_PROJECTOR_COLOR;
        }
        if (block == SOUL_ATLAS_PROJECTOR) {
            return SOUL_PROJECTOR_COLOR;
        }
        return IRON_PROJECTOR_COLOR;
    }

    public static int projectionLightColor(final Block block) {
        if (block == COPPER_ATLAS_PROJECTOR) {
            return COPPER_PROJECTION_LIGHT_COLOR;
        }
        if (block == SOUL_ATLAS_PROJECTOR) {
            return SOUL_PROJECTION_LIGHT_COLOR;
        }
        return IRON_PROJECTION_LIGHT_COLOR;
    }

    private static GlobeBlock newProjectorBlock(final BlockBehaviour.Properties properties, final MapColor mapColor) {
        return new GlobeBlock(properties
                .mapColor(mapColor)
                .strength(0.6F)
                .sound(SoundType.METAL)
                .lightLevel(state -> LIGHT_LEVEL)
                .noOcclusion());
    }

    private static Block registerBlock(final String id, final BlockFactory factory) {
        ResourceKey<Block> key = ResourceKey.create(Registries.BLOCK, Identifier.fromNamespaceAndPath(GlobeWorld.MOD_ID, id));
        return Registry.register(BuiltInRegistries.BLOCK, key, factory.create(BlockBehaviour.Properties.of().setId(key)));
    }

    private static Item registerBlockItem(final String id, final Block block, final Item.Properties properties) {
        ResourceKey<Item> key = ResourceKey.create(Registries.ITEM, Identifier.fromNamespaceAndPath(GlobeWorld.MOD_ID, id));
        BlockItem item = new BlockItem(block, properties.setId(key).useBlockDescriptionPrefix());
        item.registerBlocks(Item.BY_BLOCK, item);
        return Registry.register(BuiltInRegistries.ITEM, key, item);
    }

    private static <T extends net.minecraft.world.level.block.entity.BlockEntity> BlockEntityType<T> registerBlockEntity(
            final String id,
            final BlockEntityType<T> type) {
        return Registry.register(
                BuiltInRegistries.BLOCK_ENTITY_TYPE,
                Identifier.fromNamespaceAndPath(GlobeWorld.MOD_ID, id),
                type);
    }

    @FunctionalInterface
    private interface BlockFactory {
        Block create(BlockBehaviour.Properties properties);
    }
}
