package com.soullink;

import java.util.ArrayList;
import java.util.IdentityHashMap;
import java.util.List;
import java.util.Map;

import org.jspecify.annotations.Nullable;

import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.Component;
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
		float healthBefore = health;
		// Vida que perdió cada uno en este tick, para las estadísticas (curarse no descuenta)
		Map<ServerPlayer, Float> damage = new IdentityHashMap<>();
		boolean anySynced = false;
		// El que recibió el golpe que mata al grupo: el que ya murió o, si nadie llegó a 0 por su cuenta
		// (dos golpes en el mismo tick), el que más vida perdió.
		@Nullable ServerPlayer victim = null;
		float victimDelta = 0;
		List<ServerPlayer> newcomers = new ArrayList<>();

		for (ServerPlayer player : players) {
			Float last = synced.get(player);
			if (last != null) {
				// Cuenta aunque haya muerto en este tick: su golpe final es el que mata al grupo.
				anySynced = true;
				float delta = player.getHealth() - last;
				health += delta;
				if (delta < 0) {
					damage.put(player, -delta);
				}
				boolean dead = player.isDeadOrDying();
				if (victim == null || (dead && !victim.isDeadOrDying()) || (dead == victim.isDeadOrDying() && delta < victimDelta)) {
					victim = player;
					victimDelta = delta;
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

		GroupStats stats = GroupStats.get(server);
		damage.forEach(stats::addDamage);

		if (anySynced && health <= 0 && victim != null) {
			killGroup(state, players, victim);
			return;
		}

		// Susto: el golpe que dejó al grupo con poca vida, sin llegar a matarlo
		float scareHealth = (float) (SoulLink.config().scareHearts() * 2);
		if (victim != null && victimDelta < 0 && healthBefore > scareHealth && health <= scareHealth) {
			stats.addScare(victim);
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

	/**
	 * El primero en morir tira el inventario compartido y la experiencia, así que la víctima muere antes
	 * que el resto: todo queda donde recibió el golpe y no al lado de quien esté primero en la lista.
	 */
	private static void killGroup(SharedInventoryState state, List<ServerPlayer> players, ServerPlayer victim) {
		state.setHealth(SharedInventoryState.UNKNOWN_HEALTH);
		state.resetFood();
		state.syncedHealth().clear();
		SharedExperience.onGroupDeath(state, victim);

		@Nullable DamageSource lastSource = victim.getLastDamageSource();
		DamageSource deathSource = lastSource != null ? lastSource : victim.damageSources().generic();
		recordGroupDeath(victim, deathSource);
		List<ServerPlayer> order = new ArrayList<>(players);
		order.remove(victim);
		order.addFirst(victim);
		for (ServerPlayer player : order) {
			if (participates(player) && player.isAlive()) {
				player.setHealth(0);
				player.die(deathSource);
			}
		}
	}

	private static void recordGroupDeath(ServerPlayer victim, DamageSource source) {
		MinecraftServer server = victim.level().getServer();
		GroupStats stats = GroupStats.get(server);
		GroupStats.Death death = stats.recordGroupDeath(victim, source.getLocalizedDeathMessage(victim));
		if (SoulLink.config().announceDeaths()) {
			server.getPlayerList().broadcastSystemMessage(Component.literal("☠ Terminó el intento #" + death.attempt() + " después de "
					+ GroupStats.formatTime(death.ticks()) + ". Culpa de ").withStyle(ChatFormatting.RED)
					.append(victim.getDisplayName())
					.append(Component.literal(" (récord: " + GroupStats.formatTime(stats.bestTicks()) + ")").withStyle(ChatFormatting.GRAY)), false);
		}
	}
}
