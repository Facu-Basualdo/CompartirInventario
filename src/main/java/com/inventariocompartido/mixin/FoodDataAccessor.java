package com.inventariocompartido.mixin;

import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Accessor;

import net.minecraft.world.food.FoodData;

@Mixin(FoodData.class)
public interface FoodDataAccessor {
	@Accessor("exhaustionLevel")
	float inventariocompartido$getExhaustionLevel();

	@Accessor("exhaustionLevel")
	void inventariocompartido$setExhaustionLevel(float exhaustionLevel);
}
