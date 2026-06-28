package globe.world.client;

import globe.world.atlas.GlobeAtlasEffect;
import globe.world.atlas.GlobeAtlasLoadout;
import globe.world.network.GlobeAtlasScreenPayload;
import globe.world.network.GlobeAtlasTravelPayload;
import globe.world.network.GlobeAtlasUpdatePayload;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking;
import net.minecraft.client.gui.Gui;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.components.AbstractButton;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.client.gui.components.Tooltip;
import net.minecraft.client.gui.narration.NarrationElementOutput;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.input.InputWithModifiers;
import net.minecraft.client.renderer.RenderPipelines;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.Identifier;

public class GlobeAtlasPowerScreen extends Screen {
    private static final int PANEL_WIDTH = 250;
    private static final int PANEL_HEIGHT = 226;
    private static final int MAX_VISIBLE_DESTINATIONS = 5;
    private static final int LINE = 11;
    private static final int BUTTON_SIZE = 22;
    private static final int TEXT_COLOR = 0xFF404040;
    private static final int DISABLED_TEXT_COLOR = 0xFF777777;
    private static final int ICON_DISABLED_COLOR = 0x88FFFFFF;

    private GlobeAtlasScreenPayload data;
    private EditBox nameField;
    private Tab activeTab = Tab.POWERS;
    private boolean sentClosingName;

    public GlobeAtlasPowerScreen(final GlobeAtlasScreenPayload data) {
        super(Component.translatable("screen.globe-world.atlas_power"));
        this.data = data;
    }

    public void apply(final GlobeAtlasScreenPayload data) {
        this.data = data;
        if (!this.destinationsEnabled()) {
            this.activeTab = Tab.POWERS;
        }
        this.rebuildWidgets();
    }

    @Override
    protected void init() {
        int left = this.left();
        int top = this.top();

        this.nameField = new EditBox(this.font, left + 16, top + 13, PANEL_WIDTH - 32, 18, Component.translatable("screen.globe-world.atlas_power.name"));
        this.nameField.setMaxLength(64);
        this.nameField.setValue(this.data.name());
        this.addRenderableWidget(this.nameField);

        this.addRenderableWidget(new TabButton(left + 16, top + 38, 104, Tab.POWERS));
        TabButton destinationTab = new TabButton(left + 130, top + 38, 104, Tab.DESTINATIONS);
        destinationTab.active = this.destinationsEnabled();
        this.addRenderableWidget(destinationTab);

        if (this.activeTab == Tab.DESTINATIONS && this.destinationsEnabled()) {
            int y = top + 72;
            for (GlobeAtlasScreenPayload.Destination destination : this.data.destinations().stream().limit(MAX_VISIBLE_DESTINATIONS).toList()) {
                DestinationButton button = new DestinationButton(left + 18, y, destination);
                button.active = destination.available();
                this.addRenderableWidget(button);
                y += 20;
            }
            return;
        }

        int effectX = left + 28;
        int levelX = effectX + 28;
        int effectY = top + 82;
        for (GlobeAtlasEffect effect : GlobeAtlasEffect.values()) {
            this.addRenderableWidget(new EffectButton(effectX, effectY, effect));
            this.addRenderableWidget(new LevelTwoButton(levelX, effectY, effect));
            effectY += 25;
        }

        int powerX = left + 136;
        int powerY = top + 82;
        this.addRenderableWidget(new RangeButton(powerX, powerY, 1));
        this.addRenderableWidget(new RangeButton(powerX + 28, powerY, 2));
        this.addRenderableWidget(new RangeButton(powerX + 56, powerY, 3));
        if (this.data.surveyMode()) {
            this.addRenderableWidget(new TravelButton(powerX, powerY + 28));
        } else {
            this.addRenderableWidget(new ProjectionButton(powerX, powerY + 28));
            this.addRenderableWidget(new TravelButton(powerX + 28, powerY + 28));
        }
    }

    @Override
    public void onClose() {
        this.sendNameIfChanged();
        this.sentClosingName = true;
        super.onClose();
    }

    @Override
    public void removed() {
        if (!this.sentClosingName) {
            this.sendNameIfChanged();
        }
        super.removed();
    }

    @Override
    public void extractRenderState(final GuiGraphicsExtractor graphics, final int mouseX, final int mouseY, final float a) {
        int left = this.left();
        int top = this.top();
        this.panel(graphics, left, top, PANEL_WIDTH, PANEL_HEIGHT);

        if (this.activeTab == Tab.DESTINATIONS && this.destinationsEnabled()) {
            this.section(graphics, left + 12, top + 62, PANEL_WIDTH - 24, 100);
            if (this.data.destinations().isEmpty()) {
                graphics.centeredText(
                        this.font,
                        Component.translatable("screen.globe-world.atlas_power.no_destinations"),
                        left + PANEL_WIDTH / 2,
                        top + 107,
                        0xFFE8E8E8);
            }
        } else {
            this.section(graphics, left + 12, top + 62, 94, 100);
            this.section(graphics, left + 112, top + 62, 126, 100);
            graphics.centeredText(this.font, Component.translatable("screen.globe-world.atlas_power.effects"), left + 59, top + 67, 0xFFE8E8E8);
            graphics.centeredText(this.font, Component.translatable("screen.globe-world.atlas_power.atlas"), left + 175, top + 67, 0xFFE8E8E8);
        }

        this.status(graphics, left, top);
        super.extractRenderState(graphics, mouseX, mouseY, a);
    }

    private void panel(final GuiGraphicsExtractor graphics, final int x, final int y, final int width, final int height) {
        graphics.fill(x, y, x + width, y + height, 0xFFC6C6C6);
        graphics.outline(x, y, width, height, 0xFF000000);
        graphics.outline(x + 1, y + 1, width - 2, height - 2, 0xFFFFFFFF);
        graphics.outline(x + 3, y + 3, width - 6, height - 6, 0xFF555555);
    }

    private void section(final GuiGraphicsExtractor graphics, final int x, final int y, final int width, final int height) {
        graphics.fill(x, y, x + width, y + height, 0xFF2C2C2C);
        graphics.outline(x, y, width, height, 0xFF5F5F5F);
    }

    private void status(final GuiGraphicsExtractor graphics, final int left, final int top) {
        int statusTop = top + PANEL_HEIGHT - 58;
        graphics.fill(left + 12, statusTop, left + PANEL_WIDTH - 12, top + PANEL_HEIGHT - 8, 0xFF2C2C2C);
        graphics.outline(left + 12, statusTop, PANEL_WIDTH - 24, 50, 0xFF5F5F5F);
        this.line(
                graphics,
                left + 18,
                statusTop + 5,
                Component.translatable(this.data.surveyMode()
                        ? "screen.globe-world.atlas_power.cells"
                        : "screen.globe-world.atlas_power.discovery"),
                String.format("%.2f%%", this.data.discoveredPercent()));
        this.discoveryBar(graphics, left + 18, statusTop + 18, PANEL_WIDTH - 36, 7);

        int y = statusTop + 31;
        if (this.data.surveyMode()) {
            this.line(graphics, left + 18, y, Component.translatable("screen.globe-world.atlas_power.biomes"), Integer.toString(this.data.biomesVisited()));
            this.line(graphics, left + 126, y, Component.translatable("screen.globe-world.atlas_power.cells"), Integer.toString(this.data.visitedCells()));
        } else {
            this.line(graphics, left + 18, y, Component.translatable("screen.globe-world.atlas_power.budget"), this.data.spentPoints() + " / " + this.data.worldPoints());
            this.line(graphics, left + 126, y, Component.translatable("screen.globe-world.atlas_power.cap"), this.data.radiusCap() + "m");
        }

        y += LINE;
        this.line(graphics, left + 18, y, Component.translatable("screen.globe-world.atlas_power.cost"), this.data.loadout().cost() + " pts");
        graphics.centeredText(this.font, this.statusText(),
                left + 178, y, 0xFFE8E8E8);
    }

    private void discoveryBar(final GuiGraphicsExtractor graphics, final int x, final int y, final int width, final int height) {
        graphics.fill(x, y, x + width, y + height, 0xFF171717);
        graphics.outline(x, y, width, height, 0xFF767676);
        int fillWidth = (int)Math.round(width * clamp(this.data.discoveredPercent(), 0.0D, 100.0D) / 100.0D);
        if (fillWidth > 0) {
            graphics.fill(x + 1, y + 1, x + 1 + Math.min(width - 2, fillWidth), y + height - 1, this.data.travelUnlocked() ? 0xFF8FE8FF : 0xFF84C57A);
        }

        for (int milestone : this.data.milestoneTenths()) {
            int markerX = x + Math.round(width * clamp(milestone, 0, 1000) / 1000.0F);
            int color = this.data.discoveredPercent() * 10.0D >= milestone ? 0xFFFFFFFF : 0xFF777777;
            graphics.verticalLine(markerX, y - 2, y + height + 1, color);
        }
    }

    private static double clamp(final double value, final double min, final double max) {
        return Math.max(min, Math.min(max, value));
    }

    private static int clamp(final int value, final int min, final int max) {
        return Math.max(min, Math.min(max, value));
    }

    private void line(final GuiGraphicsExtractor graphics, final int x, final int y, final Component label, final String value) {
        graphics.text(this.font, label, x, y, 0xFFAAAAAA, false);
        graphics.text(this.font, value, x + 54, y, 0xFFE8E8E8, false);
    }

    private int left() {
        return (this.width - PANEL_WIDTH) / 2;
    }

    private int top() {
        return (this.height - PANEL_HEIGHT) / 2;
    }

    private Component statusText() {
        if (this.data.surveyMode()) {
            return this.data.travelUnlocked()
                    ? Component.translatable("screen.globe-world.atlas_power.surveyed")
                    : Component.translatable("screen.globe-world.atlas_power.locked");
        }
        return this.data.complete()
                ? Component.translatable("screen.globe-world.atlas_power.mastered")
                : Component.translatable("screen.globe-world.atlas_power.locked");
    }

    private boolean destinationsEnabled() {
        return this.data.travelUnlocked() && this.data.powered() && this.data.loadout().travelNetwork();
    }

    private int pointBudgetForThisAtlas() {
        return Math.max(0, this.data.worldPoints() - Math.max(0, this.data.spentPoints() - this.data.loadout().cost()));
    }

    private boolean canApply(final GlobeAtlasLoadout candidate) {
        return candidate.cost() <= this.pointBudgetForThisAtlas();
    }

    private GlobeAtlasLoadout withRadiusTier(final int radiusTier) {
        return new GlobeAtlasLoadout(
                radiusTier,
                this.data.loadout().effectMask(),
                this.data.loadout().levelTwoMask(),
                this.data.loadout().travelNetwork());
    }

    private GlobeAtlasLoadout toggledRadiusLevel(final int level) {
        int current = this.data.loadout().radiusTier();
        int targetTier = this.radiusTierForLevel(level);
        return this.withRadiusTier(current >= targetTier ? targetTier - 1 : targetTier);
    }

    private int radiusTierForLevel(final int level) {
        return level == 3 ? GlobeAtlasLoadout.MAX_RADIUS_TIER : level;
    }

    private GlobeAtlasLoadout toggledEffect(final GlobeAtlasEffect effect) {
        int mask = this.data.loadout().effectMask() ^ effect.mask();
        int levelTwoMask = this.data.loadout().levelTwoMask() & mask;
        return new GlobeAtlasLoadout(
                this.data.loadout().radiusTier(),
                mask,
                levelTwoMask,
                this.data.loadout().travelNetwork());
    }

    private GlobeAtlasLoadout toggledLevelTwo(final GlobeAtlasEffect effect) {
        int levelTwoMask = this.data.loadout().levelTwoMask() ^ effect.mask();
        return new GlobeAtlasLoadout(
                this.data.loadout().radiusTier(),
                this.data.loadout().effectMask(),
                levelTwoMask,
                this.data.loadout().travelNetwork());
    }

    private GlobeAtlasLoadout toggledTravel() {
        return new GlobeAtlasLoadout(
                this.data.loadout().radiusTier(),
                this.data.loadout().effectMask(),
                this.data.loadout().levelTwoMask(),
                !this.data.loadout().travelNetwork());
    }

    private String currentName() {
        return this.nameField == null ? this.data.name() : this.nameField.getValue();
    }

    private void sendNameIfChanged() {
        if (!this.currentName().equals(this.data.name())) {
            this.sendUpdate(this.data.loadout(), this.data.projectionEnabled());
        }
    }

    private void sendUpdate(final GlobeAtlasLoadout loadout, final boolean projectionEnabled) {
        ClientPlayNetworking.send(new GlobeAtlasUpdatePayload(
                this.data.pos(),
                loadout,
                this.currentName(),
                !this.data.surveyMode() && projectionEnabled));
    }

    private enum Tab {
        POWERS,
        DESTINATIONS
    }

    private class TabButton extends AbstractButton {
        private final Tab tab;

        TabButton(final int x, final int y, final int width, final Tab tab) {
            super(x, y, width, 18, Component.translatable(tab == Tab.POWERS
                    ? "screen.globe-world.atlas_power.powers"
                    : "screen.globe-world.atlas_power.destinations"));
            this.tab = tab;
        }

        @Override
        public void onPress(final InputWithModifiers input) {
            GlobeAtlasPowerScreen.this.sendNameIfChanged();
            GlobeAtlasPowerScreen.this.activeTab = this.tab;
            GlobeAtlasPowerScreen.this.rebuildWidgets();
        }

        @Override
        protected void extractContents(final GuiGraphicsExtractor graphics, final int mouseX, final int mouseY, final float a) {
            boolean selected = GlobeAtlasPowerScreen.this.activeTab == this.tab;
            int body = this.active ? (selected ? 0xFFD7D7D7 : 0xFF9A9A9A) : 0xFF505050;
            int text = this.active ? TEXT_COLOR : 0xFFCFCFCF;
            graphics.fill(this.getX(), this.getY(), this.getX() + this.width, this.getY() + this.height, body);
            graphics.outline(this.getX(), this.getY(), this.width, this.height, 0xFF373737);
            int textX = this.getX() + (this.width - GlobeAtlasPowerScreen.this.font.width(this.getMessage())) / 2;
            graphics.text(GlobeAtlasPowerScreen.this.font, this.getMessage(), textX, this.getY() + 5, text, false);
        }

        @Override
        public void updateWidgetNarration(final NarrationElementOutput output) {
            this.defaultButtonNarrationText(output);
        }
    }

    private abstract class AtlasButton extends AbstractButton {
        private boolean selected;

        AtlasButton(final int x, final int y, final Component message) {
            super(x, y, BUTTON_SIZE, BUTTON_SIZE, message);
        }

        @Override
        protected void extractContents(final GuiGraphicsExtractor graphics, final int mouseX, final int mouseY, final float a) {
            int body = this.active ? (this.selected ? 0xFFD7D7D7 : 0xFF9A9A9A) : 0xFF6E6E6E;
            graphics.fill(this.getX(), this.getY(), this.getX() + this.width, this.getY() + this.height, body);
            graphics.horizontalLine(this.getX(), this.getX() + this.width - 1, this.getY(), this.active ? 0xFFFFFFFF : 0xFF999999);
            graphics.verticalLine(this.getX(), this.getY(), this.getY() + this.height - 1, this.active ? 0xFFFFFFFF : 0xFF999999);
            graphics.horizontalLine(this.getX(), this.getX() + this.width - 1, this.getY() + this.height - 1, 0xFF373737);
            graphics.verticalLine(this.getX() + this.width - 1, this.getY(), this.getY() + this.height - 1, 0xFF373737);
            if (this.isHoveredOrFocused() && this.active) {
                graphics.outline(this.getX() + 1, this.getY() + 1, this.width - 2, this.height - 2, 0xFFFFFFA0);
            }
            this.extractIcon(graphics);
        }

        protected int iconTextColor() {
            return this.active ? TEXT_COLOR : DISABLED_TEXT_COLOR;
        }

        protected abstract void extractIcon(GuiGraphicsExtractor graphics);

        void setSelected(final boolean selected) {
            this.selected = selected;
        }

        @Override
        public void updateWidgetNarration(final NarrationElementOutput output) {
            this.defaultButtonNarrationText(output);
        }
    }

    private class EffectButton extends AtlasButton {
        private final GlobeAtlasEffect effect;

        EffectButton(final int x, final int y, final GlobeAtlasEffect effect) {
            super(x, y, Component.translatable(effect.translationKey()));
            this.effect = effect;
            boolean selected = GlobeAtlasPowerScreen.this.data.loadout().hasEffect(effect);
            this.setSelected(selected);
            this.active = selected || GlobeAtlasPowerScreen.this.canApply(GlobeAtlasPowerScreen.this.toggledEffect(effect));
            this.setTooltip(Tooltip.create(Component.translatable(effect.translationKey())));
        }

        @Override
        public void onPress(final InputWithModifiers input) {
            GlobeAtlasPowerScreen.this.sendUpdate(GlobeAtlasPowerScreen.this.toggledEffect(this.effect), GlobeAtlasPowerScreen.this.data.projectionEnabled());
        }

        @Override
        protected void extractIcon(final GuiGraphicsExtractor graphics) {
            Identifier sprite = Gui.getMobEffectSprite(this.effect.mobEffect());
            graphics.blitSprite(RenderPipelines.GUI_TEXTURED, sprite, this.getX() + 2, this.getY() + 2, 18, 18, this.active ? 0xFFFFFFFF : ICON_DISABLED_COLOR);
        }
    }

    private class LevelTwoButton extends AtlasButton {
        private final GlobeAtlasEffect effect;

        LevelTwoButton(final int x, final int y, final GlobeAtlasEffect effect) {
            super(x, y, Component.translatable("screen.globe-world.atlas_power.level_two"));
            this.effect = effect;
            boolean selected = GlobeAtlasPowerScreen.this.data.loadout().hasLevelTwo(effect);
            boolean hasEffect = GlobeAtlasPowerScreen.this.data.loadout().hasEffect(effect);
            this.setSelected(selected);
            this.active = selected || (hasEffect && GlobeAtlasPowerScreen.this.canApply(GlobeAtlasPowerScreen.this.toggledLevelTwo(effect)));
            this.setTooltip(Tooltip.create(Component.translatable(effect.translationKey()).append(" II")));
        }

        @Override
        public void onPress(final InputWithModifiers input) {
            GlobeAtlasPowerScreen.this.sendUpdate(GlobeAtlasPowerScreen.this.toggledLevelTwo(this.effect), GlobeAtlasPowerScreen.this.data.projectionEnabled());
        }

        @Override
        protected void extractIcon(final GuiGraphicsExtractor graphics) {
            graphics.centeredText(GlobeAtlasPowerScreen.this.font, "II", this.getX() + 11, this.getY() + 7, this.iconTextColor());
        }
    }

    private class RangeButton extends AtlasButton {
        private final int level;

        RangeButton(final int x, final int y, final int level) {
            super(x, y, Component.translatable("screen.globe-world.atlas_power.radius"));
            this.level = level;
            int current = GlobeAtlasPowerScreen.this.data.loadout().radiusTier();
            int targetTier = GlobeAtlasPowerScreen.this.radiusTierForLevel(level);
            boolean selected = current >= targetTier;
            this.setSelected(selected);
            this.active = selected
                    || (level == 1 || current >= level - 1)
                    && GlobeAtlasPowerScreen.this.canApply(GlobeAtlasPowerScreen.this.toggledRadiusLevel(level));
            this.setTooltip(Tooltip.create(Component.literal(GlobeAtlasPowerScreen.this.withRadiusTier(targetTier).radiusBlocks() + "m")));
        }

        @Override
        public void onPress(final InputWithModifiers input) {
            GlobeAtlasPowerScreen.this.sendUpdate(GlobeAtlasPowerScreen.this.toggledRadiusLevel(this.level), GlobeAtlasPowerScreen.this.data.projectionEnabled());
        }

        @Override
        protected void extractIcon(final GuiGraphicsExtractor graphics) {
            String text = this.level == 1 ? "R" : this.level == 2 ? "II" : "III";
            graphics.centeredText(GlobeAtlasPowerScreen.this.font, text, this.getX() + 11, this.getY() + 7, this.iconTextColor());
        }
    }

    private class ProjectionButton extends AtlasButton {
        ProjectionButton(final int x, final int y) {
            super(x, y, Component.translatable("screen.globe-world.atlas_power.projection"));
            this.setSelected(GlobeAtlasPowerScreen.this.data.projectionEnabled());
            this.active = true;
            this.setTooltip(Tooltip.create(Component.translatable("screen.globe-world.atlas_power.projection")));
        }

        @Override
        public void onPress(final InputWithModifiers input) {
            GlobeAtlasPowerScreen.this.sendUpdate(GlobeAtlasPowerScreen.this.data.loadout(), !GlobeAtlasPowerScreen.this.data.projectionEnabled());
        }

        @Override
        protected void extractIcon(final GuiGraphicsExtractor graphics) {
            graphics.centeredText(GlobeAtlasPowerScreen.this.font, "P", this.getX() + 11, this.getY() + 7, this.iconTextColor());
        }
    }

    private class TravelButton extends AtlasButton {
        TravelButton(final int x, final int y) {
            super(x, y, Component.translatable("screen.globe-world.atlas_power.travel"));
            boolean selected = GlobeAtlasPowerScreen.this.data.loadout().travelNetwork();
            this.setSelected(selected);
            this.active = selected
                    || (GlobeAtlasPowerScreen.this.data.surveyMode() || GlobeAtlasPowerScreen.this.data.travelUnlocked())
                    && GlobeAtlasPowerScreen.this.canApply(GlobeAtlasPowerScreen.this.toggledTravel());
            this.setTooltip(Tooltip.create(Component.translatable("screen.globe-world.atlas_power.travel")));
        }

        @Override
        public void onPress(final InputWithModifiers input) {
            GlobeAtlasPowerScreen.this.sendUpdate(GlobeAtlasPowerScreen.this.toggledTravel(), GlobeAtlasPowerScreen.this.data.projectionEnabled());
        }

        @Override
        protected void extractIcon(final GuiGraphicsExtractor graphics) {
            graphics.centeredText(GlobeAtlasPowerScreen.this.font, "T", this.getX() + 11, this.getY() + 7, this.iconTextColor());
        }
    }

    private class DestinationButton extends AbstractButton {
        private final GlobeAtlasScreenPayload.Destination destination;

        DestinationButton(final int x, final int y, final GlobeAtlasScreenPayload.Destination destination) {
            super(x, y, PANEL_WIDTH - 36, 18, Component.literal(destination.label()));
            this.destination = destination;
            this.setTooltip(Tooltip.create(Component.literal(destination.label())));
        }

        @Override
        public void onPress(final InputWithModifiers input) {
            GlobeAtlasPowerScreen.this.sendNameIfChanged();
            GlobeAtlasPowerScreen.this.sentClosingName = true;
            ClientPlayNetworking.send(new GlobeAtlasTravelPayload(GlobeAtlasPowerScreen.this.data.pos(), this.destination.pos()));
            if (GlobeAtlasPowerScreen.this.minecraft != null) {
                GlobeAtlasPowerScreen.this.minecraft.setScreen(null);
            }
        }

        @Override
        protected void extractContents(final GuiGraphicsExtractor graphics, final int mouseX, final int mouseY, final float a) {
            int color = this.active ? 0xFF9A9A9A : 0xFF6E6E6E;
            graphics.fill(this.getX(), this.getY(), this.getX() + this.width, this.getY() + this.height, color);
            graphics.outline(this.getX(), this.getY(), this.width, this.height, 0xFF373737);
            if (this.isHoveredOrFocused() && this.active) {
                graphics.outline(this.getX() + 1, this.getY() + 1, this.width - 2, this.height - 2, 0xFFFFFFA0);
            }
            int textX = this.getX() + (this.width - GlobeAtlasPowerScreen.this.font.width(this.getMessage())) / 2;
            graphics.text(
                    GlobeAtlasPowerScreen.this.font,
                    this.getMessage(),
                    textX,
                    this.getY() + 5,
                    this.active ? TEXT_COLOR : DISABLED_TEXT_COLOR,
                    false);
        }

        @Override
        public void updateWidgetNarration(final NarrationElementOutput output) {
            this.defaultButtonNarrationText(output);
        }
    }
}
