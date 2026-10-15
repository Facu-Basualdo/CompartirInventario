package com.soullink.mixin.trinkets;

import eu.pb4.trinkets.impl.TrinketInventoryImpl;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Accessor;

import net.minecraft.core.NonNullList;
import net.minecraft.world.item.ItemStack;

@Mixin(TrinketInventoryImpl.class)
public interface TrinketInventoryAccessor {
	@Accessor("stacks")
	NonNullList<ItemStack> soullink$getStacks();

	@Accessor("stacks")
	void soullink$setStacks(NonNullList<ItemStack> stacks);

	@Accessor("cosmeticStacks")
	NonNullList<ItemStack> soullink$getCosmeticStacks();

	@Accessor("cosmeticStacks")
	void soullink$setCosmeticStacks(NonNullList<ItemStack> stacks);
}
