package com.inventariocompartido;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

import org.jspecify.annotations.Nullable;

import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.damagesource.DamageSource;

/**
 * Reparte una única barra de vida entre todos los jugadores que participan.
 *
 * <p>Al final de cada tick suma cuánto cambió la vida de cada jugador desde el último reparto
 * (daño, curación, regeneración) y deja a todos con el resultado. Sumar en vez de copiar hace que
 * si los dos reciben daño en el mismo tick se descuenten los dos golpes.
 */
public final class SharedVitals {
	private SharedVitals() {
	}

	/** Creativo y espectador no comparten vida ni hambre: no reciben daño ni lo transmiten. */
	public static boolean participates(ServerPlayer player) {
		return !player.isSpectator() && !player.isCreative();
	}

	public static void syncHealth(MinecraftServer server) {
		List<ServerPlayer> players = List.copyOf(server.getPlayerList().getPlayers());
		if (players.isEmpty()) {
			return;
		}

		SharedInventoryState state = SharedInventoryState.get(server);
		Map<ServerPlayer, Float> synced = state.syncedHealth();
		float health = state.health();
		boolean anySynced = false;
		@Nullable DamageSource deathSource = null;
		List<ServerPlayer> newcomers = new ArrayList<>();

		for (ServerPlayer player : players) {
			Float last = synced.get(player);
			if (last != null) {
				// Cuenta aunque haya muerto en este tick: su golpe final es el que mata al grupo.
				anySynced = true;
				health += player.getHealth() - last;
				if (player.isDeadOrDying() && deathSource == null) {
					deathSource = player.getLastDamageSource();
				}
			} else if (participates(player) && player.isAlive()) {
				newcomers.add(player);
			}
		}

		if (!anySynced) {
			if (newcomers.isEmpty()) {
				synced.clear();
				return;
			}
			// Primer jugador después de que todos murieron o con el mod recién instalado: aporta su vida.
			if (health <= 0) {
				health = newcomers.getFirst().getHealth();
			}
		}

		if (anySynced && health <= 0) {
			killGroup(state, players, deathSource);
			return;
		}

		float maxHealth = 0;
		for (ServerPlayer player : players) {
			if (participates(player) && player.isAlive()) {
				maxHealth = Math.max(maxHealth, player.getMaxHealth());
			}
		}
		health = Math.min(health, maxHealth);
		state.setHealth(health);

		synced.clear();
		for (ServerPlayer player : players) {
			if (participates(player) && player.isAlive()) {
				if (player.getHealth() != health) {
					player.setHealth(health);
				}
				// Se guarda lo que quedó después del clamp a su vida máxima, no "health": si no, el próximo
				// tick vería una diferencia fantasma.
				synced.put(player, player.getHealth());
			}
		}
	}

	private static void killGroup(SharedInventoryState state, List<ServerPlayer> players, @Nullable DamageSource deathSource) {
		state.setHealth(SharedInventoryState.UNKNOWN_HEALTH);
		state.resetFood();
		state.syncedHealth().clear();

		for (ServerPlayer player : players) {
			if (participates(player) && player.isAlive()) {
				player.setHealth(0);
				player.die(deathSource != null ? deathSource : player.damageSources().generic());
			}
		}
	}
}
