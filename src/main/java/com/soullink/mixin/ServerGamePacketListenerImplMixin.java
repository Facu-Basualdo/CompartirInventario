package com.soullink.mixin;

import com.soullink.SharedInventorySync;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

import net.minecraft.server.level.ServerPlayer;
import net.minecraft.server.network.ServerGamePacketListenerImpl;

@Mixin(ServerGamePacketListenerImpl.class)
public abstract class ServerGamePacketListenerImplMixin {
	@Shadow
	public ServerPlayer player;

	/**
	 * Paquetes que pueden cambiar el inventario de quien los manda. Sin esto, los demás clientes ven el
	 * cambio recién en su próximo tick (ver SharedInventorySync).
	 *
	 * <p>Cada handler se llama primero en el hilo de red y ahí corta con una excepción para reencolarse en
	 * el del servidor, así que RETURN sólo se alcanza en el hilo del servidor.
	 */
	@Inject(
			method = {
					"handleUseItemOn",
					"handleUseItem",
					"handlePlayerAction",
					"handleAttack",
					"handleInteract",
					"handleContainerClick",
					"handleContainerClose",
					"handleContainerButtonClick",
					"handlePlaceRecipe",
					"handleSelectTrade",
					"handleSetCreativeModeSlot",
					"handlePickItemFromBlock",
					"handlePickItemFromEntity",
					"handleBundleItemSelectedPacket"
			},
			at = @At("RETURN"),
			// Uno por handler: si un nombre cambia en otra versión, que falle al cargar en vez de dejar de sincronizar.
			require = 14
	)
	private void soullink$broadcastToOthers(CallbackInfo ci) {
		SharedInventorySync.broadcastToOthers(this.player);
	}
}
