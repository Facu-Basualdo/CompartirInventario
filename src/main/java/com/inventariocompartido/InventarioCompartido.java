package com.inventariocompartido;

import net.fabricmc.api.ModInitializer;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerLifecycleEvents;
import net.fabricmc.fabric.api.networking.v1.ServerPlayConnectionEvents;

import net.minecraft.resources.Identifier;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class InventarioCompartido implements ModInitializer {
	public static final String MOD_ID = "inventariocompartido";

	public static final Logger LOGGER = LoggerFactory.getLogger(MOD_ID);

	@Override
	public void onInitialize() {
		// El primer jugador que entra al mundo (con el mod recién instalado) aporta su inventario
		// como punto de partida. A partir de ahí, el inventario compartido es la única fuente de verdad.
		ServerPlayConnectionEvents.JOIN.register((handler, sender, server) ->
				SharedInventoryState.get(server).markInitialized());

		// En singleplayer se pueden abrir varios mundos en la misma sesión: no arrastrar el estado de uno a otro.
		ServerLifecycleEvents.SERVER_STOPPED.register(server -> SharedInventoryState.clearActive());

		LOGGER.info("Inventario compartido activado");
	}

	public static Identifier id(String path) {
		return Identifier.fromNamespaceAndPath(MOD_ID, path);
	}
}
