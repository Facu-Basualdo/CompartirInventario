package com.soullink;

import net.minecraft.world.item.ItemStack;

/** Lo implementa {@code ServerPlayerGameMode} vía {@code ServerPlayerGameModeMixin}. */
public interface BlockMiner {
	/** Si el jugador está picando un bloque con este mismo objeto {@code ItemStack} en la mano. */
	boolean soullink$isMiningWith(ItemStack stack);
}
