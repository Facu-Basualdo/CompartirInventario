package com.soullink;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Optional;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParseException;
import com.google.gson.JsonParser;
import com.google.gson.JsonPrimitive;
import org.jspecify.annotations.Nullable;

/**
 * {@code config/soullink.json}. Pocas opciones a propósito: lo que se comparte por referencia (inventario y
 * hambre) no se puede apagar sin duplicar o perder ítems, así que acá sólo va lo que se puede cambiar en caliente.
 *
 * @param shareExperience si es false, cada uno tiene su experiencia (y la tira al morir)
 * @param announceDeaths  mensaje en el chat con el intento, el culpable y cuánto duró, cuando muere el grupo
 * @param sidebar         estadística en la barra lateral, o vacío para no tocarla
 * @param scareHearts     corazones desde los que un golpe cuenta como susto; 0 para no contarlos
 */
public record SoulLinkConfig(boolean shareExperience, boolean announceDeaths, Optional<GroupStats.Stat> sidebar, double scareHearts) {
	public static final SoulLinkConfig DEFAULT = new SoulLinkConfig(true, true, Optional.of(GroupStats.Stat.FAULTS), 2);

	private static final String NO_SIDEBAR = "ninguna";
	private static final Gson GSON = new GsonBuilder().setPrettyPrinting().disableHtmlEscaping().create();

	/** {@code config} es null si el archivo no se pudo leer (JSON roto): quien llama se queda con la que tenía. */
	public record Reading(@Nullable SoulLinkConfig config, List<String> warnings) {
		public boolean ok() {
			return this.config != null;
		}
	}

	/**
	 * Lee el archivo. Si no existe, lo crea con los valores por defecto. Un campo inválido usa su valor por
	 * defecto y deja un aviso, en vez de descartar todo el archivo.
	 */
	public static Reading load(Path file) {
		if (!Files.exists(file)) {
			write(file, DEFAULT);
			return new Reading(DEFAULT, List.of());
		}

		JsonObject root;
		try {
			JsonElement read = JsonParser.parseString(Files.readString(file, StandardCharsets.UTF_8));
			if (!read.isJsonObject()) {
				return new Reading(null, List.of(file.getFileName() + " no es un objeto JSON"));
			}
			root = read.getAsJsonObject();
		} catch (IOException | JsonParseException e) {
			return new Reading(null, List.of("No se pudo leer " + file.getFileName() + ": " + e.getMessage()));
		}

		List<String> warnings = new ArrayList<>();
		boolean shareExperience = bool(root, "compartirExperiencia", DEFAULT.shareExperience(), warnings);
		boolean announceDeaths = bool(root, "anunciarMuertes", DEFAULT.announceDeaths(), warnings);
		Optional<GroupStats.Stat> sidebar = sidebar(root, warnings);
		double scareHearts = scareHearts(root, warnings);

		SoulLinkConfig config = new SoulLinkConfig(shareExperience, announceDeaths, sidebar, scareHearts);
		// Después de actualizar el mod puede faltar una opción nueva: se agrega para que el dueño vea que existe.
		// Con avisos no se reescribe: se perdería el valor mal escrito en vez de dejárselo para que lo corrija.
		boolean missing = !root.has("compartirExperiencia") || !root.has("anunciarMuertes") || !root.has("barraLateral") || !root.has("corazonesDeSusto");
		if (missing && warnings.isEmpty()) {
			write(file, config);
		}
		return new Reading(config, warnings);
	}

	public static void write(Path file, SoulLinkConfig config) {
		JsonObject root = new JsonObject();
		root.addProperty("compartirExperiencia", config.shareExperience());
		root.addProperty("anunciarMuertes", config.announceDeaths());
		root.addProperty("barraLateral", config.sidebar().map(GroupStats.Stat::id).orElse(NO_SIDEBAR));
		// 2 y no 2.0: es lo que escribiría una persona
		double hearts = config.scareHearts();
		root.addProperty("corazonesDeSusto", hearts == Math.rint(hearts) ? (Number) (long) hearts : (Number) hearts);
		try {
			Files.createDirectories(file.getParent());
			Files.writeString(file, GSON.toJson(root) + "\n", StandardCharsets.UTF_8);
		} catch (IOException e) {
			SoulLink.LOGGER.error("No se pudo escribir {}", file, e);
		}
	}

	private static boolean bool(JsonObject root, String field, boolean fallback, List<String> warnings) {
		JsonElement value = root.get(field);
		if (value == null) {
			return fallback;
		}
		if (!value.isJsonPrimitive() || !value.getAsJsonPrimitive().isBoolean()) {
			warnings.add(field + " tiene que ser true o false (dice " + value + "): se usa " + fallback);
			return fallback;
		}
		return value.getAsBoolean();
	}

	private static Optional<GroupStats.Stat> sidebar(JsonObject root, List<String> warnings) {
		JsonElement value = root.get("barraLateral");
		if (value == null) {
			return DEFAULT.sidebar();
		}
		String id = value.isJsonPrimitive() ? value.getAsString() : "";
		if (id.equals(NO_SIDEBAR)) {
			return Optional.empty();
		}
		Optional<GroupStats.Stat> stat = Arrays.stream(GroupStats.Stat.values()).filter(s -> s.id().equals(id)).findFirst();
		if (stat.isEmpty()) {
			List<String> ids = new ArrayList<>(Arrays.stream(GroupStats.Stat.values()).map(GroupStats.Stat::id).toList());
			ids.add(NO_SIDEBAR);
			warnings.add("barraLateral no conoce " + value + " (las que hay: " + String.join(", ", ids) + "): se usa "
					+ DEFAULT.sidebar().map(GroupStats.Stat::id).orElse(NO_SIDEBAR));
			return DEFAULT.sidebar();
		}
		return stat;
	}

	private static double scareHearts(JsonObject root, List<String> warnings) {
		JsonElement value = root.get("corazonesDeSusto");
		if (value == null) {
			return DEFAULT.scareHearts();
		}
		JsonPrimitive number = value.isJsonPrimitive() && value.getAsJsonPrimitive().isNumber() ? value.getAsJsonPrimitive() : null;
		// Más de 10 corazones contaría como susto cualquier golpe con la vida llena
		if (number == null || !Double.isFinite(number.getAsDouble()) || number.getAsDouble() < 0 || number.getAsDouble() > 10) {
			warnings.add("corazonesDeSusto tiene que ser un número de 0 a 10 (dice " + value + "): se usa " + DEFAULT.scareHearts());
			return DEFAULT.scareHearts();
		}
		return number.getAsDouble();
	}
}
