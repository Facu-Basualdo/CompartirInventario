package com.soullink.mixin;

import com.soullink.LinkGroup;
import com.soullink.SharedInventoryState;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Mutable;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

import net.minecraft.core.NonNullList;
import net.minecraft.world.ItemStackWithSlot;
import net.minecraft.world.entity.EntityEquipment;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.storage.ValueInput;

@Mixin(Inventory.class)
public abstract class InventoryMixin {
	@Shadow
	@Final
	@Mutable
	private NonNullList<ItemStack> items;

	/**
	 * Cada vez que el servidor crea un jugador (al entrar, al reaparecer tras morir, etc.) su inventario
	 * pasa a usar las listas de su grupo: inventario + barra rápida (items) y armadura + mano secundaria
	 * (equipment). El slot seleccionado y el ítem del cursor siguen siendo de cada jugador. Player asigna
	 * gameProfile antes de crear el Inventory, así que acá ya se sabe de qué grupo es.
	 */
	@Inject(method = "<init>", at = @At("TAIL"))
	private void soullink$useSharedStorage(Player player, EntityEquipment equipment, CallbackInfo ci) {
		LinkGroup group = SharedInventoryState.groupFor(player);
		if (group == null) {
			return;
		}
		this.items = group.items();
		((EntityEquipmentAccessor) equipment).soullink$setItems(group.equipmentItems());
	}

	// load() vacía la lista antes de rellenarla con lo que el jugador tenía guardado.
	@Inject(method = "load", at = @At("HEAD"), cancellable = true)
	private void soullink$keepSharedItems(ValueInput.TypedInputList<ItemStackWithSlot> input, CallbackInfo ci) {
		if (SharedInventoryState.shouldBlockLoadInto(this.items)) {
			ci.cancel();
		}
	}
}
