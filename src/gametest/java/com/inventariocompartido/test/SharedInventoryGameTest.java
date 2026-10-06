package com.inventariocompartido.test;

import net.fabricmc.fabric.api.gametest.v1.GameTest;

import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.server.players.PlayerList;
import net.minecraft.util.ProblemReporter;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.GameType;
import net.minecraft.world.level.storage.TagValueInput;
import net.minecraft.world.level.storage.TagValueOutput;

public class SharedInventoryGameTest {
	// Un solo test: el inventario compartido es global del servidor, así que tests en paralelo se pisarían.
	@GameTest
	public void playersShareOneInventory(GameTestHelper helper) {
		PlayerList playerList = helper.getLevel().getServer().getPlayerList();
		ServerPlayer a = helper.makeMockServerPlayerInLevel();
		ServerPlayer b = helper.makeMockServerPlayerInLevel();
		a.getInventory().clearContent();

		// Inventario principal en ambos sentidos
		a.getInventory().add(new ItemStack(Items.DIAMOND, 5));
		assertCount(helper, b, Items.DIAMOND, 5, "B ve los diamantes que agarró A");
		b.getInventory().removeItem(b.getInventory().findSlotMatchingItem(new ItemStack(Items.DIAMOND)), 2);
		assertCount(helper, a, Items.DIAMOND, 3, "A ve que B usó 2 diamantes");

		// Armadura y mano secundaria
		a.setItemSlot(EquipmentSlot.HEAD, new ItemStack(Items.IRON_HELMET));
		helper.assertTrue(b.getItemBySlot(EquipmentSlot.HEAD).is(Items.IRON_HELMET), Component.literal("B tiene puesto el casco que se puso A"));
		b.setItemSlot(EquipmentSlot.OFFHAND, new ItemStack(Items.SHIELD));
		helper.assertTrue(a.getOffhandItem().is(Items.SHIELD), Component.literal("A tiene el escudo que B puso en la mano secundaria"));

		// Cargar los datos guardados (viejos) de un jugador no pisa el inventario compartido
		TagValueOutput output = TagValueOutput.createWithContext(ProblemReporter.DISCARDING, a.registryAccess());
		a.saveWithoutId(output);
		CompoundTag oldSave = output.buildResult();
		a.getInventory().add(new ItemStack(Items.EMERALD, 7));
		b.load(TagValueInput.create(ProblemReporter.DISCARDING, b.registryAccess(), oldSave));
		assertCount(helper, a, Items.EMERALD, 7, "Cargar datos viejos de B no borra las esmeraldas nuevas");
		helper.assertTrue(a.getItemBySlot(EquipmentSlot.HEAD).is(Items.IRON_HELMET), Component.literal("Cargar datos viejos no borra la armadura compartida"));

		// Al recrear al jugador (respawn) sigue usando el inventario compartido
		ServerPlayer a2 = playerList.respawn(a, true, Entity.RemovalReason.CHANGED_DIMENSION);
		assertCount(helper, a2, Items.EMERALD, 7, "El jugador recreado conserva el inventario compartido");
		a2.getInventory().add(new ItemStack(Items.GOLD_INGOT, 4));
		assertCount(helper, b, Items.GOLD_INGOT, 4, "B ve lo que agarra el jugador recreado");

		// Si uno muere, se cae todo el inventario compartido una sola vez
		// Los jugadores de prueba son invulnerables (no tienen cliente real), así que se llama a die() directo
		a2.setGameMode(GameType.SURVIVAL);
		a2.die(a2.damageSources().genericKill());
		helper.assertTrue(b.getInventory().isEmpty(), Component.literal("Tras la muerte de A, el inventario compartido queda vacío"));

		playerList.remove(a2);
		playerList.remove(b);
		helper.succeed();
	}

	private static void assertCount(GameTestHelper helper, Player player, Item item, int expected, String message) {
		int actual = player.getInventory().countItem(item);
		helper.assertTrue(actual == expected, Component.literal(message + " (esperado " + expected + ", hay " + actual + ")"));
	}
}
