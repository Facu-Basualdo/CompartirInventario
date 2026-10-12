package com.soullink.test;

import java.util.HashSet;
import java.util.Set;

import com.soullink.Lang;

import net.fabricmc.fabric.api.gametest.v1.GameTest;

import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.network.chat.Component;

public class LangGameTest {
	// Sólo lee los archivos de idioma: no toca el estado del servidor.
	@GameTest
	public void textsInEveryLanguage(GameTestHelper helper) {
		// Una clave que falta en un idioma se vería en inglés (o como la clave pelada): mejor enterarse acá
		Set<String> english = Lang.keys(Lang.ENGLISH);
		Set<String> spanish = Lang.keys(Lang.SPANISH);
		Set<String> onlyEnglish = new HashSet<>(english);
		onlyEnglish.removeAll(spanish);
		Set<String> onlySpanish = new HashSet<>(spanish);
		onlySpanish.removeAll(english);
		helper.assertTrue(english.size() > 40, Component.literal("Se leyó el archivo en inglés (" + english.size() + " claves)"));
		helper.assertTrue(onlyEnglish.isEmpty() && onlySpanish.isEmpty(),
				Component.literal("Los dos idiomas tienen las mismas claves (sólo en inglés: " + onlyEnglish + ", sólo en español: " + onlySpanish + ")"));

		// Cualquier variante usa la que tenemos; un idioma que no tenemos, el de la config (en_us por defecto)
		helper.assertTrue(Lang.resolve("es_MX").equals(Lang.SPANISH) && Lang.resolve("es_es").equals(Lang.SPANISH), Component.literal("es_* usa es_ar"));
		helper.assertTrue(Lang.resolve("en_gb").equals(Lang.ENGLISH), Component.literal("en_* usa en_us"));
		helper.assertTrue(Lang.resolve("fr_fr").equals(Lang.ENGLISH), Component.literal("Un idioma que no hay usa el de la config"));

		// Los argumentos: en orden, por posición, y un Component se agrega con su estilo
		String text = Lang.tr(Lang.SPANISH, "soullink.group.moved.many", "Ana, Beto", "rojo").getString();
		helper.assertTrue(text.equals("Ana, Beto ahora están en el grupo rojo"), Component.literal("Reemplaza los %s en orden (" + text + ")"));
		String swapped = Lang.tr(Lang.ENGLISH, "soullink.swap.done", Component.literal("Caro")).getString();
		helper.assertTrue(swapped.equals("You swapped places with Caro"), Component.literal("Un Component entra como argumento (" + swapped + ")"));
		helper.assertTrue(Lang.number(Lang.SPANISH, 17.5F).equals("17,5") && Lang.number(Lang.ENGLISH, 17.5F).equals("17.5"),
				Component.literal("Coma decimal en español, punto en inglés"));
		helper.succeed();
	}
}
