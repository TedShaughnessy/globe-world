package globe.world.client;

import globe.world.atlas.GlobeAtlasEffect;
import globe.world.atlas.GlobeAtlasLoadout;
import globe.world.network.GlobeAtlasScreenPayload;
import globe.world.network.GlobeAtlasTravelPayload;
import globe.world.network.GlobeAtlasUpdatePayload;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking;
import net.minecraft.ChatFormatting;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.CommonComponents;
import net.minecraft.network.chat.Component;

public class GlobeAtlasPowerScreen extends Screen {
    private static final int PANEL_WIDTH = 300;
    private static final int LINE = 12;

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
        int left = (this.width - PANEL_WIDTH) / 2;
        int y = Math.max(28, (this.height - 210) / 2 + 86);

        this.addRenderableWidget(Button.builder(this.radiusLabel(), button -> this.sendUpdate(this.nextRadius()))
                .bounds(left + 16, y, 128, 20)
                .build());
        this.addRenderableWidget(Button.builder(this.activeLabel(GlobeAtlasEffect.SPEED), button -> this.toggleEffect(GlobeAtlasEffect.SPEED))
                .bounds(left + 156, y, 128, 20)
                .build());
        y += 24;
        this.addRenderableWidget(Button.builder(this.activeLabel(GlobeAtlasEffect.HASTE), button -> this.toggleEffect(GlobeAtlasEffect.HASTE))
                .bounds(left + 16, y, 128, 20)
                .build());
        this.addRenderableWidget(Button.builder(this.activeLabel(GlobeAtlasEffect.REGENERATION), button -> this.toggleEffect(GlobeAtlasEffect.REGENERATION))
                .bounds(left + 156, y, 128, 20)
                .build());
        y += 24;

        Button travel = Button.builder(this.travelLabel(), button -> this.toggleTravel())
                .bounds(left + 16, y, 128, 20)
                .build();
        travel.active = this.data.complete();
        this.addRenderableWidget(travel);
        this.addRenderableWidget(Button.builder(CommonComponents.GUI_DONE, button -> this.onClose())
                .bounds(left + 156, y, 128, 20)
                .build());
        y += 28;

        for (GlobeAtlasScreenPayload.Destination destination : this.data.destinations()) {
            Button button = Button.builder(Component.literal(destination.label()), pressed ->
                            ClientPlayNetworking.send(new GlobeAtlasTravelPayload(this.data.pos(), destination.pos())))
                    .bounds(left + 16, y, 268, 20)
                    .build();
            button.active = destination.available() && this.data.loadout().travelNetwork();
            this.addRenderableWidget(button);
            y += 22;
            if (y > this.height - 28) {
                break;
            }
        }
    }

    @Override
    public void extractRenderState(final GuiGraphicsExtractor graphics, final int mouseX, final int mouseY, final float a) {
        int left = (this.width - PANEL_WIDTH) / 2;
        int top = Math.max(18, (this.height - 220) / 2);
        int bottom = Math.min(this.height - 18, top + 234 + this.data.destinations().size() * 22);

        graphics.fill(left, top, left + PANEL_WIDTH, bottom, 0xDD101317);
        graphics.outline(left, top, PANEL_WIDTH, bottom - top, 0xFF5EB8C8);
        graphics.centeredText(this.font, this.title, this.width / 2, top + 12, 0xFFEFF9FB);

        int y = top + 34;
        this.line(graphics, left, y, "Discovery", String.format("%.2f%% (%d px)", this.data.discoveredPercent(), this.data.discoveredPixels()));
        y += LINE;
        this.line(graphics, left, y, "Budget", this.data.spentPoints() + " / " + this.data.worldPoints() + " points");
        y += LINE;
        this.line(graphics, left, y, "Radius cap", this.data.radiusCap() + " blocks");
        y += LINE;
        this.line(graphics, left, y, "This Atlas", this.stateText());
        y += LINE;
        if (this.data.complete()) {
            graphics.text(this.font, Component.literal("Mastered Atlas").withStyle(ChatFormatting.AQUA), left + 16, y, 0xFF7FE7F3);
        } else {
            graphics.text(this.font, Component.literal("Mastery unlocks linked travel").withStyle(ChatFormatting.GRAY), left + 16, y, 0xFFB7BDC2);
        }

        if (!this.data.destinations().isEmpty()) {
            graphics.text(this.font, Component.literal("Destinations"), left + 16, Math.max(28, (this.height - 210) / 2 + 190), 0xFFEFF9FB);
        }

        super.extractRenderState(graphics, mouseX, mouseY, a);
    }

    private void line(final GuiGraphicsExtractor graphics, final int left, final int y, final String label, final String value) {
        graphics.text(this.font, Component.literal(label).withStyle(ChatFormatting.GRAY), left + 16, y, 0xFFADB5BD);
        graphics.text(this.font, value, left + 118, y, 0xFFE6EDF0);
    }

    private String stateText() {
        if (!this.data.loadout().active()) {
            return "inactive";
        }
        if (!this.data.powered()) {
            return this.data.overloaded() ? "overloaded" : "unpowered";
        }
        return this.data.loadout().cost() + " points, " + this.data.loadout().effectiveRadius(this.rewardsView()) + " blocks";
    }

    private GlobeAtlasLoadout nextRadius() {
        return new GlobeAtlasLoadout(
                (this.data.loadout().radiusTier() + 1) % (GlobeAtlasLoadout.MAX_RADIUS_TIER + 1),
                this.data.loadout().effectMask(),
                this.data.loadout().travelNetwork());
    }

    private Component radiusLabel() {
        return Component.literal("Radius " + this.data.loadout().radiusBlocks());
    }

    private Component activeLabel(final GlobeAtlasEffect effect) {
        Component name = Component.translatable(effect.translationKey());
        return this.data.loadout().hasEffect(effect)
                ? Component.literal("On: ").append(name)
                : Component.literal("Off: ").append(name);
    }

    private Component travelLabel() {
        return Component.literal((this.data.loadout().travelNetwork() ? "On: " : "Off: ") + "Travel");
    }

    private void toggleEffect(final GlobeAtlasEffect effect) {
        int mask = this.data.loadout().effectMask() ^ effect.mask();
        this.sendUpdate(new GlobeAtlasLoadout(
                this.data.loadout().radiusTier(),
                mask,
                this.data.loadout().travelNetwork()));
    }

    private void toggleTravel() {
        this.sendUpdate(new GlobeAtlasLoadout(
                this.data.loadout().radiusTier(),
                this.data.loadout().effectMask(),
                !this.data.loadout().travelNetwork()));
    }

    private void sendUpdate(final GlobeAtlasLoadout loadout) {
        ClientPlayNetworking.send(new GlobeAtlasUpdatePayload(this.data.pos(), loadout));
    }

    private globe.world.atlas.GlobeDiscoveryRewards rewardsView() {
        return new globe.world.atlas.GlobeDiscoveryRewards(
                this.data.discoveredPixels(),
                this.data.discoveredPercent(),
                0.0D,
                this.data.worldPoints(),
                this.data.radiusCap(),
                this.data.complete());
    }
}
