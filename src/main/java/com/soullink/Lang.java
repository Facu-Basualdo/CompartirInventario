package com.soullink;

import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.function.Function;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import org.jspecify.annotations.Nullable;

import net.minecraft.commands.CommandSourceStack;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.MutableComponent;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;

/**
 * Los textos del mod, en el idioma de cada jugador. Los traduce el servidor, no el cliente: un cliente vanilla no
 * tiene nuestros archivos de idioma, así que un {@code Component.translatable} le llegaría como la clave pelada.
 *
 * <p>El idioma sale de lo que el cliente informa al entrar ({@code clientInformation().language()}): cualquier
 * {@code es_*} usa {@code es_ar} y cualquier {@code en_*}, {@code en_us}. Lo que no tiene un jugador (la consola, los
 * títulos del scoreboard, que son iguales para todos) y los idiomas que no tenemos usan {@code idioma} de la config.
 *
 * <p>Los textos están en {@code assets/soullink/lang/<idioma>.json}, con {@code %s} o {@code %1$s} para los
 * argumentos. Un argumento puede ser un {@link Component} (un nombre de jugador con su color, una causa de muerte):
 * se agrega tal cual en vez de pasarlo a texto.
 */
public final class Lang {
	public static final String ENGLISH = "en_us";
	public static final String SPANISH = "es_ar";
	public static final List<String> AVAILABLE = List.of(ENGLISH, SPANISH);

	private static final Pattern PLACEHOLDER = Pattern.compile("%(?:(\\d+)\\$)?s");
	private static final Map<String, Map<String, String>> TEXTS = new HashMap<>();

	static {
		for (String language : AVAILABLE) {
			TEXTS.put(language, load(language));
		}
	}

	private Lang() {
	}

	/** El idioma con el que se le habla a este jugador, o el de la config si es null (la consola). */
	public static String of(@Nullable ServerPlayer player) {
		return player == null ? SoulLink.config().language() : resolve(player.clientInformation().language());
	}

	/** {@code es_mx} → {@code es_ar}, {@code en_gb} → {@code en_us}; uno que no tenemos → el de la config. */
	public static String resolve(String clientLanguage) {
		String language = clientLanguage.toLowerCase(java.util.Locale.ROOT);
		if (TEXTS.containsKey(language)) {
			return language;
		}
		if (language.startsWith("es")) {
			return SPANISH;
		}
		if (language.startsWith("en")) {
			return ENGLISH;
		}
		return SoulLink.config().language();
	}

	public static MutableComponent tr(@Nullable ServerPlayer player, String key, Object... args) {
		return tr(of(player), key, args);
	}

	public static MutableComponent tr(CommandSourceStack source, String key, Object... args) {
		return tr(of(source.getPlayer()), key, args);
	}

	/** Las claves de un idioma, para el test que verifica que los dos archivos tienen las mismas. */
	public static Set<String> keys(String language) {
		return Set.copyOf(TEXTS.getOrDefault(language, Map.of()).keySet());
	}

	/** El texto sin argumentos, para armar otros textos (por ejemplo, el nombre de un comando en ese idioma). */
	public static String raw(String language, String key) {
		String text = TEXTS.getOrDefault(language, Map.of()).get(key);
		if (text == null) {
			text = TEXTS.get(ENGLISH).get(key);
		}
		return text == null ? key : text;
	}

	public static MutableComponent tr(String language, String key, Object... args) {
		String template = raw(language, key);
		MutableComponent result = Component.empty();
		Matcher matcher = PLACEHOLDER.matcher(template);
		int last = 0;
		int next = 0;
		while (matcher.find()) {
			result.append(template.substring(last, matcher.start()));
			int index = matcher.group(1) != null ? Integer.parseInt(matcher.group(1)) - 1 : next++;
			Object arg = index < args.length ? args[index] : "";
			result.append(arg instanceof Component component ? component.copy() : Component.literal(String.valueOf(arg)));
			last = matcher.end();
		}
		result.append(template.substring(last));
		return result;
	}

	/** A cada conectado en su idioma, y a la consola en el de la config. */
	public static void broadcast(MinecraftServer server, Function<String, Component> message) {
		for (ServerPlayer player : server.getPlayerList().getPlayers()) {
			player.sendSystemMessage(message.apply(of(player)));
		}
		server.sendSystemMessage(message.apply(SoulLink.config().language()));
	}

	/** Medio corazón de precisión, con coma decimal en español: 17,5 y 17.5. */
	public static String number(String language, float value) {
		float rounded = Math.round(value * 2) / 2.0F;
		String text = rounded == (int) rounded ? Integer.toString((int) rounded) : Float.toString(rounded);
		return language.startsWith("es") ? text.replace('.', ',') : text;
	}

	private static Map<String, String> load(String language) {
		Map<String, String> texts = new HashMap<>();
		String path = "/assets/" + SoulLink.MOD_ID + "/lang/" + language + ".json";
		try (InputStream input = Lang.class.getResourceAsStream(path)) {
			if (input == null) {
				SoulLink.LOGGER.error("Falta {} en el jar", path);
				return texts;
			}
			JsonObject root = JsonParser.parseReader(new InputStreamReader(input, StandardCharsets.UTF_8)).getAsJsonObject();
			for (Map.Entry<String, JsonElement> entry : root.entrySet()) {
				texts.put(entry.getKey(), entry.getValue().getAsString());
			}
		} catch (IOException | RuntimeException e) {
			SoulLink.LOGGER.error("No se pudo leer {}", path, e);
		}
		return texts;
	}
}
