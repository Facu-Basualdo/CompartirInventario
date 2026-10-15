package com.soullink.mixin.trinkets;

import com.soullink.trinkets.SharedTrinkets;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

import net.minecraft.server.level.ServerPlayer;

/**
 * Trinkets arma los slots en el constructor de LivingEntity, cuando el jugador todavía no tiene perfil y no se sabe de
 * qué grupo es: se enganchan al final del de ServerPlayer (login, respawn).
 */
@Mixin(ServerPlayer.class)
public abstract class TrinketsServerPlayerMixin {
	@Inject(method = "<init>", at = @At("TAIL"))
	private void soullink$shareTrinkets(CallbackInfo ci) {
		SharedTrinkets.bindAll((ServerPlayer) (Object) this);
	}
}
