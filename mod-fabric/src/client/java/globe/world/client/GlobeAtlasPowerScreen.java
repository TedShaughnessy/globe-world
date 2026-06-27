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
import net.minecraft.client.gui.components.Tooltip;
import net.minecraft.client.gui.narration.NarrationElementOutput;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.input.InputWithModifiers;
import net.minecraft.client.renderer.RenderPipelines;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.Identifier;

public class GlobeAtlasPowerScreen extends Screen {
    private static final int PANEL_WIDTH = 230;
    private static final int BASE_PANEL_HEIGHT = 152;
    private static final int MAX_VISIBLE_DESTINATIONS = 4;
    private static final int LINE = 11;
    private static final int BUTTON_SIZE = 22;
    private static final int TEXT_COLOR = 0xFF404040;
    private static final int DISABLED_TEXT_COLOR = 0xFF777777;
    private static final int ICON_DISABLED_COLOR = 0x88FFFFFF;

    private GlobeAtlasScreenPayload data;

    public GlobeAtlasPowerScreen(final GlobeAtlasScreenPayload data) {
        super(Component.translatable("screen.globe-world.atlas_power"));
        this.data = data;
    }

    public void apply(final GlobeAtlasScreenPayload data) {
        this.data = data;
        this.rebuildWidgets();
    }

    @Override
    protected void init() {
        int left = this.left();
        int top = this.top();
        int effectX = left + 28;
        int levelX = effectX + 28;
        int effectY = top + 34;
        for (GlobeAtlasEffect effect : GlobeAtlasEffect.values()) {
            this.addRenderableWidget(new EffectButton(effectX, effectY, effect));
            this.addRenderableWidget(new LevelTwoButton(levelX, effectY, effect));
            effectY += 25;
        }

        this.addRenderableWidget(new RadiusButton(left + 145, top + 34));
        this.addRenderableWidget(new TravelButton(left + 173, top + 34));

        int y = top + 126;
        for (GlobeAtlasScreenPayload.Destination destination : this.data.destinations().stream().limit(MAX_VISIBLE_DESTINATIONS).toList()) {
            DestinationButton button = new DestinationButton(left + 18, y, destination);
            button.active = destination.available() && this.data.loadout().travelNetwork();
            this.addRenderableWidget(button);
            y += 20;
        }
    }

    @Override
    public void extractRenderState(final GuiGraphicsExtractor graphics, final int mouseX, final int mouseY, final float a) {
        int left = this.left();
        int top = this.top();
        int height = this.panelHeight();
        this.panel(graphics, left, top, PANEL_WIDTH, height);
        this.section(graphics, left + 12, top + 10, 94, 105);
        this.section(graphics, left + 112, top + 10, 106, 105);

        graphics.centeredText(this.font, Component.translatable("screen.globe-world.atlas_power.effects"), left + 59, top + 16, 0xFFE8E8E8);
        graphics.centeredText(this.font, Component.translatable("screen.globe-world.atlas_power.atlas"), left + 165, top + 16, 0xFFE8E8E8);

        int y = top + 64;
        this.line(graphics, left + 126, y, Component.translatable("screen.globe-world.atlas_power.discovery"), String.format("%.2f%%", this.data.discoveredPercent()));
        y += LINE;
        this.line(graphics, left + 126, y, Component.translatable("screen.globe-world.atlas_power.budget"), this.data.spentPoints() + " / " + this.data.worldPoints());
        y += LINE;
        this.line(graphics, left + 126, y, Component.translatable("screen.globe-world.atlas_power.cap"), this.data.radiusCap() + "m");
        y += LINE;
        this.line(graphics, left + 126, y, Component.translatable("screen.globe-world.atlas_power.cost"), this.data.loadout().cost() + " pts");
        graphics.centeredText(this.font, this.data.complete()
                        ? Component.translatable("screen.globe-world.atlas_power.mastered")
                        : Component.translatable("screen.globe-world.atlas_power.locked"),
                left + 165, top + 101, 0xFFE8E8E8);

        if (!this.data.destinations().isEmpty()) {
            graphics.text(this.font, Component.translatable("screen.globe-world.atlas_power.destinations"), left + 18, top + 115, TEXT_COLOR, false);
        }

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

    private void line(final GuiGraphicsExtractor graphics, final int x, final int y, final Component label, final String value) {
        graphics.text(this.font, label, x, y, 0xFFAAAAAA, false);
        graphics.text(this.font, value, x + 54, y, 0xFFE8E8E8, false);
    }

    private int panelHeight() {
        int destinations = Math.min(this.data.destinations().size(), MAX_VISIBLE_DESTINATIONS);
        return BASE_PANEL_HEIGHT + destinations * 20;
    }

    private int left() {
        return (this.width - PANEL_WIDTH) / 2;
    }

    private int top() {
        return (this.height - this.panelHeight()) / 2;
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

    private GlobeAtlasLoadout nextAffordableRadius() {
        int current = this.data.loadout().radiusTier();
        for (int offset = 1; offset <= GlobeAtlasLoadout.MAX_RADIUS_TIER + 1; offset++) {
            GlobeAtlasLoadout candidate = this.withRadiusTier((current + offset) % (GlobeAtlasLoadout.MAX_RADIUS_TIER + 1));
            if (this.canApply(candidate)) {
                return candidate;
            }
        }
        return this.data.loadout();
    }

    private boolean hasAlternativeRadius() {
        return this.nextAffordableRadius().radiusTier() != this.data.loadout().radiusTier();
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

    private void sendUpdate(final GlobeAtlasLoadout loadout) {
        ClientPlayNetworking.send(new GlobeAtlasUpdatePayload(this.data.pos(), loadout));
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
            GlobeAtlasPowerScreen.this.sendUpdate(GlobeAtlasPowerScreen.this.toggledEffect(this.effect));
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
            GlobeAtlasPowerScreen.this.sendUpdate(GlobeAtlasPowerScreen.this.toggledLevelTwo(this.effect));
        }

        @Override
        protected void extractIcon(final GuiGraphicsExtractor graphics) {
            graphics.centeredText(GlobeAtlasPowerScreen.this.font, "II", this.getX() + 11, this.getY() + 7, this.iconTextColor());
        }
    }

    private class RadiusButton extends AtlasButton {
        RadiusButton(final int x, final int y) {
            super(x, y, Component.translatable("screen.globe-world.atlas_power.radius"));
            this.setSelected(GlobeAtlasPowerScreen.this.data.loadout().radiusTier() > 0);
            this.active = GlobeAtlasPowerScreen.this.hasAlternativeRadius();
            this.setTooltip(Tooltip.create(Component.literal(GlobeAtlasPowerScreen.this.data.loadout().radiusBlocks() + "m")));
        }

        @Override
        public void onPress(final InputWithModifiers input) {
            GlobeAtlasPowerScreen.this.sendUpdate(GlobeAtlasPowerScreen.this.nextAffordableRadius());
        }

        @Override
        protected void extractIcon(final GuiGraphicsExtractor graphics) {
            graphics.centeredText(GlobeAtlasPowerScreen.this.font, "R", this.getX() + 11, this.getY() + 7, this.iconTextColor());
        }
    }

    private class TravelButton extends AtlasButton {
        TravelButton(final int x, final int y) {
            super(x, y, Component.translatable("screen.globe-world.atlas_power.travel"));
            boolean selected = GlobeAtlasPowerScreen.this.data.loadout().travelNetwork();
            this.setSelected(selected);
            this.active = selected || (GlobeAtlasPowerScreen.this.data.complete() && GlobeAtlasPowerScreen.this.canApply(GlobeAtlasPowerScreen.this.toggledTravel()));
            this.setTooltip(Tooltip.create(Component.translatable("screen.globe-world.atlas_power.travel")));
        }

        @Override
        public void onPress(final InputWithModifiers input) {
            GlobeAtlasPowerScreen.this.sendUpdate(GlobeAtlasPowerScreen.this.toggledTravel());
        }

        @Override
        protected void extractIcon(final GuiGraphicsExtractor graphics) {
            graphics.centeredText(GlobeAtlasPowerScreen.this.font, "T", this.getX() + 11, this.getY() + 7, this.iconTextColor());
        }
    }

    private class DestinationButton extends AbstractButton {
        private final GlobeAtlasScreenPayload.Destination destination;

        DestinationButton(final int x, final int y, final GlobeAtlasScreenPayload.Destination destination) {
            super(x, y, 194, 18, Component.literal(destination.label()));
            this.destination = destination;
            this.setTooltip(Tooltip.create(Component.literal(destination.label())));
        }

        @Override
        public void onPress(final InputWithModifiers input) {
            ClientPlayNetworking.send(new GlobeAtlasTravelPayload(GlobeAtlasPowerScreen.this.data.pos(), this.destination.pos()));
        }

        @Override
        protected void extractContents(final GuiGraphicsExtractor graphics, final int mouseX, final int mouseY, final float a) {
            int color = this.active ? 0xFF9A9A9A : 0xFF6E6E6E;
            graphics.fill(this.getX(), this.getY(), this.getX() + this.width, this.getY() + this.height, color);
            graphics.outline(this.getX(), this.getY(), this.width, this.height, 0xFF373737);
            if (this.isHoveredOrFocused() && this.active) {
                graphics.outline(this.getX() + 1, this.getY() + 1, this.width - 2, this.height - 2, 0xFFFFFFA0);
            }
            graphics.centeredText(GlobeAtlasPowerScreen.this.font, this.getMessage(), this.getX() + this.width / 2, this.getY() + 5, this.active ? TEXT_COLOR : DISABLED_TEXT_COLOR);
        }

        @Override
        public void updateWidgetNarration(final NarrationElementOutput output) {
            this.defaultButtonNarrationText(output);
        }
    }
}
