package globe.world.client;

import globe.world.config.DayNightCycleMode;
import globe.world.config.GameplaySettings;
import globe.world.config.GlobeSettings;
import globe.world.config.PresentationSettings;
import globe.world.config.TilingMode;
import globe.world.config.TopologySettings;
import globe.world.util.GlobeCurvature;
import globe.world.util.TerrainMode;
import net.fabricmc.loader.api.FabricLoader;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.components.AbstractWidget;
import net.minecraft.client.gui.components.AbstractSliderButton;
import net.minecraft.client.gui.components.Checkbox;
import net.minecraft.client.gui.components.CycleButton;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.client.gui.components.MultiLineTextWidget;
import net.minecraft.client.gui.components.StringWidget;
import net.minecraft.client.gui.components.Tooltip;
import net.minecraft.client.gui.layouts.Layout;
import net.minecraft.client.gui.layouts.LayoutElement;
import net.minecraft.client.gui.layouts.LinearLayout;
import net.minecraft.client.gui.navigation.ScreenRectangle;
import net.minecraft.client.input.MouseButtonEvent;
import net.minecraft.network.chat.Component;

import java.text.NumberFormat;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.function.BooleanSupplier;
import java.util.function.Consumer;
import java.util.function.DoubleConsumer;
import java.util.function.IntConsumer;
import java.util.function.Supplier;

public class GlobeWorldSettingsControls implements Layout {
    private static final int CONTROL_WIDTH = 310;
    private static final int DAY_LENGTH_SLIDER_WIDTH = 153;
    private static final int DAY_NIGHT_MODE_WIDTH = 153;
    private static final int ROW_SPACING = 4;
    private static final int SECTION_SPACING = 12;
    private static final int INFO_WIDTH = CONTROL_WIDTH;
    private static final int CUSTOM_MIN_TILE_SIZE_CHUNKS = TopologySettings.MIN_TILE_SIZE_CHUNKS;
    private static final int SIMPLE_MIN_TILE_SIZE_CHUNKS = 8;
    private static final int SIMPLE_ALLOW_MOBS_AT_WORLD_SPAWN_MAX_TILE_CHUNKS = 16;
    private static final int SIMPLE_SCROLLING_DAY_MIN_TILE_BLOCKS = 7_000;
    private static final String DISTANT_HORIZONS_MOD_ID = "distanthorizons";
    private static final String CURVATURE_TOOLTIP = "Curves the terrain. Comfortable is a gentler curve; "
            + "Realistic uses the full globe curve for the tile.";
    private static final String DAY_LENGTH_TOOLTIP = "Scales the length of the Minecraft day night cycle";
    private static final String ALLOW_MOBS_AT_WORLD_SPAWN_TOOLTIP = "Allows natural mobs to spawn inside vanilla's 24-block world-spawn exclusion.";
    private static final String PLAYER_MOB_SPAWN_EXCLUSION_TOOLTIP = "Minimum natural-spawn distance from the nearest non-spectator player.";
    private static final String AVOID_WATER_ONLY_SEEDS_TOOLTIP = "Rerolls random seeds whose wrapped Overworld tile appears to be all ocean. "
            + "Explicit seed text is never changed.";
    private static final String FORCE_MISSING_STRONGHOLD_TOOLTIP = "Adds a canonical stronghold if the wrapped Overworld has no canonical stronghold. "
            + "if no stronghold exists throwing an Eye of Ender will create a portal where you stand";
    private static final String FORCE_MISSING_FORTRESS_TOOLTIP = "Adds a canonical fortress if the wrapped Nether has no canonical fortress.";
    private static final String MULTIPLAYER_READ_ONLY_TEXT = "Globe World settings are controlled by the server.";
    private static final boolean DISTANT_HORIZONS_LOADED = FabricLoader.getInstance().isModLoaded(DISTANT_HORIZONS_MOD_ID);
    private static final double DISTANT_HORIZONS_EARTH_RADIUS_BLOCKS = 6_371_000.0D;
    private static final long DISTANT_HORIZONS_MIN_CURVATURE_RATIO = 50L;
    private static final long DISTANT_HORIZONS_MAX_CURVATURE_RATIO = 5_000L;
    private static final List<Double> DAY_LENGTH_PRESETS = List.of(
            GameplaySettings.DAY_LENGTH_HALF_MULTIPLIER,
            1.0D,
            2.0D,
            3.0D,
            4.0D,
            5.0D,
            6.0D,
            7.0D,
            8.0D,
            9.0D,
            GameplaySettings.DAY_LENGTH_MAX_MULTIPLIER
    );
    private static final List<TerrainMode> TERRAIN_MODE_OPTIONS = List.of(
            TerrainMode.AUTO,
            TerrainMode.COMPACT_TORUS,
            TerrainMode.EDGE_BLEND,
            TerrainMode.PERIODIC_LATTICE
    );
    private static final List<NetherSizePreset> ALL_NETHER_SIZE_PRESETS = List.of(
            NetherSizePreset.DISABLED,
            NetherSizePreset.ONE_EIGHTH,
            NetherSizePreset.ONE_FOURTH,
            NetherSizePreset.ONE_HALF,
            NetherSizePreset.SAME_SIZE,
            NetherSizePreset.DOUBLE,
            NetherSizePreset.QUADRUPLE,
            NetherSizePreset.CUSTOM
    );
    private static final List<PortalRatioPreset> PORTAL_RATIO_PRESETS = List.of(
            PortalRatioPreset.REVERSE_THIRTY_TWO,
            PortalRatioPreset.REVERSE_SIXTEEN,
            PortalRatioPreset.REVERSE_EIGHT,
            PortalRatioPreset.REVERSE_FOUR,
            PortalRatioPreset.REVERSE_TWO,
            PortalRatioPreset.ONE_TO_ONE,
            PortalRatioPreset.FORWARD_TWO,
            PortalRatioPreset.FORWARD_FOUR,
            PortalRatioPreset.FORWARD_EIGHT,
            PortalRatioPreset.FORWARD_SIXTEEN,
            PortalRatioPreset.FORWARD_THIRTY_TWO
    );
    private static final List<TilePreset> TILE_PRESETS = List.of(
            new TilePreset(8, "128 m"),
            new TilePreset(16, "256 m"),
            new TilePreset(32, "512 m"),
            new TilePreset(64, "1 km"),
            new TilePreset(128, "2 km"),
            new TilePreset(256, "4.1 km"),
            new TilePreset(512, "8.2 km"),
            new TilePreset(1024, "16.4 km (Width of Ibiza)"),
            new TilePreset(2048, "32.8 km (Length of Lake Tahoe)"),
            new TilePreset(4096, "65.5 km (Width of Singapore)"),
            new TilePreset(8192, "131 km (Width of Hawaii)"),
            new TilePreset(16384, "262 km (Width of Ireland)"),
            new TilePreset(32768, "524 km (Width of Iceland)"),
            new TilePreset(65536, "1,048 km (Length of Italy)"),
            new TilePreset(131072, "2,097 km"),
            new TilePreset(262144, "4,194 km (Width of the US)"),
            new TilePreset(524288, "8,388 km (The Moon)"),
            new TilePreset(1048576, "16,777 km"),
            new TilePreset(2097152, "33,554 km (The Earth)")
    );
    private static final List<TilePreset> SIMPLE_TILE_PRESETS = TILE_PRESETS.stream()
            .filter(preset -> preset.chunks() >= SIMPLE_MIN_TILE_SIZE_CHUNKS)
            .toList();

    private final boolean createWorld;
    private final boolean editable;
    private final Supplier<GlobeSettings> settingsGetter;
    private final Consumer<GlobeSettings> settingsSetter;
    private final List<Row> rows = new ArrayList<>();
    private final List<LayoutElement> elements = new ArrayList<>();
    private int x;
    private int y;
    private int width;
    private int height;
    private CreateMode createMode;
    private boolean updatingText;
    private boolean updatingCheckboxes;
    private Runnable layoutChangedCallback = () -> {
    };

    private CycleButton<CreateMode> createModeButton;
    private TilePresetSlider simpleTileSlider;
    private EditBox customTileField;
    private CycleButton<TerrainMode> overworldTopologyButton;
    private MultiLineTextWidget overworldInfo;
    private GlobeCurvatureSlider overworldCurvatureSlider;
    private MultiLineTextWidget overworldDistantHorizonsAdvice;
    private CycleButton<NetherSizePreset> netherSizeButton;
    private Checkbox tileNetherCheckbox;
    private EditBox customNetherTileField;
    private PortalRatioSlider portalRatioSlider;
    private CycleButton<TerrainMode> netherTopologyButton;
    private MultiLineTextWidget netherInfo;
    private GlobeCurvatureSlider netherCurvatureSlider;
    private StringWidget progressionStructuresLabel;
    private Checkbox avoidWaterOnlySeedsCheckbox;
    private Checkbox forceMissingStrongholdCheckbox;
    private Checkbox forceMissingNetherFortressCheckbox;
    private StringWidget naturalSpawningLabel;
    private Checkbox allowMobsAtWorldSpawnCheckbox;
    private PlayerMobSpawnExclusionSlider playerMobSpawnExclusionSlider;
    private StringWidget dayNightLabel;
    private CycleButton<DayNightCycleMode> dayNightCycleButton;
    private DayLengthMultiplierSlider dayLengthSlider;
    private MultiLineTextWidget multiplayerReadOnlyInfo;

    private GlobeWorldSettingsControls(
            boolean createWorld,
            boolean editable,
            Supplier<GlobeSettings> settingsGetter,
            Consumer<GlobeSettings> settingsSetter) {
        this.createWorld = createWorld;
        this.editable = editable;
        this.settingsGetter = settingsGetter;
        this.settingsSetter = settingsSetter;
        this.createMode = createWorld ? CreateMode.fromSettings(currentGlobeSettings()) : CreateMode.CUSTOM;
        build();
        refresh();
    }

    public static GlobeWorldSettingsControls createWorld(
            Supplier<GlobeSettings> settingsGetter,
            Consumer<GlobeSettings> settingsSetter) {
        return new GlobeWorldSettingsControls(true, true, settingsGetter, settingsSetter);
    }

    public static GlobeWorldSettingsControls pauseMenu(
            Supplier<GlobeSettings> settingsGetter,
            Consumer<GlobeSettings> settingsSetter) {
        return pauseMenu(settingsGetter, settingsSetter, true);
    }

    public static GlobeWorldSettingsControls pauseMenu(
            Supplier<GlobeSettings> settingsGetter,
            Consumer<GlobeSettings> settingsSetter,
            boolean editable) {
        return new GlobeWorldSettingsControls(false, editable, settingsGetter, settingsSetter);
    }

    public void setLayoutChangedCallback(Runnable layoutChangedCallback) {
        this.layoutChangedCallback = layoutChangedCallback != null ? layoutChangedCallback : () -> {
        };
    }

    private void build() {
        Minecraft minecraft = Minecraft.getInstance();

        multiplayerReadOnlyInfo = infoText(Component.literal(MULTIPLAYER_READ_ONLY_TEXT));
        addRow(multiplayerReadOnlyInfo, () -> !createWorld && !editable);

        if (createWorld) {
            createModeButton = CycleButton.<CreateMode>builder(mode -> Component.literal(mode.displayName), createMode)
                    .withValues(CreateMode.values())
                    .create(0, 0, CONTROL_WIDTH, 20, Component.literal("Globe World"), (button, mode) -> {
                        this.createMode = mode;
                        setGlobeSettings(globeSettingsForMode(mode));
                    });
            addRow(createModeButton, () -> true);
        }

        simpleTileSlider = new TilePresetSlider(0, 0, CONTROL_WIDTH, 20, currentTilePreset(), preset ->
                setSimpleTilePreset(preset));
        addRow(simpleTileSlider, () -> createWorld && createMode == CreateMode.SIMPLE, SECTION_SPACING);

        customTileField = new EditBox(minecraft.font, 110, 20, Component.literal("Overworld Tile Size"));
        customTileField.setMaxLength(7);
        customTileField.setEditable(editable);
        customTileField.setResponder(text -> {
            if (updatingText) {
                return;
            }
            try {
                int tileSize = sanitizeCustomTileInput(text);
                setTopology(currentTopology()
                        .withTileSize(tileSize)
                        .withNetherTileSize(simpleDefaultNetherTileSize(tileSize)));
            } catch (NumberFormatException ignored) {
            }
        });
        StringWidget customTileLabel = new StringWidget(
                CONTROL_WIDTH - customTileField.getWidth() - ROW_SPACING,
                customTileField.getHeight(),
                Component.literal("Overworld Tile Size (even chunks)"),
                minecraft.font
        );
        addRow(
                new LabeledInputRow(
                        customTileLabel,
                        customTileField,
                        CONTROL_WIDTH
                ),
                () -> createWorld && createMode == CreateMode.CUSTOM,
                SECTION_SPACING
        );

        overworldTopologyButton = CycleButton.<TerrainMode>builder(
                        mode -> topologyLabel(mode, TerrainMode.forOverworldTileSize(currentTopology().tileSize())),
                        currentTopology().terrainMode()
                )
                .withValues(TERRAIN_MODE_OPTIONS)
                .withTooltip(mode -> tooltip(mode.tooltip()))
                .create(0, 0, CONTROL_WIDTH, 20, Component.literal("Overworld Topology"),
                        (button, mode) -> setTopology(currentTopology().withTerrainMode(mode)));
        addRow(
                overworldTopologyButton,
                () -> createWorld && createMode == CreateMode.CUSTOM && currentTopology().enabled()
        );

        overworldInfo = infoText(Component.empty());
        addRow(overworldInfo, () -> !createWorld || currentTopology().enabled() && createMode == CreateMode.CUSTOM);

        avoidWaterOnlySeedsCheckbox = progressionCheckbox(
                "Avoid Water-Only Seeds",
                AVOID_WATER_ONLY_SEEDS_TOOLTIP,
                currentTopology().avoidWaterOnlySeeds(),
                selected -> setTopology(currentTopology().withAvoidWaterOnlySeeds(selected))
        );
        addRow(avoidWaterOnlySeedsCheckbox, () -> createWorld && createMode == CreateMode.CUSTOM && currentTopology().enabled());

        overworldCurvatureSlider = new GlobeCurvatureSlider(
                0,
                0,
                CONTROL_WIDTH,
                20,
                Component.literal("Overworld Curvature"),
                currentPresentation().curvaturePercent(),
                percent -> setPresentation(currentPresentation().withCurvaturePercent(percent))
        );
        overworldCurvatureSlider.setTooltip(tooltip(CURVATURE_TOOLTIP));
        addRow(overworldCurvatureSlider, () -> (!createWorld || createMode == CreateMode.CUSTOM) && currentTopology().enabled());

        overworldDistantHorizonsAdvice = infoText(Component.empty());
        addRow(
                overworldDistantHorizonsAdvice,
                () -> (!createWorld || createMode == CreateMode.CUSTOM)
                        && DISTANT_HORIZONS_LOADED
                        && distantHorizonsAdviceSupported(currentTopology())
        );

        netherSizeButton = CycleButton.<NetherSizePreset>builder(
                        preset -> Component.literal(preset.displayName),
                        this::netherSizePreset
                )
                .withValues(new CycleButton.ValueListSupplier<NetherSizePreset>() {
                    @Override
                    public List<NetherSizePreset> getSelectedList() {
                        return validNetherSizePresets(currentTopology());
                    }

                    @Override
                    public List<NetherSizePreset> getDefaultList() {
                        return ALL_NETHER_SIZE_PRESETS;
                    }
                })
                .withTooltip(preset -> tooltip(netherSizeTooltip(preset)))
                .create(0, 0, CONTROL_WIDTH, 20, Component.literal("Nether Size"),
                        (button, preset) -> setTopology(applyNetherSizePreset(currentTopology(), preset)));
        addSectionRow(netherSizeButton, () -> createWorld && createMode == CreateMode.SIMPLE && currentTopology().enabled());

        tileNetherCheckbox = progressionCheckbox(
                "Tile Nether",
                "Enables Nether wrapping and the custom Nether controls.",
                currentTopology().netherEnabled(),
                selected -> setTopology(currentTopology().withNetherMode(selected ? TilingMode.SQUARE : TilingMode.DISABLED))
        );
        addSectionRow(tileNetherCheckbox, () -> createWorld && createMode == CreateMode.CUSTOM && currentTopology().enabled());

        customNetherTileField = new EditBox(minecraft.font, 110, 20, Component.literal("Nether Tile Size"));
        customNetherTileField.setMaxLength(7);
        customNetherTileField.setEditable(editable);
        customNetherTileField.setResponder(text -> {
            if (updatingText) {
                return;
            }
            try {
                int tileSize = sanitizeCustomTileInput(text);
                setTopology(currentTopology()
                        .withNetherMode(TilingMode.SQUARE)
                        .withNetherTileSize(tileSize));
            } catch (NumberFormatException ignored) {
            }
        });
        StringWidget customNetherTileLabel = new StringWidget(
                CONTROL_WIDTH - customNetherTileField.getWidth() - ROW_SPACING,
                customNetherTileField.getHeight(),
                Component.literal("Nether Tile Size (even chunks)"),
                minecraft.font
        );
        addRow(
                new LabeledInputRow(
                        customNetherTileLabel,
                        customNetherTileField,
                        CONTROL_WIDTH
                ),
                () -> createWorld && createMode == CreateMode.CUSTOM && currentTopology().enabled()
        );

        portalRatioSlider = new PortalRatioSlider(
                0,
                0,
                CONTROL_WIDTH,
                20,
                portalRatioPreset(),
                preset -> setTopology(currentTopology().withNetherPortalScale(preset.numerator, preset.denominator))
        );
        addRow(portalRatioSlider, () -> createWorld && createMode == CreateMode.CUSTOM && currentTopology().enabled());

        netherTopologyButton = CycleButton.<TerrainMode>builder(
                        mode -> topologyLabel(mode, TerrainMode.forNetherTileSize(currentTopology().netherTileSize())),
                        currentTopology().netherTerrainMode()
                )
                .withValues(TERRAIN_MODE_OPTIONS)
                .withTooltip(mode -> tooltip(mode.tooltip()))
                .create(0, 0, CONTROL_WIDTH, 20, Component.literal("Nether Topology"),
                        (button, mode) -> setTopology(currentTopology().withNetherTerrainMode(mode)));
        addRow(
                netherTopologyButton,
                () -> createWorld && createMode == CreateMode.CUSTOM && currentTopology().netherEnabled()
        );

        netherInfo = infoText(Component.empty());
        addRow(netherInfo, () -> !createWorld || currentTopology().enabled() && createMode == CreateMode.CUSTOM, createWorld ? ROW_SPACING : SECTION_SPACING);

        netherCurvatureSlider = new GlobeCurvatureSlider(
                0,
                0,
                CONTROL_WIDTH,
                20,
                Component.literal("Nether Curvature"),
                currentPresentation().netherCurvaturePercent(),
                percent -> setPresentation(currentPresentation().withNetherCurvaturePercent(percent))
        );
        netherCurvatureSlider.setTooltip(tooltip(CURVATURE_TOOLTIP));
        addRow(netherCurvatureSlider, () -> (!createWorld || createMode == CreateMode.CUSTOM) && currentTopology().enabled());

        progressionStructuresLabel = sectionLabel("Progression Structures", minecraft);
        addSectionRow(progressionStructuresLabel, () -> createWorld && createMode == CreateMode.CUSTOM && progressionStructuresVisible(currentTopology()));

        forceMissingStrongholdCheckbox = progressionCheckbox(
                "Force Missing Stronghold",
                FORCE_MISSING_STRONGHOLD_TOOLTIP,
                currentTopology().forceMissingStronghold(),
                selected -> setTopology(currentTopology().withForceMissingStronghold(selected))
        );
        addRow(forceMissingStrongholdCheckbox, () -> createWorld && createMode == CreateMode.CUSTOM && currentTopology().enabled());

        forceMissingNetherFortressCheckbox = progressionCheckbox(
                "Force Missing Fortress",
                FORCE_MISSING_FORTRESS_TOOLTIP,
                currentTopology().forceMissingNetherFortress(),
                selected -> setTopology(currentTopology().withForceMissingNetherFortress(selected))
        );
        addRow(forceMissingNetherFortressCheckbox, () -> createWorld && createMode == CreateMode.CUSTOM && currentTopology().netherEnabled());

        naturalSpawningLabel = sectionLabel("Mob Spawning", minecraft);
        addSectionRow(naturalSpawningLabel, () -> customGameplayControlsVisible());

        allowMobsAtWorldSpawnCheckbox = progressionCheckbox(
                "Allow Mobs At World Spawn",
                ALLOW_MOBS_AT_WORLD_SPAWN_TOOLTIP,
                currentGameplay().allowMobsAtWorldSpawn(),
                selected -> setGameplay(currentGameplay().withAllowMobsAtWorldSpawn(selected))
        );
        addRow(allowMobsAtWorldSpawnCheckbox, () -> customGameplayControlsVisible());

        playerMobSpawnExclusionSlider = new PlayerMobSpawnExclusionSlider(
                0,
                0,
                CONTROL_WIDTH,
                20,
                currentGameplay().playerMobSpawnExclusionBlocks(),
                blocks -> setGameplay(currentGameplay().withPlayerMobSpawnExclusionBlocks(blocks))
        );
        playerMobSpawnExclusionSlider.setTooltip(tooltip(PLAYER_MOB_SPAWN_EXCLUSION_TOOLTIP));
        addRow(playerMobSpawnExclusionSlider, () -> customGameplayControlsVisible());

        dayNightLabel = sectionLabel("Day/Night", minecraft);
        addSectionRow(dayNightLabel, () -> currentTopology().enabled() && (!createWorld || createMode == CreateMode.CUSTOM));

        dayLengthSlider = new DayLengthMultiplierSlider(
                0,
                0,
                DAY_LENGTH_SLIDER_WIDTH,
                20,
                currentGameplay().dayLengthMultiplier(),
                multiplier -> setGameplay(currentGameplay().withDayLengthMultiplier(multiplier))
        );
        dayLengthSlider.setTooltip(tooltip(DAY_LENGTH_TOOLTIP));

        dayNightCycleButton = CycleButton.<DayNightCycleMode>builder(mode -> Component.literal(mode.displayName()), currentGameplay().dayNightCycleMode())
                .withValues(DayNightCycleMode.values())
                .withTooltip(mode -> tooltip(dayCycleTooltip(mode)))
                .create(0, 0, DAY_NIGHT_MODE_WIDTH, 20, Component.literal("Day Cycle"),
                        (button, mode) -> setGameplay(currentGameplay().withDayNightCycleMode(mode)));

        LinearLayout dayNightRow = LinearLayout.horizontal().spacing(ROW_SPACING);
        dayNightRow.addChild(dayLengthSlider);
        dayNightRow.addChild(dayNightCycleButton);
        addRow(dayNightRow, () -> currentTopology().enabled() && (!createWorld || createMode == CreateMode.CUSTOM));
    }

    private static StringWidget sectionLabel(String label, Minecraft minecraft) {
        return new StringWidget(CONTROL_WIDTH, 20, Component.literal(label), minecraft.font);
    }

    private Checkbox progressionCheckbox(
            String label,
            String tooltipText,
            boolean selected,
            Consumer<Boolean> onChanged) {
        Checkbox checkbox = Checkbox.builder(Component.literal(label), Minecraft.getInstance().font)
                .selected(selected)
                .maxWidth(CONTROL_WIDTH)
                .onValueChange((button, value) -> {
                    if (!updatingCheckboxes) {
                        onChanged.accept(value);
                    }
                })
                .build();
        checkbox.setTooltip(tooltip(tooltipText));
        return checkbox;
    }

    private static Tooltip tooltip(String text) {
        return Tooltip.create(Component.literal(text));
    }

    private static Component topologyLabel(TerrainMode mode, TerrainMode autoMode) {
        if (mode == TerrainMode.AUTO) {
            return Component.literal("Auto (%s)".formatted(autoMode.displayName()));
        }
        return Component.literal(mode.displayName());
    }

    private static String dayCycleTooltip(DayNightCycleMode mode) {
        return switch (mode) {
            case VANILLA -> "Vanilla keeps the same time of day everywhere.";
            case SCROLLING -> "Scrolling adds time zones along east-west travel; north-south stays consistent.";
        };
    }

    private static String scrollingDayCycleDisabledTooltip() {
        return "Scrolling day cycle is disabled in simple mode below %s blocks because a running player can keep pace with the sun."
                .formatted(number(SIMPLE_SCROLLING_DAY_MIN_TILE_BLOCKS));
    }

    private String netherSizeTooltip(NetherSizePreset preset) {
        if (preset == NetherSizePreset.DISABLED) {
            return "Uses vanilla infinite Nether generation without wrapping.";
        }
        if (createMode == CreateMode.SIMPLE) {
            return "Changes both the Nether tile size and the portal travel distance.";
        }
        return "Changes the Nether tile size. Portal travel distance is controlled separately.";
    }

    private static String portalRatioTooltip(PortalRatioPreset preset) {
        if (preset.numerator == 1 && preset.denominator == 1) {
            return "One Nether block maps to one Overworld block.";
        }
        if (preset.denominator == 1) {
            return "One Nether block maps to %d Overworld blocks.".formatted(preset.numerator);
        }
        return "%d Nether blocks map to one Overworld block.".formatted(preset.denominator);
    }

    private MultiLineTextWidget infoText(Component text) {
        return new MultiLineTextWidget(text, Minecraft.getInstance().font).setMaxWidth(INFO_WIDTH).setMaxRows(3);
    }

    private GlobeSettings currentGlobeSettings() {
        GlobeSettings settings = settingsGetter.get();
        return settings == null ? GlobeSettings.DEFAULT : settings;
    }

    private TopologySettings currentTopology() {
        return currentGlobeSettings().topology();
    }

    private PresentationSettings currentPresentation() {
        return currentGlobeSettings().presentation();
    }

    private GameplaySettings currentGameplay() {
        return currentGlobeSettings().gameplay();
    }

    private void setGlobeSettings(GlobeSettings settings) {
        settingsSetter.accept(settings);
        refresh();
    }

    private void setTopology(TopologySettings topology) {
        setGlobeSettings(currentGlobeSettings().withTopology(topology));
    }

    private void setPresentation(PresentationSettings presentation) {
        setGlobeSettings(currentGlobeSettings().withPresentation(presentation));
    }

    private void setGameplay(GameplaySettings gameplay) {
        setGlobeSettings(currentGlobeSettings().withGameplay(gameplay));
    }

    private GlobeSettings globeSettingsForMode(CreateMode mode) {
        return switch (mode) {
            case DISABLED -> new GlobeSettings(
                    TopologySettings.DEFAULT,
                    PresentationSettings.DISABLED,
                    GameplaySettings.DEFAULT
            );
            case SIMPLE, CUSTOM -> GlobeSettings.DEFAULT.withTopology(TopologySettings.DEFAULT
                    .withMode(TilingMode.SQUARE)
                    .withNetherMode(TilingMode.SQUARE)
                    .withNetherTileSize(simpleDefaultNetherTileSize(TopologySettings.DEFAULT.tileSize()))
                    .withNetherPortalScale(
                            TopologySettings.DEFAULT_NETHER_PORTAL_SCALE_NUMERATOR,
                            TopologySettings.DEFAULT_NETHER_PORTAL_SCALE_DENOMINATOR
                    ));
        };
    }

    private GlobeSettings simpleSettingsForTileSize(int tileSize) {
        GlobeSettings globeSettings = globeSettingsForMode(CreateMode.SIMPLE);
        TopologySettings topology = globeSettings.topology()
                .withTileSize(tileSize)
                .withNetherTileSize(simpleDefaultNetherTileSize(tileSize));
        topology = withPortalScaleMatchingTileRatio(topology);
        globeSettings = globeSettings.withTopology(topology);
        boolean preserveCustomWorldSpawnSpawning = currentGameplay().allowMobsAtWorldSpawn()
                && !simpleAllowMobsAtWorldSpawn(currentTopology().tileSize());
        globeSettings = globeSettings.withGameplay(globeSettings.gameplay()
                .withPlayerMobSpawnExclusionBlocks(GameplaySettings.PLAYER_MOB_SPAWN_EXCLUSION_DEFAULT_BLOCKS)
                .withAllowMobsAtWorldSpawn(simpleAllowMobsAtWorldSpawn(tileSize) || preserveCustomWorldSpawnSpawning));
        if (!simpleScrollingDayCycleSupported(topology)) {
            globeSettings = globeSettings.withGameplay(globeSettings.gameplay().withDayNightCycleMode(DayNightCycleMode.VANILLA));
        }
        return globeSettings;
    }

    private void setSimpleTilePreset(TilePreset preset) {
        if (currentTopology().tileSize() == preset.chunks()) {
            return;
        }
        setGlobeSettings(simpleSettingsForTileSize(preset.chunks()));
    }

    private void refresh() {
        GlobeSettings globeSettings = currentGlobeSettings();
        TopologySettings topology = globeSettings.topology();
        PresentationSettings presentation = globeSettings.presentation();
        GameplaySettings gameplay = globeSettings.gameplay();
        if (createWorld && createMode == CreateMode.SIMPLE) {
            TopologySettings simpleTopology = topology.enabled() && !topology.avoidWaterOnlySeeds()
                    ? topology.withAvoidWaterOnlySeeds(true)
                    : topology;
            GameplaySettings simpleGameplay = gameplay
                    .withPlayerMobSpawnExclusionBlocks(GameplaySettings.PLAYER_MOB_SPAWN_EXCLUSION_DEFAULT_BLOCKS);
            if (simpleAllowMobsAtWorldSpawn(simpleTopology.tileSize())) {
                simpleGameplay = simpleGameplay.withAllowMobsAtWorldSpawn(true);
            }
            if (!simpleScrollingDayCycleSupported(simpleTopology)
                    && simpleGameplay.dayNightCycleMode() == DayNightCycleMode.SCROLLING) {
                simpleGameplay = simpleGameplay.withDayNightCycleMode(DayNightCycleMode.VANILLA);
            }
            if (!simpleTopology.equals(topology) || !simpleGameplay.equals(gameplay)) {
                globeSettings = globeSettings
                        .withTopology(simpleTopology)
                        .withGameplay(simpleGameplay);
                settingsSetter.accept(globeSettings);
                topology = globeSettings.topology();
                presentation = globeSettings.presentation();
                gameplay = globeSettings.gameplay();
            }
        }

        if (createWorld && topology.enabled() && createMode == CreateMode.DISABLED) {
            createMode = CreateMode.fromSettings(globeSettings);
        }

        if (createModeButton != null) {
            createModeButton.setValue(createMode);
        }
        if (simpleTileSlider != null) {
            simpleTileSlider.setPreset(currentTilePreset());
        }
        if (customTileField != null) {
            setTileFieldValue(customTileField, topology.tileSize());
        }
        overworldTopologyButton.setValue(topology.terrainMode());
        overworldInfo.setMessage(overworldInfo(topology));
        overworldCurvatureSlider.setPercent(presentation.curvaturePercent());
        overworldCurvatureSlider.active = topology.enabled();
        overworldDistantHorizonsAdvice.setMessage(distantHorizonsAdvice(topology));
        netherSizeButton.setValue(netherSizePreset(topology));
        syncCheckbox(tileNetherCheckbox, topology.netherEnabled());
        if (customNetherTileField != null) {
            setTileFieldValue(customNetherTileField, topology.netherTileSize());
            customNetherTileField.setEditable(editable && topology.netherEnabled());
            customNetherTileField.active = topology.netherEnabled();
        }
        portalRatioSlider.setPreset(portalRatioPreset(topology));
        portalRatioSlider.active = topology.netherEnabled();
        netherTopologyButton.setValue(topology.netherTerrainMode());
        netherInfo.setMessage(netherInfo(topology));
        netherCurvatureSlider.setPercent(presentation.netherCurvaturePercent());
        netherCurvatureSlider.active = topology.netherEnabled();
        syncCheckbox(avoidWaterOnlySeedsCheckbox, topology.avoidWaterOnlySeeds());
        avoidWaterOnlySeedsCheckbox.active = createWorld && editable && topology.enabled();
        syncCheckbox(forceMissingStrongholdCheckbox, topology.forceMissingStronghold());
        syncCheckbox(forceMissingNetherFortressCheckbox, topology.forceMissingNetherFortress());
        forceMissingStrongholdCheckbox.active = createWorld && editable && topology.enabled();
        forceMissingNetherFortressCheckbox.active = createWorld && editable && topology.netherEnabled();
        syncCheckbox(allowMobsAtWorldSpawnCheckbox, gameplay.allowMobsAtWorldSpawn());
        allowMobsAtWorldSpawnCheckbox.active = editable && topology.enabled();
        playerMobSpawnExclusionSlider.setBlocks(gameplay.playerMobSpawnExclusionBlocks());
        playerMobSpawnExclusionSlider.active = editable && topology.enabled();
        dayNightCycleButton.setValue(gameplay.dayNightCycleMode());
        boolean dayNightCycleSelectable = topology.enabled() && simpleScrollingDayCycleSupported(topology);
        dayNightCycleButton.active = dayNightCycleSelectable;
        if (!dayNightCycleSelectable) {
            dayNightCycleButton.setTooltip(tooltip(scrollingDayCycleDisabledTooltip()));
        }
        dayLengthSlider.setMultiplier(gameplay.dayLengthMultiplier());
        dayLengthSlider.active = topology.enabled();

        applyEditability();
        arrange();
        layoutChangedCallback.run();
    }

    private void applyEditability() {
        if (editable) {
            return;
        }

        if (createModeButton != null) {
            createModeButton.active = false;
        }
        if (simpleTileSlider != null) {
            simpleTileSlider.active = false;
        }
        if (customTileField != null) {
            customTileField.setEditable(false);
            customTileField.active = false;
        }
        overworldTopologyButton.active = false;
        overworldCurvatureSlider.active = false;
        netherSizeButton.active = false;
        tileNetherCheckbox.active = false;
        if (customNetherTileField != null) {
            customNetherTileField.setEditable(false);
            customNetherTileField.active = false;
        }
        portalRatioSlider.active = false;
        netherTopologyButton.active = false;
        netherCurvatureSlider.active = false;
        avoidWaterOnlySeedsCheckbox.active = false;
        forceMissingStrongholdCheckbox.active = false;
        forceMissingNetherFortressCheckbox.active = false;
        allowMobsAtWorldSpawnCheckbox.active = false;
        playerMobSpawnExclusionSlider.active = false;
        dayNightCycleButton.active = false;
        dayLengthSlider.active = false;
    }

    private void syncCheckbox(Checkbox checkbox, boolean selected) {
        if (checkbox.selected() == selected) {
            return;
        }
        updatingCheckboxes = true;
        try {
            checkbox.onPress(null);
        } finally {
            updatingCheckboxes = false;
        }
    }

    private boolean progressionStructuresVisible(TopologySettings topology) {
        return topology.enabled() || topology.netherEnabled();
    }

    private boolean customGameplayControlsVisible() {
        return currentTopology().enabled() && (!createWorld || createMode == CreateMode.CUSTOM);
    }

    private Component overworldInfo(TopologySettings topology) {
        if (!topology.enabled()) {
            return Component.literal("Overworld tile: Disabled");
        }
        return Component.literal("Overworld tile: ")
                .append(tileSummary(topology.tileSize(), effectiveOverworldTerrainMode(topology)));
    }

    private Component netherInfo(TopologySettings topology) {
        if (!topology.netherEnabled()) {
            return Component.literal("Nether tile: Infinite");
        }
        return Component.literal("Nether tile: ")
                .append(tileSummary(topology.netherTileSize(), effectiveNetherTerrainMode(topology)))
                .append(", portal ")
                .append(Component.literal(topology.netherPortalScaleSummaryLabel()));
    }

    private static TerrainMode effectiveOverworldTerrainMode(TopologySettings topology) {
        return effectiveTerrainMode(topology.terrainMode(), TerrainMode.forOverworldTileSize(topology.tileSize()));
    }

    private static TerrainMode effectiveNetherTerrainMode(TopologySettings topology) {
        return effectiveTerrainMode(topology.netherTerrainMode(), TerrainMode.forNetherTileSize(topology.netherTileSize()));
    }

    private static TerrainMode effectiveTerrainMode(TerrainMode mode, TerrainMode autoMode) {
        return mode == TerrainMode.AUTO ? autoMode : mode;
    }

    private Component tileSummary(int chunks, TerrainMode terrainMode) {
        long blocks = (long) chunks * 16L;
        return Component.literal("%s, %s chunks, %s terrain".formatted(
                formatBlocks(blocks),
                number(chunks),
                terrainMode.displayName()
        ));
    }

    private Component distantHorizonsAdvice(TopologySettings topology) {
        long ratio = distantHorizonsCurveRatio(topology.tileSize());
        return Component.literal("Set Distant Horizons Earth curvature to %s for realism.".formatted(number(ratio)));
    }

    private static boolean distantHorizonsAdviceSupported(TopologySettings topology) {
        if (!topology.enabled()) {
            return false;
        }

        long ratio = distantHorizonsCurveRatio(topology.tileSize());
        return ratio >= DISTANT_HORIZONS_MIN_CURVATURE_RATIO
                && ratio <= DISTANT_HORIZONS_MAX_CURVATURE_RATIO;
    }

    private boolean simpleScrollingDayCycleSupported(TopologySettings topology) {
        return !createWorld
                || createMode != CreateMode.SIMPLE
                || (long) topology.tileSize() * 16L >= SIMPLE_SCROLLING_DAY_MIN_TILE_BLOCKS;
    }

    private static boolean simpleAllowMobsAtWorldSpawn(int tileSizeChunks) {
        return tileSizeChunks <= SIMPLE_ALLOW_MOBS_AT_WORLD_SPAWN_MAX_TILE_CHUNKS;
    }

    private static long distantHorizonsCurveRatio(int tileSizeChunks) {
        double radius = GlobeCurvature.curvatureRadiusBlocks(
                tileSizeChunks,
                PresentationSettings.CURVATURE_REALISTIC_PERCENT
        );
        if (radius <= 0.0D) {
            return 0L;
        }
        return Math.max(1L, Math.round(DISTANT_HORIZONS_EARTH_RADIUS_BLOCKS / radius));
    }

    private static String formatBlocks(long blocks) {
        if (blocks < 1000L) {
            return number(blocks) + " m";
        }
        double kilometers = blocks / 1000.0D;
        if (kilometers < 10.0D && Math.abs(kilometers - Math.rint(kilometers)) > 0.05D) {
            return String.format(Locale.ROOT, "%.1f km", kilometers);
        }
        if (kilometers < 100.0D) {
            return String.format(Locale.ROOT, "%.1f km", kilometers);
        }
        return number(Math.round(kilometers)) + " km";
    }

    private static String number(long value) {
        return NumberFormat.getIntegerInstance(Locale.US).format(value);
    }

    private static int sanitizeCustomTileInput(String text) {
        return TopologySettings.sanitizeTileSize(Integer.parseInt(text));
    }

    private void setTileFieldValue(EditBox field, int tileSize) {
        String sanitizedValue = Integer.toString(tileSize);
        if (field.isFocused() && !field.getValue().isBlank()) {
            return;
        }
        if (field.getValue().equals(sanitizedValue)) {
            return;
        }
        updatingText = true;
        try {
            field.setValue(sanitizedValue);
        } finally {
            updatingText = false;
        }
    }

    private static String multiplierLabel(double multiplier) {
        double sanitized = GameplaySettings.sanitizeDayLengthMultiplier(multiplier);
        if (sanitized == GameplaySettings.DAY_LENGTH_HALF_MULTIPLIER) {
            return "0.5x";
        }
        return "%dx".formatted((int) sanitized);
    }

    private TilePreset currentTilePreset() {
        int tileSize = currentTopology().tileSize();
        for (TilePreset preset : SIMPLE_TILE_PRESETS) {
            if (preset.chunks() == tileSize) {
                return preset;
            }
        }
        return SIMPLE_TILE_PRESETS.stream()
                .min((a, b) -> Integer.compare(Math.abs(a.chunks() - tileSize), Math.abs(b.chunks() - tileSize)))
                .orElse(SIMPLE_TILE_PRESETS.get(0));
    }

    private NetherSizePreset netherSizePreset() {
        return netherSizePreset(currentTopology());
    }

    private NetherSizePreset netherSizePreset(TopologySettings topology) {
        if (!topology.netherEnabled()) {
            return NetherSizePreset.DISABLED;
        }

        for (NetherSizePreset preset : validNetherSizePresets(topology)) {
            if (preset != NetherSizePreset.DISABLED
                    && preset != NetherSizePreset.CUSTOM
                    && preset.tileSize(topology.tileSize()) == topology.netherTileSize()) {
                return preset;
            }
        }
        return createMode == CreateMode.CUSTOM ? NetherSizePreset.CUSTOM : NetherSizePreset.SAME_SIZE;
    }

    private List<NetherSizePreset> validNetherSizePresets(TopologySettings topology) {
        List<NetherSizePreset> presets = new ArrayList<>();
        presets.add(NetherSizePreset.DISABLED);
        int minTileSize = createMode == CreateMode.SIMPLE ? SIMPLE_MIN_TILE_SIZE_CHUNKS : CUSTOM_MIN_TILE_SIZE_CHUNKS;
        for (NetherSizePreset preset : NetherSizePreset.values()) {
            if (preset == NetherSizePreset.DISABLED || preset == NetherSizePreset.CUSTOM) {
                continue;
            }
            int tileSize = preset.tileSize(topology.tileSize());
            if (tileSize >= minTileSize && preset.matchesExactly(topology.tileSize())) {
                presets.add(preset);
            }
        }
        if (createMode == CreateMode.CUSTOM) {
            presets.add(NetherSizePreset.CUSTOM);
        }
        return presets;
    }

    private TopologySettings applyNetherSizePreset(TopologySettings topology, NetherSizePreset preset) {
        if (preset == NetherSizePreset.DISABLED) {
            return topology.withNetherMode(TilingMode.DISABLED);
        }
        if (preset == NetherSizePreset.CUSTOM) {
            return topology.withNetherMode(TilingMode.SQUARE);
        }
        int tileSize = preset.tileSize(topology.tileSize());
        TopologySettings updated = topology.withNetherMode(TilingMode.SQUARE).withNetherTileSize(tileSize);
        if (createMode != CreateMode.SIMPLE) {
            return updated;
        }
        return withPortalScaleMatchingTileRatio(updated);
    }

    private PortalRatioPreset portalRatioPreset() {
        return portalRatioPreset(currentTopology());
    }

    private static PortalRatioPreset portalRatioPreset(TopologySettings topology) {
        for (PortalRatioPreset preset : PORTAL_RATIO_PRESETS) {
            if (preset.numerator == topology.netherPortalScaleNumerator()
                    && preset.denominator == topology.netherPortalScaleDenominator()) {
                return preset;
            }
        }
        return PortalRatioPreset.FORWARD_EIGHT;
    }

    private static int simpleDefaultNetherTileSize(int overworldTileSize) {
        return Math.max(SIMPLE_MIN_TILE_SIZE_CHUNKS, TopologySettings.defaultNetherTileSize(overworldTileSize));
    }

    private static TopologySettings withPortalScaleMatchingTileRatio(TopologySettings topology) {
        PortalRatioPreset preset = portalRatioPresetForTileRatio(topology.tileSize(), topology.netherTileSize());
        return topology.withNetherPortalScale(preset.numerator, preset.denominator);
    }

    private static PortalRatioPreset portalRatioPresetForTileRatio(int overworldTileSize, int netherTileSize) {
        for (PortalRatioPreset preset : PORTAL_RATIO_PRESETS) {
            if ((long) netherTileSize * (long) preset.numerator
                    == (long) overworldTileSize * (long) preset.denominator) {
                return preset;
            }
        }
        return PortalRatioPreset.FORWARD_EIGHT;
    }

    private static boolean isSimpleNetherSize(TopologySettings topology) {
        if (!topology.netherEnabled()) {
            return true;
        }
        if (portalRatioPreset(topology) != portalRatioPresetForTileRatio(topology.tileSize(), topology.netherTileSize())) {
            return false;
        }
        if (topology.netherTileSize() < SIMPLE_MIN_TILE_SIZE_CHUNKS) {
            return false;
        }
        for (NetherSizePreset preset : NetherSizePreset.values()) {
            if (preset == NetherSizePreset.DISABLED || preset == NetherSizePreset.CUSTOM) {
                continue;
            }
            int tileSize = preset.tileSize(topology.tileSize());
            if (tileSize >= SIMPLE_MIN_TILE_SIZE_CHUNKS
                    && preset.matchesExactly(topology.tileSize())
                    && tileSize == topology.netherTileSize()) {
                return true;
            }
        }
        return false;
    }

    private void addRow(LayoutElement element, BooleanSupplier visible) {
        addRow(element, visible, ROW_SPACING);
    }

    private void addSectionRow(LayoutElement element, BooleanSupplier visible) {
        addRow(element, visible, SECTION_SPACING);
    }

    private void addRow(LayoutElement element, BooleanSupplier visible, int topSpacing) {
        rows.add(new Row(element, visible, topSpacing));
        elements.add(element);
    }

    private void arrange() {
        int currentY = y;
        int maxWidth = 0;
        boolean first = true;

        for (Row row : rows) {
            boolean visible = row.visible.getAsBoolean();
            row.element.visitWidgets(widget -> widget.visible = visible);
            if (!visible) {
                continue;
            }
            if (!first) {
                currentY += row.topSpacing();
            }
            row.element.setPosition(x, currentY);
            if (row.element instanceof Layout layout) {
                layout.arrangeElements();
            }
            currentY += row.element.getHeight();
            maxWidth = Math.max(maxWidth, row.element.getWidth());
            first = false;
        }

        this.width = maxWidth;
        this.height = first ? 0 : currentY - y;
    }

    @Override
    public void setX(int x) {
        this.x = x;
        arrange();
    }

    @Override
    public void setY(int y) {
        this.y = y;
        arrange();
    }

    @Override
    public int getX() {
        return x;
    }

    @Override
    public int getY() {
        return y;
    }

    @Override
    public int getWidth() {
        return width;
    }

    @Override
    public int getHeight() {
        return height;
    }

    @Override
    public ScreenRectangle getRectangle() {
        return new ScreenRectangle(x, y, width, height);
    }

    @Override
    public void visitChildren(Consumer<LayoutElement> layoutElementVisitor) {
        elements.forEach(layoutElementVisitor);
    }

    private record Row(LayoutElement element, BooleanSupplier visible, int topSpacing) {
    }

    private static class LabeledInputRow implements LayoutElement {
        private final StringWidget label;
        private final EditBox input;
        private final int width;
        private int x;
        private int y;

        private LabeledInputRow(StringWidget label, EditBox input, int width) {
            this.label = label;
            this.input = input;
            this.width = width;
        }

        @Override
        public void setX(int x) {
            this.x = x;
            this.label.setX(x);
            this.input.setX(x + this.width - this.input.getWidth());
        }

        @Override
        public void setY(int y) {
            this.y = y;
            this.label.setY(y);
            this.input.setY(y);
        }

        @Override
        public int getX() {
            return x;
        }

        @Override
        public int getY() {
            return y;
        }

        @Override
        public int getWidth() {
            return width;
        }

        @Override
        public int getHeight() {
            return Math.max(label.getHeight(), input.getHeight());
        }

        @Override
        public void visitWidgets(Consumer<AbstractWidget> widgetVisitor) {
            widgetVisitor.accept(label);
            widgetVisitor.accept(input);
        }
    }

    private record TilePreset(int chunks, String label) {
    }

    private enum CreateMode {
        DISABLED("Disabled"),
        SIMPLE("Enabled - Simple"),
        CUSTOM("Enabled - Custom");

        private final String displayName;

        CreateMode(String displayName) {
            this.displayName = displayName;
        }

        private static CreateMode fromSettings(GlobeSettings settings) {
            TopologySettings topology = settings.topology();
            if (!topology.enabled()) {
                return DISABLED;
            }
            if (topology.terrainMode() != TerrainMode.AUTO || topology.netherTerrainMode() != TerrainMode.AUTO) {
                return CUSTOM;
            }
            return SIMPLE_TILE_PRESETS.stream().anyMatch(preset -> preset.chunks() == topology.tileSize())
                    && isSimpleNetherSize(topology) ? SIMPLE : CUSTOM;
        }
    }

    private static class TilePresetSlider extends AbstractSliderButton {
        private final Consumer<TilePreset> onValueChanged;
        private boolean changingWithMouse;
        private TilePreset pendingPreset;

        private TilePresetSlider(int x, int y, int width, int height, TilePreset initialPreset, Consumer<TilePreset> onValueChanged) {
            super(x, y, width, height, Component.empty(), valueFromPreset(initialPreset));
            this.onValueChanged = onValueChanged;
            this.pendingPreset = presetFromValue(this.value);
            updateMessage();
        }

        private void setPreset(TilePreset preset) {
            this.value = valueFromPreset(preset);
            this.pendingPreset = presetFromValue(this.value);
            updateMessage();
        }

        @Override
        protected void updateMessage() {
            this.setMessage(Component.literal("Overworld Tile Size: ").append(Component.literal(presetFromValue(this.value).label())));
        }

        @Override
        protected void applyValue() {
            TilePreset preset = presetFromValue(this.value);
            this.value = valueFromPreset(preset);
            if (this.changingWithMouse) {
                this.pendingPreset = preset;
            } else {
                this.onValueChanged.accept(preset);
            }
        }

        @Override
        public void onClick(MouseButtonEvent event, boolean doubleClick) {
            this.changingWithMouse = true;
            this.pendingPreset = presetFromValue(this.value);
            super.onClick(event, doubleClick);
        }

        @Override
        public void onRelease(MouseButtonEvent event) {
            super.onRelease(event);
            this.changingWithMouse = false;
            TilePreset preset = presetFromValue(this.value);
            this.pendingPreset = preset;
            this.onValueChanged.accept(preset);
        }

        private static double valueFromPreset(TilePreset preset) {
            int index = SIMPLE_TILE_PRESETS.indexOf(preset);
            if (index < 0 || SIMPLE_TILE_PRESETS.size() <= 1) {
                return 0.0D;
            }
            return (double) index / (double) (SIMPLE_TILE_PRESETS.size() - 1);
        }

        private static TilePreset presetFromValue(double value) {
            int index = (int) Math.round(Math.clamp(value, 0.0D, 1.0D) * (SIMPLE_TILE_PRESETS.size() - 1));
            return SIMPLE_TILE_PRESETS.get(index);
        }
    }

    private static class DayLengthMultiplierSlider extends AbstractSliderButton {
        private final DoubleConsumer onValueChanged;
        private boolean changingWithMouse;
        private double pendingMultiplier;

        private DayLengthMultiplierSlider(
                int x,
                int y,
                int width,
                int height,
                double initialMultiplier,
                DoubleConsumer onValueChanged) {
            super(x, y, width, height, Component.empty(), valueFromMultiplier(initialMultiplier));
            this.onValueChanged = onValueChanged;
            this.pendingMultiplier = multiplierFromValue(this.value);
            updateMessage();
        }

        private void setMultiplier(double multiplier) {
            this.value = valueFromMultiplier(multiplier);
            this.pendingMultiplier = multiplierFromValue(this.value);
            updateMessage();
        }

        @Override
        protected void updateMessage() {
            this.setMessage(Component.literal("Day Length: ").append(Component.literal(multiplierLabel(multiplierFromValue(this.value)))));
        }

        @Override
        protected void applyValue() {
            double multiplier = multiplierFromValue(this.value);
            this.value = valueFromMultiplier(multiplier);
            if (this.changingWithMouse) {
                this.pendingMultiplier = multiplier;
            } else {
                this.onValueChanged.accept(multiplier);
            }
        }

        @Override
        public void onClick(MouseButtonEvent event, boolean doubleClick) {
            this.changingWithMouse = true;
            this.pendingMultiplier = multiplierFromValue(this.value);
            super.onClick(event, doubleClick);
        }

        @Override
        public void onRelease(MouseButtonEvent event) {
            super.onRelease(event);
            this.changingWithMouse = false;
            double multiplier = multiplierFromValue(this.value);
            this.pendingMultiplier = multiplier;
            this.onValueChanged.accept(multiplier);
        }

        private static double valueFromMultiplier(double multiplier) {
            double sanitized = GameplaySettings.sanitizeDayLengthMultiplier(multiplier);
            int index = DAY_LENGTH_PRESETS.indexOf(sanitized);
            if (index < 0 || DAY_LENGTH_PRESETS.size() <= 1) {
                return 0.0D;
            }
            return (double) index / (double) (DAY_LENGTH_PRESETS.size() - 1);
        }

        private static double multiplierFromValue(double value) {
            int index = (int) Math.round(Math.clamp(value, 0.0D, 1.0D) * (DAY_LENGTH_PRESETS.size() - 1));
            return DAY_LENGTH_PRESETS.get(index);
        }
    }

    private static class PlayerMobSpawnExclusionSlider extends AbstractSliderButton {
        private final IntConsumer onValueChanged;
        private boolean changingWithMouse;
        private int pendingBlocks;

        private PlayerMobSpawnExclusionSlider(
                int x,
                int y,
                int width,
                int height,
                int initialBlocks,
                IntConsumer onValueChanged) {
            super(x, y, width, height, Component.empty(), valueFromBlocks(initialBlocks));
            this.onValueChanged = onValueChanged;
            this.pendingBlocks = blocksFromValue(this.value);
            updateMessage();
        }

        private void setBlocks(int blocks) {
            this.value = valueFromBlocks(blocks);
            this.pendingBlocks = blocksFromValue(this.value);
            updateMessage();
        }

        @Override
        protected void updateMessage() {
            this.setMessage(Component.literal("Player Mob Exclusion: ")
                    .append(Component.literal(blocksFromValue(this.value) + " m")));
        }

        @Override
        protected void applyValue() {
            int blocks = blocksFromValue(this.value);
            this.value = valueFromBlocks(blocks);
            if (this.changingWithMouse) {
                this.pendingBlocks = blocks;
            } else {
                this.onValueChanged.accept(blocks);
            }
        }

        @Override
        public void onClick(MouseButtonEvent event, boolean doubleClick) {
            this.changingWithMouse = true;
            this.pendingBlocks = blocksFromValue(this.value);
            super.onClick(event, doubleClick);
        }

        @Override
        public void onRelease(MouseButtonEvent event) {
            super.onRelease(event);
            this.changingWithMouse = false;
            int blocks = blocksFromValue(this.value);
            this.pendingBlocks = blocks;
            this.onValueChanged.accept(blocks);
        }

        private static double valueFromBlocks(int blocks) {
            int sanitized = GameplaySettings.sanitizePlayerMobSpawnExclusionBlocks(blocks);
            int range = GameplaySettings.PLAYER_MOB_SPAWN_EXCLUSION_MAX_BLOCKS
                    - GameplaySettings.PLAYER_MOB_SPAWN_EXCLUSION_MIN_BLOCKS;
            if (range <= 0) {
                return 0.0D;
            }
            return (double) (sanitized - GameplaySettings.PLAYER_MOB_SPAWN_EXCLUSION_MIN_BLOCKS) / (double) range;
        }

        private static int blocksFromValue(double value) {
            int range = GameplaySettings.PLAYER_MOB_SPAWN_EXCLUSION_MAX_BLOCKS
                    - GameplaySettings.PLAYER_MOB_SPAWN_EXCLUSION_MIN_BLOCKS;
            int blocks = GameplaySettings.PLAYER_MOB_SPAWN_EXCLUSION_MIN_BLOCKS
                    + (int) Math.round(Math.clamp(value, 0.0D, 1.0D) * range);
            return GameplaySettings.sanitizePlayerMobSpawnExclusionBlocks(blocks);
        }
    }

    private static class PortalRatioSlider extends AbstractSliderButton {
        private final Consumer<PortalRatioPreset> onValueChanged;
        private boolean changingWithMouse;
        private PortalRatioPreset pendingPreset;

        private PortalRatioSlider(
                int x,
                int y,
                int width,
                int height,
                PortalRatioPreset initialPreset,
                Consumer<PortalRatioPreset> onValueChanged) {
            super(x, y, width, height, Component.empty(), valueFromPreset(initialPreset));
            this.onValueChanged = onValueChanged;
            this.pendingPreset = presetFromValue(this.value);
            updateMessage();
        }

        private void setPreset(PortalRatioPreset preset) {
            this.value = valueFromPreset(preset);
            this.pendingPreset = presetFromValue(this.value);
            updateMessage();
        }

        @Override
        protected void updateMessage() {
            PortalRatioPreset preset = presetFromValue(this.value);
            this.setMessage(Component.literal("Portal Ratio: ").append(Component.literal(preset.displayName)));
            this.setTooltip(tooltip(portalRatioTooltip(preset)));
        }

        @Override
        protected void applyValue() {
            PortalRatioPreset preset = presetFromValue(this.value);
            this.value = valueFromPreset(preset);
            if (this.changingWithMouse) {
                this.pendingPreset = preset;
            } else {
                this.onValueChanged.accept(preset);
            }
        }

        @Override
        public void onClick(MouseButtonEvent event, boolean doubleClick) {
            this.changingWithMouse = true;
            this.pendingPreset = presetFromValue(this.value);
            super.onClick(event, doubleClick);
        }

        @Override
        public void onRelease(MouseButtonEvent event) {
            super.onRelease(event);
            this.changingWithMouse = false;
            PortalRatioPreset preset = presetFromValue(this.value);
            this.pendingPreset = preset;
            this.onValueChanged.accept(preset);
        }

        private static double valueFromPreset(PortalRatioPreset preset) {
            int index = PORTAL_RATIO_PRESETS.indexOf(preset);
            if (index < 0 || PORTAL_RATIO_PRESETS.size() <= 1) {
                return 0.0D;
            }
            return (double) index / (double) (PORTAL_RATIO_PRESETS.size() - 1);
        }

        private static PortalRatioPreset presetFromValue(double value) {
            int index = (int) Math.round(Math.clamp(value, 0.0D, 1.0D) * (PORTAL_RATIO_PRESETS.size() - 1));
            return PORTAL_RATIO_PRESETS.get(index);
        }
    }

    private enum NetherSizePreset {
        DISABLED("Infinite", 0, 1),
        ONE_EIGHTH("1/8 size", 1, 8),
        ONE_FOURTH("1/4 size", 1, 4),
        ONE_HALF("1/2 size", 1, 2),
        SAME_SIZE("Same size", 1, 1),
        DOUBLE("2x size", 2, 1),
        QUADRUPLE("4x size", 4, 1),
        CUSTOM("Custom", 0, 1);

        private final String displayName;
        private final int numerator;
        private final int denominator;

        NetherSizePreset(String displayName, int numerator, int denominator) {
            this.displayName = displayName;
            this.numerator = numerator;
            this.denominator = denominator;
        }

        private boolean matchesExactly(int overworldTileSize) {
            return this == DISABLED
                    || this == CUSTOM
                    || ((long) overworldTileSize * (long) numerator) % (long) denominator == 0L;
        }

        private int tileSize(int overworldTileSize) {
            if (this == DISABLED || this == CUSTOM) {
                return 0;
            }
            return Math.max(1, (int) ((long) overworldTileSize * (long) numerator / (long) denominator));
        }
    }

    private enum PortalRatioPreset {
        REVERSE_THIRTY_TWO("1:32 reverse", 1, 32),
        REVERSE_SIXTEEN("1:16 reverse", 1, 16),
        REVERSE_EIGHT("1:8 reverse", 1, 8),
        REVERSE_FOUR("1:4 reverse", 1, 4),
        REVERSE_TWO("1:2 reverse", 1, 2),
        ONE_TO_ONE("1:1", 1, 1),
        FORWARD_TWO("1:2", 2, 1),
        FORWARD_FOUR("1:4", 4, 1),
        FORWARD_EIGHT("1:8 vanilla", 8, 1),
        FORWARD_SIXTEEN("1:16", 16, 1),
        FORWARD_THIRTY_TWO("1:32", 32, 1);

        private final String displayName;
        private final int numerator;
        private final int denominator;

        PortalRatioPreset(String displayName, int numerator, int denominator) {
            this.displayName = displayName;
            this.numerator = numerator;
            this.denominator = denominator;
        }
    }
}
