package com.soullink;

import java.nio.file.Path;

import net.fabricmc.api.ModInitializer;
import net.fabricmc.fabric.api.command.v2.CommandRegistrationCallback;
import net.fabricmc.fabric.api.entity.event.v1.ServerLivingEntityEvents;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerLifecycleEvents;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerTickEvents;
import net.fabricmc.fabric.api.networking.v1.ServerPlayConnectionEvents;
import net.fabricmc.loader.api.FabricLoader;

import net.minecraft.resources.Identifier;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.monster.Enemy;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class SoulLink implements ModInitializer {
	public static final String MOD_ID = "soullink";

	public static final Logger LOGGER = LoggerFactory.getLogger(MOD_ID);

	private static volatile SoulLinkConfig config = SoulLinkConfig.DEFAULT;

	@Override
	public void onInitialize() {
		SoulLinkConfig.Reading reading = SoulLinkConfig.load(configFile());
		String logLanguage = reading.ok() ? reading.config().language() : Lang.ENGLISH;
		reading.warnings().forEach(warning -> LOGGER.warn("[config] {}", warning.in(logLanguage).getString()));
		if (reading.ok()) {
			config = reading.config();
		} else {
			LOGGER.error("[config] Se usan los valores por defecto");
		}

		// Antes que todo: con el id viejo, el estado compartido está en otra carpeta
		ServerLifecycleEvents.SERVER_STARTING.register(IdMigration::migrateWorldData);

		// El primer jugador que entra al mundo (con el mod recién instalado) aporta su inventario
		// como punto de partida. A partir de ahí, el inventario compartido es la única fuente de verdad.
		// A los que tenían partida antes del mod se les devuelven sus ítems en un cofre.
		ServerPlayConnectionEvents.JOIN.register((handler, sender, server) -> {
			SharedInventoryState.get(server).markInitialized();
			PersonalItemsChest.placeRescuedItems(handler.player);
		});

		// La vida no se comparte por referencia: se reparte al final de cada tick.
		ServerTickEvents.END_SERVER_TICK.register(SharedVitals::syncHealth);
		// Después de la vida: si murió el grupo, la experiencia ya quedó reiniciada.
		ServerTickEvents.END_SERVER_TICK.register(SharedExperience::sync);
		// Después de la vida: si el grupo murió en este tick, el intento nuevo arranca en 0.
		ServerTickEvents.END_SERVER_TICK.register(GroupStats::tick);

		// Lo que cambió durante el tick les llega a todos ahora y no en su próximo tick.
		ServerTickEvents.END_SERVER_TICK.register(SharedInventorySync::broadcastToAll);

		ServerLivingEntityEvents.AFTER_DEATH.register((entity, source) -> {
			if (entity instanceof Enemy && source.getEntity() instanceof ServerPlayer killer && SharedVitals.participates(killer)) {
				GroupStats.get(killer.level().getServer()).addMobKill(killer);
			}
		});
		ServerLifecycleEvents.SERVER_STARTED.register(StatsScoreboard::setUp);

		CommandRegistrationCallback.EVENT.register((dispatcher, buildContext, selection) -> {
			SwapPlaces.register(dispatcher);
			SoulLinkCommand.register(dispatcher);
		});

		// En singleplayer se pueden abrir varios mundos en la misma sesión: no arrastrar el estado de uno a otro.
		ServerLifecycleEvents.SERVER_STOPPED.register(server -> SharedInventoryState.clearActive());

		LOGGER.info("Inventario compartido activado");
	}

	public static SoulLinkConfig config() {
		return config;
	}

	/** Vuelve a leer el archivo. Si está roto, se queda con la config que había. */
	public static SoulLinkConfig.Reading reloadConfig(MinecraftServer server) {
		SoulLinkConfig.Reading reading = SoulLinkConfig.load(configFile());
		if (reading.ok()) {
			config = reading.config();
			StatsScoreboard.applySidebar(server);
		}
		return reading;
	}

	public static Path configFile() {
		return FabricLoader.getInstance().getConfigDir().resolve(MOD_ID + ".json");
	}

	public static Identifier id(String path) {
		return Identifier.fromNamespaceAndPath(MOD_ID, path);
	}
}
