package com.soullink.test;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Optional;

import com.soullink.GroupStats;
import com.soullink.SoulLinkConfig;

import net.fabricmc.fabric.api.gametest.v1.GameTest;

import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.network.chat.Component;

public class ConfigGameTest {
	// Sólo lee archivos: no toca lo compartido, así que puede correr en paralelo con el otro test.
	@GameTest
	public void configFallsBackPerField(GameTestHelper helper) throws IOException {
		Path directory = Files.createTempDirectory("soullink-config");
		Path file = directory.resolve("soullink.json");

		// Sin archivo: se crea con los valores por defecto
		SoulLinkConfig.Reading created = SoulLinkConfig.load(file);
		helper.assertTrue(Files.exists(file) && SoulLinkConfig.DEFAULT.equals(created.config()), Component.literal("Sin archivo se crea uno con los valores por defecto"));

		// Un valor mal escrito usa el de por defecto, avisa, y no pisa los demás
		Files.writeString(file, """
				{ "compartirExperiencia": false, "anunciarMuertes": "sí", "barraLateral": "mobs", "corazonesDeSusto": 50 }
				""", StandardCharsets.UTF_8);
		SoulLinkConfig.Reading mixed = SoulLinkConfig.load(file);
		SoulLinkConfig config = mixed.config();
		helper.assertTrue(config != null && !config.shareExperience(), Component.literal("Se respeta compartirExperiencia: false"));
		helper.assertTrue(config != null && config.announceDeaths(), Component.literal("anunciarMuertes inválido usa true"));
		helper.assertTrue(config != null && config.sidebar().equals(Optional.of(GroupStats.Stat.MOBS)), Component.literal("Se respeta barraLateral: mobs"));
		helper.assertTrue(config != null && config.scareHearts() == 2, Component.literal("corazonesDeSusto fuera de rango usa 2"));
		helper.assertTrue(mixed.warnings().size() == 2, Component.literal("Avisa de los dos valores mal escritos (" + mixed.warnings() + ")"));
		helper.assertTrue(Files.readString(file, StandardCharsets.UTF_8).contains("\"sí\""), Component.literal("Con avisos no se reescribe el archivo"));

		// "ninguna" apaga la barra lateral
		Files.writeString(file, "{ \"barraLateral\": \"ninguna\" }", StandardCharsets.UTF_8);
		SoulLinkConfig.Reading none = SoulLinkConfig.load(file);
		helper.assertTrue(none.config() != null && none.config().sidebar().isEmpty(), Component.literal("barraLateral: ninguna no muestra nada"));
		helper.assertTrue(Files.readString(file, StandardCharsets.UTF_8).contains("corazonesDeSusto"), Component.literal("Las opciones que faltan se agregan al archivo"));

		// JSON roto: no hay config, y quien llama se queda con la que tenía
		Files.writeString(file, "{ roto", StandardCharsets.UTF_8);
		helper.assertFalse(SoulLinkConfig.load(file).ok(), Component.literal("Un JSON roto no se usa"));
		helper.succeed();
	}
}
