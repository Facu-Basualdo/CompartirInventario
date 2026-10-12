package com.soullink;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.file.Files;
import java.nio.file.Path;

import net.minecraft.server.MinecraftServer;
import net.minecraft.world.level.storage.LevelResource;

/**
 * Hasta la 1.2.1 el id del mod era {@code inventariocompartido}, y el estado compartido se guardaba en
 * {@code world/data/inventariocompartido/shared_inventory.dat}. Con el id nuevo, {@link SharedInventoryState} lo busca
 * en {@code world/data/soullink/}: sin mover el archivo, un mundo existente arrancaría con el inventario vacío.
 */
public final class IdMigration {
	static final String OLD_ID = "inventariocompartido";
	private static final String FILE = "shared_inventory.dat";

	private IdMigration() {
	}

	/** SERVER_STARTING: corre antes de que se carguen los mundos, así que nadie leyó todavía el archivo. */
	public static void migrateWorldData(MinecraftServer server) {
		Path data = server.getWorldPath(LevelResource.DATA);
		if (moveIfMissing(data.resolve(OLD_ID).resolve(FILE), data.resolve(SoulLink.MOD_ID).resolve(FILE))) {
			SoulLink.LOGGER.info("Se movió {} de data/{} a data/{} (el id del mod cambió)", FILE, OLD_ID, SoulLink.MOD_ID);
		}
	}

	/**
	 * Mueve el archivo sólo si el nuevo no existe: si ya hay uno con el id nuevo, es el que vale y el viejo queda
	 * como estaba. Devuelve si movió.
	 */
	public static boolean moveIfMissing(Path oldFile, Path newFile) {
		if (!Files.exists(oldFile) || Files.exists(newFile)) {
			return false;
		}
		try {
			Files.createDirectories(newFile.getParent());
			Files.move(oldFile, newFile);
			return true;
		} catch (IOException e) {
			// Mejor no arrancar que arrancar con el estado vacío y pisar el de verdad al guardar
			throw new UncheckedIOException("No se pudo mover " + oldFile + " a " + newFile, e);
		}
	}
}
