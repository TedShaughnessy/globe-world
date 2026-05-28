package globe.world;

import net.fabricmc.api.ModInitializer;
import net.minecraft.core.Registry;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.Identifier;
import net.minecraft.server.level.TicketType;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class GlobeWorld implements ModInitializer {
	public static final String MOD_ID = "globe-world";

	// This logger is used to write text to the console and the log file.
	// It is considered best practice to use your mod id as the logger's name.
	// That way, it's clear which mod wrote info, warnings, and errors.
	public static final Logger LOGGER = LoggerFactory.getLogger(MOD_ID);
	public static final TicketType CANONICAL_ALIAS_TICKET = Registry.register(
			BuiltInRegistries.TICKET_TYPE,
			Identifier.fromNamespaceAndPath(MOD_ID, "canonical_alias"),
			new TicketType(TicketType.NO_TIMEOUT, TicketType.FLAG_LOADING));

	@Override
	public void onInitialize() {
		GlobeDebugCommands.register();
	}
}
