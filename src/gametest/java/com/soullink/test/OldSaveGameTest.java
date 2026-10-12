package com.soullink.test;

import com.mojang.serialization.DynamicOps;

import com.soullink.GroupStats;
import com.soullink.LinkGroup;
import com.soullink.SharedInventoryState;

import net.fabricmc.fabric.api.gametest.v1.GameTest;

import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.NbtOps;
import net.minecraft.nbt.Tag;
import net.minecraft.network.chat.Component;
import net.minecraft.world.item.Items;

public class OldSaveGameTest {
	// Sólo decodifica: no toca el estado del servidor, así que puede correr junto al test grande.
	@GameTest
	public void readsSaveFromBeforeGroups(GameTestHelper helper) {
		DynamicOps<Tag> ops = helper.getLevel().registryAccess().createSerializationContext(NbtOps.INSTANCE);

		// shared_inventory.dat de la 2.1.0: un solo grupo, con sus campos en la raíz
		CompoundTag diamond = new CompoundTag();
		diamond.putInt("Slot", 4);
		diamond.putString("id", "minecraft:diamond");
		diamond.putInt("count", 3);
		ListTag items = new ListTag();
		items.add(diamond);
		CompoundTag inventory = new CompoundTag();
		inventory.put("items", items);
		inventory.putFloat("health", 12.5F);
		inventory.putDouble("experience", 40);
		inventory.putBoolean("initialized", true);

		SharedInventoryState state = SharedInventoryState.CODEC.parse(ops, inventory).getOrThrow();
		LinkGroup general = state.group(SharedInventoryState.GENERAL);
		helper.assertTrue(general.items().get(4).is(Items.DIAMOND) && general.items().get(4).getCount() == 3, Component.literal("Los ítems de la 2.1.0 quedan en el grupo general"));
		helper.assertTrue(general.health() == 12.5F && general.experiencePoints() == 40, Component.literal("La vida y la experiencia de la 2.1.0 quedan en el grupo general"));
		helper.assertTrue(state.isInitialized() && state.groups().size() == 1, Component.literal("No aparece ningún otro grupo"));

		// stats.dat de la 2.1.0: el intento en la raíz
		CompoundTag stats = new CompoundTag();
		stats.putInt("attempt", 5);
		stats.putLong("bestTicks", 1200);
		GroupStats read = GroupStats.CODEC.parse(ops, stats).getOrThrow();
		helper.assertTrue(read.run(SharedInventoryState.GENERAL).attempt() == 5 && read.run(SharedInventoryState.GENERAL).bestTicks() == 1200,
				Component.literal("Los intentos de la 2.1.0 quedan en el grupo general"));

		// Y lo que se escribe ahora se sigue leyendo igual: el general en la raíz, como antes
		Tag written = SharedInventoryState.CODEC.encodeStart(ops, state).getOrThrow();
		helper.assertTrue(written instanceof CompoundTag tag && tag.contains("items") && tag.getFloatOr("health", 0) == 12.5F,
				Component.literal("El grupo general se sigue guardando en la raíz (una versión anterior lo puede leer)"));
		helper.succeed();
	}
}
