package com.soullink.mixin;

import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Accessor;

import net.minecraft.world.entity.player.Player;
import net.minecraft.world.food.FoodData;

/** Para pasar a un jugador conectado al hambre de otro grupo (ver LinkGroups.move). */
@Mixin(Player.class)
public interface PlayerAccessor {
	@Accessor("foodData")
	void soullink$setFoodData(FoodData foodData);
}
