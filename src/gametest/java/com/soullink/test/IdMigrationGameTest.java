package com.soullink.test;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.file.Files;
import java.nio.file.Path;

import com.soullink.IdMigration;

import net.fabricmc.fabric.api.gametest.v1.GameTest;

import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.network.chat.Component;

/** El paso de inventariocompartido a soullink, sobre una carpeta temporal: el mundo del test es de todos los tests. */
public class IdMigrationGameTest {
	@GameTest
	public void movesSharedStateToTheNewId(GameTestHelper helper) {
		try {
			Path data = Files.createTempDirectory("soullink");
			Path vieja = data.resolve("inventariocompartido").resolve("shared_inventory.dat");
			Path nueva = data.resolve("soullink").resolve("shared_inventory.dat");
			Files.createDirectories(vieja.getParent());
			Files.writeString(vieja, "el inventario de siempre");

			helper.assertTrue(IdMigration.moveIfMissing(vieja, nueva), Component.literal("Con el archivo viejo y sin el nuevo, lo mueve"));
			helper.assertValueEqual(Files.readString(nueva), "el inventario de siempre", "El contenido llega entero");
			helper.assertFalse(Files.exists(vieja), Component.literal("Y el viejo ya no está"));

			Files.writeString(vieja, "uno viejo que apareció después");
			helper.assertFalse(IdMigration.moveIfMissing(vieja, nueva), Component.literal("Si ya hay uno con el id nuevo, no lo pisa"));
			helper.assertValueEqual(Files.readString(nueva), "el inventario de siempre", "El nuevo queda como estaba");

			helper.assertFalse(IdMigration.moveIfMissing(data.resolve("no-existe.dat"), data.resolve("otro.dat")), Component.literal("Sin archivo viejo no hace nada"));
		} catch (IOException e) {
			throw new UncheckedIOException(e);
		}
		helper.succeed();
	}
}
