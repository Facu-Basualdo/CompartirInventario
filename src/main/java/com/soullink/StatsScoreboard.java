package com.soullink;

import org.jspecify.annotations.Nullable;

import net.minecraft.network.chat.Component;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.ServerScoreboard;
import net.minecraft.world.scores.DisplaySlot;
import net.minecraft.world.scores.Objective;
import net.minecraft.world.scores.ScoreHolder;
import net.minecraft.world.scores.criteria.ObjectiveCriteria;

/**
 * Copia las estadísticas a objetivos del scoreboard de vanilla ({@code soullink_culpas}, {@code soullink_mobs}…).
 * Así se ven en la barra lateral, o con {@code /scoreboard}, sin instalar nada en el cliente.
 *
 * <p>La fuente de verdad es {@link GroupStats}: al arrancar el servidor se reescriben todos los puntajes, así
 * que borrar un objetivo a mano no pierde nada.
 */
public final class StatsScoreboard {
	private StatsScoreboard() {
	}

	public static String objectiveName(GroupStats.Stat stat) {
		return SoulLink.MOD_ID + "_" + stat.id();
	}

	/** SERVER_STARTED: crea los objetivos que falten, les pone los puntajes guardados y arma la barra lateral. */
	public static void setUp(MinecraftServer server) {
		for (GroupStats.PlayerStats player : GroupStats.get(server).players()) {
			update(server, player);
		}
		applySidebar(server);
	}

	public static void update(MinecraftServer server, GroupStats.PlayerStats player) {
		ServerScoreboard scoreboard = server.getScoreboard();
		ScoreHolder holder = ScoreHolder.forNameOnly(player.name());
		for (GroupStats.Stat stat : GroupStats.Stat.values()) {
			scoreboard.getOrCreatePlayerScore(holder, objective(scoreboard, stat)).set(stat.of(player));
		}
	}

	/**
	 * Pone en la barra lateral la estadística de la config. No pisa un objetivo que haya puesto otro (un
	 * minijuego, un datapack): sólo ocupa la barra si está vacía o ya tiene una de las nuestras.
	 */
	public static void applySidebar(MinecraftServer server) {
		ServerScoreboard scoreboard = server.getScoreboard();
		@Nullable Objective current = scoreboard.getDisplayObjective(DisplaySlot.SIDEBAR);
		if (current != null && !isOurs(current)) {
			SoulLink.config().sidebar().ifPresent(stat -> SoulLink.LOGGER.info(
					"La barra lateral ya muestra {}: no se pone {}", current.getName(), objectiveName(stat)));
			return;
		}
		@Nullable Objective wanted = SoulLink.config().sidebar().map(stat -> objective(scoreboard, stat)).orElse(null);
		if (current != wanted) {
			scoreboard.setDisplayObjective(DisplaySlot.SIDEBAR, wanted);
		}
	}

	/** Borra los puntajes de todos: se recrean los objetivos y, si la barra mostraba uno, vuelve a mostrarlo. */
	public static void reset(MinecraftServer server) {
		ServerScoreboard scoreboard = server.getScoreboard();
		for (GroupStats.Stat stat : GroupStats.Stat.values()) {
			@Nullable Objective objective = scoreboard.getObjective(objectiveName(stat));
			if (objective != null) {
				scoreboard.removeObjective(objective);
			}
		}
		setUp(server);
	}

	private static boolean isOurs(Objective objective) {
		for (GroupStats.Stat stat : GroupStats.Stat.values()) {
			if (objective.getName().equals(objectiveName(stat))) {
				return true;
			}
		}
		return false;
	}

	private static Objective objective(ServerScoreboard scoreboard, GroupStats.Stat stat) {
		@Nullable Objective objective = scoreboard.getObjective(objectiveName(stat));
		if (objective == null) {
			objective = scoreboard.addObjective(objectiveName(stat), ObjectiveCriteria.DUMMY, Component.literal(stat.displayName()),
					ObjectiveCriteria.RenderType.INTEGER, false, null);
		}
		return objective;
	}
}
