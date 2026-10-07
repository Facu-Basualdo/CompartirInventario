package com.inventariocompartido.mixin;

import java.util.ArrayList;
import java.util.List;

import com.inventariocompartido.SharedInventoryState;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

import net.minecraft.world.ItemStackWithSlot;
import net.minecraft.world.entity.EntityEquipment;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.food.FoodData;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.storage.ValueInput;
import net.minecraft.world.level.storage.ValueOutput;

@Mixin(Player.class)
public abstract class PlayerMixin {
	/**
	 * Marca que el inventario guardado en el archivo del jugador es una copia del compartido. Un archivo
	 * sin la marca es de antes de instalar el mod: ese inventario es del jugador y no se puede tirar.
	 */
	@Unique
	private static final String SHARED_MARKER = "InventarioCompartido";

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

	@Inject(method = "addAdditionalSaveData", at = @At("TAIL"))
	private void inventariocompartido$markSharedSave(ValueOutput output, CallbackInfo ci) {
		if (SharedInventoryState.forPlayer((Player) (Object) this) != null) {
			output.putBoolean(SHARED_MARKER, true);
		}
	}

	/**
	 * Corre antes que la carga de vanilla, que va a descartar el inventario guardado porque ya hay uno
	 * compartido (ver InventoryMixin y EntityEquipmentMixin). Si el archivo es de antes del mod, se
	 * rescatan esos ítems para dejarlos en un cofre cuando el jugador termine de entrar.
	 */
	@Inject(method = "readAdditionalSaveData", at = @At("HEAD"))
	private void inventariocompartido$rescuePersonalItems(ValueInput input, CallbackInfo ci) {
		Player self = (Player) (Object) this;
		SharedInventoryState state = SharedInventoryState.forPlayer(self);
		if (state == null || !state.isInitialized() || input.getBooleanOr(SHARED_MARKER, false)) {
			return;
		}

		List<ItemStack> items = new ArrayList<>();
		for (ItemStackWithSlot item : input.listOrEmpty("Inventory", ItemStackWithSlot.CODEC)) {
			if (!item.stack().isEmpty()) {
				items.add(item.stack());
			}
		}
		input.read("equipment", EntityEquipment.CODEC).ifPresent(equipment -> {
			for (ItemStack stack : ((EntityEquipmentAccessor) equipment).inventariocompartido$getItems().values()) {
				if (!stack.isEmpty()) {
					items.add(stack);
				}
			}
		});
		if (!items.isEmpty()) {
			state.addRescuedItems(self.getGameProfile().id(), items);
		}
	}
}
