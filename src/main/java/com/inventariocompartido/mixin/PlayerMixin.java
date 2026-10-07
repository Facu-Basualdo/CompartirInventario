package com.inventariocompartido.mixin;

import com.inventariocompartido.SharedInventoryState;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

import net.minecraft.world.entity.player.Player;
import net.minecraft.world.food.FoodData;

@Mixin(Player.class)
public abstract class PlayerMixin {
	@Shadow
	protected FoodData foodData;

	// Igual que el inventario: cada ServerPlayer que se crea (login, respawn) usa el mismo FoodData.
	@Inject(method = "<init>", at = @At("TAIL"))
	private void inventariocompartido$useSharedFood(CallbackInfo ci) {
		SharedInventoryState state = SharedInventoryState.forPlayer((Player) (Object) this);
		if (state != null) {
			this.foodData = state.foodData();
		}
	}
}
