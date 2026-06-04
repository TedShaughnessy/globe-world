package globe.world.client;

import globe.world.config.TilingSettings;
import net.minecraft.client.gui.components.AbstractSliderButton;
import net.minecraft.client.input.MouseButtonEvent;
import net.minecraft.network.chat.Component;

import java.util.function.IntConsumer;

public class GlobeCurvatureSlider extends AbstractSliderButton {
    private final Component label;
    private final IntConsumer onValueChanged;
    private boolean changingWithMouse;
    private int pendingPercent;

    public GlobeCurvatureSlider(int x, int y, int width, int height, int initialPercent, IntConsumer onValueChanged) {
        this(x, y, width, height, Component.literal("Globe Curvature"), initialPercent, onValueChanged);
    }

    public GlobeCurvatureSlider(
            int x,
            int y,
            int width,
            int height,
            Component label,
            int initialPercent,
            IntConsumer onValueChanged) {
        super(x, y, width, height, Component.empty(), valueFromPercent(initialPercent));
        this.label = label;
        this.onValueChanged = onValueChanged;
        this.pendingPercent = percentFromValue(this.value);
        updateMessage();
    }

    public void setPercent(int percent) {
        this.value = valueFromPercent(percent);
        this.pendingPercent = percentFromValue(this.value);
        updateMessage();
    }

    @Override
    protected void updateMessage() {
        int percent = percentFromValue(this.value);
        Component value = switch (percent) {
            case TilingSettings.CURVATURE_DISABLED_PERCENT -> Component.literal("0% (Disabled)");
            case TilingSettings.CURVATURE_COMFORTABLE_PERCENT -> Component.literal("50% (Comfortable)");
            case TilingSettings.CURVATURE_REALISTIC_PERCENT -> Component.literal("100% (Realistic)");
            default -> Component.literal(percent + "%");
        };
        this.setMessage(Component.empty().append(this.label).append(": ").append(value));
    }

    @Override
    protected void applyValue() {
        int percent = percentFromValue(this.value);
        this.value = valueFromPercent(percent);
        if (this.changingWithMouse) {
            this.pendingPercent = percent;
        } else {
            this.onValueChanged.accept(percent);
        }
    }

    @Override
    public void onClick(MouseButtonEvent event, boolean doubleClick) {
        this.changingWithMouse = true;
        this.pendingPercent = percentFromValue(this.value);
        super.onClick(event, doubleClick);
    }

    @Override
    public void onRelease(MouseButtonEvent event) {
        super.onRelease(event);
        this.changingWithMouse = false;
        int percent = percentFromValue(this.value);
        this.pendingPercent = percent;
        this.onValueChanged.accept(percent);
    }

    private static double valueFromPercent(int percent) {
        return TilingSettings.sanitizeCurvaturePercent(percent) / 100.0;
    }

    private static int percentFromValue(double value) {
        return TilingSettings.sanitizeCurvaturePercent((int) Math.round(value * 100.0));
    }
}
