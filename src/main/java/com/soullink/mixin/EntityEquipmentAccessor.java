package com.soullink.mixin;

import java.util.EnumMap;

import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Mutable;
import org.spongepowered.asm.mixin.gen.Accessor;

import net.minecraft.world.entity.EntityEquipment;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.item.ItemStack;

@Mixin(EntityEquipment.class)
public interface EntityEquipmentAccessor {
	@Accessor("items")
	EnumMap<EquipmentSlot, ItemStack> soullink$getItems();

	@Mutable
	@Accessor("items")
	void soullink$setItems(EnumMap<EquipmentSlot, ItemStack> items);
}
