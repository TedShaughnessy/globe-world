package globe.world.util;

public enum GlobeEntityAliasMode {
    OFF("OFF"),
    AUTO("AUTO"),
    FORCE_DEBUG("FORCE_DEBUG");

    private final String displayName;

    GlobeEntityAliasMode(String displayName) {
        this.displayName = displayName;
    }

    public String displayName() {
        return displayName;
    }

    public GlobeEntityAliasMode next() {
        GlobeEntityAliasMode[] all = values();
        return all[(ordinal() + 1) % all.length];
    }
}
