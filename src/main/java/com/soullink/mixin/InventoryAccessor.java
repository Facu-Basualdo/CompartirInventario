package com.soullink.mixin;

import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Mutable;
import org.spongepowered.asm.mixin.gen.Accessor;

import net.minecraft.core.NonNullList;
import net.minecraft.world.entity.EntityEquipment;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.item.ItemStack;

/** Para pasar a un jugador conectado a las listas de otro grupo (ver LinkGroups.move). */
@Mixin(Inventory.class)
public interface InventoryAccessor {
	@Mutable
	@Accessor("items")
	void soullink$setItems(NonNullList<ItemStack> items);

	@Accessor("equipment")
	EntityEquipment soullink$getEquipment();
}
