package com.soullink.mixin;

import java.util.function.Supplier;

import com.soullink.BlockMiner;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

import net.minecraft.core.NonNullList;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.inventory.AbstractContainerMenu;
import net.minecraft.world.inventory.Slot;
import net.minecraft.world.item.ItemStack;

@Mixin(AbstractContainerMenu.class)
public abstract class AbstractContainerMenuMixin {
	@Shadow
	@Final
	public NonNullList<Slot> slots;

	/**
	 * El cliente reinicia el progreso de picar si el ítem de la mano deja de ser igual, componentes
	 * incluidos, al que tenía al empezar (MultiPlayerGameMode.sameDestroyTarget). Con la herramienta
	 * compartida, cuando otro jugador rompe un bloque con ella le baja la durabilidad, y el slot nuevo
	 * le borraba el progreso a quien seguía picando. Se le retiene el aviso hasta que termine o suelte:
	 * como no se marca como enviado, el próximo broadcastChanges lo vuelve a intentar.
	 */
	@Inject(method = "synchronizeSlotToRemote", at = @At("HEAD"), cancellable = true)
	private void soullink$holdToolWhileMining(int slotIndex, ItemStack stack, Supplier<ItemStack> copy, CallbackInfo ci) {
		Slot slot = this.slots.get(slotIndex);
		if (slot.container instanceof Inventory inventory
				&& slot.getContainerSlot() == inventory.getSelectedSlot()
				&& inventory.player instanceof ServerPlayer player
				&& ((BlockMiner) player.gameMode).soullink$isMiningWith(stack)) {
			ci.cancel();
		}
	}
}
