package com.soullink;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.function.Consumer;
import java.util.function.ToIntFunction;

import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import org.jspecify.annotations.Nullable;

import net.minecraft.core.UUIDUtil;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.ComponentSerialization;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.level.saveddata.SavedData;
import net.minecraft.world.level.saveddata.SavedDataType;

/**
 * Estadísticas del grupo: cuántas veces murió, cuánto duró cada intento y qué hizo cada jugador. Se llevan
 * por UUID, así que sirven igual con dos jugadores que con diez, y el que se desconecta conserva las suyas.
 *
 * <p>Se guarda en {@code data/soullink/stats.dat} dentro del mundo, aparte del estado compartido: borrar las
 * estadísticas no puede tocar el inventario.
 */
public class GroupStats extends SavedData {
	/** Cuántas muertes del grupo se recuerdan para mostrar con /soullink. */
	private static final int HISTORY_SIZE = 10;

	/** Lo que se muestra por jugador, en el chat y como objetivo del scoreboard. */
	public enum Stat {
		FAULTS("culpas", "Culpas", player -> player.faults),
		HEARTS_LOST("corazones", "Corazones perdidos", player -> Math.round(player.damage / 2)),
		SCARES("sustos", "Sustos", player -> player.scares),
		EXPERIENCE("experiencia", "Experiencia juntada", player -> (int) Math.min(Integer.MAX_VALUE, Math.round(player.experience))),
		MOBS("mobs", "Mobs matados", player -> player.mobs);

		private final String id;
		private final String displayName;
		private final ToIntFunction<PlayerStats> value;

		Stat(String id, String displayName, ToIntFunction<PlayerStats> value) {
			this.id = id;
			this.displayName = displayName;
			this.value = value;
		}

		public String id() {
			return this.id;
		}

		public String displayName() {
			return this.displayName;
		}

		public int of(PlayerStats player) {
			return this.value.applyAsInt(player);
		}
	}

	public static final class PlayerStats {
		private static final Codec<PlayerStats> CODEC = RecordCodecBuilder.create(instance -> instance.group(
				Codec.STRING.fieldOf("name").forGetter(player -> player.name),
				Codec.INT.optionalFieldOf("faults", 0).forGetter(player -> player.faults),
				Codec.FLOAT.optionalFieldOf("damage", 0.0F).forGetter(player -> player.damage),
				Codec.INT.optionalFieldOf("scares", 0).forGetter(player -> player.scares),
				Codec.DOUBLE.optionalFieldOf("experience", 0.0).forGetter(player -> player.experience),
				Codec.INT.optionalFieldOf("mobs", 0).forGetter(player -> player.mobs)
		).apply(instance, PlayerStats::new));

		/** Un jugador sin estadísticas todavía. Sólo para leer: las que se suman se crean en {@code change}. */
		public static final PlayerStats EMPTY = new PlayerStats("", 0, 0, 0, 0, 0);

		private String name;
		private int faults;
		/** En puntos de vida (medios corazones). */
		private float damage;
		private int scares;
		/** En puntos, como la experiencia compartida. */
		private double experience;
		private int mobs;

		private PlayerStats(String name, int faults, float damage, int scares, double experience, int mobs) {
			this.name = name;
			this.faults = faults;
			this.damage = damage;
			this.scares = scares;
			this.experience = experience;
			this.mobs = mobs;
		}

		public String name() {
			return this.name;
		}

		public float heartsLost() {
			return this.damage / 2;
		}

		/** Puntos de experiencia juntados, sin redondear. */
		public double experience() {
			return this.experience;
		}
	}

	/** Una muerte del grupo: en qué intento, cuánto duró, quién recibió el golpe y de qué murió. */
	public record Death(int attempt, long ticks, String victim, Component cause) {
		private static final Codec<Death> CODEC = RecordCodecBuilder.create(instance -> instance.group(
				Codec.INT.fieldOf("attempt").forGetter(Death::attempt),
				Codec.LONG.fieldOf("ticks").forGetter(Death::ticks),
				Codec.STRING.fieldOf("victim").forGetter(Death::victim),
				ComponentSerialization.CODEC.fieldOf("cause").forGetter(Death::cause)
		).apply(instance, Death::new));
	}

	private static final Codec<GroupStats> CODEC = RecordCodecBuilder.create(instance -> instance.group(
			Codec.INT.optionalFieldOf("attempt", 1).forGetter(stats -> stats.attempt),
			Codec.LONG.optionalFieldOf("attemptTicks", 0L).forGetter(stats -> stats.attemptTicks),
			Codec.LONG.optionalFieldOf("bestTicks", 0L).forGetter(stats -> stats.bestTicks),
			Codec.unboundedMap(UUIDUtil.STRING_CODEC, PlayerStats.CODEC).optionalFieldOf("players", Map.of()).forGetter(stats -> stats.players),
			Death.CODEC.listOf().optionalFieldOf("deaths", List.of()).forGetter(stats -> stats.deaths)
	).apply(instance, GroupStats::new));

	private static final SavedDataType<GroupStats> TYPE = new SavedDataType<>(SoulLink.id("stats"), GroupStats::new, CODEC, null);

	private int attempt;
	/** Ticks jugados en este intento: sólo corre con alguien vivo conectado. */
	private long attemptTicks;
	private long bestTicks;
	private final Map<UUID, PlayerStats> players = new HashMap<>();
	private final List<Death> deaths = new ArrayList<>();

	private GroupStats() {
		this(1, 0, 0, Map.of(), List.of());
	}

	// El codec devuelve colecciones inmutables: se copian para poder sumarles.
	private GroupStats(int attempt, long attemptTicks, long bestTicks, Map<UUID, PlayerStats> players, List<Death> deaths) {
		this.attempt = attempt;
		this.attemptTicks = attemptTicks;
		this.bestTicks = bestTicks;
		this.players.putAll(players);
		this.deaths.addAll(deaths);
	}

	public static GroupStats get(MinecraftServer server) {
		return server.getDataStorage().computeIfAbsent(TYPE);
	}

	/** END_SERVER_TICK: el reloj del intento corre mientras haya alguien jugando, no con el servidor vacío. */
	public static void tick(MinecraftServer server) {
		for (ServerPlayer player : server.getPlayerList().getPlayers()) {
			if (SharedVitals.participates(player) && player.isAlive()) {
				GroupStats stats = get(server);
				stats.attemptTicks++;
				stats.setDirty();
				return;
			}
		}
	}

	public int attempt() {
		return this.attempt;
	}

	public long attemptTicks() {
		return this.attemptTicks;
	}

	/** El intento más largo, contando el que está en curso. */
	public long bestTicks() {
		return Math.max(this.bestTicks, this.attemptTicks);
	}

	/** Las muertes del grupo, de la más vieja a la más nueva (sólo las últimas). */
	public List<Death> deaths() {
		return List.copyOf(this.deaths);
	}

	/** Los jugadores, de más culpas a menos y, entre iguales, del que más corazones perdió al que menos. */
	public List<PlayerStats> ranking() {
		return this.players.values().stream()
				.sorted(Comparator.comparingInt((PlayerStats player) -> player.faults).thenComparingDouble(player -> player.damage).reversed())
				.toList();
	}

	public @Nullable PlayerStats of(UUID player) {
		return this.players.get(player);
	}

	public List<PlayerStats> players() {
		return List.copyOf(this.players.values());
	}

	void addDamage(ServerPlayer player, float damage) {
		change(player, stats -> stats.damage += damage);
	}

	void addScare(ServerPlayer player) {
		change(player, stats -> stats.scares++);
	}

	void addExperience(ServerPlayer player, double points) {
		change(player, stats -> stats.experience += points);
	}

	void addMobKill(ServerPlayer player) {
		change(player, stats -> stats.mobs++);
	}

	/** Murió el grupo: la culpa es de quien recibió el golpe, y arranca otro intento. */
	Death recordGroupDeath(ServerPlayer victim, Component cause) {
		change(victim, stats -> stats.faults++);
		Death death = new Death(this.attempt, this.attemptTicks, victim.getGameProfile().name(), cause);
		this.deaths.add(death);
		if (this.deaths.size() > HISTORY_SIZE) {
			this.deaths.removeFirst();
		}
		this.bestTicks = bestTicks();
		this.attempt++;
		this.attemptTicks = 0;
		this.setDirty();
		return death;
	}

	/** Todo vuelve a cero, como en un mundo nuevo. */
	public void reset() {
		this.attempt = 1;
		this.attemptTicks = 0;
		this.bestTicks = 0;
		this.players.clear();
		this.deaths.clear();
		this.setDirty();
	}

	private void change(ServerPlayer player, Consumer<PlayerStats> change) {
		String name = player.getGameProfile().name();
		PlayerStats stats = this.players.computeIfAbsent(player.getGameProfile().id(), uuid -> new PlayerStats(name, 0, 0, 0, 0, 0));
		stats.name = name;
		change.accept(stats);
		this.setDirty();
		StatsScoreboard.update(player.level().getServer(), stats);
	}

	/** Ticks como h:mm:ss (o m:ss si no llega a una hora). */
	public static String formatTime(long ticks) {
		long seconds = ticks / 20;
		long hours = seconds / 3600;
		long minutes = seconds / 60 % 60;
		long rest = seconds % 60;
		return hours > 0 ? "%d:%02d:%02d".formatted(hours, minutes, rest) : "%d:%02d".formatted(minutes, rest);
	}
}
