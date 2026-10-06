package com.inventariocompartido.mixin;

import java.util.EnumMap;

import com.inventariocompartido.SharedInventoryState;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

import net.minecraft.world.entity.EntityEquipment;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.item.ItemStack;

@Mixin(EntityEquipment.class)
public abstract class EntityEquipmentMixin {
	@Shadow
	@Final
	private EnumMap<EquipmentSlot, ItemStack> items;

	// setAll() hace clear() + putAll(): al cargar los datos de un jugador borraría la armadura compartida.
	@Inject(method = "setAll", at = @At("HEAD"), cancellable = true)
	private void inventariocompartido$keepSharedEquipment(EntityEquipment other, CallbackInfo ci) {
		if (SharedInventoryState.shouldBlockLoadInto(this.items)) {
			ci.cancel();
		}
	}
}
