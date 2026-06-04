package globe.world.client;

import globe.world.config.DayNightCycleMode;
import globe.world.config.TilingMode;
import globe.world.config.TilingSettings;
import globe.world.util.GlobeCurvature;
import globe.world.util.TerrainMode;
import net.fabricmc.loader.api.FabricLoader;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.components.AbstractWidget;
import net.minecraft.client.gui.components.AbstractSliderButton;
import net.minecraft.client.gui.components.CycleButton;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.client.gui.components.MultiLineTextWidget;
import net.minecraft.client.gui.components.StringWidget;
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
import java.util.function.Supplier;

public class GlobeWorldSettingsControls implements Layout {
    private static final int CONTROL_WIDTH = 310;
    private static final int DAY_LENGTH_SLIDER_WIDTH = 153;
    private static final int DAY_NIGHT_MODE_WIDTH = 153;
    private static final int ROW_SPACING = 4;
    private static final int SECTION_SPACING = 12;
    private static final int INFO_WIDTH = CONTROL_WIDTH;
    private static final String DISTANT_HORIZONS_MOD_ID = "distanthorizons";
    private static final boolean DISTANT_HORIZONS_LOADED = FabricLoader.getInstance().isModLoaded(DISTANT_HORIZONS_MOD_ID);
    private static final double DISTANT_HORIZONS_EARTH_RADIUS_BLOCKS = 6_371_000.0D;
    private static final List<Integer> CURVATURE_PRESETS = List.of(
            TilingSettings.CURVATURE_DISABLED_PERCENT,
            TilingSettings.CURVATURE_COMFORTABLE_PERCENT,
            TilingSettings.CURVATURE_REALISTIC_PERCENT
    );
    private static final List<Double> DAY_LENGTH_PRESETS = List.of(
            TilingSettings.DAY_LENGTH_HALF_MULTIPLIER,
            1.0D,
            2.0D,
            3.0D,
            4.0D,
            5.0D,
            6.0D,
            7.0D,
            8.0D,
            9.0D,
            TilingSettings.DAY_LENGTH_MAX_MULTIPLIER
    );
    private static final List<NetherGlobeMode> ALL_NETHER_MODES = List.of(
            NetherGlobeMode.DISABLED,
            NetherGlobeMode.SAME_SIZE,
            NetherGlobeMode.ONE_EIGHTH
    );
    private static final List<NetherGlobeMode> NETHER_MODES_WITHOUT_ONE_EIGHTH = List.of(
            NetherGlobeMode.DISABLED,
            NetherGlobeMode.SAME_SIZE
    );
    private static final List<TilePreset> TILE_PRESETS = List.of(
            new TilePreset(2, "32 m"),
            new TilePreset(4, "64 m"),
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

    private final boolean createWorld;
    private final Supplier<TilingSettings> settingsGetter;
    private final Consumer<TilingSettings> settingsSetter;
    private final List<Row> rows = new ArrayList<>();
    private final List<LayoutElement> elements = new ArrayList<>();
    private int x;
    private int y;
    private int width;
    private int height;
    private CreateMode createMode;
    private boolean updatingText;
    private Runnable layoutChangedCallback = () -> {
    };

    private CycleButton<CreateMode> createModeButton;
    private TilePresetSlider simpleTileSlider;
    private EditBox customTileField;
    private MultiLineTextWidget overworldInfo;
    private CycleButton<Integer> overworldCurvatureButton;
    private GlobeCurvatureSlider overworldCurvatureSlider;
    private MultiLineTextWidget overworldDistantHorizonsAdvice;
    private CycleButton<NetherGlobeMode> netherModeButton;
    private MultiLineTextWidget netherInfo;
    private CycleButton<Integer> netherCurvatureButton;
    private GlobeCurvatureSlider netherCurvatureSlider;
    private CycleButton<DayNightCycleMode> dayNightCycleButton;
    private DayLengthMultiplierSlider dayLengthSlider;

    private GlobeWorldSettingsControls(
            boolean createWorld,
            Supplier<TilingSettings> settingsGetter,
            Consumer<TilingSettings> settingsSetter) {
        this.createWorld = createWorld;
        this.settingsGetter = settingsGetter;
        this.settingsSetter = settingsSetter;
        this.createMode = createWorld ? CreateMode.fromSettings(settingsGetter.get()) : CreateMode.CUSTOM;
        build();
        refresh();
    }

    public static GlobeWorldSettingsControls createWorld(
            Supplier<TilingSettings> settingsGetter,
            Consumer<TilingSettings> settingsSetter) {
        return new GlobeWorldSettingsControls(true, settingsGetter, settingsSetter);
    }

    public static GlobeWorldSettingsControls pauseMenu(
            Supplier<TilingSettings> settingsGetter,
            Consumer<TilingSettings> settingsSetter) {
        return new GlobeWorldSettingsControls(false, settingsGetter, settingsSetter);
    }

    public void setLayoutChangedCallback(Runnable layoutChangedCallback) {
        this.layoutChangedCallback = layoutChangedCallback != null ? layoutChangedCallback : () -> {
        };
    }

    private void build() {
        Minecraft minecraft = Minecraft.getInstance();

        if (createWorld) {
            createModeButton = CycleButton.<CreateMode>builder(mode -> Component.literal(mode.displayName), createMode)
                    .withValues(CreateMode.values())
                    .create(0, 0, CONTROL_WIDTH, 20, Component.literal("Globe World"), (button, mode) -> {
                        this.createMode = mode;
                        setSettings(settingsForMode(mode));
                    });
            addRow(createModeButton, () -> true);
        }

        simpleTileSlider = new TilePresetSlider(0, 0, CONTROL_WIDTH, 20, currentTilePreset(), preset ->
                setSettings(settingsGetter.get().withTileSize(preset.chunks())));
        addRow(simpleTileSlider, () -> createWorld && createMode == CreateMode.SIMPLE, SECTION_SPACING);

        customTileField = new EditBox(minecraft.font, 110, 20, Component.literal("Overworld Tile Size"));
        customTileField.setMaxLength(7);
        customTileField.setResponder(text -> {
            if (updatingText) {
                return;
            }
            try {
                int tileSize = Math.max(1, Integer.parseInt(text));
                setSettings(settingsGetter.get().withTileSize(tileSize));
            } catch (NumberFormatException ignored) {
            }
        });
        addRow(
                new LabeledInputRow(
                        new StringWidget(
                                CONTROL_WIDTH - customTileField.getWidth() - ROW_SPACING,
                                customTileField.getHeight(),
                                Component.literal("Overworld Tile Size (chunks)"),
                                minecraft.font
                        ),
                        customTileField,
                        CONTROL_WIDTH
                ),
                () -> createWorld && createMode == CreateMode.CUSTOM,
                SECTION_SPACING
        );

        overworldInfo = infoText(Component.empty());
        addRow(overworldInfo, () -> !createWorld || settingsGetter.get().enabled() && createMode == CreateMode.CUSTOM);

        overworldCurvatureButton = curvatureButton(
                "Overworld Curvature",
                settingsGetter.get().curvaturePercent(),
                percent -> setSettings(settingsGetter.get().withCurvaturePercent(percent))
        );
        addRow(overworldCurvatureButton, () -> createWorld && createMode == CreateMode.SIMPLE && settingsGetter.get().enabled());

        overworldCurvatureSlider = new GlobeCurvatureSlider(
                0,
                0,
                CONTROL_WIDTH,
                20,
                Component.literal("Overworld Curvature"),
                settingsGetter.get().curvaturePercent(),
                percent -> setSettings(settingsGetter.get().withCurvaturePercent(percent))
        );
        addRow(overworldCurvatureSlider, () -> (!createWorld || createMode == CreateMode.CUSTOM) && settingsGetter.get().enabled());

        overworldDistantHorizonsAdvice = infoText(Component.empty());
        addRow(overworldDistantHorizonsAdvice, () -> DISTANT_HORIZONS_LOADED && settingsGetter.get().enabled());

        netherModeButton = CycleButton.<NetherGlobeMode>builder(mode -> Component.literal(mode.displayName), netherGlobeMode())
                .withValues(() -> !settingsGetter.get().supportsNetherOneEighthOverworldSize(), ALL_NETHER_MODES, NETHER_MODES_WITHOUT_ONE_EIGHTH)
                .create(0, 0, CONTROL_WIDTH, 20, Component.literal("Nether Globe"), (button, mode) -> setSettings(mode.apply(settingsGetter.get())));
        addSectionRow(netherModeButton, () -> createWorld && settingsGetter.get().enabled());

        netherInfo = infoText(Component.empty());
        addRow(netherInfo, () -> !createWorld || settingsGetter.get().enabled() && createMode == CreateMode.CUSTOM, createWorld ? ROW_SPACING : SECTION_SPACING);

        netherCurvatureButton = curvatureButton(
                "Nether Curvature",
                settingsGetter.get().netherCurvaturePercent(),
                percent -> setSettings(settingsGetter.get().withNetherCurvaturePercent(percent))
        );
        addRow(netherCurvatureButton, () -> createWorld && createMode == CreateMode.SIMPLE && settingsGetter.get().enabled());

        netherCurvatureSlider = new GlobeCurvatureSlider(
                0,
                0,
                CONTROL_WIDTH,
                20,
                Component.literal("Nether Curvature"),
                settingsGetter.get().netherCurvaturePercent(),
                percent -> setSettings(settingsGetter.get().withNetherCurvaturePercent(percent))
        );
        addRow(netherCurvatureSlider, () -> (!createWorld || createMode == CreateMode.CUSTOM) && settingsGetter.get().enabled());

        dayLengthSlider = new DayLengthMultiplierSlider(
                0,
                0,
                DAY_LENGTH_SLIDER_WIDTH,
                20,
                settingsGetter.get().dayLengthMultiplier(),
                multiplier -> setSettings(settingsGetter.get().withDayLengthMultiplier(multiplier))
        );

        dayNightCycleButton = CycleButton.<DayNightCycleMode>builder(mode -> Component.literal(mode.displayName()), settingsGetter.get().dayNightCycleMode())
                .withValues(DayNightCycleMode.values())
                .create(0, 0, DAY_NIGHT_MODE_WIDTH, 20, Component.literal("Day Cycle"),
                        (button, mode) -> setSettings(settingsGetter.get().withDayNightCycleMode(mode)));

        LinearLayout dayNightRow = LinearLayout.horizontal().spacing(ROW_SPACING);
        dayNightRow.addChild(dayLengthSlider);
        dayNightRow.addChild(dayNightCycleButton);
        addSectionRow(dayNightRow, () -> settingsGetter.get().enabled());
    }

    private CycleButton<Integer> curvatureButton(String label, int initialPercent, Consumer<Integer> onChanged) {
        return CycleButton.<Integer>builder(GlobeWorldSettingsControls::curvatureLabel, initialPercent)
                .withValues(CURVATURE_PRESETS)
                .create(0, 0, CONTROL_WIDTH, 20, Component.literal(label), (button, percent) -> onChanged.accept(percent));
    }

    private static Component curvatureLabel(int percent) {
        return switch (TilingSettings.sanitizeCurvaturePercent(percent)) {
            case TilingSettings.CURVATURE_DISABLED_PERCENT -> Component.literal("Off");
            case TilingSettings.CURVATURE_COMFORTABLE_PERCENT -> Component.literal("Comfortable");
            case TilingSettings.CURVATURE_REALISTIC_PERCENT -> Component.literal("Realistic");
            default -> Component.literal(percent + "%");
        };
    }

    private MultiLineTextWidget infoText(Component text) {
        return new MultiLineTextWidget(text, Minecraft.getInstance().font).setMaxWidth(INFO_WIDTH).setMaxRows(3);
    }

    private void setSettings(TilingSettings settings) {
        settingsSetter.accept(settings.sanitized());
        refresh();
    }

    private TilingSettings settingsForMode(CreateMode mode) {
        return switch (mode) {
            case DISABLED -> TilingSettings.DISABLED;
            case SIMPLE, CUSTOM -> TilingSettings.DEFAULT
                    .withMode(TilingMode.SQUARE)
                    .withNetherMode(TilingMode.SQUARE)
                    .withNetherOneEighthOverworldSize(true);
        };
    }

    private void refresh() {
        TilingSettings settings = settingsGetter.get().sanitized();
        if (!settings.equals(settingsGetter.get())) {
            settingsSetter.accept(settings);
        }

        if (createWorld && settings.enabled() && createMode == CreateMode.DISABLED) {
            createMode = CreateMode.fromSettings(settings);
        }

        if (createModeButton != null) {
            createModeButton.setValue(createMode);
        }
        if (simpleTileSlider != null) {
            simpleTileSlider.setPreset(currentTilePreset());
        }
        if (customTileField != null) {
            updatingText = true;
            customTileField.setValue(Integer.toString(settings.tileSize()));
            updatingText = false;
        }
        overworldInfo.setMessage(overworldInfo(settings));
        overworldCurvatureButton.setValue(settings.curvaturePercent());
        overworldCurvatureSlider.setPercent(settings.curvaturePercent());
        overworldCurvatureSlider.active = settings.enabled();
        overworldDistantHorizonsAdvice.setMessage(distantHorizonsAdvice(settings));
        netherModeButton.setValue(netherGlobeMode());
        netherInfo.setMessage(netherInfo(settings));
        netherCurvatureButton.setValue(settings.netherCurvaturePercent());
        netherCurvatureButton.active = settings.netherEnabled();
        netherCurvatureSlider.setPercent(settings.netherCurvaturePercent());
        netherCurvatureSlider.active = settings.netherEnabled();
        dayNightCycleButton.setValue(settings.dayNightCycleMode());
        dayNightCycleButton.active = settings.enabled();
        dayLengthSlider.setMultiplier(settings.dayLengthMultiplier());
        dayLengthSlider.active = settings.enabled();

        arrange();
        layoutChangedCallback.run();
    }

    private Component overworldInfo(TilingSettings settings) {
        if (!settings.enabled()) {
            return Component.literal("Overworld tile: Disabled");
        }
        return Component.literal("Overworld tile: ")
                .append(tileSummary(settings.tileSize(), TerrainMode.forOverworldTileSize(settings.tileSize())));
    }

    private Component netherInfo(TilingSettings settings) {
        if (!settings.netherEnabled()) {
            return Component.literal("Nether tile: Disabled");
        }
        return Component.literal("Nether tile: ")
                .append(tileSummary(settings.netherTileSize(), TerrainMode.forNetherTileSize(settings.netherTileSize())));
    }

    private Component tileSummary(int chunks, TerrainMode terrainMode) {
        long blocks = (long) chunks * 16L;
        return Component.literal("%s, %s chunks, %s terrain".formatted(
                formatBlocks(blocks),
                number(chunks),
                terrainMode.displayName()
        ));
    }

    private Component distantHorizonsAdvice(TilingSettings settings) {
        long ratio = distantHorizonsCurveRatio(settings.tileSize());
        return Component.literal("Set Distant Horizons Earth curvature to %s for realism.".formatted(number(ratio)));
    }

    private static long distantHorizonsCurveRatio(int tileSizeChunks) {
        double radius = GlobeCurvature.curvatureRadiusBlocks(
                tileSizeChunks,
                TilingSettings.CURVATURE_REALISTIC_PERCENT
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

    private static String multiplierLabel(double multiplier) {
        double sanitized = TilingSettings.sanitizeDayLengthMultiplier(multiplier);
        if (sanitized == TilingSettings.DAY_LENGTH_HALF_MULTIPLIER) {
            return "0.5x";
        }
        return "%dx".formatted((int) sanitized);
    }

    private TilePreset currentTilePreset() {
        int tileSize = settingsGetter.get().tileSize();
        for (TilePreset preset : TILE_PRESETS) {
            if (preset.chunks() == tileSize) {
                return preset;
            }
        }
        return TILE_PRESETS.stream()
                .min((a, b) -> Integer.compare(Math.abs(a.chunks() - tileSize), Math.abs(b.chunks() - tileSize)))
                .orElse(TILE_PRESETS.get(0));
    }

    private NetherGlobeMode netherGlobeMode() {
        TilingSettings settings = settingsGetter.get();
        if (!settings.netherEnabled()) {
            return NetherGlobeMode.DISABLED;
        }
        return settings.effectiveNetherOneEighthOverworldSize() ? NetherGlobeMode.ONE_EIGHTH : NetherGlobeMode.SAME_SIZE;
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

        private static CreateMode fromSettings(TilingSettings settings) {
            if (!settings.enabled()) {
                return DISABLED;
            }
            return TILE_PRESETS.stream().anyMatch(preset -> preset.chunks() == settings.tileSize()) ? SIMPLE : CUSTOM;
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
            int index = TILE_PRESETS.indexOf(preset);
            if (index < 0 || TILE_PRESETS.size() <= 1) {
                return 0.0D;
            }
            return (double) index / (double) (TILE_PRESETS.size() - 1);
        }

        private static TilePreset presetFromValue(double value) {
            int index = (int) Math.round(Math.clamp(value, 0.0D, 1.0D) * (TILE_PRESETS.size() - 1));
            return TILE_PRESETS.get(index);
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
            double sanitized = TilingSettings.sanitizeDayLengthMultiplier(multiplier);
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

    private enum NetherGlobeMode {
        DISABLED("Disabled"),
        SAME_SIZE("Same size"),
        ONE_EIGHTH("1/8 size");

        private final String displayName;

        NetherGlobeMode(String displayName) {
            this.displayName = displayName;
        }

        private TilingSettings apply(TilingSettings settings) {
            return switch (this) {
                case DISABLED -> settings.withNetherMode(TilingMode.DISABLED).withNetherOneEighthOverworldSize(false);
                case SAME_SIZE -> settings.withNetherMode(TilingMode.SQUARE).withNetherOneEighthOverworldSize(false);
                case ONE_EIGHTH -> settings.withNetherMode(TilingMode.SQUARE).withNetherOneEighthOverworldSize(true);
            };
        }
    }
}
