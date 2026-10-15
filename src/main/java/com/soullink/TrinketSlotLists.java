package com.soullink;

import java.util.ArrayList;
import java.util.List;

import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;

import net.minecraft.core.NonNullList;
import net.minecraft.world.ItemStackWithSlot;
import net.minecraft.world.item.ItemStack;

/**
 * Las listas de un slot de Trinkets que comparte un grupo (los ítems y los cosméticos). Sólo tipos de vanilla: el grupo
 * las guarda aunque Trinkets no esté instalado. Quién engancha cada jugador a estas listas: {@code trinkets.SharedTrinkets}.
 */
public record TrinketSlotLists(NonNullList<ItemStack> stacks, NonNullList<ItemStack> cosmetic) {
	static final Codec<TrinketSlotLists> CODEC = RecordCodecBuilder.create(instance -> instance.group(
			Codec.INT.optionalFieldOf("size", 1).forGetter(lists -> lists.stacks.size()),
			ItemStackWithSlot.CODEC.listOf().optionalFieldOf("items", List.of()).forGetter(lists -> withSlots(lists.stacks)),
			Codec.INT.optionalFieldOf("cosmeticSize", 0).forGetter(lists -> lists.cosmetic.size()),
			ItemStackWithSlot.CODEC.listOf().optionalFieldOf("cosmetic", List.of()).forGetter(lists -> withSlots(lists.cosmetic))
	).apply(instance, (size, items, cosmeticSize, cosmetic) -> new TrinketSlotLists(fill(size, items), fill(cosmeticSize, cosmetic))));

	private static NonNullList<ItemStack> fill(int size, List<ItemStackWithSlot> items) {
		NonNullList<ItemStack> list = NonNullList.withSize(Math.max(0, size), ItemStack.EMPTY);
		for (ItemStackWithSlot item : items) {
			if (item.isValidInContainer(list.size())) {
				list.set(item.slot(), item.stack());
			}
		}
		return list;
	}

	private static List<ItemStackWithSlot> withSlots(NonNullList<ItemStack> list) {
		List<ItemStackWithSlot> result = new ArrayList<>();
		for (int slot = 0; slot < list.size(); slot++) {
			if (!list.get(slot).isEmpty()) {
				result.add(new ItemStackWithSlot(slot, list.get(slot)));
			}
		}
		return result;
	}
}
