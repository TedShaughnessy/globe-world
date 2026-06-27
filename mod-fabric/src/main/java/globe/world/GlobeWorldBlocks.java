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
    public static final Block GLOBE = registerBlock(
            "globe",
            properties -> new GlobeBlock(properties
                    .mapColor(MapColor.COLOR_LIGHT_BLUE)
                    .strength(0.6F)
                    .sound(SoundType.WOOD)
                    .noOcclusion()));
    public static final BlockEntityType<GlobeBlockEntity> GLOBE_BLOCK_ENTITY = registerBlockEntity(
            "globe",
            FabricBlockEntityTypeBuilder.create(GlobeBlockEntity::new, GLOBE).build());
    public static final Item GLOBE_ITEM = registerBlockItem("globe", GLOBE, new Item.Properties());

    private GlobeWorldBlocks() {
    }

    public static void register() {
        CreativeModeTabEvents.modifyOutputEvent(CreativeModeTabs.FUNCTIONAL_BLOCKS).register(output -> output.accept(GLOBE_ITEM));
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
