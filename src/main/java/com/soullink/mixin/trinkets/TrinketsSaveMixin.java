package com.soullink.mixin.trinkets;

import com.soullink.SharedInventoryState;
import com.soullink.trinkets.SharedTrinkets;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.storage.ValueOutput;

/** Marca que los trinkets guardados en el archivo del jugador son una copia de los compartidos (ver SharedTrinkets.afterLoad). */
@Mixin(Player.class)
public abstract class TrinketsSaveMixin {
	@Inject(method = "addAdditionalSaveData", at = @At("TAIL"))
	private void soullink$markSharedTrinkets(ValueOutput output, CallbackInfo ci) {
		if (SharedInventoryState.forPlayer((Player) (Object) this) != null) {
			output.putBoolean(SharedTrinkets.SHARED_MARKER, true);
		}
	}
}
