package com.inventariocompartido;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

import com.inventariocompartido.mixin.ServerPlayerAccessor;

import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.level.gamerules.GameRules;

/**
 * Reparte una única barra de experiencia entre todos los jugadores que participan.
 *
 * <p>La experiencia son tres campos primitivos de cada jugador (nivel, progreso y puntaje), así que no
 * se puede compartir por referencia: igual que la vida, al final de cada tick se suma cuánto cambió la
 * de cada uno desde el último reparto y se deja a todos con el resultado. Se suma en puntos y no en
 * niveles porque un nivel alto vale muchos más puntos que uno bajo.
 */
public final class SharedExperience {
	private SharedExperience() {
	}

	public static void sync(MinecraftServer server) {
		List<ServerPlayer> players = List.copyOf(server.getPlayerList().getPlayers());
		if (players.isEmpty()) {
			return;
		}

		SharedInventoryState state = SharedInventoryState.get(server);
		Map<ServerPlayer, Experience> synced = state.syncedExperience();
		double points = state.experiencePoints();
		int score = state.experienceScore();
		boolean anySynced = false;
		List<ServerPlayer> newcomers = new ArrayList<>();

		for (ServerPlayer player : players) {
			if (!SharedVitals.participates(player) || !player.isAlive()) {
				continue;
			}
			Experience last = synced.get(player);
			if (last != null) {
				anySynced = true;
				points += pointsOf(player) - last.points();
				score += player.totalExperience - last.score();
			} else {
				newcomers.add(player);
			}
		}

		if (!anySynced && newcomers.isEmpty()) {
			synced.clear();
			return;
		}

		// Los que entran adoptan la experiencia compartida. Aportan la suya sólo el primero con el mod recién
		// instalado y los que traen un archivo de antes del mod: si no, esa experiencia se perdería.
		boolean unknown = points < 0;
		if (unknown) {
			points = 0;
			score = 0;
		}
		for (int i = 0; i < newcomers.size(); i++) {
			ServerPlayer newcomer = newcomers.get(i);
			boolean preMod = state.takeExperienceContribution(newcomer.getGameProfile().id());
			if (preMod || (unknown && !anySynced && i == 0)) {
				points += pointsOf(newcomer);
				score += newcomer.totalExperience;
			}
		}

		int level = levelFor(Math.max(points, 0));
		float progress = progressFor(Math.max(points, 0), level);
		score = Math.max(score, 0);
		// Se guarda lo que queda después de redondear el progreso a float: si no, el próximo tick vería una
		// diferencia fantasma.
		state.setExperience(pointsOf(level, progress), score);

		synced.clear();
		for (ServerPlayer player : players) {
			if (SharedVitals.participates(player) && player.isAlive()) {
				apply(player, level, progress, score);
				synced.put(player, new Experience(pointsOf(player), player.totalExperience));
			}
		}
	}

	/** Muere el grupo: como en vanilla, se pierde la experiencia salvo con keepInventory. */
	static void onGroupDeath(SharedInventoryState state, ServerPlayer victim) {
		state.syncedExperience().clear();
		if (!victim.level().getGameRules().get(GameRules.KEEP_INVENTORY)) {
			state.setExperience(0, 0);
		}
	}

	/**
	 * Cada jugador que muere tira orbes según su nivel, y como todos tienen el nivel compartido, el grupo
	 * tiraría la misma experiencia una vez por jugador. Sólo la tira el primero que muere en el tick.
	 */
	public static boolean shouldDropExperience(ServerPlayer player) {
		if (!SharedVitals.participates(player)) {
			return true;
		}
		SharedInventoryState state = SharedInventoryState.get(player.level().getServer());
		return state.claimDeathDrop(player, player.level().getServer().getTickCount());
	}

	private static void apply(ServerPlayer player, int level, float progress, int score) {
		if (player.experienceLevel == level && player.experienceProgress == progress && player.totalExperience == score) {
			return;
		}
		player.experienceLevel = level;
		player.experienceProgress = progress;
		player.totalExperience = score;
		// Vanilla sólo reenvía la barra cuando cambia totalExperience: sin esto, si sólo cambia el nivel (al
		// encantar) el cliente no se entera.
		((ServerPlayerAccessor) player).inventariocompartido$setLastSentExp(-1);
	}

	private static double pointsOf(ServerPlayer player) {
		return pointsOf(player.experienceLevel, player.experienceProgress);
	}

	private static double pointsOf(int level, float progress) {
		return pointsBelow(level) + progress * (double) pointsNeeded(level);
	}

	/** Igual que Player.getXpNeededForNextLevel(), pero sin desbordar con niveles enormes (/xp). */
	private static long pointsNeeded(int level) {
		if (level >= 30) {
			return 112 + (level - 30) * 9L;
		}
		return level >= 15 ? 37 + (level - 15) * 5L : 7 + level * 2L;
	}

	/** Suma de pointsNeeded de 0 a level - 1, en forma cerrada. */
	private static double pointsBelow(int level) {
		double l = level;
		if (level <= 16) {
			return l * l + 6 * l;
		}
		return level <= 31 ? 2.5 * l * l - 40.5 * l + 360 : 4.5 * l * l - 162.5 * l + 2220;
	}

	private static int levelFor(double points) {
		int low = 0;
		int high = Integer.MAX_VALUE;
		while (low < high) {
			int mid = (int) (((long) low + high + 1) >>> 1);
			if (pointsBelow(mid) <= points) {
				low = mid;
			} else {
				high = mid - 1;
			}
		}
		return low;
	}

	private static float progressFor(double points, int level) {
		float progress = (float) ((points - pointsBelow(level)) / pointsNeeded(level));
		return Math.clamp(progress, 0.0F, Math.nextDown(1.0F));
	}

	record Experience(double points, int score) {
	}
}
