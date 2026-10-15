package com.soullink.mixin.trinkets;

import com.soullink.trinkets.SharedTrinkets;
import org.jspecify.annotations.Nullable;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

import net.minecraft.world.entity.Entity;
import net.minecraft.world.level.storage.ValueInput;

/**
 * Como InventoryMixin con el inventario: cargar el archivo de un jugador no pisa los slots de Trinkets compartidos.
 * Envuelve toda la carga porque Trinkets lee (o vacía) los slots dentro de readAdditionalSaveData.
 */
@Mixin(Entity.class)
public abstract class TrinketsLoadMixin {
	@Unique
	private SharedTrinkets.@Nullable LoadSnapshot soullink$trinketsBeforeLoad;

	@Inject(method = "load", at = @At("HEAD"))
	private void soullink$rememberSharedTrinkets(ValueInput input, CallbackInfo ci) {
		soullink$trinketsBeforeLoad = SharedTrinkets.beforeLoad((Entity) (Object) this, input);
	}

	@Inject(method = "load", at = @At("RETURN"))
	private void soullink$restoreSharedTrinkets(ValueInput input, CallbackInfo ci) {
		SharedTrinkets.LoadSnapshot snapshot = soullink$trinketsBeforeLoad;
		soullink$trinketsBeforeLoad = null;
		if (snapshot != null) {
			SharedTrinkets.afterLoad(snapshot);
		}
	}
}
