package com.soullink;

import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;

/**
 * Adelanta el aviso de slots cambiados a los clientes.
 *
 * <p>Vanilla manda los slots que cambiaron desde {@code ServerPlayer.tick()}, una vez por tick y por
 * jugador. Los paquetes se procesan entre ticks, así que lo que hace un jugador (poner un bloque, tirar
 * un ítem) le llegaba a los demás recién en su próximo tick: hasta 50 ms de demora además del ping.
 */
public final class SharedInventorySync {
	private SharedInventorySync() {
	}

	/** Después de procesar un paquete de {@code source}, que puede haber cambiado la lista compartida. */
	public static void broadcastToOthers(ServerPlayer source) {
		MinecraftServer server = source.level().getServer();
		if (!server.isSameThread()) {
			return;
		}
		for (ServerPlayer player : server.getPlayerList().getPlayers()) {
			if (player != source) {
				player.containerMenu.broadcastChanges();
			}
		}
	}

	/**
	 * Al final del tick, para lo que cambió durante el tick (levantar ítems del piso, etc.) después de
	 * que el otro jugador ya hubiera hecho su broadcast.
	 */
	public static void broadcastToAll(MinecraftServer server) {
		for (ServerPlayer player : server.getPlayerList().getPlayers()) {
			player.containerMenu.broadcastChanges();
		}
	}
}
