package com.soullink.client;

import java.io.IOException;
import java.math.BigDecimal;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.concurrent.CompletableFuture;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParseException;
import com.google.gson.JsonParser;
import com.google.gson.JsonPrimitive;
import org.jspecify.annotations.Nullable;

import net.minecraft.ChatFormatting;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.components.CycleButton;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.client.gui.components.MultiLineTextWidget;
import net.minecraft.client.gui.components.ScrollableLayout;
import net.minecraft.client.gui.components.StringWidget;
import net.minecraft.client.gui.components.Tooltip;
import net.minecraft.client.gui.layouts.GridLayout;
import net.minecraft.client.gui.layouts.HeaderAndFooterLayout;
import net.minecraft.client.gui.layouts.LinearLayout;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.CommonComponents;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.MutableComponent;
import net.minecraft.server.MinecraftServer;
import net.minecraft.util.ARGB;

/**
 * Edita un archivo de config JSON sin conocer sus campos: una fila por valor (sí/no, una lista de opciones, o un
 * campo de texto para números y textos) y un título por cada objeto. Al guardar escribe el archivo y lo recarga
 * con lo mismo que usa el comando de recargar. Si el mod lo rechaza o avisa de un valor inválido, el archivo vuelve
 * a quedar como estaba: si no, el mod arrancaría la próxima vez con el valor por defecto sin que nadie lo note.
 *
 * <p>La misma clase está copiada en los cuatro mods del repo (Hero's Forge, Soul Link, Bloodmoon &amp; Bosses y Mythic
 * Loot), sin cambios salvo el paquete: lo propio de cada mod va en la subclase.
 */
public abstract class PantallaDeConfiguracion extends Screen {
	private static final Gson GSON = new GsonBuilder().setPrettyPrinting().disableHtmlEscaping().create();
	private static final int ANCHO_ETIQUETA = 200;
	private static final int ANCHO_CONTROL = 110;
	private static final int ANCHO_FILA = ANCHO_ETIQUETA + 8 + ANCHO_CONTROL;
	private static final int ALTO_FILA = 20;
	private static final int SANGRIA = 10;
	private static final int ROJO = ARGB.opaque(0xFF5555);

	private sealed interface Entrada permits Seccion, Valor {
		String ruta();

		String clave();

		int profundidad();
	}

	/** Un objeto del JSON: sólo el título, sus valores vienen después. */
	private record Seccion(String ruta, String clave, int profundidad) implements Entrada {
	}

	/** {@code objeto} es el que lo contiene: ahí se escribe el valor nuevo. */
	private record Valor(JsonObject objeto, String ruta, String clave, int profundidad) implements Entrada {
		@Nullable JsonPrimitive actual() {
			JsonElement valor = objeto.get(clave);
			return valor != null && valor.isJsonPrimitive() ? valor.getAsJsonPrimitive() : null;
		}
	}

	/** Null: al cerrar no se vuelve a ninguna (así la abren las pruebas). */
	private final @Nullable Screen anterior;
	private final Path archivo;
	/** Lo que se edita. Los sí/no y las opciones se cambian acá al tocarlos; los textos, recién al guardar. */
	private final @Nullable JsonObject raiz;
	private final @Nullable String errorDeLectura;
	private final List<Entrada> entradas = new ArrayList<>();
	/** Por ruta. Sobrevive a rearmar los widgets: si no, cambiar el tamaño de la ventana borraría lo escrito. */
	private final Map<String, String> textos = new HashMap<>();
	private final Map<String, String> textosOriginales = new HashMap<>();
	private HeaderAndFooterLayout marco = new HeaderAndFooterLayout(this);
	private @Nullable ScrollableLayout lista;
	private @Nullable Button botonGuardar;
	private List<Component> problemas = List.of();
	private boolean guardando;

	protected PantallaDeConfiguracion(Component titulo, @Nullable Screen anterior, Path archivo) {
		super(titulo);
		this.anterior = anterior;
		this.archivo = archivo;
		JsonObject leida = null;
		String error = null;
		try {
			JsonElement contenido = JsonParser.parseString(Files.readString(archivo, StandardCharsets.UTF_8));
			if (contenido.isJsonObject()) {
				leida = contenido.getAsJsonObject();
			} else {
				error = "no es un objeto JSON";
			}
		} catch (IOException | JsonParseException e) {
			error = String.valueOf(e.getMessage());
		}
		raiz = leida;
		errorDeLectura = error;
		if (raiz != null) {
			recorrer(raiz, "", 0);
		}
	}

	/** La traducción de la clave en el idioma del jugador, o null si el mod no la tiene. */
	protected abstract @Nullable MutableComponent traducir(String clave, Object... args);

	/** El comienzo de las claves de traducción de esta pantalla, por ejemplo {@code herosforge.config}. */
	protected abstract String prefijo();

	/**
	 * Vuelve a leer el archivo y lo aplica. Con un mundo abierto en esta instancia corre en el hilo de su servidor,
	 * y si no (el menú principal, o conectado a otro servidor), en el del cliente con {@code servidor} null.
	 *
	 * @return lo que el mod rechazó o reemplazó por el valor por defecto; vacía si quedó todo como se escribió
	 */
	protected abstract List<Component> recargar(@Nullable MinecraftServer servidor);

	/** Los valores posibles de un texto que no es libre (un idioma, una rareza); vacía si se escribe a mano. */
	protected List<String> opciones(String ruta) {
		return List.of();
	}

	/** Para un número que en el archivo está como entero pero acepta decimales. */
	protected boolean admiteDecimales(String ruta) {
		return false;
	}

	/** False para un archivo que es sólo del cliente: no hace falta pasar por el servidor ni avisar de él. */
	protected boolean esDelServidor() {
		return true;
	}

	/** Para sumar botones al pie, entre Guardar y Cancelar. */
	protected void agregarBotones(LinearLayout pie) {
	}

	protected Component nombre(String ruta, String clave) {
		Component traducido = traducirPrimera(prefijo() + ".campo." + ruta, prefijo() + ".campo." + clave);
		if (traducido != null) {
			return traducido;
		}
		// Las claves que son ids de mobs (minecraft:zombie) tienen su nombre en vanilla
		int dosPuntos = clave.indexOf(':');
		if (dosPuntos > 0) {
			return Component.translatable("entity." + clave.substring(0, dosPuntos) + "." + clave.substring(dosPuntos + 1));
		}
		return Component.literal(humanizar(clave));
	}

	private void recorrer(JsonObject objeto, String prefijo, int profundidad) {
		for (Map.Entry<String, JsonElement> entrada : objeto.entrySet()) {
			String ruta = prefijo + entrada.getKey();
			JsonElement valor = entrada.getValue();
			if (valor.isJsonObject()) {
				entradas.add(new Seccion(ruta, entrada.getKey(), profundidad));
				recorrer(valor.getAsJsonObject(), ruta + ".", profundidad + 1);
				continue;
			}
			entradas.add(new Valor(objeto, ruta, entrada.getKey(), profundidad));
			if (valor.isJsonPrimitive() && !valor.getAsJsonPrimitive().isBoolean()) {
				String texto = texto(valor.getAsJsonPrimitive());
				textos.put(ruta, texto);
				textosOriginales.put(ruta, texto);
			}
		}
	}

	@Override
	protected void init() {
		marco = new HeaderAndFooterLayout(this);
		marco.addTitleHeader(title, font);

		LinearLayout contenido = LinearLayout.vertical().spacing(6);
		for (Component aviso : avisos()) {
			contenido.addChild(new MultiLineTextWidget(aviso, font).setMaxWidth(ANCHO_FILA));
		}
		GridLayout grilla = new GridLayout().columnSpacing(8).rowSpacing(4);
		int fila = 0;
		for (Entrada entrada : entradas) {
			int sangria = entrada.profundidad() * SANGRIA;
			int arriba = fila > 0 && entrada instanceof Seccion ? 8 : 0;
			Component nombre = nombre(entrada.ruta(), entrada.clave());
			if (entrada instanceof Valor valor) {
				StringWidget etiqueta = new StringWidget(ANCHO_ETIQUETA - sangria, ALTO_FILA, nombre, font);
				etiqueta.setTooltip(ayuda(valor));
				grilla.addChild(etiqueta, fila, 0, celda -> celda.paddingLeft(sangria));
				grilla.addChild(control(valor, nombre), fila, 1);
			} else {
				MutableComponent titulo = nombre.copy().withStyle(ChatFormatting.GOLD, ChatFormatting.BOLD);
				grilla.addChild(new StringWidget(ANCHO_FILA - sangria, 12, titulo, font), fila, 0, 1, 2, celda -> celda.paddingLeft(sangria).paddingTop(arriba));
			}
			fila++;
		}
		contenido.addChild(grilla);
		lista = marco.addToContents(new ScrollableLayout(minecraft, contenido, marco.getContentHeight()));

		LinearLayout pie = marco.addToFooter(LinearLayout.horizontal().spacing(8));
		botonGuardar = pie.addChild(Button.builder(tr("guardar"), boton -> guardar()).width(100).build());
		agregarBotones(pie);
		pie.addChild(Button.builder(CommonComponents.GUI_CANCEL, boton -> onClose()).width(100).build());

		marco.visitWidgets(this::addRenderableWidget);
		repositionElements();
		actualizarGuardar();
	}

	@Override
	protected void repositionElements() {
		if (lista == null) {
			return;
		}
		lista.arrangeElements();
		lista.setMaxHeight(marco.getContentHeight());
		marco.arrangeElements();
		lista.setPosition(width / 2 - lista.getWidth() / 2, marco.getHeaderHeight());
	}

	/** Lo que va arriba de la lista: por qué no se puede editar, o por qué no se guardó. */
	private List<Component> avisos() {
		List<Component> avisos = new ArrayList<>();
		if (errorDeLectura != null) {
			avisos.add(tr("no_se_leyo", archivo.getFileName(), errorDeLectura).withStyle(ChatFormatting.RED));
		}
		// El archivo que se edita es el de esta instalación: el servidor tiene el suyo
		if (esDelServidor() && minecraft.getConnection() != null && minecraft.getSingleplayerServer() == null) {
			avisos.add(tr("servidor_remoto", archivo.getFileName()).withStyle(ChatFormatting.YELLOW));
		}
		if (!problemas.isEmpty()) {
			avisos.add(tr("no_se_guardo").withStyle(ChatFormatting.RED));
			for (Component problema : problemas) {
				avisos.add(Component.literal("• ").append(problema).withStyle(ChatFormatting.RED));
			}
		}
		return avisos;
	}

	private net.minecraft.client.gui.components.AbstractWidget control(Valor valor, Component nombre) {
		JsonPrimitive actual = valor.actual();
		if (actual == null) {
			// Una lista o un null: no hay ninguno en las configs de hoy, pero se respeta lo que haya en el archivo
			StringWidget soloLectura = new StringWidget(ANCHO_CONTROL, ALTO_FILA, tr("en_el_archivo").withStyle(ChatFormatting.GRAY), font);
			soloLectura.setTooltip(Tooltip.create(Component.literal(String.valueOf(valor.objeto().get(valor.clave())))));
			return soloLectura;
		}
		if (actual.isBoolean()) {
			return CycleButton.onOffBuilder(actual.getAsBoolean())
					.displayOnlyValue()
					.create(0, 0, ANCHO_CONTROL, ALTO_FILA, nombre, (boton, nuevo) -> valor.objeto().addProperty(valor.clave(), nuevo));
		}
		List<String> opciones = new ArrayList<>(opciones(valor.ruta()));
		if (!opciones.isEmpty() && actual.isString()) {
			// Uno que no está en la lista (mal escrito a mano) se conserva hasta que se elija otro
			if (!opciones.contains(actual.getAsString())) {
				opciones.add(actual.getAsString());
			}
			return CycleButton.builder(this::nombreDeOpcion, actual.getAsString())
					.withValues(opciones)
					.displayOnlyValue()
					.create(0, 0, ANCHO_CONTROL, ALTO_FILA, nombre, (boton, nuevo) -> valor.objeto().addProperty(valor.clave(), nuevo));
		}
		EditBox caja = new EditBox(font, 0, 0, ANCHO_CONTROL, ALTO_FILA, nombre);
		caja.setMaxLength(256);
		caja.setValue(textos.getOrDefault(valor.ruta(), ""));
		caja.moveCursorToStart(false);
		pintar(caja, valor, actual);
		caja.setResponder(texto -> {
			textos.put(valor.ruta(), texto);
			pintar(caja, valor, actual);
			actualizarGuardar();
		});
		return caja;
	}

	private void pintar(EditBox caja, Valor valor, JsonPrimitive original) {
		boolean valido = interpretar(valor, original, textos.getOrDefault(valor.ruta(), "")) != null;
		caja.setTextColor(valido ? EditBox.DEFAULT_TEXT_COLOR : ROJO);
	}

	private Tooltip ayuda(Valor valor) {
		MutableComponent texto = Component.empty();
		Component ayuda = traducirPrimera(prefijo() + ".ayuda." + valor.ruta(), prefijo() + ".ayuda." + valor.clave());
		if (ayuda != null) {
			texto.append(ayuda).append("\n");
		}
		// En la etiqueta y no en el campo: el campo toma el foco al abrir la pantalla y su ayuda taparía la fila de abajo
		JsonPrimitive actual = valor.actual();
		if (actual != null && actual.isNumber()) {
			texto.append(tr(entero(valor, actual) ? "entero" : "decimal").withStyle(ChatFormatting.GRAY)).append("\n");
		}
		// La clave del archivo, para encontrarla en el README o editarla a mano
		return Tooltip.create(texto.append(Component.literal(valor.ruta()).withStyle(ChatFormatting.DARK_GRAY)));
	}

	private Component nombreDeOpcion(String opcion) {
		Component traducido = traducir(prefijo() + ".valor." + (opcion.isEmpty() ? "ninguno" : opcion));
		return traducido != null ? traducido : Component.literal(opcion.isEmpty() ? "—" : opcion);
	}

	private void actualizarGuardar() {
		if (botonGuardar == null) {
			return;
		}
		boolean todoValido = true;
		for (Entrada entrada : entradas) {
			if (entrada instanceof Valor valor && esTexto(valor) && interpretar(valor, valor.actual(), textos.get(valor.ruta())) == null) {
				todoValido = false;
				break;
			}
		}
		botonGuardar.active = raiz != null && !guardando && todoValido;
	}

	/** Si se edita con un campo de texto (y no con un botón de sí/no o de opciones). */
	private boolean esTexto(Valor valor) {
		JsonPrimitive actual = valor.actual();
		return actual != null && !actual.isBoolean() && (actual.isNumber() || opciones(valor.ruta()).isEmpty());
	}

	private boolean entero(Valor valor, JsonPrimitive original) {
		String texto = original.getAsString();
		return !admiteDecimales(valor.ruta()) && texto.indexOf('.') < 0 && texto.indexOf('e') < 0 && texto.indexOf('E') < 0;
	}

	/** El valor que va al archivo, o null si el texto no sirve para ese campo. */
	private @Nullable JsonPrimitive interpretar(Valor valor, @Nullable JsonPrimitive original, @Nullable String texto) {
		if (original == null || texto == null) {
			return null;
		}
		if (!original.isNumber()) {
			return new JsonPrimitive(texto);
		}
		BigDecimal numero;
		try {
			// Con coma también: es como se escriben los decimales en español
			numero = new BigDecimal(texto.trim().replace(',', '.'));
		} catch (NumberFormatException e) {
			return null;
		}
		if (entero(valor, original)) {
			try {
				return new JsonPrimitive(numero.longValueExact());
			} catch (ArithmeticException e) {
				return null;
			}
		}
		// 4.0 y no 4: escrito sin decimales, la próxima vez que se abra la pantalla se tomaría como entero
		return new JsonPrimitive(numero.scale() > 0 ? numero : numero.setScale(1));
	}

	private void guardar() {
		if (raiz == null || guardando) {
			return;
		}
		for (Entrada entrada : entradas) {
			if (!(entrada instanceof Valor valor) || !esTexto(valor)) {
				continue;
			}
			String texto = textos.get(valor.ruta());
			// Lo que no se tocó queda como estaba, con el formato del archivo (Gson escribe 8.0E-4, no 0.0008)
			if (texto == null || texto.equals(textosOriginales.get(valor.ruta()))) {
				continue;
			}
			JsonPrimitive nuevo = interpretar(valor, valor.actual(), texto);
			if (nuevo == null) {
				return;
			}
			valor.objeto().add(valor.clave(), nuevo);
			textosOriginales.put(valor.ruta(), texto);
		}
		String contenido = GSON.toJson(raiz) + "\n";
		guardando = true;
		actualizarGuardar();
		MinecraftServer servidor = esDelServidor() ? minecraft.getSingleplayerServer() : null;
		CompletableFuture<List<Component>> tarea = servidor != null
				? servidor.submit(() -> aplicar(contenido, servidor))
				: CompletableFuture.completedFuture(aplicar(contenido, null));
		tarea.whenCompleteAsync((resultado, error) -> terminar(error == null ? resultado : List.of(Component.literal(String.valueOf(error.getMessage())))), minecraft);
	}

	private List<Component> aplicar(String contenido, @Nullable MinecraftServer servidor) {
		String antes = null;
		try {
			antes = Files.readString(archivo, StandardCharsets.UTF_8);
		} catch (IOException e) {
			// Lo borraron con la pantalla abierta: se crea de nuevo
		}
		try {
			Files.createDirectories(archivo.getParent());
			Files.writeString(archivo, contenido, StandardCharsets.UTF_8);
		} catch (IOException e) {
			return List.of(tr("no_se_escribio", archivo.getFileName(), String.valueOf(e.getMessage())));
		}
		List<Component> rechazado = recargar(servidor);
		if (!rechazado.isEmpty() && antes != null) {
			try {
				Files.writeString(archivo, antes, StandardCharsets.UTF_8);
			} catch (IOException e) {
				return List.of(tr("no_se_escribio", archivo.getFileName(), String.valueOf(e.getMessage())));
			}
			recargar(servidor);
		}
		return rechazado;
	}

	private void terminar(List<Component> rechazado) {
		guardando = false;
		// Si se cerró mientras el servidor recargaba, no hay que volver a abrir nada
		if (minecraft.gui.screen() != this) {
			return;
		}
		if (rechazado.isEmpty()) {
			minecraft.gui.setScreen(anterior);
			return;
		}
		problemas = rechazado;
		rebuildWidgets();
	}

	@Override
	public void onClose() {
		minecraft.gui.setScreen(anterior);
	}

	private MutableComponent tr(String clave, Object... args) {
		MutableComponent traducido = traducir(prefijo() + ".pantalla." + clave, args);
		return traducido != null ? traducido : Component.literal(clave);
	}

	private @Nullable Component traducirPrimera(String... claves) {
		for (String clave : claves) {
			Component traducido = traducir(clave);
			if (traducido != null) {
				return traducido;
			}
		}
		return null;
	}

	private static String texto(JsonPrimitive valor) {
		String texto = valor.getAsString();
		// Gson escribe los decimales chicos como 8.0E-4: en la pantalla va 0.0008
		if (valor.isNumber() && (texto.indexOf('E') >= 0 || texto.indexOf('e') >= 0)) {
			return new BigDecimal(texto).stripTrailingZeros().toPlainString();
		}
		return texto;
	}

	/** {@code bloquesPorNivel} → "Bloques por nivel", {@code velocidad_ataque} → "Velocidad ataque". */
	static String humanizar(String clave) {
		StringBuilder texto = new StringBuilder();
		for (int i = 0; i < clave.length(); i++) {
			char letra = clave.charAt(i);
			if (letra == '_' || letra == '-') {
				texto.append(' ');
			} else if (Character.isUpperCase(letra) && i > 0) {
				texto.append(' ').append(Character.toLowerCase(letra));
			} else {
				texto.append(letra);
			}
		}
		return texto.isEmpty() ? clave : texto.substring(0, 1).toUpperCase(Locale.ROOT) + texto.substring(1);
	}
}
